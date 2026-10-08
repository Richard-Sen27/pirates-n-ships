package com.richardsenger.piratesnships.ship.hull.runtime;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.CoreConfig;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.ship.hull.CellKind;
import com.richardsenger.piratesnships.ship.hull.Compartment;
import com.richardsenger.piratesnships.ship.hull.FloodingConfig;
import com.richardsenger.piratesnships.ship.hull.HullAnalysis;
import com.richardsenger.piratesnships.ship.hull.HullAnalyzer;
import com.richardsenger.piratesnships.ship.hull.HullGrid;
import com.richardsenger.piratesnships.ship.hull.HullVec;
import com.richardsenger.piratesnships.ship.hull.PartialCellRule;
import com.richardsenger.piratesnships.ship.hull.flooding.FloodReport;
import com.richardsenger.piratesnships.ship.hull.flooding.FloodSimulation;
import com.richardsenger.piratesnships.ship.hull.flooding.FloodTickInput;
import com.richardsenger.piratesnships.ship.hull.flooding.RecomputeDebouncer;
import com.richardsenger.piratesnships.ship.hull.pump.BilgePumpBlock;
import com.richardsenger.piratesnships.ship.hull.pump.BilgePumps;
import com.richardsenger.piratesnships.ship.hull.pump.PumpIntake;
import com.richardsenger.piratesnships.ship.hull.pump.PumpSet;
import com.richardsenger.piratesnships.ship.hull.world.HullBlockClassifier;
import com.richardsenger.piratesnships.ship.hull.world.HullGridSnapshotter;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.sable.WaterRegions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Server-side hull state of one loaded ship (docs/design.md §4.2-§4.5): grid snapshot, analysis, flood simulation,
 * breaches, the server's water occlusion regions and the client sync bookkeeping. Owned by {@link HullRuntimes}, used on
 * the server thread only (the analysis itself may run on {@link #ANALYSIS}).
 */
public final class HullRuntime {

    /** Key of the flood state in the sub-level's user data. */
    public static final String USER_DATA_KEY = Constants.MOD_ID + "_hull";

    /** One background thread for re-analyses: pure {@link HullAnalyzer} work on an immutable grid snapshot. */
    private static final ExecutorService ANALYSIS = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pirates_n_ships hull analysis");
        t.setDaemon(true);
        return t;
    });

    private final ServerLevel level;
    private final UUID id;
    private final BreachSet breaches = new BreachSet();
    /** Bilge pumps on this ship (from the grid snapshot, kept current on block changes) and the players' uses. */
    private final PumpSet pumps = new PumpSet();
    private final RecomputeDebouncer debouncer = FloodingConfig.newDebouncer();
    private FloodSimulation sim;

    private @Nullable CompletableFuture<HullAnalysis> pending;
    /** Door / hatch toggles since the pending snapshot was taken (packed plot pos → open), re-applied after rebind. */
    private final List<long[]> togglesSinceSnapshot = new ArrayList<>();

    private final List<WaterRegions.Handle> regions = new ArrayList<>();
    private List<CellSet> regionCells = List.of();
    /** Partial-block candidates of the current analysis ({@link PartialCellRule}), computed once per analysis. */
    private PartialCellRule partials = PartialCellRule.NONE;
    private boolean partialsEnabled = true;
    /** Partial cells the sea vetoed at the last check ({@link PartialCellRule#submergedCount}); a change rebuilds. */
    private int partialsSubmerged = -1;
    private int[] floodedCounts = new int[0];
    private boolean regionsDirty = true;
    private long lastRegionBuild = Long.MIN_VALUE / 2;
    private final Set<UUID> sentTo = new HashSet<>();

    /** Footprint probes of the current analysis ({@link SeaLevel#probes}), grid coordinates. */
    private List<int[]> probes = List.of();
    private double seaWorldY = Double.NaN;
    private double seaShipFrame = SeaLevel.NO_WATER;
    private @Nullable FloodReport lastReport;
    /** Working pumps per compartment in the last tick (empty when none worked), for the ship HUD. */
    private int[] pumpingNow = new int[0];
    private boolean saveDirty;
    private long ticks;

    private HullRuntime(ServerLevel level, UUID id) {
        this.level = level;
        this.id = id;
    }

    /** Creates the runtime with a synchronous first analysis, restoring a persisted flood state if the ship has one. */
    static HullRuntime create(ShipBody ship) {
        HullRuntime rt = new HullRuntime(ship.level(), ship.id());
        CompoundTag saved = ship.userData(USER_DATA_KEY);
        FloodState state = saved.isEmpty() ? null : FloodState.fromTag(saved);
        if (state != null) {
            rt.breaches.load(state.breaches());
        }
        rt.sim = new FloodSimulation(HullAnalyzer.analyze(rt.snapshot(ship), localUp(ship)), FloodingConfig.params());
        if (state != null) {
            state.applyWater(rt.sim);
        }
        rt.probes = SeaLevel.probes(rt.sim.analysis().grid());
        rt.partials = PartialCellRule.of(rt.sim.analysis());
        rt.sampleSea(ship);
        return rt;
    }

    public UUID id() {
        return id;
    }

    public FloodSimulation simulation() {
        return sim;
    }

    public BreachSet breaches() {
        return breaches;
    }

    public PumpSet pumps() {
        return pumps;
    }

    /**
     * The compartment the bilge pump at plot position {@code pumpPos} drains ({@link PumpIntake}), with the configured
     * reach, against the current analysis and water; -1 when its intake reaches none.
     */
    public int pumpIntake(BlockPos pumpPos) {
        HullAnalysis a = sim.analysis();
        HullGrid g = a.grid();
        return PumpIntake.find(a, pumpPos.getX() - g.originX(), pumpPos.getY() - g.originY(), pumpPos.getZ() - g.originZ(),
                FloodingConfig.PUMP_REACH.get(), c -> sim.volume(c) > PumpIntake.DRY);
    }

    public List<CellSet> regionCells() {
        return regionCells;
    }

    /** Whether a plot cell is in one of the ship's dry regions (server view). */
    public boolean inRegion(BlockPos plot) {
        for (CellSet s : regionCells) {
            if (s.contains(plot.getX(), plot.getY(), plot.getZ())) {
                return true;
            }
        }
        return false;
    }

    List<WaterRegions.Handle> regionHandles() {
        return regions;
    }

    public int regionCount() {
        return regions.size();
    }

    /** Whether world water touches the hull ({@link SeaLevel#hullSurface}); without it nothing floods or floats. */
    public boolean seesSea() {
        return Double.isFinite(seaWorldY);
    }

    /** World Y of the sea surface at the hull ({@link SeaLevel#hullSurface}), NaN when no water touches the hull. Read-only, for sailing. */
    public double seaWorldY() {
        return seaWorldY;
    }

    public double seaShipFrame() {
        return seaShipFrame;
    }

    public @Nullable FloodReport lastReport() {
        return lastReport;
    }

    /** Whether a pump drained compartment {@code compartment} in the last tick (ship HUD, HUD1). */
    public boolean pumping(int compartment) {
        return compartment >= 0 && compartment < pumpingNow.length && pumpingNow[compartment] > 0;
    }

    public boolean isAnalysing() {
        return pending != null || debouncer.isDirty();
    }

    // ------------------------------------------------------------------ game tick

    void tick(ShipBody ship) {
        ticks++;
        sim.setParams(FloodingConfig.params());
        sampleSea(ship);
        FloodTickInput input = FloodTickInput.calm(seaShipFrame);
        int[] working = workingPumps();
        pumpingNow = working == null ? new int[0] : working;
        lastReport = sim.tick(working == null ? input : input.withPumps(working));
        if (lastReport.inflow() > 0 || lastReport.outflow() > 0 || lastReport.pumped() > 0) {
            saveDirty = true;
        }

        HullVec up = localUp(ship);
        if (pending == null && (debouncer.tick() || sim.analysis().tiltExceeds(up, FloodingConfig.REANALYSIS_TILT_DEGREES.get()))) {
            startAnalysis(ship, up);
        }
        if (pending != null && pending.isDone()) {
            finishAnalysis();
        }

        updateRegions(ship);
        if (ticks % DryHullConfig.SYNC_CHECK_TICKS.get() == 0) {
            syncNewViewers(ship);
        }
        if (saveDirty && ticks % DryHullConfig.SAVE_INTERVAL_TICKS.get() == 0) {
            save(ship);
        }
    }

    /** Working pumps per compartment this tick (players' uses and crew orders), or null for none. */
    private int @Nullable [] workingPumps() {
        if (pumps.isEmpty()) {
            return null;
        }
        if (!FloodingConfig.PUMP_ENABLED.get()) {
            pumps.clearUses();
            return null;
        }
        return pumps.activeCounts(level.getGameTime(), sim.analysis().compartments().size(),
                p -> BilgePumps.crewOperating(id, p), this::pumpIntake);
    }

    private void startAnalysis(ShipBody ship, HullVec up) {
        HullGrid grid = snapshot(ship);
        togglesSinceSnapshot.clear();
        pending = DryHullConfig.ASYNC_ANALYSIS.get()
                ? CompletableFuture.supplyAsync(() -> HullAnalyzer.analyze(grid, up), ANALYSIS)
                : CompletableFuture.completedFuture(HullAnalyzer.analyze(grid, up));
    }

    private void finishAnalysis() {
        CompletableFuture<HullAnalysis> done = pending;
        pending = null;
        HullAnalysis next;
        try {
            next = done.join();
        } catch (RuntimeException e) {
            Constants.LOG.error("Hull analysis of ship {} failed", id, e);
            return;
        }
        double lost = sim.rebind(next);
        probes = SeaLevel.probes(next.grid());
        partials = PartialCellRule.of(next);
        HullGrid g = next.grid();
        for (long[] t : togglesSinceSnapshot) {
            BlockPos p = BlockPos.of(t[0]);
            sim.setOpen(p.getX() - g.originX(), p.getY() - g.originY(), p.getZ() - g.originZ(), t[1] != 0);
        }
        togglesSinceSnapshot.clear();
        if (lost > 0) {
            Constants.LOG.debug("Ship {}: {} blocks of water lost in re-analysis", id, lost);
        }
        regionsDirty = true;
        saveDirty = true;
    }

    /** Forces a synchronous re-analysis now (tests, commands). */
    public void reanalyseNow(ShipBody ship) {
        HullVec up = localUp(ship);
        HullGrid grid = snapshot(ship);
        togglesSinceSnapshot.clear();
        pending = CompletableFuture.completedFuture(HullAnalyzer.analyze(grid, up));
        finishAnalysis();
        lastRegionBuild = Long.MIN_VALUE / 2;
        updateRegions(ship);
    }

    private HullGrid snapshot(ShipBody ship) {
        BlockPos[] b = ship.plotBounds();
        Set<BlockPos> found = new HashSet<>();
        HullGrid grid = HullGridSnapshotter.snapshot(level, b[0].offset(-1, -1, -1), b[1].offset(1, 1, 1), breaches.positions(),
                state -> state.getBlock() instanceof BilgePumpBlock, found::add);
        pumps.replace(found);
        return grid;
    }

    // ------------------------------------------------------------------ sea level

    /**
     * Measures the sea at the hull itself (docs/design.md §4.5). For each footprint probe ({@link SeaLevel#probes}, the
     * lowest solid cell of up to nine columns) the world block just below the hull's bottom face, or the one that face is
     * in, must be water; the surface is then the top of that water column, followed upward at most
     * {@code sea_probe_height} blocks. {@link SeaLevel#hullSurface} turns the probes into the sea or none. Water beside
     * a dry dock or below a cliff does not touch the hull, so it never counts.
     */
    private void sampleSea(ShipBody ship) {
        int rise = DryHullConfig.SEA_PROBE_HEIGHT.get();
        HullGrid g = sim.analysis().grid();
        double[] samples = new double[probes.size()];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < samples.length; i++) {
            int[] c = probes.get(i);
            Vec3 w = ship.toWorld(new Vec3(g.originX() + c[0] + 0.5, g.originY() + c[1] + 0.5, g.originZ() + c[2] + 0.5));
            samples[i] = waterAtHull(pos, Mth.floor(w.x), w.y - 0.5, Mth.floor(w.z), rise);
        }
        double before = seaWorldY;
        seaWorldY = SeaLevel.hullSurface(samples);
        seaShipFrame = shipFrameSea(ship);
        if (Double.isFinite(before) != Double.isFinite(seaWorldY) && CoreConfig.DEBUG.get()) {
            Constants.LOG.info("Ship {}: {} ({} probes)", id,
                    Double.isFinite(seaWorldY) ? "afloat, sea at y=" + seaWorldY : "no sea at the hull", samples.length);
        }
    }

    /** World Y of the water surface touching the hull bottom ({@code bottomY}) in column x/z, or {@code NaN}. */
    private double waterAtHull(BlockPos.MutableBlockPos pos, int x, double bottomY, int z, int rise) {
        int y = Mth.floor(bottomY - 0.5);
        if (!isWater(pos.set(x, y, z))) {
            y++;
            if (!isWater(pos.set(x, y, z))) {
                return Double.NaN;
            }
        }
        for (int k = 0; k < rise && isWater(pos.set(x, y + 1, z)); k++) {
            y++;
        }
        pos.set(x, y, z);
        return y + level.getFluidState(pos).getHeight(level, pos);
    }

    private boolean isWater(BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.WATER);
    }

    /**
     * Sea level in ship-frame height along the analysis up vector (the frame the simulation's heights use), with the
     * analysis grid's center as reference point. Using the current up here is wrong: plot coordinates are in the
     * millions, so a tiny difference between the two up vectors shifts heights by hundreds of blocks. Exact at the
     * reference point; elsewhere off by the tilt since the last analysis (at most reanalysis_tilt_degrees).
     */
    private double shipFrameSea(ShipBody ship) {
        HullVec up = sim == null ? localUp(ship) : sim.analysis().up();
        HullGrid g = sim == null ? null : sim.analysis().grid();
        Vec3 ref = g == null ? Vec3.atCenterOf(ship.plotBounds()[0])
                : new Vec3(g.originX() + g.sizeX() / 2.0, g.originY() + g.sizeY() / 2.0, g.originZ() + g.sizeZ() / 2.0);
        if (!Double.isFinite(seaWorldY)) {
            // finite but far below the hull: the simulation drains instead of seeing infinities
            return up.dot(new HullVec(ref.x, ref.y, ref.z)) - 1.0e4;
        }
        return SeaLevel.inShipFrame(up, new HullVec(ref.x, ref.y, ref.z), ship.toWorld(ref).y, seaWorldY);
    }

    private static HullVec localUp(ShipBody ship) {
        Vector3d u = ship.localUp();
        return SeaLevel.up(u.x, u.y, u.z);
    }

    // ------------------------------------------------------------------ physics substep

    /** Records the buoyancy correction for this substep, for the ship's pose right now. */
    void physicsTick(ShipBody ship, double timeStep) {
        if (!DryHullConfig.ENABLED.get() || !Double.isFinite(seaWorldY)) {
            return;
        }
        HullVec up = localUp(ship);
        FloodReport report = sim.report(shipFrameSea(ship));
        for (HullBuoyancy.PointForce f : HullBuoyancy.forces(report, up, DryHullConfig.buoyancy())) {
            ship.applyBuoyancyImpulse(new Vector3d(f.point().x(), f.point().y(), f.point().z()),
                    new Vector3d(f.force().x(), f.force().y(), f.force().z()).mul(timeStep));
        }
    }

    // ------------------------------------------------------------------ block changes

    /** A block in the ship's plot changed (already set in the level). */
    void onBlockChanged(BlockPos pos, BlockState oldState, BlockState newState) {
        boolean wasPump = oldState.getBlock() instanceof BilgePumpBlock, isPump = newState.getBlock() instanceof BilgePumpBlock;
        if (isPump && !wasPump) {
            pumps.add(pos);
        } else if (wasPump && !isPump) {
            pumps.remove(pos);
        }
        CellKind oldKind = HullBlockClassifier.classify(oldState, level, pos);
        CellKind newKind = HullBlockClassifier.classify(newState, level, pos);
        boolean shapeChanged = HullBlockClassifier.uncoveredFaces(oldState, level, pos, oldKind)
                != HullBlockClassifier.uncoveredFaces(newState, level, pos, newKind);
        HullGrid g = sim.analysis().grid();
        int x = pos.getX() - g.originX(), y = pos.getY() - g.originY(), z = pos.getZ() - g.originZ();
        boolean inGrid = g.inBounds(x, y, z);
        if (oldKind == CellKind.OPENING && newKind == CellKind.OPENING && oldState.getBlock() == newState.getBlock()) {
            boolean open = HullBlockClassifier.isOpen(newState);
            togglesSinceSnapshot.add(new long[] {pos.asLong(), open ? 1 : 0});
            if (inGrid && sim.setOpen(x, y, z, open)) {
                return;
            }
        }
        if (breaches.onBlockChanged(pos, inGrid ? g.kind(x, y, z) : null, newKind)) {
            saveDirty = true;
            debouncer.markDirty();
            // A breach patched (a hull patch or any watertight block put back): close its opening at once, so the
            // inflow stops now instead of after the debounced re-analysis that turns the cell solid.
            if (newKind == CellKind.SOLID && inGrid && g.kind(x, y, z) == CellKind.OPENING && sim.setOpen(x, y, z, false)) {
                togglesSinceSnapshot.add(new long[] {pos.asLong(), 0});
            }
        }
        if (oldKind != newKind || shapeChanged || !inGrid) {
            debouncer.markDirty();
        }
    }

    // ------------------------------------------------------------------ regions and sync

    private void updateRegions(ShipBody ship) {
        int n = sim.analysis().compartments().size();
        if (floodedCounts.length != n) {
            floodedCounts = new int[n];
            Arrays.fill(floodedCounts, -1);
        }
        for (Compartment c : sim.analysis().compartments()) {
            int flooded = c.profile().countBelow(sim.level(c.id()));
            if (flooded != floodedCounts[c.id()]) {
                floodedCounts[c.id()] = flooded;
                regionsDirty = true;
            }
        }
        boolean partialsNow = DryHullConfig.PARTIAL_BLOCKS.get();
        if (partialsNow != partialsEnabled) {
            partialsEnabled = partialsNow;
            regionsDirty = true;
        }
        // deck hatches and doors join while the outside air they open to is above the sea (HV1, PartialCellRule)
        int submerged = partials.submergedCount(seaShipFrame);
        if (submerged != partialsSubmerged) {
            partialsSubmerged = submerged;
            regionsDirty = true;
        }
        if (!regionsDirty || ticks - lastRegionBuild < DryHullConfig.REGION_REBUILD_TICKS.get()) {
            return;
        }
        regionsDirty = false;
        lastRegionBuild = ticks;
        List<CellSet> cells = new ArrayList<>();
        if (DryHullConfig.ENABLED.get()) {
            HullGrid g = sim.analysis().grid();
            List<BitSet> dry = new ArrayList<>(n);
            for (Compartment c : sim.analysis().compartments()) {
                dry.add(sim.dryCells(c.id()));
            }
            // partial blocks whose empty part faces only dry cells, or outside air above the sea, are drawn dry too
            // (design.md §4.3, §4.4 HV1)
            BitSet[] extra = partialsEnabled && partials.candidateCount() > 0 ? partials.additions(dry, seaShipFrame) : null;
            for (Compartment c : sim.analysis().compartments()) {
                BitSet d = dry.get(c.id());
                if (extra != null && !d.isEmpty()) {
                    d.or(extra[c.id()]);
                }
                CellSet s = CellSet.fromGrid(g, d);
                if (!s.isEmpty()) {
                    cells.add(s);
                }
            }
        }
        if (cells.equals(regionCells) && regions.size() == cells.size()) {
            return;
        }
        clearServerRegions();
        for (CellSet s : cells) {
            WaterRegions.Handle h = WaterRegions.add(level, s.minX(), s.minY(), s.minZ(), s.sizeX(), s.sizeY(), s.sizeZ(), s.bits());
            if (h != null) {
                regions.add(h);
            }
        }
        regionCells = List.copyOf(cells);
        sentTo.clear();
        syncNewViewers(ship);
    }

    private void syncNewViewers(ShipBody ship) {
        List<UUID> tracking = ship.trackingPlayers();
        sentTo.retainAll(tracking);
        HullRegionsPayload payload = null;
        for (UUID uuid : tracking) {
            if (sentTo.contains(uuid) || !(level.getPlayerByUUID(uuid) instanceof ServerPlayer player)) {
                continue;
            }
            if (payload == null) {
                payload = new HullRegionsPayload(id, regionCells);
            }
            Services.NETWORK.sendToPlayer(player, payload);
            sentTo.add(uuid);
        }
    }

    private void clearServerRegions() {
        for (WaterRegions.Handle h : regions) {
            WaterRegions.remove(h);
        }
        regions.clear();
    }

    // ------------------------------------------------------------------ lifecycle

    void save(ShipBody ship) {
        ship.setUserData(USER_DATA_KEY, FloodState.capture(breaches, sim).toTag());
        saveDirty = false;
    }

    /** Drops the server regions and pending work. The ship's clients clean up through Sable's client removal. */
    void dispose(@Nullable ShipBody ship) {
        if (ship != null && !ship.isRemoved()) {
            save(ship);
        }
        clearServerRegions();
        regionCells = List.of();
        if (pending != null) {
            pending.cancel(false);
            pending = null;
        }
    }
}
