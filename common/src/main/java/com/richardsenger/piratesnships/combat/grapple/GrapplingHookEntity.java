package com.richardsenger.piratesnships.combat.grapple;

import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

/**
 * A thrown grappling hook on its rope (docs/design.md §8.3, §8.4, G11). Server-authoritative: the state, the ship it
 * hangs on and the rope's ends live here on the server; the client only renders from the synched data.
 *
 * <ul>
 *   <li><b>Flying</b>: a thrown projectile with {@code grapple.gravity}. It stops and drops when the rope runs out
 *       ({@code max_rope_length} from the thrower) or when it falls into water.</li>
 *   <li><b>Latched</b>: it hit a block of a ship that is not the thrower's own. It hangs at that plot position (Sable
 *       returns ship hits in plot coordinates, docs/sable-notes.md §9.0e) and follows the ship; the entity itself stays
 *       in world space at the plot position's world point. While latched {@link GrappleService} hauls the ships
 *       together every physics substep.</li>
 *   <li><b>Retracting</b>: it missed (land, water, an entity, the thrower's own ship): it lies there for
 *       {@code retract_ticks}, then the rope pulls it back to the thrower.</li>
 * </ul>
 *
 * The thrown item travels with the hook ({@link #getItem()}) and goes back to the thrower when the hook is released,
 * unless a snapped rope loses it ({@code rope_breaks_lose_hook}). Hooks are not saved with the chunk: a logout
 * releases them first ({@link GrappleService#onLogout}).
 */
public class GrapplingHookEntity extends ThrowableItemProjectile {

    public enum State { FLYING, LATCHED, RETRACTING }

    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(GrapplingHookEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> TAUT = SynchedEntityData.defineId(GrapplingHookEntity.class, EntityDataSerializers.BOOLEAN);
    /** Plot block the hook hangs on (plot coordinates are in the millions, so the integer part is sent exactly). */
    private static final EntityDataAccessor<BlockPos> PLOT_BLOCK = SynchedEntityData.defineId(GrapplingHookEntity.class, EntityDataSerializers.BLOCK_POS);
    /** Hook point relative to {@link #PLOT_BLOCK}. */
    private static final EntityDataAccessor<Vector3f> PLOT_OFFSET = SynchedEntityData.defineId(GrapplingHookEntity.class, EntityDataSerializers.VECTOR3);

    /** A flying hook that has not hit anything after this many ticks drops. */
    private static final int MAX_FLIGHT_TICKS = 200;
    /** Ticks between searches for the anchor block on the thrower's ship. */
    private static final int ANCHOR_REFRESH_TICKS = 10;

    // server state
    private boolean consumed;
    private boolean finished;
    private int retractTicks;
    private @Nullable UUID shipId;
    private @Nullable Vec3 plotPos;
    private @Nullable BlockPos latchedBlock;
    private @Nullable Vec3 pendingPos;

    // rope ends for the physics substep, refreshed every game tick while latched
    private @Nullable UUID throwerShipId;
    private @Nullable Vec3 anchorPlot;
    private @Nullable Vec3 throwerPos;
    private boolean throwerAboardTarget;
    private int anchorRefreshAt;

    public GrapplingHookEntity(EntityType<? extends GrapplingHookEntity> type, Level level) {
        super(type, level);
    }

    /** A hook thrown by {@code thrower}, starting at its eyes (aim it with {@code shootFromRotation}). */
    public GrapplingHookEntity(Level level, LivingEntity thrower, ItemStack hook, boolean consumed) {
        super(GrappleContent.HOOK.get(), thrower, level);
        setItem(hook);
        this.consumed = consumed;
    }

    /** A hook starting at {@code pos} with {@code velocity} (blocks per tick), owned by {@code thrower}. */
    public GrapplingHookEntity(Level level, @Nullable Entity thrower, Vec3 pos, Vec3 velocity, ItemStack hook, boolean consumed) {
        super(GrappleContent.HOOK.get(), pos.x, pos.y, pos.z, level);
        setOwner(thrower);
        setItem(hook);
        this.consumed = consumed;
        setDeltaMovement(velocity);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, (byte) State.FLYING.ordinal());
        builder.define(TAUT, false);
        builder.define(PLOT_BLOCK, BlockPos.ZERO);
        builder.define(PLOT_OFFSET, new Vector3f());
    }

    @Override
    protected Item getDefaultItem() {
        return CombatContent.GRAPPLING_HOOK.get();
    }

