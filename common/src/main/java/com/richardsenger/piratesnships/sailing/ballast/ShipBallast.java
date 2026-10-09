package com.richardsenger.piratesnships.sailing.ballast;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.CoreConfig;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntime;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.ship.assembly.ShipSplits;
import com.richardsenger.piratesnships.ship.hull.Compartment;
import com.richardsenger.piratesnships.ship.hull.HullAnalysis;
import com.richardsenger.piratesnships.ship.hull.HullGrid;
import com.richardsenger.piratesnships.ship.hull.HullVec;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullConfig;
import com.richardsenger.piratesnships.ship.hull.runtime.HullBuoyancy;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntime;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntimes;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.trade.cargo.CargoLoad;
import dev.ryanhcode.sable.api.physics.mass.MassTracker;
import dev.ryanhcode.sable.physics.chunk.VoxelNeighborhoodState;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * The shipwright's ballast on the server (docs/design.md §5.2, TRM1): for every ship of ours (one with a sailing
 * runtime) that is not a wreck, the {@link BallastRule} torque worked out from its blocks, recorded in the
 * {@code pirates_n_ships:ballast} force group every physics substep as a body-frame angular impulse, the way
 * {@code sailing.waves.WaveForces} records the waves.
 *
 * <p><b>Lifecycle.</b> Deterministic from the blocks, so nothing is saved: the first level tick that sees a ship's
 * sailing runtime (assembly, load, a split's new body) works it out. It is worked out again, at most once per
 * {@link #RECOMPUTE_TICKS}, when a block is added to or removed from the ship ({@link #onBlockChanged}: another block, or
 * the same block with a different Sable mass, volume or solidity, such as a slab doubled) and when the hull runtime's
 * analysis grid changes (its dry compartments lift too). A changed state of the same block that weighs the same (a
 * crate's {@code load}, which is normalised to empty anyway, a cannon's {@code shot}, waterlogging, a door) changes
 * nothing.
 *
 * <p><b>What counts as built.</b> Every block with a collision shape, at Sable's mass and volume for its state, with
 * cargo containers at their empty {@code load} state (so cargo trims the ship), and the hull runtime's dry compartment
 * cells (our dry lift, {@code HullBuoyancy}) when dry hulls are on.
 *
 * <p><b>When.</b> The torque acts only while the hull runtime sees the sea under the hull, faded in over the first half
 * of the design draft (so a ship lifting out of the water, on a slipway or on land gets none).
 */
public final class ShipBallast {

    /** Smallest gap between two computations of one ship [ticks]. */
    public static final int RECOMPUTE_TICKS = 20;

    private static final class Entry {
        BallastRule.Result result = BallastRule.Result.NONE;
        /** Torque in the plot frame [kpg·blocks²/s²], before the draft fade. */
        final Vector3d torque = new Vector3d();
        long computedAt = Long.MIN_VALUE / 2;
        boolean dirty = true;
        @Nullable HullAnalysis analysis;
        @Nullable HullGrid grid;
        /** Plot y of the lowest block's bottom face and the design draft above it (to the hull runtime's sea surface). */
        int minY;
        double designDraft;
        /** Fade factor of this game tick (0 = not afloat). */
        double scale;
        long gameTime = Long.MIN_VALUE;
        int computations;
        /** The config the result was worked out with; a change recomputes at once. */
        @Nullable BallastRule.Params params;
    }

    private static final Map<ServerLevel, Map<UUID, Entry>> LEVELS = new IdentityHashMap<>();

    private ShipBallast() {
    }

    // ------------------------------------------------------------------ game tick

    /** End of a level tick (after the sailing runtimes): finds new ships, recomputes dirty ones, measures the draft. */
    public static synchronized void onLevelTick(ServerLevel level) {
        java.util.Collection<SailingRuntime> runtimes = SailingRuntimes.runtimes(level);
        Map<UUID, Entry> m = LEVELS.get(level);
        if (runtimes.isEmpty()) {
            if (m != null) m.clear();
            return;
        }
        if (m == null) {
            m = new HashMap<>();
            LEVELS.put(level, m);
        }
        long now = level.getGameTime();
        BallastRule.Params params = SailingConfig.ballastParams();
        for (SailingRuntime rt : runtimes) {
            ShipBody ship = SableShips.byId(level, rt.id());
            if (ship == null || ship.isRemoved() || ShipSplits.isWreck(ship)) {
                m.remove(rt.id());
                continue;
            }
            Entry e = m.computeIfAbsent(rt.id(), id -> new Entry());
            e.gameTime = now;
            HullRuntime hull = HullRuntimes.get(level, ship.id());
            HullAnalysis analysis = hull == null ? null : hull.simulation().analysis();
            if (analysis != e.analysis) {
                // a re-analysis (block changes, a tilt): only a different grid can change the dry cells
                if (analysis == null || e.grid == null || !analysis.grid().equals(e.grid)) {
                    e.dirty = true;
                }
                e.analysis = analysis;
            }
            boolean configChanged = !params.equals(e.params);
            if (configChanged || e.dirty && now - e.computedAt >= RECOMPUTE_TICKS) {
                compute(ship, analysis, params, e);
                e.computedAt = now;
                e.dirty = false;
            }
            e.scale = scale(ship, hull, e);
        }
        m.values().removeIf(e -> e.gameTime < now);
    }

    private static void compute(ShipBody ship, @Nullable HullAnalysis analysis, BallastRule.Params params, Entry e) {
        BlockPos ref = ship.plotBounds()[0];
        BallastRule.Hull hull = new BallastRule.Hull();
        int minY = describe(ship.level(), ship.plotBlocks(), ref, hull);
        if (analysis != null) {
            dryCells(analysis, ref, hull);
        }
        double gravity = ship.gravity().length();
        BallastRule.Result r = BallastRule.solve(hull, gravity, params);
        boolean changed = r.torqueX() != e.torque.x || r.torqueZ() != e.torque.z;
        e.result = r;
        e.params = params;
        e.torque.set(r.torqueX(), 0.0, r.torqueZ());
        e.grid = analysis == null ? null : analysis.grid();
        e.minY = minY == Integer.MAX_VALUE ? ref.getY() : minY;
        e.designDraft = r.mass() > 0 ? r.waterline() - BallastRule.DRY_SURFACE_DROP - (e.minY - ref.getY()) : 0.0;
        e.computations++;
        if (changed && e.computations > 1) {
            ship.addVelocity(new Vector3d(), new Vector3d()); // wakes a resting body for the new total (docs/sable-notes.md §9.0i)
        }
        if (CoreConfig.DEBUG.get()) {
            Constants.LOG.info("Ballast of ship {}: {}", ship.id(), r);
        }
    }

    /**
     * Adds the blocks of {@code blocks} (plot positions) to {@code hull}, relative to {@code ref}; returns the lowest
     * plot y of a block with a collision shape ({@link Integer#MAX_VALUE} for none). Containers count at their empty
     * {@code load} state. Mass: {@code PhysicsBlockPropertyHelper#getMass} (l.24) at {@code MassTracker.BLOCK_CENTER_OF_MASS}
     * (l.33), as {@code MassTracker} sums a sub-level (l.100-125); volume: {@code PhysicsBlockPropertyHelper#getVolume}
     * (l.70); solidity: {@code VoxelNeighborhoodState#isSolid} (l.78), the test both use.
     */
    static int describe(BlockGetter level, List<BlockPos> blocks, BlockPos ref, BallastRule.Hull hull) {
        int minY = Integer.MAX_VALUE;
        for (BlockPos p : blocks) {
            BlockState state = designState(level.getBlockState(p));
            if (!VoxelNeighborhoodState.isSolid(level, p, state)) {
                continue;
            }
            double mass = PhysicsBlockPropertyHelper.getMass(level, p, state);
            double volume = PhysicsBlockPropertyHelper.getVolume(state);
            Vector3dc c = MassTracker.BLOCK_CENTER_OF_MASS.apply(level, state);
            double x = p.getX() - ref.getX(), y = p.getY() - ref.getY(), z = p.getZ() - ref.getZ();
            hull.block(x + 0.5, y + 0.5, z + 0.5, volume, mass, x + c.x(), y + c.y(), z + c.z());
            minY = Math.min(minY, p.getY());
        }
        return minY;
    }

    /** The state a block counts with: a cargo container empty ({@link CargoLoad#LOAD} 0), anything else as it is. */
    static BlockState designState(BlockState state) {
        return state.hasProperty(CargoLoad.LOAD) ? state.setValue(CargoLoad.LOAD, 0) : state;
    }

    /** Adds the dry compartment cells of {@code analysis}, lifting as our dry hull buoyancy does when it is on. */
    private static void dryCells(HullAnalysis analysis, BlockPos ref, BallastRule.Hull hull) {
        HullBuoyancy.Params p = DryHullConfig.buoyancy();
        if (!DryHullConfig.ENABLED.get() || !p.dryEnabled()) {
            return;
        }
        double lift = p.forcePerBlock() * p.dryScale();
        HullGrid g = analysis.grid();
        for (Compartment c : analysis.compartments()) {
            BitSet cells = c.cells();
            for (int i = cells.nextSetBit(0); i >= 0; i = cells.nextSetBit(i + 1)) {
                HullVec v = g.center(i);
                hull.dryCell(v.x() - ref.getX(), v.y() - ref.getY(), v.z() - ref.getZ(), lift);
            }
        }
    }

    /**
     * The fade of this tick: 0 without sea at the hull, else the draft at the centre of mass's column (the hull runtime's
     * sea surface above the bottom of the lowest block) over half the design draft, clamped to [0, 1].
     */
    private static double scale(ShipBody ship, @Nullable HullRuntime hull, Entry e) {
        if (hull == null || !hull.seesSea() || e.torque.lengthSquared() == 0.0) {
            return 0.0;
        }
        Vector3d com = new Vector3d();
        if (!ship.centerOfMass(com)) {
            return 0.0;
        }
        double bottom = ship.toWorld(com.set(com.x, e.minY, com.z), new Vector3d()).y;
        double draft = hull.seaWorldY() - bottom;
        double full = 0.5 * e.designDraft;
        if (!(full > 1.0e-3)) {
            return draft > 0.0 ? 1.0 : 0.0;
        }
        return Math.min(Math.max(draft / full, 0.0), 1.0);
    }

    // ------------------------------------------------------------------ physics substep

    /** Physics substep: records the ballast torque of every afloat ship. */
    public static synchronized void onPhysicsTick(ServerLevel level, double timeStep) {
        Map<UUID, Entry> m = LEVELS.get(level);
        if (m == null || m.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Vector3d impulse = new Vector3d();
        for (Map.Entry<UUID, Entry> en : m.entrySet()) {
            Entry e = en.getValue();
            if (e.scale <= 0.0 || e.gameTime < now - 1) {
                continue;
            }
            ShipBody ship = SableShips.byId(level, en.getKey());
            if (ship == null || ship.isRemoved()) {
                continue;
            }
            ship.applyBallastImpulse(impulse.set(e.torque).mul(e.scale * timeStep));
        }
    }

    // ------------------------------------------------------------------ block changes

    /**
     * From {@link com.richardsenger.piratesnships.ship.ShipBlockChanges}: a block added to or removed from one of our
     * ships marks its ballast for a recompute. Returns at once for the rest of the world's block changes.
     */
    public static synchronized void onBlockChanged(ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
        Map<UUID, Entry> m = LEVELS.get(level);
        if (m == null || m.isEmpty() || oldState == newState || !changesTheBuild(level, pos, oldState, newState)) {
            return;
        }
        ShipBody ship = SableShips.containing(level, pos);
        Entry e = ship == null ? null : m.get(ship.id());
        if (e != null) {
            e.dirty = true;
        }
    }

    /** Whether a block change adds, removes or replaces a block as far as the ballast is concerned. */
    static boolean changesTheBuild(BlockGetter level, BlockPos pos, BlockState oldState, BlockState newState) {
        if (oldState.getBlock() != newState.getBlock()) {
            return true;
        }
        BlockState a = designState(oldState), b = designState(newState);
        if (a == b) {
            return false;
        }
        boolean solidA = VoxelNeighborhoodState.isSolid(level, pos, a), solidB = VoxelNeighborhoodState.isSolid(level, pos, b);
        return solidA != solidB || solidA && (PhysicsBlockPropertyHelper.getMass(level, pos, a) != PhysicsBlockPropertyHelper.getMass(level, pos, b)
                || PhysicsBlockPropertyHelper.getVolume(a) != PhysicsBlockPropertyHelper.getVolume(b));
    }

    // ------------------------------------------------------------------ lifecycle and queries

    public static synchronized void onShipRemoved(ServerLevel level, UUID ship) {
        Map<UUID, Entry> m = LEVELS.get(level);
        if (m != null) {
            m.remove(ship);
        }
    }

    public static synchronized void clear() {
        LEVELS.clear();
    }

    /** The last ballast worked out for {@code ship}, or null when it has none (not ours, a wreck, not seen yet). */
    public static synchronized @Nullable BallastRule.Result result(ServerLevel level, UUID ship) {
        Map<UUID, Entry> m = LEVELS.get(level);
        Entry e = m == null ? null : m.get(ship);
        return e == null ? null : e.result;
    }

    /** This tick's draft fade of {@code ship} (0 = no ballast acts), or 0. */
    public static synchronized double scale(ServerLevel level, UUID ship) {
        Map<UUID, Entry> m = LEVELS.get(level);
        Entry e = m == null ? null : m.get(ship);
        return e == null ? 0.0 : e.scale;
    }

    /** How often {@code ship}'s ballast has been worked out (tests). */
    public static synchronized int computations(ServerLevel level, UUID ship) {
        Map<UUID, Entry> m = LEVELS.get(level);
        Entry e = m == null ? null : m.get(ship);
        return e == null ? 0 : e.computations;
    }
}
