package com.richardsenger.piratesnships.worldsim.lane;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sea lanes between registered ports (WS2, design.md §10.4). Lanes are found by {@link LanePathfinder} on the port
 * dimension's {@link BiomeSeaGrid} and cached in {@link LaneData}; a failed search is cached for
 * {@code lanes.retry_failed_ticks}.
 *
 * <p>Off the hot path: {@link #between} only reads the cache and, on a miss, queues the pair; the voyage scheduler
 * works on one queued lane per check for at most {@code lanes.millis_per_check} ms ({@link #computeNext}), so a long
 * search is spread over several checks. Biome samples are cached per dimension, so later searches over the same sea
 * are much cheaper. {@link #compute} searches at once (commands, tests).
 *
 * <pre>{@code
 * Optional<Lane> lane = Lanes.between(server, from, to);       // cached or queued
 * Lane.Position p = Lanes.positionAlong(lane.get(), 350.0);    // 350 blocks out
 * }</pre>
 */
public final class Lanes {

    /** A searched pair: the lane (if found), the pathfinder's result and the time it took. */
    public record Computed(Optional<Lane> lane, LanePathfinder.Result result, long nanos, long biomeSamples) {
    }

    public enum Status { READY, QUEUED, FAILED, UNKNOWN_PORT }

    private record Pair(ResourceLocation a, ResourceLocation b) {
    }

    private static final Map<String, Pair> QUEUE = new LinkedHashMap<>();
    private static final Map<ResourceKey<Level>, BiomeSeaGrid> GRIDS = new HashMap<>();
    /** A grid holding more cached cells than this is dropped and rebuilt (memory bound). */
    private static final long MAX_GRID_SAMPLES = 2_000_000;

    private Lanes() {
    }

    /** The cached lane from {@code a} to {@code b}; on a miss the pair is queued (unless it failed recently). */
    public static Optional<Lane> between(MinecraftServer server, ResourceLocation a, ResourceLocation b) {
        LaneData data = data(server);
        Optional<Lane> lane = data.lane(a, b);
        if (lane.isPresent()) return lane;
        if (!recentlyFailed(server, data, a, b)) QUEUE.putIfAbsent(LaneData.key(a, b), new Pair(a, b));
        return Optional.empty();
    }

    /** Where the pair stands, without queueing it. */
    public static Status status(MinecraftServer server, ResourceLocation a, ResourceLocation b) {
        PortRegistry ports = PortRegistry.get(server);
        if (ports.index().byId(a).isEmpty() || ports.index().byId(b).isEmpty()) return Status.UNKNOWN_PORT;
        LaneData data = data(server);
        if (data.lane(a, b).isPresent()) return Status.READY;
        if (recentlyFailed(server, data, a, b)) return Status.FAILED;
        return Status.QUEUED;
    }

    /**
     * Works on one queued lane for at most {@code lanes.millis_per_check} milliseconds: continues the search in progress
     * or starts the oldest queued one; a search that does not finish in time goes on in the next call. Returns whether
     * a lane search finished (found or failed) in this call.
     */
    public static boolean computeNext(MinecraftServer server) {
        long deadline = System.nanoTime() + VoyageConfig.MILLIS_PER_CHECK.get() * 1_000_000L;
        if (running == null) {
            Iterator<Pair> it = QUEUE.values().iterator();
            while (it.hasNext() && running == null) {
                Pair p = it.next();
                it.remove();
                LaneData data = data(server);
                if (data.lane(p.a, p.b).isPresent() || recentlyFailed(server, data, p.a, p.b)) continue;
                running = start(server, p);
            }
            if (running == null) return false;
        }
        Running r = running;
        long t0 = System.nanoTime();
        Optional<LanePathfinder.Result> result = r.search.step(deadline);
        r.nanos += System.nanoTime() - t0;
        if (result.isEmpty()) return false;
        running = null;
        store(server, r.from, r.to, result.get(), r.nanos, r.grid.samples() - r.samplesBefore);
        return true;
    }

    /** Pairs waiting for {@link #computeNext} (including one being searched). */
    public static int queued() {
        return QUEUE.size() + (running != null ? 1 : 0);
    }

    /** A background search in progress. */
    private static final class Running {
        final Port from, to;
        final BiomeSeaGrid grid;
        final LanePathfinder.Search search;
        final long samplesBefore;
        long nanos;

        Running(Port from, Port to, BiomeSeaGrid grid, LanePathfinder.Search search, long samplesBefore) {
            this.from = from;
            this.to = to;
            this.grid = grid;
            this.search = search;
            this.samplesBefore = samplesBefore;
        }
    }

    private static Running running;

    private static Running start(MinecraftServer server, Pair p) {
        Optional<Port> pa = PortRegistry.get(server).index().byId(p.a);
        Optional<Port> pb = PortRegistry.get(server).index().byId(p.b);
        if (pa.isEmpty() || pb.isEmpty()) return null;
        ServerLevel level = pa.get().dimension().equals(pb.get().dimension()) ? server.getLevel(pa.get().dimension()) : null;
        if (level == null) {
            data(server).putFailure(p.a, p.b, server.overworld().getGameTime());
            return null;
        }
        BiomeSeaGrid grid = grid(level);
        long before = grid.samples();
        long t0 = System.nanoTime();
        BlockPos s = endpoint(pa.get()), e = endpoint(pb.get());
        Running r = new Running(pa.get(), pb.get(), grid,
                new LanePathfinder.Search(grid, s.getX(), s.getZ(), e.getX(), e.getZ(), VoyageConfig.laneParams()), before);
        r.nanos = System.nanoTime() - t0;
        return r;
    }

    /** Searches the lane now on the ports' dimension grid and caches the outcome (lane or failure). */
    public static Computed compute(MinecraftServer server, ResourceLocation a, ResourceLocation b) {
        Optional<Port> pa = PortRegistry.get(server).index().byId(a);
        Optional<Port> pb = PortRegistry.get(server).index().byId(b);
        if (pa.isEmpty() || pb.isEmpty() || !pa.get().dimension().equals(pb.get().dimension())) {
            return new Computed(Optional.empty(), new LanePathfinder.Result(LanePathfinder.Status.NO_PATH, List.of(), 0, 0), 0, 0);
        }
        ServerLevel level = server.getLevel(pa.get().dimension());
        if (level == null) {
            return new Computed(Optional.empty(), new LanePathfinder.Result(LanePathfinder.Status.NO_PATH, List.of(), 0, 0), 0, 0);
        }
        return compute(server, pa.get(), pb.get(), grid(level));
    }

    /** Searches the lane between two ports on {@code grid} now and caches the outcome (tests pass their own grid). */
    public static Computed compute(MinecraftServer server, Port from, Port to, SeaGrid grid) {
        long samplesBefore = grid instanceof BiomeSeaGrid b ? b.samples() : 0;
        long t0 = System.nanoTime();
        BlockPos s = endpoint(from), e = endpoint(to);
        LanePathfinder.Result r = LanePathfinder.find(grid, s.getX(), s.getZ(), e.getX(), e.getZ(), VoyageConfig.laneParams());
        long nanos = System.nanoTime() - t0;
        long samples = grid instanceof BiomeSeaGrid b ? b.samples() - samplesBefore : 0;
        if (running != null && LaneData.key(running.from.id(), running.to.id()).equals(LaneData.key(from.id(), to.id()))) running = null;
        return store(server, from, to, r, nanos, samples);
    }

    /** Caches the outcome of a search (lane or failure) and logs it. */
    private static Computed store(MinecraftServer server, Port from, Port to, LanePathfinder.Result r, long nanos, long samples) {
        LaneData data = data(server);
        long now = server.overworld().getGameTime();
        QUEUE.remove(LaneData.key(from.id(), to.id()));
        if (!r.found()) {
            data.putFailure(from.id(), to.id(), now);
            Constants.LOG.debug("No lane {} -> {}: {} after {} cells", from.id(), to.id(), r.status(), r.expanded());
            return new Computed(Optional.empty(), r, nanos, samples);
        }
        Lane lane = Lane.of(from.id(), to.id(), r.waypoints(), now);
        data.putLane(lane);
        Constants.LOG.debug("Lane {} -> {}: {} blocks, {} waypoints, {} cells, {} samples, {} ms", from.id(), to.id(),
                Math.round(lane.length()), lane.waypoints().size(), r.expanded(), samples, nanos / 1_000_000);
        return new Computed(Optional.of(lane), r, nanos, samples);
    }

    /** Where a lane starts or ends at {@code port}: its first berth, else its centre. */
    public static BlockPos endpoint(Port port) {
        return port.berths().isEmpty() ? port.centre() : port.berths().get(0).pos();
    }

    /** The position {@code progress} blocks along {@code lane}. */
    public static Lane.Position positionAlong(Lane lane, double progress) {
        return lane.positionAlong(progress);
    }

    /** Forgets the cache entries of a port (its lanes and failures) and its queued pairs. */
    public static void forgetPort(MinecraftServer server, ResourceLocation port) {
        data(server).forgetPort(port);
        QUEUE.values().removeIf(p -> p.a.equals(port) || p.b.equals(port));
        if (running != null && (running.from.id().equals(port) || running.to.id().equals(port))) running = null;
    }

    // --- Contracts ------------------------------------------------------------------------------------------

    /** Distance and risk of a delivery route, for harbor master contracts. */
    public record Route(double distance, double risk, boolean viaLane) {
    }

    /**
     * The route between two registered ports in one dimension for contract rewards: the cached lane's length if there
     * is one (the pair is queued otherwise), else the straight distance between the endpoints; the risk grows with
     * the pirate islands within {@code lanes.risk_radius} of the lane (or of the straight line).
     */
    public static Optional<Route> route(MinecraftServer server, ResourceLocation a, ResourceLocation b) {
        Optional<Port> pa = PortRegistry.get(server).index().byId(a);
        Optional<Port> pb = PortRegistry.get(server).index().byId(b);
        if (pa.isEmpty() || pb.isEmpty() || !pa.get().dimension().equals(pb.get().dimension())) return Optional.empty();
        Optional<Lane> lane = between(server, a, b);
        List<Lane.Point> points = lane.map(Lane::waypoints).orElseGet(() -> {
            BlockPos s = endpoint(pa.get()), e = endpoint(pb.get());
            return List.of(new Lane.Point(s.getX(), s.getZ()), new Lane.Point(e.getX(), e.getZ()));
        });
        List<Lane.Point> islands = new ArrayList<>();
        for (Port p : PortRegistry.get(server).index().all()) {
            if (p.kind() == PortKind.PIRATE_ISLAND && p.dimension().equals(pa.get().dimension())
                    && !p.id().equals(a) && !p.id().equals(b)) {
                islands.add(new Lane.Point(p.centre().getX(), p.centre().getZ()));
            }
        }
        double risk = LaneRisk.risk(points, islands, VoyageConfig.RISK_RADIUS.get(), VoyageConfig.BASE_RISK.get(),
                VoyageConfig.RISK_PER_PIRATE_ISLAND.get());
        return Optional.of(new Route(Lane.length(points), risk, lane.isPresent()));
    }

    // --- Internals ------------------------------------------------------------------------------------------

    private static LaneData data(MinecraftServer server) {
        LaneData data = LaneData.get(server);
        data.checkVersion(VoyageConfig.VERSION.get());
        return data;
    }

    private static boolean recentlyFailed(MinecraftServer server, LaneData data, ResourceLocation a, ResourceLocation b) {
        return data.failure(a, b).filter(f -> server.overworld().getGameTime() - f.tick() < VoyageConfig.RETRY_FAILED_TICKS.get()).isPresent();
    }

    /** The dimension's grid, rebuilt when the cell size changed or it grew too large. */
    static BiomeSeaGrid grid(ServerLevel level) {
        int cell = VoyageConfig.CELL_BLOCKS.get();
        BiomeSeaGrid g = GRIDS.get(level.dimension());
        if (g == null || g.cellBlocks() != cell || g.samples() > MAX_GRID_SAMPLES) {
            g = BiomeSeaGrid.of(level, cell);
            GRIDS.put(level.dimension(), g);
        }
        return g;
    }

    /** Drops the queue and the grids (server stop). */
    public static void clear() {
        QUEUE.clear();
        GRIDS.clear();
        running = null;
    }
}
