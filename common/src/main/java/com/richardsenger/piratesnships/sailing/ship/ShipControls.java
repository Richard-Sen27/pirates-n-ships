package com.richardsenger.piratesnships.sailing.ship;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.anchor.AnchorConfig;
import com.richardsenger.piratesnships.sailing.anchor.AnchorEntities;
import com.richardsenger.piratesnships.sailing.anchor.AnchorTravel;
import com.richardsenger.piratesnships.sailing.block.CapstanBlock;
import com.richardsenger.piratesnships.sailing.force.AnchorState;
import com.richardsenger.piratesnships.sailing.force.SailingParams;
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
 *       phase. Breaking that capstan loses the anchor. The anchor is a visible entity ({@code sailing.anchor}) that
 *       runs out from the hawse at the hull side; a trip takes as long as the chain needs for the depth.</li>
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

    /** Use of a capstan at {@code pos} (plot): drops the ship's anchor, or raises it when it is out. */
    public static Component useCapstan(ServerLevel level, BlockPos pos) {
        ShipBody ship = SableShips.containing(level, pos);
        SailingRuntime rt = ship == null ? null : SailingRuntimes.getOrCreate(ship);
        if (ship == null || rt == null) {
            return Component.translatable(KEY_CAPSTAN_NOT_ON_SHIP);
        }
        if (!SailingConfig.ANCHOR_ENABLED.get()) {
            return Component.translatable(KEY_CAPSTAN_OFF);
        }
        ShipAnchor a = rt.anchor();
        if (a != null && (a.state().phase() == AnchorState.Phase.DROPPING || a.state().phase() == AnchorState.Phase.HOLDING)) {
            setAnchor(ship, rt, a.withState(a.state().raise()));
            return Component.translatable(KEY_RAISING, seconds(a.state().hold() * a.raiseTicks()));
        }
        if (a != null && a.state().phase() == AnchorState.Phase.RAISING) {
            // reversed mid-way: the anchor runs out again from where it is to the same point
            setAnchor(ship, rt, a.withState(a.state().drop()));
            return Component.translatable(KEY_DROPPING, fmt(ship.toWorld(a.hawse()).y - AnchorTravel.HEIGHT - a.point().y),
                    seconds((1.0 - a.state().hold()) * a.dropTicks()));
        }
        // the anchor runs out from the hawse at the hull side, straight down to the first solid block in reach
        Vec3 hawsePlot = AnchorEntities.hawse(level, rt.bow(), pos);
        Vec3 hawse = ship.toWorld(hawsePlot);
        int x = (int) Math.floor(hawse.x), z = (int) Math.floor(hawse.z);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        OptionalInt floor = AnchorGround.floorY((int) Math.floor(hawse.y), SailingConfig.ANCHOR_CHAIN_LENGTH.get(),
                y -> level.getBlockState(m.set(x, y, z)).blocksMotion());
        if (floor.isEmpty()) {
            return Component.translatable(KEY_NO_GROUND, SailingConfig.ANCHOR_CHAIN_LENGTH.get());
        }
        Vec3 point = new Vec3(hawse.x, floor.getAsInt(), hawse.z);
        double distance = AnchorTravel.distance(hawse.y, point.y);
        int dropTicks, raiseTicks;
        if (AnchorConfig.DEPTH_TRAVEL.get()) {
            int min = AnchorConfig.MIN_TRAVEL_TICKS.get(), max = AnchorConfig.MAX_TRAVEL_TICKS.get();
            dropTicks = AnchorTravel.ticks(distance, AnchorConfig.DROP_SPEED.get(), min, max);
            raiseTicks = AnchorTravel.ticks(distance, AnchorConfig.RAISE_SPEED.get(), min, max);
        } else {
            SailingParams.AnchorParams p = SailingConfig.sailingParams().anchor();
            dropTicks = p.dropTicks();
            raiseTicks = p.raiseTicks();
        }
        ShipAnchor dropped = new ShipAnchor(AnchorState.RAISED.drop(), point, pos.immutable(), hawsePlot, dropTicks, raiseTicks);
        if (a != null && !a.capstan().equals(pos)) {
            showPhase(level, a.capstan(), AnchorState.Phase.RAISED);
        }
        setAnchor(ship, rt, dropped);
        return Component.translatable(KEY_DROPPING, fmt(distance), seconds(dropTicks));
    }

    /**
     * Game tick: advances the anchor state machine of one ship at this trip's travel times, keeps the capstan's block
     * state in step and places the visible anchor ({@link AnchorEntities#sync}).
     */
    static void tickAnchor(ShipBody ship, SailingRuntime rt, SailingParams.AnchorParams p) {
        ShipAnchor a = rt.anchor();
        if (a != null) {
            AnchorState next = a.state().tick(a.travelParams(p));
            if (!next.equals(a.state())) {
                setAnchor(ship, rt, next.phase() == AnchorState.Phase.RAISED ? null : a.withState(next));
                if (next.phase() == AnchorState.Phase.RAISED) {
                    showPhase(ship.level(), a.capstan(), AnchorState.Phase.RAISED);
                }
            }
        }
        AnchorEntities.sync(ship, rt.bow(), rt.anchor());
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