    @Override
    protected double getDefaultGravity() {
        // server config is synced, so client and server simulate the same arc
        return GrappleConfig.GRAVITY.get();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    // ------------------------------------------------------------------ synched state (both sides)

    public State state() {
        byte b = entityData.get(STATE);
        return b >= 0 && b < State.values().length ? State.values()[b] : State.FLYING;
    }

    private void setState(State s) {
        entityData.set(STATE, (byte) s.ordinal());
    }

    /** The rope is taut and hauling (server: computed every tick; client: synched). */
    public boolean taut() {
        return entityData.get(TAUT);
    }

    /** Plot position the hook hangs on (both sides; meaningful only while {@link State#LATCHED}). */
    public Vec3 syncedPlotPos() {
        BlockPos b = entityData.get(PLOT_BLOCK);
        Vector3f o = entityData.get(PLOT_OFFSET);
        return new Vec3(b.getX() + (double) o.x, b.getY() + (double) o.y, b.getZ() + (double) o.z);
    }

    // ------------------------------------------------------------------ server accessors

    public @Nullable UUID shipId() {
        return shipId;
    }

    public @Nullable Vec3 plotPos() {
        return plotPos;
    }

    /** The ship block the hook bit into (plot coordinates), or null. */
    public @Nullable BlockPos latchedBlock() {
        return latchedBlock;
    }

    boolean consumed() {
        return consumed;
    }

    boolean finished() {
        return finished;
    }

    @Nullable UUID throwerShipId() {
        return throwerShipId;
    }

    @Nullable Vec3 anchorPlot() {
        return anchorPlot;
    }

    @Nullable Vec3 throwerPos() {
        return throwerPos;
    }

    boolean throwerAboardTarget() {
        return throwerAboardTarget;
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        if (level().isClientSide) {
            if (state() == State.FLYING) {
                super.tick();
            } else {
                baseTick();
            }
            return;
        }
        ServerLevel level = (ServerLevel) level();
        GrappleService.adopt(this);
        Entity owner = getOwner();
        if (state() == State.FLYING) {
            super.tick();
            if (isRemoved() || finished) {
                return;
            }
            if (pendingPos != null) {
                setPos(pendingPos);
                pendingPos = null;
            } else if (state() == State.FLYING) {
                if (isInWater()) {
                    miss(position(), true);
                } else if (owner != null && GrappleRules.ropeRunsOut(distanceTo(owner), GrappleConfig.MAX_ROPE_LENGTH.get())) {
                    miss(position(), false);
                } else if (tickCount > MAX_FLIGHT_TICKS) {
                    miss(position(), false);
                }
            }
        } else {
            baseTick();
        }

        State s = state();
        ShipBody ship = s == State.LATCHED && shipId != null ? SableShips.byId(level, shipId) : null;
        Vec3 hookWorld = ship != null && plotPos != null ? ship.toWorld(plotPos) : position();
        boolean ownerPresent = owner != null && owner.isAlive() && owner.level() == level;
        double ownerDistance = ownerPresent ? owner.position().distanceTo(hookWorld) : Double.POSITIVE_INFINITY;
        GrappleRules.Release release = GrappleRules.release(GrappleConfig.ENABLED.get(), ownerPresent, s == State.LATCHED,
                ship != null, ownerDistance, GrappleConfig.MAX_ROPE_LENGTH.get());
        if (release != GrappleRules.Release.NONE) {
            release(release);
            return;
        }
        switch (s) {
            case LATCHED -> tickLatched(level, ship, hookWorld, owner);
            case RETRACTING -> tickRetracting(ownerDistance);
            default -> { }
        }
    }

    private void tickLatched(ServerLevel level, ShipBody ship, Vec3 hookWorld, Entity owner) {
        setPos(hookWorld);
        setDeltaMovement(Vec3.ZERO);
        ShipBody thrower = GrappleService.shipOf(level, owner);
        throwerPos = owner.position();
        throwerAboardTarget = thrower != null && thrower.id().equals(shipId);
        if (thrower != null && !throwerAboardTarget) {
            if (!thrower.id().equals(throwerShipId) || anchorPlot == null || tickCount >= anchorRefreshAt) {
                anchorPlot = GrappleService.nearestBlockCenter(thrower, hookWorld);
                anchorRefreshAt = tickCount + ANCHOR_REFRESH_TICKS;
            }
            throwerShipId = anchorPlot != null ? thrower.id() : null;
        } else {
            throwerShipId = null;
            anchorPlot = null;
        }
        boolean taut;
        if (throwerShipId != null) {
            Vec3 anchor = thrower.toWorld(anchorPlot);
            taut = GrappleRules.taut(horizontal(hookWorld, anchor), GrappleConfig.holdLength(), GrappleConfig.HAUL_FORCE.get());
        } else {
            taut = !throwerAboardTarget && GrappleRules.taut(horizontal(hookWorld, throwerPos),
                    GrappleConfig.holdLength(), GrappleConfig.SHORE_HAUL_FORCE.get());
        }
        entityData.set(TAUT, taut);
    }

    private void tickRetracting(double ownerDistance) {
        if (!onGround()) {
            Vec3 v = getDeltaMovement().add(0, -getDefaultGravity(), 0).scale(isInWater() ? 0.8 : 0.98);
            setDeltaMovement(v);
            move(MoverType.SELF, v);
        } else {
            setDeltaMovement(Vec3.ZERO);
        }
        retractTicks++;
        if (retractTicks >= GrappleConfig.RETRACT_TICKS.get() || ownerDistance > 2 * GrappleConfig.MAX_ROPE_LENGTH.get()) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.8f, 1.0f);
            finish(true);
        }
    }

    static double horizontal(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    // ------------------------------------------------------------------ hits

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (level().isClientSide || state() != State.FLYING) return;
        double damage = GrappleConfig.ENTITY_DAMAGE.get();
        if (damage > 0) {
            result.getEntity().hurt(damageSources().thrown(this, getOwner()), (float) damage);
        }
        miss(position(), false);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!(level() instanceof ServerLevel level) || state() != State.FLYING) return;
        BlockPos pos = result.getBlockPos();
        // a ship block is hit in plot space (Sable's clip returns the sub-level result as is, sable-notes §9.0e)
        ShipBody ship = SableShips.containing(level, pos);
        Entity owner = getOwner();
        ShipBody throwerShip = owner == null ? null : GrappleService.shipOf(level, owner);
        Vec3 at = result.getLocation();
        GrappleRules.HitKind kind = GrappleRules.blockHit(ship == null ? null : ship.id(), throwerShip == null ? null : throwerShip.id());
        if (kind == GrappleRules.HitKind.LATCH) {
            latch(level, ship, pos, at);
        } else {
            miss(ship != null ? ship.toWorld(at) : at, false);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        // dispatch only; the hook is never discarded on a hit
        super.onHit(result);
    }

    /** Latches the hook onto {@code ship} at plot position {@code at} on block {@code block}. */
    void latch(ServerLevel level, ShipBody ship, BlockPos block, Vec3 at) {
        shipId = ship.id();
        plotPos = at;
        latchedBlock = block.immutable();
        entityData.set(PLOT_BLOCK, latchedBlock);
        entityData.set(PLOT_OFFSET, new Vector3f((float) (at.x - block.getX()), (float) (at.y - block.getY()), (float) (at.z - block.getZ())));
        setState(State.LATCHED);
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        Vec3 world = ship.toWorld(at);
        pendingPos = world;
        level.playSound(null, world.x, world.y, world.z, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0f, 0.9f);
        level.playSound(null, world.x, world.y, world.z, SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS, 0.8f, 1.0f);
        level.sendParticles(ParticleTypes.CRIT, world.x, world.y, world.z, 6, 0.1, 0.1, 0.1, 0.1);
    }

    /** The hook missed: it lies at {@code worldPos} and is pulled back after {@code retract_ticks}. */
    void miss(Vec3 worldPos, boolean splash) {
        setState(State.RETRACTING);
        retractTicks = 0;
        setNoGravity(true); // our own fall in tickRetracting
        setDeltaMovement(Vec3.ZERO);
        pendingPos = worldPos;
        setPos(worldPos);
        entityData.set(TAUT, false);
        if (splash && level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SPLASH, worldPos.x, worldPos.y + 0.3, worldPos.z, 12, 0.2, 0.1, 0.2, 0.1);
            level.playSound(null, worldPos.x, worldPos.y, worldPos.z, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 0.6f, 1.2f);
        }
    }

    // ------------------------------------------------------------------ release

    /** Lets go of the hook for {@code reason}: a snapped rope cracks and may lose the hook, anything else reels it in. */
    public void release(GrappleRules.Release reason) {
        if (finished) return;
        if (reason == GrappleRules.Release.TOO_FAR) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.LEASH_KNOT_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f);
        } else {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.8f, 1.0f);
        }
        finish(GrappleRules.hookReturned(reason, GrappleConfig.ROPE_BREAKS_LOSE_HOOK.get()));
    }

    /** Ends the hook: the item goes back to the thrower ({@code returned}) or drops where the hook was. */
    void finish(boolean returned) {
        if (finished) return;
        finished = true;
        entityData.set(TAUT, false);
        Entity owner = getOwner();
        if (returned) {
            giveBackTo(owner instanceof Player p && p.isAlive() ? p : null);
        } else if (consumed) {
            dropAt(position());
        }
        discard();
    }

    /** Puts the thrown item back into {@code player}'s inventory (dropped at the player if full), else at the hook. */
    void giveBackTo(@Nullable Player player) {
        finished = true;
        if (!consumed) return;
        consumed = false;
        ItemStack stack = getItem().copy();
        if (stack.isEmpty()) return;
        if (player == null) {
            dropAt(position());
        } else if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private void dropAt(Vec3 at) {
        consumed = false;
        ItemStack stack = getItem().copy();
        if (!stack.isEmpty() && level() instanceof ServerLevel level) {
            level.addFreshEntity(new ItemEntity(level, at.x, at.y, at.z, stack));
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (!level().isClientSide) {
            GrappleService.forget(this);
        }
    }

    /** All hooks in {@code entities} (helper for tests and the client). */
    public static List<GrapplingHookEntity> of(List<? extends Entity> entities) {
        return entities.stream().filter(e -> e instanceof GrapplingHookEntity).map(e -> (GrapplingHookEntity) e).toList();
    }
}
