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

    public static Component rudderMessage(int step, int stepsPerSide, double maxAngle) {
        if (step == 0) {
            return Component.translatable(KEY_RUDDER_MIDSHIPS);
        }
        double angle = RudderSteps.angle(step, stepsPerSide, maxAngle);
        return Component.translatable(KEY_RUDDER, Math.abs(step), stepsPerSide,
                Component.translatable(step > 0 ? KEY_STARBOARD : KEY_PORT), String.format(Locale.ROOT, "%.0f", Math.abs(angle)));
    }

    // ------------------------------------------------------------------ capstan

    /**
     * Use of a capstan at {@code pos} (plot): drops the ship's anchor, or raises it when it is out (AN2a). A drop lets the
     * anchor go from the hawse with the hawse's velocity ({@link ShipAnchor#dropped}); it is refused when there is no
     * ground within the chain's length straight below the hawse. Raising winds the chain in at
     * {@code anchor_chain.raise_speed}; using the capstan while it winds lets the anchor go again from where it is.
     */
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
            double chain = Math.max(a.paidOut(), a.ring().distanceTo(ship.toWorld(a.hawse())));
            return Component.translatable(KEY_RAISING, seconds(chain / AnchorConfig.RAISE_SPEED.get() * 20.0));
        }
        if (a != null && a.state().phase() == AnchorState.Phase.RAISING) {
            // reversed mid-way: the anchor runs out again from where it is
            setAnchor(ship, rt, a.withState(a.state().drop()));
            Vec3 ring = a.ring();
            OptionalInt ground = groundBelow(level, ring, SailingConfig.ANCHOR_CHAIN_LENGTH.get());
            double depth = ground.isEmpty() ? 0.0 : Math.max(0.0, ring.y - AnchorTravel.HEIGHT - ground.getAsInt());
            return Component.translatable(KEY_DROPPING, fmt(depth), seconds(depth / AnchorConfig.SINK_SPEED.get() * 20.0));
        }
        // the anchor leaves the hawse at the hull side with the ship's velocity there; refused without ground in reach
        Vec3 hawsePlot = AnchorEntities.hawse(level, rt.bow(), pos);
        Vec3 hawse = ship.toWorld(hawsePlot);
        OptionalInt floor = groundBelow(level, hawse, SailingConfig.ANCHOR_CHAIN_LENGTH.get());
        if (floor.isEmpty()) {
            return Component.translatable(KEY_NO_GROUND, SailingConfig.ANCHOR_CHAIN_LENGTH.get());
        }
        double depth = AnchorTravel.distance(hawse.y, floor.getAsInt());
        ShipAnchor dropped = ShipAnchor.dropped(pos, hawsePlot, hawse, ship.velocityAt(hawsePlot));
        if (a != null && !a.capstan().equals(pos)) {
            showPhase(level, a.capstan(), AnchorState.Phase.RAISED);
        }
        setAnchor(ship, rt, dropped);
        return Component.translatable(KEY_DROPPING, fmt(depth), seconds(depth / AnchorConfig.SINK_SPEED.get() * 20.0));
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
