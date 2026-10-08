package com.richardsenger.piratesnships.sailing.ship;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.anchor.AnchorConfig;
import com.richardsenger.piratesnships.sailing.anchor.AnchorEntities;
import com.richardsenger.piratesnships.sailing.anchor.AnchorPhysics;
import com.richardsenger.piratesnships.sailing.anchor.AnchorTravel;
import com.richardsenger.piratesnships.sailing.block.CapstanBlock;
import com.richardsenger.piratesnships.sailing.force.AnchorState;
import com.richardsenger.piratesnships.sailing.helm.HelmBlockEntity;
import com.richardsenger.piratesnships.sailing.helm.HelmConfig;
import com.richardsenger.piratesnships.sailing.helm.WheelMath;
import com.richardsenger.piratesnships.ship.assembly.HelmBlock;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.Locale;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Server logic of the helm's rudder and the capstan's anchor (docs/design.md §5.3).
 *
 * <ul>
 *   <li><b>Rudder:</b> with wheel steering (HELM1, {@code helm.wheel.drag_steering}, the default) it follows the
 *       wheel angle of the helm's block entity, set through {@link #setWheel}; with the old click steps it lives in
 *       the helm's block state ({@link HelmBlock#RUDDER}). The runtime caches both, so the physics substep reads no
 *       blocks.</li>
 *   <li><b>Anchor:</b> one per ship, in the runtime and persisted in the ship's Sable user data
 *       ({@code pirates_n_ships_sailing.anchor}, {@link ShipAnchor#CODEC}) at every change. Every capstan of a ship
 *       works the same anchor; dropping hangs it from the capstan used, and only that capstan's block state shows the
 *       phase. Breaking that capstan loses the anchor. The anchor is a visible entity ({@code sailing.anchor}) and,
 *       since AN2a, a body of its own: it falls from the hawse at the hull side, lands where it falls, and its chain
 *       pulls the ship ({@code AnchorPhysics}).</li>
 * </ul>
 */
public final class ShipControls {

    public static final String KEY = Constants.MOD_ID + ".sailing.";
    public static final String KEY_RUDDER_MIDSHIPS = KEY + "rudder.midships";
    public static final String KEY_RUDDER = KEY + "rudder.step";
    public static final String KEY_PORT = KEY + "rudder.port";
    public static final String KEY_STARBOARD = KEY + "rudder.starboard";
    public static final String KEY_STEERING_OFF = KEY + "rudder.disabled";
    public static final String KEY_CAPSTAN_NOT_ON_SHIP = KEY + "capstan.not_on_ship";
    public static final String KEY_CAPSTAN_OFF = KEY + "capstan.disabled";
    public static final String KEY_NO_GROUND = KEY + "capstan.no_ground";
    public static final String KEY_DROPPING = KEY + "capstan.dropping";
    public static final String KEY_RAISING = KEY + "capstan.raising";
    /** CRW3: a drop while the anchor is out. */
    public static final String KEY_ALREADY_OUT = KEY + "capstan.already_out";
    /** CRW3: a raise while the anchor is stowed. */
    public static final String KEY_STOWED = KEY + "capstan.stowed";

    static final String ANCHOR_TAG = "anchor";

    private ShipControls() {
    }

    // ------------------------------------------------------------------ helm

    /** {@link HelmBlock.SteeringHandler}: one rudder step towards the clicked side, or midships. */
    public static Component steer(ServerLevel level, BlockPos pos, BlockState state, Player player, BlockHitResult hit) {
        if (!SailingConfig.STEERING_ENABLED.get()) {
            return Component.translatable(KEY_STEERING_OFF);
        }
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        Vec3 h = hit.getLocation();
        RudderSteps.Click click = RudderSteps.click(h.x - (pos.getX() + 0.5), h.z - (pos.getZ() + 0.5),
                facing.getStepX(), facing.getStepZ());
        return setRudder(level, pos, click);
    }

    /** Applies a rudder click to the helm at {@code pos} (plot) and returns the helmsman's message. */
    public static Component setRudder(ServerLevel level, BlockPos pos, RudderSteps.Click click) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof HelmBlock)) {
            return Component.empty();
        }
        int steps = SailingConfig.RUDDER_STEPS.get();
        int step = RudderSteps.apply(RudderSteps.fromProperty(state.getValue(HelmBlock.RUDDER)), click, steps);
        level.setBlock(pos, state.setValue(HelmBlock.RUDDER, RudderSteps.toProperty(step)), Block.UPDATE_ALL); // runtime follows the block change
        // HELM1: the wheel shows the step, so it agrees with the rudder (and stays there if wheel steering comes back)
        setWheel(level, pos, WheelMath.wheelForFraction(RudderSteps.angle(step, steps, 1.0), HelmConfig.lockAngle()));
        return rudderMessage(step, steps, SailingConfig.MAX_RUDDER_ANGLE.get());
    }

    /**
     * HELM1: turns the wheel of the helm at {@code pos} (plot) to {@code degrees} (positive = starboard): saved and
     * synced in its block entity, and handed to the ship's runtime, whose rudder follows it with wheel steering.
     */
    public static void setWheel(ServerLevel level, BlockPos pos, double degrees) {
        if (level.getBlockEntity(pos) instanceof HelmBlockEntity be) {
            be.setWheel(degrees);
        }
        ShipBody ship = SableShips.containing(level, pos);
        SailingRuntime rt = ship == null ? null : SailingRuntimes.get(level, ship.id());
        if (rt != null) {
            rt.setWheelAngle(degrees);
        }
    }

    /** What {@link #setRudderAngle} did. */
    public enum RudderResult {
        /** The wheel and the rudder step now show the angle. */
        SET,
        /** A player holds the wheel of this helm (HELM1 session): nothing changed. */
        PLAYER_AT_WHEEL,
        /** No helm at that position. */
        NOT_A_HELM
    }

    /**
     * WS3a: puts the rudder of the helm at {@code helmPlotPos} to {@code degrees} (positive = starboard, clamped to
     * {@code sailing.max_rudder_angle}) whichever way the ship is steered: turns the wheel to the matching angle (the
     * rudder's source with {@code helm.wheel.drag_steering}) and sets the nearest click step in the block state (its
     * source without). For the NPC helmsman ({@code station.helm.HelmCourses}); refused while a player holds that
     * wheel. Writes nothing that already shows the angle, so calling it every few ticks sends no needless updates.
     */
    public static RudderResult setRudderAngle(ServerLevel level, BlockPos helmPlotPos, double degrees) {
        BlockState state = level.getBlockState(helmPlotPos);
        if (!(state.getBlock() instanceof HelmBlock)) {
            return RudderResult.NOT_A_HELM;
        }
        if (playerAtWheel(level, helmPlotPos)) {
            return RudderResult.PLAYER_AT_WHEEL;
        }
        double max = SailingConfig.MAX_RUDDER_ANGLE.get();
        double fraction = max <= 0.0 || !Double.isFinite(degrees) ? 0.0 : Math.max(-1.0, Math.min(1.0, degrees / max));
        int steps = SailingConfig.RUDDER_STEPS.get();
        int step = (int) Math.round(fraction * Math.max(1, Math.min(RudderSteps.MAX_STEPS, steps)));
        int property = RudderSteps.toProperty(step);
        if (state.getValue(HelmBlock.RUDDER) != property) {
            level.setBlock(helmPlotPos, state.setValue(HelmBlock.RUDDER, property), Block.UPDATE_ALL); // runtime follows the block change
        }
        double wheel = WheelMath.wheelForFraction(fraction, HelmConfig.lockAngle());
        ShipBody ship = SableShips.containing(level, helmPlotPos);
        SailingRuntime rt = ship == null ? null : SailingRuntimes.get(level, ship.id());
        boolean shown = level.getBlockEntity(helmPlotPos) instanceof HelmBlockEntity be && Math.abs(be.wheel() - wheel) < 1e-3;
        if (!shown || rt != null && Math.abs(rt.wheelAngle() - wheel) >= 1e-3) {
            setWheel(level, helmPlotPos, wheel);
        }
        return RudderResult.SET;
    }

    /** Whether a player holds the wheel of the helm at {@code helmPlotPos} right now (HELM1 session). */
    public static boolean playerAtWheel(ServerLevel level, BlockPos helmPlotPos) {
        return com.richardsenger.piratesnships.sailing.helm.HelmService.isHeld(level, helmPlotPos);
    }

    public static Component rudderMessage(int step, int stepsPerSide, double maxAngle) {
        if (step == 0) {
            return Component.translatable(KEY_RUDDER_MIDSHIPS);
        }
        double angle = RudderSteps.angle(step, stepsPerSide, maxAngle);
        return Component.translatable(KEY_RUDDER, Math.abs(step), stepsPerSide,
                Component.translatable(step > 0 ? KEY_STARBOARD : KEY_PORT), String.format(Locale.ROOT, "%.0f", Math.abs(angle)));
    }

    // ------------------------------------------------------------------ capstan

    /** What {@link #dropAnchor} or {@link #raiseAnchor} did, or what {@link #dropCheck} says a drop would do. */
    public enum AnchorResult {
        /** The anchor was let go (or would be): it falls from the hawse, or runs out again from where it was winding in. */
        DROPPING,
        /** The capstan winds the chain in. */
        RAISING,
        /** A drop: the anchor is out already (falling or holding). */
        ALREADY_OUT,
        /** A raise: the capstan winds the chain in already. */
        ALREADY_RAISING,
        /** A raise: the anchor is stowed at the hawse. */
        STOWED,
        /** A drop: no ground within the chain's length straight below the hawse. */
        NO_GROUND,
        /** {@code sailing_runtime.anchor_enabled} is off. */
        DISABLED,
        /** The capstan is not on an assembled ship. */
        NOT_ON_SHIP;

        /** Whether the anchor's phase changed (or, for {@link #dropCheck}, would change). */
        public boolean changed() {
            return this == DROPPING || this == RAISING;
        }
    }

    /** The outcome of a capstan command (CRW3) and the line the player is shown. */
    public record CapstanResult(AnchorResult result, Component message) {
    }

    /** {@link #dropCheck}'s answer: {@code depth} [blocks] is meaningful for {@link AnchorResult#DROPPING} only. */
    public record DropCheck(AnchorResult result, double depth) {
    }

    /**
     * What a drop at the capstan {@code pos} (plot) would do now, without doing it: {@link AnchorResult#DROPPING} with
     * the depth the anchor falls when it can go, else why not. No side effects on the anchor or the world (the ship's
     * sailing runtime may be created, it is a cache). The crew's capstan station asks it before an order starts.
     */
    public static DropCheck dropCheck(ServerLevel level, BlockPos pos) {
        return planDrop(level, pos).check();
    }

    /** A planned drop: the check and what applying it needs (a fresh drop has a hawse, a reversal has none). */
    private record DropPlan(DropCheck check, @Nullable ShipBody ship, @Nullable SailingRuntime rt, @Nullable ShipAnchor anchor,
                            @Nullable Vec3 hawsePlot, @Nullable Vec3 hawse) {
        static DropPlan of(AnchorResult r) {
            return new DropPlan(new DropCheck(r, 0.0), null, null, null, null, null);
        }
    }

    private static DropPlan planDrop(ServerLevel level, BlockPos pos) {
        ShipBody ship = SableShips.containing(level, pos);
        SailingRuntime rt = ship == null ? null : SailingRuntimes.getOrCreate(ship);
        if (ship == null || rt == null) {
            return DropPlan.of(AnchorResult.NOT_ON_SHIP);
        }
        if (!SailingConfig.ANCHOR_ENABLED.get()) {
            return DropPlan.of(AnchorResult.DISABLED);
        }
        ShipAnchor a = rt.anchor();
        AnchorState.Phase phase = a == null ? AnchorState.Phase.RAISED : a.state().phase();
        if (phase == AnchorState.Phase.DROPPING || phase == AnchorState.Phase.HOLDING) {
            return DropPlan.of(AnchorResult.ALREADY_OUT);
        }
        if (phase == AnchorState.Phase.RAISING) {
            // reversed mid-way: the anchor runs out again from where it is
            Vec3 ring = a.ring();
            OptionalInt ground = groundBelow(level, ring, SailingConfig.ANCHOR_CHAIN_LENGTH.get());
            double depth = ground.isEmpty() ? 0.0 : Math.max(0.0, ring.y - AnchorTravel.HEIGHT - ground.getAsInt());
            return new DropPlan(new DropCheck(AnchorResult.DROPPING, depth), ship, rt, a, null, null);
        }
        // the anchor leaves the hawse at the hull side with the ship's velocity there; refused without ground in reach
        Vec3 hawsePlot = AnchorEntities.hawse(level, rt.bow(), pos);
        Vec3 hawse = ship.toWorld(hawsePlot);
        OptionalInt floor = groundBelow(level, hawse, SailingConfig.ANCHOR_CHAIN_LENGTH.get());
        if (floor.isEmpty()) {
            return DropPlan.of(AnchorResult.NO_GROUND);
        }
        return new DropPlan(new DropCheck(AnchorResult.DROPPING, AnchorTravel.distance(hawse.y, floor.getAsInt())),
                ship, rt, a, hawsePlot, hawse);
    }

    /**
     * Lets the ship's anchor go from the capstan at {@code pos} (plot), AN2a: from the hawse with the hawse's velocity
     * ({@link ShipAnchor#dropped}), or, while the capstan winds it in, again from where it is. Refused when there is no
     * ground within the chain's length straight below the hawse; nothing happens when it is out already.
     */
    public static CapstanResult dropAnchor(ServerLevel level, BlockPos pos) {
        DropPlan plan = planDrop(level, pos);
        AnchorResult r = plan.check().result();
        if (r != AnchorResult.DROPPING) {
            return new CapstanResult(r, message(r));
        }
        ShipBody ship = plan.ship();
        SailingRuntime rt = plan.rt();
        ShipAnchor a = plan.anchor();
        if (plan.hawse() == null) {
            setAnchor(ship, rt, a.withState(a.state().drop()));
        } else {
            ShipAnchor dropped = ShipAnchor.dropped(pos, plan.hawsePlot(), plan.hawse(), ship.velocityAt(plan.hawsePlot()));
            if (a != null && !a.capstan().equals(pos)) {
                showPhase(level, a.capstan(), AnchorState.Phase.RAISED);
            }
            setAnchor(ship, rt, dropped);
        }
        double depth = plan.check().depth();
        return new CapstanResult(r, Component.translatable(KEY_DROPPING, fmt(depth), seconds(depth / AnchorConfig.SINK_SPEED.get() * 20.0)));
    }

    /**
     * Heaves the ship's anchor in from the capstan at {@code pos} (plot), AN2a: the capstan winds the chain in at
     * {@code anchor_chain.raise_speed} by itself until the anchor is stowed. Nothing happens when it winds already
     * ({@link AnchorResult#ALREADY_RAISING}) or the anchor is stowed ({@link AnchorResult#STOWED}).
     */
    public static CapstanResult raiseAnchor(ServerLevel level, BlockPos pos) {
        ShipBody ship = SableShips.containing(level, pos);
        SailingRuntime rt = ship == null ? null : SailingRuntimes.getOrCreate(ship);
        if (ship == null || rt == null) {
            return new CapstanResult(AnchorResult.NOT_ON_SHIP, message(AnchorResult.NOT_ON_SHIP));
        }
        if (!SailingConfig.ANCHOR_ENABLED.get()) {
            return new CapstanResult(AnchorResult.DISABLED, message(AnchorResult.DISABLED));
        }
        ShipAnchor a = rt.anchor();
        AnchorState.Phase phase = a == null ? AnchorState.Phase.RAISED : a.state().phase();
        if (phase == AnchorState.Phase.RAISED) {
            return new CapstanResult(AnchorResult.STOWED, message(AnchorResult.STOWED));
        }
        Component raising = Component.translatable(KEY_RAISING, seconds(raiseTicks(chainOut(ship, a))));
        if (phase == AnchorState.Phase.RAISING) {
            return new CapstanResult(AnchorResult.ALREADY_RAISING, raising);
        }
        setAnchor(ship, rt, a.withState(a.state().raise()));
        return new CapstanResult(AnchorResult.RAISING, raising);
    }

    /**
     * Use of a capstan at {@code pos} (plot) by a player: drops the ship's anchor, or raises it when it is out (AN2a);
     * using the capstan while it winds lets the anchor go again from where it is. The toggle over {@link #dropAnchor}
     * and {@link #raiseAnchor} (CRW3 split them for the crew's capstan station).
     */
    public static Component useCapstan(ServerLevel level, BlockPos pos) {
        AnchorState.Phase phase = anchorPhase(level, pos);
        boolean out = phase == AnchorState.Phase.DROPPING || phase == AnchorState.Phase.HOLDING;
        return (out ? raiseAnchor(level, pos) : dropAnchor(level, pos)).message();
    }

    /**
     * The phase of the anchor of the ship the capstan at {@code pos} (plot) stands on ({@link AnchorState.Phase#RAISED}
     * when it is stowed), or null when it is on no assembled ship. A query.
     */
    public static AnchorState.@Nullable Phase anchorPhase(ServerLevel level, BlockPos pos) {
        ShipBody ship = SableShips.containing(level, pos);
        SailingRuntime rt = ship == null ? null : SailingRuntimes.getOrCreate(ship);
        if (rt == null) {
            return null;
        }
        ShipAnchor a = rt.anchor();
        return a == null ? AnchorState.Phase.RAISED : a.state().phase();
    }

    /**
     * Chain the capstan at {@code pos} (plot) still has to wind in to stow the anchor [blocks]: the paid-out length or
     * the distance from the hawse to the anchor's ring, whichever is longer; 0 when the anchor is stowed or there is no
     * ship. A query.
     */
    public static double chainOut(ServerLevel level, BlockPos pos) {
        ShipBody ship = SableShips.containing(level, pos);
        SailingRuntime rt = ship == null ? null : SailingRuntimes.getOrCreate(ship);
        ShipAnchor a = rt == null ? null : rt.anchor();
        return a == null ? 0.0 : chainOut(ship, a);
    }

    private static double chainOut(ShipBody ship, ShipAnchor a) {
        return Math.max(a.paidOut(), a.ring().distanceTo(ship.toWorld(a.hawse())));
    }

    /** Ticks the capstan needs to wind {@code chain} blocks in at {@code anchor_chain.raise_speed}. */
    public static double raiseTicks(double chain) {
        return chain / AnchorConfig.RAISE_SPEED.get() * 20.0;
    }

    private static Component message(AnchorResult r) {
        return switch (r) {
            case NOT_ON_SHIP -> Component.translatable(KEY_CAPSTAN_NOT_ON_SHIP);
            case DISABLED -> Component.translatable(KEY_CAPSTAN_OFF);
            case NO_GROUND -> Component.translatable(KEY_NO_GROUND, SailingConfig.ANCHOR_CHAIN_LENGTH.get());
            case ALREADY_OUT -> Component.translatable(KEY_ALREADY_OUT);
            case STOWED -> Component.translatable(KEY_STOWED);
            default -> Component.empty();
        };
    }

    /** Top of the first solid block straight below {@code from} within {@code reach} blocks ({@link AnchorGround}). */
    private static OptionalInt groundBelow(ServerLevel level, Vec3 from, int reach) {
        int x = (int) Math.floor(from.x), z = (int) Math.floor(from.z);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        return AnchorGround.floorY((int) Math.floor(from.y), reach, y -> level.getBlockState(m.set(x, y, z)).blocksMotion());
    }

    /**
     * Game tick: moves the anchor of one ship ({@link AnchorPhysics#tick}: falling, dragging, winding in), keeps its
     * state in the runtime, the ship's user data and the capstan's block state, and places the visible anchor
     * ({@link AnchorEntities#sync}).
     */
    static void tickAnchor(ShipBody ship, SailingRuntime rt) {
        ShipAnchor a = rt.anchor();
        if (a != null) {
            AnchorPhysics.Step step = AnchorPhysics.tick(ship, a, rt.anchorEnabled());
            ShipAnchor next = step.anchor();
            if (next == null) {
                setAnchor(ship, rt, null);
                showPhase(ship.level(), a.capstan(), AnchorState.Phase.RAISED);
            } else if (!next.equals(a)) {
                setAnchor(ship, rt, next);
            }
            rt.setAnchorStatus(step.status());
        }
        AnchorEntities.sync(ship, rt.bow(), rt.anchor(), rt.anchorStatus());
    }

    /** Sets the ship's anchor in the runtime and its user data and shows the phase on the capstan. Null = stowed. */
    static void setAnchor(ShipBody ship, SailingRuntime rt, @Nullable ShipAnchor anchor) {
        rt.setAnchor(anchor);
        CompoundTag data = ship.userData(SailingRuntimes.USER_DATA_KEY);
        if (anchor == null) {
            data.remove(ANCHOR_TAG);
        } else {
            ShipAnchor.CODEC.encodeStart(NbtOps.INSTANCE, anchor).ifSuccess(t -> data.put(ANCHOR_TAG, t));
            showPhase(ship.level(), anchor.capstan(), anchor.state().phase());
        }
        ship.setUserData(SailingRuntimes.USER_DATA_KEY, data);
    }

    /** The anchor stored in a ship's sailing user data, or null. */
    static @Nullable ShipAnchor readAnchor(CompoundTag data) {
        if (!data.contains(ANCHOR_TAG)) {
            return null;
        }
        return ShipAnchor.CODEC.parse(NbtOps.INSTANCE, data.get(ANCHOR_TAG)).resultOrPartial(e ->
                Constants.LOG.warn("Dropping unreadable anchor state: {}", e)).orElse(null);
    }

    static void showPhase(ServerLevel level, BlockPos capstan, AnchorState.Phase phase) {
        BlockState s = level.getBlockState(capstan);
        CapstanBlock.Phase shown = CapstanBlock.Phase.of(phase);
        if (s.getBlock() instanceof CapstanBlock && s.getValue(CapstanBlock.ANCHOR) != shown) {
            level.setBlock(capstan, s.setValue(CapstanBlock.ANCHOR, shown), Block.UPDATE_ALL);
        }
    }

    private static String seconds(double ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0);
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.0f", d);
    }
}
