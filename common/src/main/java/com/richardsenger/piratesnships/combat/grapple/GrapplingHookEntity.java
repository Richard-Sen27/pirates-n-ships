package com.richardsenger.piratesnships.combat.grapple;

import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.ship.assembly.ShipSplits;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A thrown grappling hook on its rope (docs/design.md §8.3, §8.4, G11). Server-authoritative: the state, the ship it
 * hangs on and the rope's ends live here on the server; the client only renders from the synched data.
 *
 * <ul>
 *   <li><b>Flying</b>: a thrown projectile with {@code grapple.gravity}. It stops and drops when the rope runs out
 *       ({@link #ropeLength()} from the rope's near end: {@code max_rope_length} for a throw, longer from a crossbow or
 *       musket, GR1) or when it falls into water. Passing within {@code ring_catch_radius} of a mooring ring on another
 *       ship, it latches onto the ring.</li>
 *   <li><b>Latched</b>: it hit a block of a ship that is not the thrower's own. It hangs at that plot position (Sable
 *       returns ship hits in plot coordinates, docs/sable-notes.md §9.0e) and follows the ship; the entity itself stays
 *       in world space at the plot position's world point. While latched {@link GrappleService} hauls the ships
 *       together every physics substep.</li>
 *   <li><b>Retracting</b>: it missed (land, water, an entity, the thrower's own ship): it lies there for
 *       {@code retract_ticks}, then the rope pulls it back to the thrower.</li>
 * </ul>
 *
 * <b>The rope's near end</b> (GR1) is the thrower, or the mooring ring it was tied to ({@link #tieTo},
 * {@link GrappleRules.NearEnd}): a tied rope hauls with the ring's ship (or holds from a ring on land) whatever the
 * thrower does, and snaps when the hook is farther from the ring than {@link #breakLength()}. A hook latched on a ring
 * holds {@code ring_hold_multiplier} times the rope's length; breaking either ring releases the hook.
 *
 * <p>The thrown item travels with the hook ({@link #getItem()}) and goes back to the thrower when the hook is released,
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
    /** Mooring ring the rope's near end is tied to (plot position on a ship, else world), or empty (GR1). */
    private static final EntityDataAccessor<Optional<BlockPos>> TIED_RING = SynchedEntityData.defineId(GrapplingHookEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);

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
    /** Rope length of this launch [blocks] (GR1: longer from a crossbow or musket); NaN = {@code max_rope_length}. */
    private double ropeLength = Double.NaN;
    /** The hook is latched on a mooring ring: it holds {@code ring_hold_multiplier} times longer. */
    private boolean onRing;
    /** Mooring ring the near end is tied to (plot position on a ship, world position on land), or null. */
    private @Nullable BlockPos tiedRing;
    /** Ship of {@link #tiedRing}, null for a ring on land. */
    private @Nullable UUID tiedShip;

    // rope ends for the physics substep, refreshed every game tick while latched
    private @Nullable UUID throwerShipId;
    private @Nullable Vec3 anchorPlot;
    private @Nullable Vec3 throwerPos;
    private boolean throwerAboardTarget;
    private int anchorRefreshAt;
    /** Stall detector of the rope, updated every physics substep by {@link GrappleService}. */
    private final GrappleRules.Holding holding = new GrappleRules.Holding();

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
        builder.define(TIED_RING, Optional.empty());
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

    /** The mooring ring the rope is tied to, as synched (both sides). */
    public Optional<BlockPos> syncedTiedRing() {
        return entityData.get(TIED_RING);
    }

    // ------------------------------------------------------------------ server accessors

    /** Rope length of this hook [blocks]. */
    public double ropeLength() {
        return Double.isNaN(ropeLength) ? GrappleConfig.MAX_ROPE_LENGTH.get() : ropeLength;
    }

    void setRopeLength(double length) {
        this.ropeLength = length;
    }

    /** The hook is latched on a mooring ring. */
    public boolean onRing() {
        return onRing;
    }

    /** The mooring ring the rope's near end is tied to (plot position on a ship), or null. */
    public @Nullable BlockPos tiedRing() {
        return tiedRing;
    }

    /** Ties the rope's near end to the ring at {@code ring} on ship {@code ship} (null: on land). */
    void tieTo(BlockPos ring, @Nullable UUID ship) {
        tiedRing = ring.immutable();
        tiedShip = ship;
        anchorPlot = null;
        holding.reset(); // a new rope end: start over
        entityData.set(TIED_RING, Optional.of(tiedRing));
    }

    /** Distance at which the rope snaps now ({@link GrappleRules#breakLength}). */
    public double breakLength() {
        return GrappleRules.breakLength(ropeLength(), state() == State.LATCHED && onRing, GrappleConfig.RING_HOLD_MULTIPLIER.get());
    }

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

    /**
     * The ship under the hook or the thrower split (RS1): a latched hook follows the block it bit into to the piece that
     * holds it now (a wreck piece too), and the anchor on the thrower's ship is looked up again on the next tick.
     */
    void followSplit(ShipSplits.SplitEvent split) {
        if (shipId != null && shipId.equals(split.parent()) && latchedBlock != null && plotPos != null) {
            ShipSplits.Relocation r = split.relocate(latchedBlock);
            if (r.moved(shipId, latchedBlock)) {
                BlockPos d = r.pos().subtract(latchedBlock);
                shipId = r.ship();
                latchedBlock = r.pos();
                plotPos = plotPos.add(d.getX(), d.getY(), d.getZ());
                entityData.set(PLOT_BLOCK, latchedBlock); // PLOT_OFFSET is relative to the block and stays
            }
        }
        if (split.parent().equals(throwerShipId)) {
            anchorRefreshAt = 0;
        }
    }

    /** The rope's holding state (server). */
    public GrappleRules.Holding holding() {
        return holding;
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
            if (catchRing(level, owner)) {
                baseTick(); // caught by a ring on its way: no flight step this tick
            } else {
                super.tick();
            }
            if (isRemoved() || finished) {
                return;
            }
            if (pendingPos != null) {
                setPos(pendingPos);
                pendingPos = null;
            } else if (state() == State.FLYING) {
                Vec3 near = nearEndPos(level, owner);
                if (isInWater()) {
                    miss(position(), true);
                } else if (near != null && GrappleRules.ropeRunsOut(position().distanceTo(near), ropeLength())) {
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
        boolean tied = tiedRing != null;
        ShipBody ringShip = tied && tiedShip != null ? SableShips.byId(level, tiedShip) : null;
        boolean ringsIntact = (!tied || (tiedShip == null || ringShip != null) && MooringRingBlock.isRing(level, tiedRing))
                && (s != State.LATCHED || !onRing || ship == null || MooringRingBlock.isRing(level, latchedBlock));
        // a tied rope does not need its thrower nearby, only a thrower to give the hook back to
        boolean ownerPresent = owner != null && (tied || owner.isAlive() && owner.level() == level);
        Vec3 near = ownerPresent && ringsIntact ? nearEndPos(level, owner) : null;
        double nearDistance = near != null ? near.distanceTo(hookWorld) : Double.POSITIVE_INFINITY;
        GrappleRules.Release release = GrappleRules.release(GrappleConfig.ENABLED.get(), ownerPresent, s == State.LATCHED,
                ship != null, ringsIntact, nearDistance, breakLength());
        if (release != GrappleRules.Release.NONE) {
            release(release);
            return;
        }
        switch (s) {
            case LATCHED -> tickLatched(level, ship, hookWorld, owner, ringShip, near);
            case RETRACTING -> tickRetracting(nearDistance);
            default -> { }
        }
    }

    /**
     * Where the rope's near end is (world): the ring it is tied to ({@link GrappleRules.NearEnd#RING}), else the
     * thrower; null when neither can be found.
     */
    private @Nullable Vec3 nearEndPos(ServerLevel level, @Nullable Entity owner) {
        if (tiedRing != null) {
            Vec3 c = MooringRingBlock.ringCenter(level, tiedRing);
            if (tiedShip == null) {
                return c;
            }
            ShipBody ringShip = SableShips.byId(level, tiedShip);
            return ringShip != null ? ringShip.toWorld(c) : null;
        }
        return owner != null ? owner.position() : null;
    }

    /** The ship at the rope's near end: the tied ring's ship, else the ship the thrower stands on (null: land). */
    private @Nullable ShipBody nearEndShip(ServerLevel level, @Nullable Entity owner) {
        UUID ringShip = tiedShip;
        ShipBody thrower = tiedRing == null ? GrappleService.shipOf(level, owner) : null;
        UUID id = GrappleRules.haulingShip(GrappleRules.nearEnd(tiedRing != null), ringShip, thrower == null ? null : thrower.id());
        if (id == null) {
            return null;
        }
        return thrower != null && thrower.id().equals(id) ? thrower : SableShips.byId(level, id);
    }

    /**
     * A flying hook passing within {@code ring_catch_radius} of a mooring ring on a ship other than the near end's
     * latches onto the ring, even if it would otherwise fly past or glance off (GR1). Checked along this tick's path
     * before the flight step. True when it latched.
     */
    private boolean catchRing(ServerLevel level, @Nullable Entity owner) {
        if (!GrappleConfig.ENABLED.get() || !GrappleConfig.RINGS_ENABLED.get()) {
            return false;
        }
        Vec3 from = position();
        Vec3 to = from.add(getDeltaMovement());
        ShipBody nearShip = nearEndShip(level, owner);
        GrappleService.RingCatch c = GrappleService.findRing(level, from, to, GrappleConfig.RING_CATCH_RADIUS.get(),
                nearShip == null ? null : nearShip.id());
        if (c == null) {
            return false;
        }
        latch(level, c.ship(), c.block(), c.center(), true);
        return true;
    }

    private void tickLatched(ServerLevel level, ShipBody ship, Vec3 hookWorld, Entity owner, @Nullable ShipBody ringShip, Vec3 near) {
        setPos(hookWorld);
        setDeltaMovement(Vec3.ZERO);
        boolean tied = tiedRing != null;
        ShipBody nearShip = tied ? ringShip : GrappleService.shipOf(level, owner);
        throwerPos = near;
        throwerAboardTarget = nearShip != null && nearShip.id().equals(shipId);
        if (nearShip != null && !throwerAboardTarget) {
            if (tied) {
                anchorPlot = MooringRingBlock.ringCenter(level, tiedRing); // the ring is the rope's end
            } else if (!nearShip.id().equals(throwerShipId) || anchorPlot == null || tickCount >= anchorRefreshAt) {
                anchorPlot = GrappleService.nearestBlockCenter(nearShip, hookWorld);
                anchorRefreshAt = tickCount + ANCHOR_REFRESH_TICKS;
            }
            UUID next = anchorPlot != null ? nearShip.id() : null;
            if (next == null || !next.equals(throwerShipId)) {
                holding.reset(); // a new rope end: start over
            }
            throwerShipId = next;
        } else {
            if (throwerShipId != null) {
                holding.reset(); // from ship-to-ship to shore (or aboard the target)
            }
            throwerShipId = null;
            anchorPlot = null;
        }
        boolean taut;
        if (throwerShipId != null) {
            Vec3 anchor = nearShip.toWorld(anchorPlot);
            taut = GrappleRules.taut(horizontal(hookWorld, anchor), GrappleConfig.holdLength(), GrappleConfig.HAUL_FORCE.get());
        } else {
            taut = !throwerAboardTarget && GrappleRules.taut(horizontal(hookWorld, throwerPos),
                    GrappleConfig.holdLength(), GrappleConfig.SHORE_HAUL_FORCE.get());
        }
        entityData.set(TAUT, taut && !holding.holding()); // a holding rope does not pull
    }

    private void tickRetracting(double nearDistance) {
        if (!onGround()) {
            Vec3 v = getDeltaMovement().add(0, -getDefaultGravity(), 0).scale(isInWater() ? 0.8 : 0.98);
            setDeltaMovement(v);
            move(MoverType.SELF, v);
        } else {
            setDeltaMovement(Vec3.ZERO);
        }
        retractTicks++;
        if (retractTicks >= GrappleConfig.RETRACT_TICKS.get() || nearDistance > 2 * ropeLength()) {
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
        ShipBody throwerShip = nearEndShip(level, getOwner());
        Vec3 at = result.getLocation();
        GrappleRules.HitKind kind = GrappleRules.blockHit(ship == null ? null : ship.id(), throwerShip == null ? null : throwerShip.id());
        if (kind == GrappleRules.HitKind.LATCH) {
            BlockState state = level.getBlockState(pos);
            boolean ring = state.getBlock() instanceof MooringRingBlock && GrappleConfig.RINGS_ENABLED.get();
            latch(level, ship, pos, ring ? MooringRingBlock.ringCenter(state, pos) : at, ring);
        } else {
            miss(ship != null ? ship.toWorld(at) : at, false);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        // dispatch only; the hook is never discarded on a hit
        super.onHit(result);
    }

    /**
     * Latches the hook onto {@code ship} at plot position {@code at} on block {@code block}; {@code ring}: the block is
     * a mooring ring, which holds the hook harder ({@link #breakLength}).
     */
    void latch(ServerLevel level, ShipBody ship, BlockPos block, Vec3 at, boolean ring) {
        onRing = ring;
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
