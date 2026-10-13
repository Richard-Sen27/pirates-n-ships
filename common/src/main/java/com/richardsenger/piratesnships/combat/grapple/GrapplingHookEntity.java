package com.richardsenger.piratesnships.combat.grapple;

import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.ship.assembly.ShipRejoin;
import com.richardsenger.piratesnships.sailing.rope.RopeAnchor;
import com.richardsenger.piratesnships.ship.assembly.ShipSplits;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A thrown grappling hook on its rope (docs/design.md §8.3, §8.4, G11). Server-authoritative: the state, the ship it
 * hangs on and the rope's ends live here on the server; the client only renders from the synched data.
 *
 * <ul>
 *   <li><b>Flying</b>: a thrown projectile on one plain ballistic arc from its first tick (vanilla's drag of 0.99 per
 *       tick and the launch's gravity: {@code hand_throw_gravity} by hand, GR8, {@code grapple.gravity} times
 *       {@code launch.musket_gravity_factor} from the musket, GR4; synched so the client flies the same arc). It stops and drops when the rope runs out
 *       ({@link #ropeLength()} from the rope's near end: {@code max_rope_length} for a throw, longer from the musket)
 *       or when it falls into water. Passing within {@code ring_catch_radius} of a mooring ring or a cleat (any
 *       {@link RopeAnchor} the grapple uses, RP1) on another ship, it latches onto its tie point.</li>
 *   <li><b>Latched</b> (GR4: on any solid surface, {@link GrappleRules#blockHit}): on a ship block (another ship, or
 *       the near end's own ship with {@code latch_own_ship}) it hangs at that plot position (Sable returns ship hits in
 *       plot coordinates, docs/sable-notes.md §9.0e) and follows the ship; on a world block (with
 *       {@code latch_world_blocks}) it holds the world position, {@link #shipId()} null. The entity itself stays in
 *       world space at the hook point's world position. While latched {@link GrappleService} applies the rope force
 *       chosen by {@link GrappleRules#haul} every physics substep (ships together, ship to shore, ship to a world
 *       block as a kedge, or none for a line within one body).</li>
 *   <li><b>Retracting</b>: it missed (water, an entity, a block it slips off, a surface its toggle forbids): it lies
 *       there for {@code retract_ticks}, then the rope pulls it back to the thrower.</li>
 * </ul>
 *
 * <b>The rope's near end</b> (GR1) is the thrower, or the mooring ring or cleat (RP1) it was tied to ({@link #tieTo},
 * {@link GrappleRules.NearEnd}): a tied rope hauls with the ring's ship (or holds from a ring on land) whatever the
 * thrower does, and snaps when the hook is farther from the ring than {@link #breakLength()}. A hook latched on a ring
 * holds {@code ring_hold_multiplier} times the rope's length; breaking either ring releases the hook. RAIL3: so does a
 * hook on a railing or railing gate ({@code #pirates_n_ships:grapple_latches}, {@code grapple.latches_hold}), and a haul
 * pulling harder than {@code grapple.railing_break_force} tears that block out and lets the hook go.
 *
 * <p><b>Pose</b> (GR4, {@link HookPose}): in flight the yaw and pitch follow the velocity exactly ({@link #updateRotation});
 * latching stores the pose of the face it bit ({@link #pose()}, {@link #latchRotation()}, synched, in the frame of that
 * block), so every client draws the same; free, it falls point down.
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
    /**
     * Gravity of this flight [blocks per tick squared] (GR4: lower for a musket shot; GR8: a hand throw has its own),
     * NaN for {@code grapple.gravity}.
     */
    private static final EntityDataAccessor<Float> FLIGHT_GRAVITY = SynchedEntityData.defineId(GrapplingHookEntity.class, EntityDataSerializers.FLOAT);
    /** GR4: the latched pose's {@link HookPose.Kind} (ordinal), meaningful only while {@link State#LATCHED}. */
    private static final EntityDataAccessor<Byte> LATCH_KIND = SynchedEntityData.defineId(GrapplingHookEntity.class, EntityDataSerializers.BYTE);
    /** GR4: the latched pose, in the frame of the block it bit (the ship's plot, or the world). */
    private static final EntityDataAccessor<Quaternionf> LATCH_ROT = SynchedEntityData.defineId(GrapplingHookEntity.class, EntityDataSerializers.QUATERNION);
    /** GR7: the hand-held rope's length while it holds its thrower ({@link RopeSwing}); negative while it pays out freely. */
    private static final EntityDataAccessor<Float> SWING_LENGTH = SynchedEntityData.defineId(GrapplingHookEntity.class, EntityDataSerializers.FLOAT);
    /** GR8: how many pulls the thrower has made on the hand-held rope (each one shows a hand-over-hand, {@link RopePose}). */
    private static final EntityDataAccessor<Integer> PULLS = SynchedEntityData.defineId(GrapplingHookEntity.class, EntityDataSerializers.INT);

    /** How far below the eyes the thrower holds the rope [blocks] (the renderer's third-person hand height). */
    public static final double HAND_BELOW_EYES = 0.45;

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
    /** Rope length of this launch [blocks] (longer from the musket); NaN = {@code max_rope_length}. */
    private double ropeLength = Double.NaN;
    /** The hook is latched on a mooring ring (or cleat, or RAIL3 strong point): it holds {@code ring_hold_multiplier} times longer. */
    private boolean onRing;
    /** RAIL3: the hook holds a strong point ({@code #grapple_latches}, a railing): torn out above {@code railing_break_force}. */
    private boolean onStrongPoint;
    /** RAIL3: the highest rope tension since the last game tick [kpg·m/s²], from the physics substeps. */
    private double load;
    /** Mooring ring the near end is tied to (plot position on a ship, world position on land), or null. */
    private @Nullable BlockPos tiedRing;
    /** Ship of {@link #tiedRing}, null for a ring on land. */
    private @Nullable UUID tiedShip;
    /** GR7: the hand-held rope's length while it holds its thrower ({@link RopeSwingService}), NaN while it pays out. */
    private double swingLength = Double.NaN;
    /** Hauling by hand (GR5): the frozen rope length and its tension. */
    private final GrappleHaul.State haul = new GrappleHaul.State();

    // rope ends for the physics substep, refreshed every game tick while latched
    private @Nullable UUID throwerShipId;
    private @Nullable Vec3 anchorPlot;
    private @Nullable Vec3 throwerPos;
    private boolean throwerAboardTarget;
    /** The force the latched rope applies now (GR4), refreshed every game tick. */
    private GrappleRules.Haul haulKind = GrappleRules.Haul.NONE;
    private int anchorRefreshAt;
    /** Stall detector of the rope, updated every physics substep by {@link GrappleService}. */
    private final GrappleRules.Holding holding = new GrappleRules.Holding();
    /** Client: {@link #tickCount} when the hook was last seen latching, for the settle (GR4); -1 before. */
    private int latchedAtTick = -1;
    /** GR8, both sides: {@link #tickCount} of the last pull on the hand-held rope; -1 before any. */
    private int pulledAtTick = -1;

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
        // GR4: point first from the start, not swung in from yaw 0
        setYRot(HookPose.yaw(velocity.x, velocity.z));
        setXRot(HookPose.pitch(velocity.x, velocity.y, velocity.z));
        yRotO = getYRot();
        xRotO = getXRot();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, (byte) State.FLYING.ordinal());
        builder.define(TAUT, false);
        builder.define(PLOT_BLOCK, BlockPos.ZERO);
        builder.define(PLOT_OFFSET, new Vector3f());
        builder.define(TIED_RING, Optional.empty());
        builder.define(FLIGHT_GRAVITY, Float.NaN);
        builder.define(LATCH_KIND, (byte) HookPose.Kind.HANGING.ordinal());
        builder.define(LATCH_ROT, new Quaternionf());
        builder.define(SWING_LENGTH, -1.0f);
        builder.define(PULLS, 0);
    }

    @Override
    protected Item getDefaultItem() {
        return CombatContent.GRAPPLING_HOOK.get();
    }

    @Override
    protected double getDefaultGravity() {
        // the flight's gravity is synced with the spawn, so client and server simulate the same arc
        return flightGravity();
    }

    /**
     * Sets this flight's gravity [blocks per tick squared] (GR4, GR8: {@link GrappleConfig#gravity} of the launch's
     * mode); call before the hook is added.
     */
    void setFlightGravity(double gravity) {
        entityData.set(FLIGHT_GRAVITY, (float) Math.max(0.0, gravity));
    }

    /** This flight's gravity [blocks per tick squared]; {@code grapple.gravity} unless the launch set its own. */
    public double flightGravity() {
        float g = entityData.get(FLIGHT_GRAVITY);
        return Float.isNaN(g) ? GrappleConfig.GRAVITY.get() : g;
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

    /**
     * GR4: the hook's pose ({@link HookPose}): {@link HookPose.Kind#FLYING} in flight, the synched latched pose while
     * latched, {@link HookPose.Kind#FALLING} while it lies free and is pulled back (both sides).
     */
    public HookPose.Kind pose() {
        return switch (state()) {
            case FLYING -> HookPose.Kind.FLYING;
            case RETRACTING -> HookPose.Kind.FALLING;
            case LATCHED -> {
                byte b = entityData.get(LATCH_KIND);
                yield b >= 0 && b < HookPose.Kind.values().length ? HookPose.Kind.values()[b] : HookPose.Kind.HANGING;
            }
        };
    }

    /** GR4: the latched pose's rotation in the frame of the block it bit, the ship's plot or the world (copy, both sides). */
    public Quaternionf latchRotation() {
        return new Quaternionf(entityData.get(LATCH_ROT));
    }

    /**
     * GR4: the hook's rotation in its own frame ({@link HookPose}) for the pose of this tick, without a ship's turn:
     * in flight from its yaw and pitch (which follow the velocity, {@link #updateRotation}), latched the synched pose,
     * free point down.
     */
    public Quaternionf poseRotation(float partialTick) {
        return switch (state()) {
            case FLYING -> HookPose.fromYawPitch(Mth.rotLerp(partialTick, yRotO, getYRot()), Mth.lerp(partialTick, xRotO, getXRot()));
            case LATCHED -> latchRotation();
            case RETRACTING -> HookPose.falling(getYRot());
        };
    }

    /** Client: ticks since the hook was seen latching (with the partial tick), for the settle; huge when unknown. */
    public float ticksSinceLatch(float partialTick) {
        return latchedAtTick < 0 ? Float.MAX_VALUE : tickCount - latchedAtTick + partialTick;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (level().isClientSide && (STATE.equals(key) || LATCH_ROT.equals(key)) && state() == State.LATCHED) {
            latchedAtTick = tickCount;
        }
        if (level().isClientSide && PULLS.equals(key) && tickCount > 0) {
            pulledAtTick = tickCount; // not for the count a newly seen hook arrives with
        }
    }

    /**
     * GR4: in flight the hook points along its velocity exactly (vanilla eases a projectile's angles by a fifth per
     * tick, so a hook would fly sideways for a few ticks after the throw and lag behind the arc).
     */
    @Override
    protected void updateRotation() {
        Vec3 v = getDeltaMovement();
        if (state() != State.FLYING || v.lengthSqr() < 1.0e-10) {
            return;
        }
        setYRot(HookPose.yaw(v.x, v.z));
        setXRot(HookPose.pitch(v.x, v.y, v.z));
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

    /**
     * GR7: the hand-held rope's length while it holds its thrower, as synched (both sides); NaN while it pays out freely
     * (the thrower on the ground, or not holding it).
     */
    public double syncedSwingLength() {
        float f = entityData.get(SWING_LENGTH);
        return f < 0f ? Double.NaN : f;
    }

    /** GR7: the hand-held rope's length while it holds its thrower (server), NaN while it pays out freely. */
    public double swingLength() {
        return swingLength;
    }

    /** GR8: the number of pulls on the hand-held rope so far (both sides, synced). */
    public int pulls() {
        return entityData.get(PULLS);
    }

    /** GR8: ticks since the last pull on the hand-held rope was made (server) or seen (client); negative before any. */
    public int ticksSincePull() {
        return pulledAtTick < 0 ? -1 : tickCount - pulledAtTick;
    }

    /** GR8: counts a pull on the hand-held rope (server), so every client shows the hand-over-hand. */
    void countPull() {
        entityData.set(PULLS, entityData.get(PULLS) + 1);
        pulledAtTick = tickCount;
    }

    /** GR7: sets the hand-held rope's length (NaN: free) and syncs it. */
    void setSwingLength(double length) {
        swingLength = length;
        entityData.set(SWING_LENGTH, Double.isNaN(length) ? -1.0f : (float) length);
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

    /** The hook is latched on a mooring ring, a cleat, or (RAIL3) a strong point holding like one. */
    public boolean onRing() {
        return onRing;
    }

    /** RAIL3: the hook holds a strong point of {@code #pirates_n_ships:grapple_latches} (a railing or railing gate). */
    public boolean onStrongPoint() {
        return onStrongPoint;
    }

    /** RAIL3: records a rope tension of this physics substep (the haul's or a hand haul's) for the tear-out check. */
    void recordLoad(double tension) {
        if (tension > load) load = tension;
    }

    /** The mooring ring the rope's near end is tied to (plot position on a ship), or null. */
    public @Nullable BlockPos tiedRing() {
        return tiedRing;
    }

    /** Ship of the ring the rope is tied to, null for a ring on land or an untied rope. */
    public @Nullable UUID tiedShip() {
        return tiedShip;
    }

    /** Hauling by hand (GR5): whether the rope is frozen, its length and tension (server). */
    public GrappleHaul.State haul() {
        return haul;
    }

    /**
     * Whether a SHORE rope drags the hooked ship by itself ({@code shore_haul_force}): always from a cleat or ring on
     * land; from a hand on land only with {@code grapple.hauling} off (GR5: a hand-held rope pays out unless the player
     * freezes it by sneaking).
     */
    boolean shoreDrags() {
        return nearEndFixed() || !GrappleConfig.HAULING.get();
    }

    /**
     * True when the near end is fixed: tied to a mooring ring or cleat (RP1). GR5: nothing else fixes it; a rope whose
     * near end is in the thrower's hand always runs from the hook to that hand.
     */
    public boolean nearEndFixed() {
        return tiedRing != null;
    }

    /** Ties the rope's near end to the ring at {@code ring} on ship {@code ship} (null: on land). */
    void tieTo(BlockPos ring, @Nullable UUID ship) {
        setSwingLength(Double.NaN); // GR7: a tied rope no longer hangs from the hand
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

    /** The ship the hook is latched on, null while it flies, retracts, or holds a world block ({@link #onWorldBlock()}). */
    public @Nullable UUID shipId() {
        return shipId;
    }

    /** The hook is latched on a world block (GR4): {@link #plotPos()} and {@link #latchedBlock()} are world positions. */
    public boolean onWorldBlock() {
        return state() == State.LATCHED && shipId == null && plotPos != null;
    }

    /** The force the latched rope applies ({@link GrappleRules#haul}), as of the last game tick (server). */
    public GrappleRules.Haul haulKind() {
        return haulKind;
    }

    public @Nullable Vec3 plotPos() {
        return plotPos;
    }

    /** The block the hook bit into (plot coordinates on a ship, world coordinates on a world block), or null. */
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

    /**
     * The piece under the hook or the thrower was rejoined to its ship (RS2): a latched hook follows its block into the
     * ship's plot (moved and turned like the block), and the thrower's anchor is looked up again on the next tick.
     */
    void followRejoin(ShipRejoin.Rejoined rejoin) {
        if (shipId != null && shipId.equals(rejoin.absorbed()) && latchedBlock != null && plotPos != null) {
            shipId = rejoin.keeper();
            latchedBlock = rejoin.transform().apply(latchedBlock);
            plotPos = rejoin.transform().apply(plotPos);
            entityData.set(PLOT_BLOCK, latchedBlock);
            entityData.set(PLOT_OFFSET, new Vector3f((float) (plotPos.x - latchedBlock.getX()), (float) (plotPos.y - latchedBlock.getY()),
                    (float) (plotPos.z - latchedBlock.getZ())));
        }
        if (rejoin.absorbed().equals(throwerShipId)) {
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
        boolean worldLatch = s == State.LATCHED && shipId == null && plotPos != null;
        ShipBody ship = s == State.LATCHED && shipId != null ? SableShips.byId(level, shipId) : null;
        Vec3 hookWorld = ship != null && plotPos != null ? ship.toWorld(plotPos) : worldLatch ? plotPos : position();
        // the far end holds: its ship is there, or (GR4) the world block it bit into still stands
        boolean farEndPresent = worldLatch ? blockStands(level, latchedBlock) : ship != null;
        boolean tied = tiedRing != null;
        ShipBody ringShip = tied && tiedShip != null ? SableShips.byId(level, tiedShip) : null;
        boolean ringsIntact = (!tied || (tiedShip == null || ringShip != null) && GrappleService.isGrappleAnchor(level, tiedRing))
                && (s != State.LATCHED || !onRing || ship == null && !worldLatch || holdStands(level));
        // a tied rope does not need its thrower nearby, only a thrower to give the hook back to
        boolean ownerPresent = owner != null && (nearEndFixed() || owner.isAlive() && owner.level() == level);
        Vec3 near = ownerPresent && ringsIntact ? nearEndPos(level, owner) : null;
        double nearDistance = near != null ? near.distanceTo(hookWorld) : Double.POSITIVE_INFINITY;
        GrappleRules.Release release = GrappleRules.release(GrappleConfig.ENABLED.get(), ownerPresent, s == State.LATCHED,
                farEndPresent, ringsIntact, nearDistance, breakLength());
        if (release != GrappleRules.Release.NONE) {
            release(release);
            return;
        }
        if (s == State.LATCHED && tearOut(level)) {
            return;
        }
        switch (s) {
            case LATCHED -> tickLatched(level, ship, hookWorld, owner, near);
            case RETRACTING -> tickRetracting(nearDistance);
            default -> { }
        }
    }

    /**
     * Where the rope's near end is (world): the ring it is tied to ({@link GrappleRules.NearEnd#RING}), else the
     * thrower; null when neither can be found.
     */
    private @Nullable Vec3 nearEndPos(ServerLevel level, @Nullable Entity owner) {
        if (nearEndFixed()) {
            return fixedNearEnd(level);
        }
        return owner != null ? owner.position() : null;
    }

    /** World position of the ring the rope is tied to, null when untied (or its ship is gone). */
    private @Nullable Vec3 fixedNearEnd(ServerLevel level) {
        if (tiedRing == null) {
            return null;
        }
        Vec3 local = RopeAnchor.point(level, tiedRing);
        UUID shipOf = tiedShip;
        if (shipOf == null) {
            return local;
        }
        ShipBody s = SableShips.byId(level, shipOf);
        return s != null ? s.toWorld(local) : null;
    }

    /**
     * World position of the rope's near end for riders (GR2, server): the ring it is tied to, else the
     * thrower's hand ({@link #HAND_BELOW_EYES} below the eyes); null when it cannot be found.
     */
    public @Nullable Vec3 ropeNearEnd(ServerLevel level) {
        if (nearEndFixed()) {
            return fixedNearEnd(level);
        }
        Entity owner = getOwner();
        return owner != null && owner.isAlive() && owner.level() == level
                ? owner.getEyePosition().subtract(0, HAND_BELOW_EYES, 0) : null;
    }

    /**
     * World position of the latched hook (the rope's far end, server): on its ship, or the world point it holds (GR4);
     * null when it is not latched or its ship is gone.
     */
    public @Nullable Vec3 ropeFarEnd(ServerLevel level) {
        if (state() != State.LATCHED || plotPos == null) {
            return null;
        }
        if (shipId == null) {
            return plotPos;
        }
        ShipBody ship = SableShips.byId(level, shipId);
        return ship != null ? ship.toWorld(plotPos) : null;
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
     * A flying hook passing within {@code ring_catch_radius} of a mooring ring (or a cleat, RP1) on a ship other than
     * the near end's latches onto it, even if it would otherwise fly past or glance off (GR1). Checked along this tick's path
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
        Vec3 v = inFrameOf(c.ship(), getDeltaMovement());
        latch(level, c.ship(), c.block(), c.center(), true, HookPose.latched(HookPose.Kind.HANGING, 0, 1, 0, v.x, v.y, v.z), HookPose.Kind.HANGING);
        return true;
    }

    private void tickLatched(ServerLevel level, @Nullable ShipBody ship, Vec3 hookWorld, Entity owner, Vec3 near) {
        setPos(hookWorld);
        setDeltaMovement(Vec3.ZERO);
        ShipBody nearShip = nearEndFixed() ? nearEndShip(level, owner) : GrappleService.shipOf(level, owner);
        throwerPos = near;
        throwerAboardTarget = nearShip != null && nearShip.id().equals(shipId);
        haulKind = GrappleRules.haul(shipId, nearShip == null ? null : nearShip.id());
        if (haulKind == GrappleRules.Haul.SHIPS || haulKind == GrappleRules.Haul.KEDGE) {
            if (tiedRing != null) {
                anchorPlot = RopeAnchor.point(level, tiedRing); // the ring (or cleat) is the rope's end
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
            taut = haulKind == GrappleRules.Haul.SHORE && shoreDrags() && throwerPos != null && GrappleRules.taut(horizontal(hookWorld, throwerPos),
                    GrappleConfig.holdLength(), GrappleConfig.SHORE_HAUL_FORCE.get());
        }
        RopeSwingService.tickRope(level, this, ship, hookWorld, owner); // GR7: the hand-held rope holds its thrower in the air
        boolean hauled = GrappleHaul.tick(level, this, ship, hookWorld, owner); // GR5: a frozen rope pulled taut by hand
        entityData.set(TAUT, taut && !holding.holding() || hauled); // a holding rope does not pull
    }

    /**
     * The ring, cleat or strong point (RAIL3) a hook latched {@link #onRing} holds is still there: a strong point is
     * still in {@code #grapple_latches}, a ring or cleat still a grapple anchor.
     */
    private boolean holdStands(ServerLevel level) {
        return onStrongPoint ? latchedBlock != null && level.getBlockState(latchedBlock).is(GrappleContent.GRAPPLE_LATCHES)
                : GrappleService.isGrappleAnchor(level, latchedBlock);
    }

    /**
     * RAIL3: a haul harder than {@code grapple.railing_break_force} in the last game tick tears out the strong point
     * the hook holds (it breaks and drops as if broken by hand) and the hook lets go. True when it did.
     */
    private boolean tearOut(ServerLevel level) {
        double last = load;
        load = 0.0;
        if (!onStrongPoint || latchedBlock == null || !GrappleRules.tornOut(last, GrappleConfig.RAILING_BREAK_FORCE.get())) {
            return false;
        }
        level.destroyBlock(latchedBlock, true);
        release(GrappleRules.Release.TORN_OUT);
        return true;
    }

    /** The world block at {@code pos} still stands with a collision shape for a hook to hold on (GR4). */
    private static boolean blockStands(ServerLevel level, @Nullable BlockPos pos) {
        return pos != null && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
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
        BlockState state = level.getBlockState(pos);
        GrappleRules.HitKind kind = GrappleRules.blockHit(ship == null ? null : ship.id(), throwerShip == null ? null : throwerShip.id(),
                !state.is(GrappleContent.HOOK_SLIPS), GrappleConfig.LATCH_OWN_SHIP.get(), GrappleConfig.LATCH_WORLD_BLOCKS.get());
        if (GrappleRules.latches(kind)) {
            boolean ring = state.getBlock() instanceof RopeAnchor anchor && GrappleService.anchorEnabled(anchor);
            // RAIL3: a railing (#grapple_latches) holds like a ring where the hook bit, and can be torn out
            boolean strong = !ring && GrappleRules.holdsLikeRing(false, state.is(GrappleContent.GRAPPLE_LATCHES),
                    GrappleConfig.LATCHES_HOLD.get());
            // GR4: the pose from the face it bit and the way it flew, both in the frame of that block
            boolean hangs = ring || state.is(GrappleContent.GRAPPLE_LATCHES);
            Direction face = result.getDirection();
            HookPose.Kind pose = HookPose.latchKind(face.getStepX(), face.getStepY(), face.getStepZ(), hangs);
            Vec3 v = inFrameOf(ship, getDeltaMovement());
            latch(level, ship, pos, ring ? ((RopeAnchor) state.getBlock()).anchorPoint(state, pos) : at, ring || strong,
                    HookPose.latched(pose, face.getStepX(), face.getStepY(), face.getStepZ(), v.x, v.y, v.z), pose);
            onStrongPoint = strong;
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
     * Latches the hook onto {@code ship} at plot position {@code at} on block {@code block}, or (GR4, {@code ship}
     * null) onto the world block {@code block} at world position {@code at}; {@code ring}: the block is a mooring
     * ring or cleat, which holds the hook harder ({@link #breakLength}).
     */
    void latch(ServerLevel level, @Nullable ShipBody ship, BlockPos block, Vec3 at, boolean ring, Quaternionf pose, HookPose.Kind kind) {
        entityData.set(LATCH_KIND, (byte) kind.ordinal());
        entityData.set(LATCH_ROT, new Quaternionf(pose));
        onRing = ring;
        onStrongPoint = false;
        load = 0.0;
        shipId = ship == null ? null : ship.id();
        plotPos = at;
        latchedBlock = block.immutable();
        entityData.set(PLOT_BLOCK, latchedBlock);
        entityData.set(PLOT_OFFSET, new Vector3f((float) (at.x - block.getX()), (float) (at.y - block.getY()), (float) (at.z - block.getZ())));
        setState(State.LATCHED);
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        Vec3 world = ship == null ? at : ship.toWorld(at);
        pendingPos = world;
        level.playSound(null, world.x, world.y, world.z, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0f, 0.9f);
        level.playSound(null, world.x, world.y, world.z, SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS, 0.8f, 1.0f);
        level.sendParticles(ParticleTypes.CRIT, world.x, world.y, world.z, 6, 0.1, 0.1, 0.1, 0.1);
        if (ship != null && getOwner() instanceof net.minecraft.server.level.ServerPlayer thrower) { // ADV1: onto another ship
            ShipBody near = nearEndShip(level, thrower);
            if (near == null || !near.id().equals(ship.id())) com.richardsenger.piratesnships.core.advancement.ModAdvancements.grappleLatched(thrower);
        }
    }

    /** A world direction {@code v} in the frame of {@code ship}'s plot (unchanged without a ship). */
    private static Vec3 inFrameOf(@Nullable ShipBody ship, Vec3 v) {
        if (ship == null) {
            return v;
        }
        Quaterniond toPlot = ship.orientation().conjugate();
        Vector3d d = toPlot.transform(new Vector3d(v.x, v.y, v.z));
        return new Vec3(d.x, d.y, d.z);
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
        haul.clear();
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
