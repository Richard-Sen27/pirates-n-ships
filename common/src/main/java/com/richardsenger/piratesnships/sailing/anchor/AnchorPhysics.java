package com.richardsenger.piratesnships.sailing.anchor;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.force.AnchorState;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntime;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.sailing.ship.ShipAnchor;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.HashMap;
import java.util.WeakHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Server side of the anchor's physics (docs/design.md §5.2, AN2a): moves the anchor's body every game tick and pulls the
 * ship with the chain every physics substep.
 *
 * <ul>
 *   <li><b>Game tick</b> ({@link #tick}, from {@code ShipControls.tickAnchor}): a dropped anchor falls and sinks
 *       ({@link AnchorMotion#fall}) from the hawse with the hawse's velocity at the drop, so a ship with way on leaves it
 *       astern, and lands where it falls; the chain pays out with the distance up to
 *       {@code sailing_runtime.anchor_chain_length}, after which the anchor hangs at the chain's end and does not hold.
 *       On the seabed the paid-out length stays (the capstan's brake). When the ship pulls harder than the anchor holds
 *       the anchor drags toward the ship at up to {@code anchor_chain.drag_scrape_rate}; raising winds the chain in at
 *       {@code raise_speed} and pulls the anchor along the seabed and up ({@link AnchorMotion#heave}).</li>
 *   <li><b>Physics substep</b> ({@link #onPhysicsTick}): a resting anchor's chain pulls the ship at the hawse
 *       ({@link AnchorChain#force}) in the {@code pirates_n_ships:anchor} force group; the heeling and pitching part of
 *       its moment is scaled by {@code anchor_chain.heel_factor}. Because the hawse sits at the hull side, a ship with
 *       way on swings toward that side around its anchor and comes to rest head to the chain.</li>
 * </ul>
 */
public final class AnchorPhysics {

    /** Ticks after the last scrape during which the anchor still counts as dragging (and not as holding). */
    public static final int DRAG_MEMORY_TICKS = 10;
    /** Game tick length [s]. */
    static final double DT = 0.05;

    private static final Map<ServerLevel, Map<UUID, Long>> LAST_DRAG = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<UUID, Vector3d>> LAST_FORCE = new WeakHashMap<>();

    /**
     * Result of one game tick.
     *
     * @param anchor the anchor afterwards, null when it was stowed this tick
     * @param status what it does (null when stowed)
     */
    public record Step(@Nullable ShipAnchor anchor, @Nullable AnchorStatus status) { }

    /** Config values of one tick. */
    record Settings(double sinkSpeed, double waterDrag, double chainLength, double raiseSpeed, double scrapeRate,
                    double atRestSpeed, AnchorChain.Params chain) {

        static Settings current() {
            return new Settings(AnchorConfig.SINK_SPEED.get(), AnchorConfig.WATER_DRAG.get(), SailingConfig.ANCHOR_CHAIN_LENGTH.get(),
                    AnchorConfig.RAISE_SPEED.get(), AnchorConfig.DRAG_SCRAPE_RATE.get(), AnchorConfig.AT_REST_SPEED.get(),
                    AnchorConfig.chainParams());
        }
    }

    private AnchorPhysics() {
    }

    public static void registerEvents() {
        SableShips.onPhysicsTick(AnchorPhysics::onPhysicsTick);
        SableShips.onShipRemoved((level, id, destroyed) -> forget(level, id));
        CommonEvents.SERVER_STOPPED.register(server -> {
            LAST_DRAG.clear();
            LAST_FORCE.clear();
        });
    }

    /** The world's blocks for {@link AnchorMotion}: unloaded cells count as solid, so the anchor never loads chunks. */
    public static AnchorMotion.Terrain terrain(ServerLevel level) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        return new AnchorMotion.Terrain() {
            @Override
            public boolean solid(int x, int y, int z) {
                m.set(x, y, z);
                return !level.isLoaded(m) || level.getBlockState(m).blocksMotion();
            }

            @Override
            public boolean water(int x, int y, int z) {
                m.set(x, y, z);
                return level.isLoaded(m) && level.getFluidState(m).is(FluidTags.WATER);
            }
        };
    }

    /**
     * One game tick of the anchor of {@code ship}.
     *
     * @param enabled whether anchors hold ({@code sailing_runtime.anchor_enabled}); off, the anchor still moves
     */
    public static Step tick(ShipBody ship, ShipAnchor a, boolean enabled) {
        ServerLevel level = ship.level();
        Settings s = Settings.current();
        AnchorMotion.Terrain t = terrain(level);
        Vec3 hawse = ship.toWorld(a.hawse());
        AnchorMotion.Body b = a.body();
        if (a.paidOut() < 0.0) {
            b = b.withPaidOut(a.ring().distanceTo(hawse)); // loaded from an old save
        }
        AnchorState state = a.state();
        boolean scraped = false;
        boolean atEnd = false;
        switch (state.phase()) {
            case DROPPING -> {
                b = AnchorMotion.fall(b, hawse, DT, s.sinkSpeed(), s.waterDrag(), s.chainLength(), t);
                atEnd = b.atChainEnd();
                if (b.resting()) {
                    state = state.landed();
                }
            }
            case HOLDING -> {
                if (!AnchorMotion.supported(b.pos(), t)) {
                    b = new AnchorMotion.Body(b.pos(), Vec3.ZERO, b.paidOut(), false, false);
                    state = state.lifted();
                    break;
                }
                Vec3 d = b.ring().subtract(hawse);
                double dist = d.length();
                double horizontal = dist > 1.0e-9 ? Math.sqrt(d.x * d.x + d.z * d.z) / dist : 0.0;
                double step = AnchorChain.dragStep(dist - b.paidOut(), horizontal, s.chain(), s.scrapeRate(), DT);
                if (step > 0.0) {
                    AnchorMotion.Body dragged = AnchorMotion.drag(b, hawse, step, t);
                    scraped = !dragged.pos().equals(b.pos());
                    b = dragged;
                    if (!b.resting()) {
                        state = state.lifted();
                    }
                }
            }
            case RAISING -> {
                double l = b.paidOut() - s.raiseSpeed() * DT;
                if (l <= 0.0) {
                    forget(level, ship.id());
                    return new Step(null, null);
                }
                b = AnchorMotion.heave(b, hawse, l, DT, s.sinkSpeed(), s.waterDrag(), t);
            }
            case RAISED -> {
                return new Step(null, null);
            }
        }
        ShipAnchor next = a.withBody(b).withState(state);
        long now = level.getGameTime();
        Map<UUID, Long> drags = LAST_DRAG.computeIfAbsent(level, l -> new HashMap<>());
        if (scraped) {
            drags.put(ship.id(), now);
        }
        Long last = drags.get(ship.id());
        boolean dragging = last != null && now - last <= DRAG_MEMORY_TICKS;
        Vec3 ring = next.ring();
        double distance = ring.distanceTo(hawse);
        boolean resting = next.resting();
        boolean taut = state.phase() == AnchorState.Phase.DROPPING ? atEnd : AnchorChain.taut(distance, next.paidOut());
        boolean holding = enabled && resting && state.phase() == AnchorState.Phase.HOLDING && s.chain().holding() > 0.0 && !dragging;
        boolean anchored = holding && horizontalSpeed(ship) < s.atRestSpeed();
        return new Step(next, new AnchorStatus(hawse, ring, next.paidOut(), distance, resting, taut, dragging, holding, anchored));
    }

    private static double horizontalSpeed(ShipBody ship) {
        Vector3d lin = new Vector3d();
        ship.velocities(lin, new Vector3d());
        return Math.sqrt(lin.x * lin.x + lin.z * lin.z);
    }

    /** The last chain force on {@code ship} (world, kpg·blocks/s²), or null when the chain did not pull. For tests and debug. */
    public static @Nullable Vector3d lastForce(ServerLevel level, UUID ship) {
        Map<UUID, Vector3d> m = LAST_FORCE.get(level);
        Vector3d f = m == null ? null : m.get(ship);
        return f == null ? null : new Vector3d(f);
    }

    private static void forget(ServerLevel level, UUID ship) {
        Map<UUID, Long> d = LAST_DRAG.get(level);
        if (d != null) {
            d.remove(ship);
        }
        Map<UUID, Vector3d> f = LAST_FORCE.get(level);
        if (f != null) {
            f.remove(ship);
        }
    }

    /** Physics substep: every resting anchor's chain pulls its ship at the hawse. */
    static void onPhysicsTick(ServerLevel level, double timeStep) {
        if (!SailingConfig.ANCHOR_ENABLED.get()) {
            LAST_FORCE.remove(level);
            return;
        }
        AnchorChain.Params params = null;
        double heel = 0.0;
        Vector3d com = new Vector3d(), comWorld = new Vector3d(), hawsePlot = new Vector3d(), hawseWorld = new Vector3d();
        Vector3d lin = new Vector3d(), ang = new Vector3d(), v = new Vector3d(), ring = new Vector3d();
        Quaterniond q = new Quaterniond();
        for (SailingRuntime rt : SailingRuntimes.runtimes(level)) {
            ShipAnchor a = rt.anchor();
            Map<UUID, Vector3d> forces = LAST_FORCE.computeIfAbsent(level, l -> new HashMap<>());
            if (a == null || !rt.anchorEnabled() || a.state().phase() != AnchorState.Phase.HOLDING || !a.resting() || a.paidOut() < 0.0) {
                forces.remove(rt.id());
                continue;
            }
            ShipBody ship = SableShips.byId(level, rt.id());
            double mass = ship == null ? 0.0 : ship.mass();
            if (ship == null || !(mass > 0.0) || !ship.centerOfMass(com)) {
                continue;
            }
            if (params == null) {
                params = AnchorConfig.chainParams();
                heel = AnchorConfig.HEEL_FACTOR.get();
            }
            try {
                ship.velocities(lin, ang);
                ship.toWorld(com, comWorld);
                hawsePlot.set(a.hawse().x, a.hawse().y, a.hawse().z);
                ship.toWorld(hawsePlot, hawseWorld);
                ang.cross(v.set(hawseWorld).sub(comWorld), v).add(lin); // velocity of the hawse
                Vec3 r = a.ring();
                AnchorChain.Result res = AnchorChain.force(hawseWorld, ring.set(r.x, r.y, r.z), a.paidOut(), v, mass, params);
                Vector3d world = res.force();
                if (!world.isFinite() || world.lengthSquared() == 0.0) {
                    forces.remove(rt.id());
                    continue;
                }
                forces.put(rt.id(), new Vector3d(world));
                Vector3d local = ship.orientation(q).transformInverse(new Vector3d(world));
                Vector3d torque = new Vector3d(hawsePlot).sub(com).cross(local);
                torque.x *= heel; // roll and pitch (the plot's horizontal axes); yaw stays full
                torque.z *= heel;
                ship.applyAnchorImpulse(local.mul(timeStep), torque.mul(timeStep));
            } catch (RuntimeException e) {
                Constants.LOG.error("Anchor chain of ship {} failed", rt.id(), e);
            }
        }
    }
}
