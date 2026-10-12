package com.richardsenger.piratesnships.worldsim.materialize;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.wind.WindOverride;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.helm.HelmCourses;
import com.richardsenger.piratesnships.trade.TradeData;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.good.TradeGoods;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.GoodRole;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.trade.market.PortProfile;
import com.richardsenger.piratesnships.world.port.Berth;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.worldsim.lane.Lane;
import com.richardsenger.piratesnships.worldsim.lane.LanePathfinder;
import com.richardsenger.piratesnships.worldsim.lane.Lanes;
import com.richardsenger.piratesnships.worldsim.lane.SeaGrid;
import com.richardsenger.piratesnships.worldsim.voyage.Voyage;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageConfig;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageData;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageEnd;
import com.richardsenger.piratesnships.worldsim.voyage.Voyages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LANE1 GameTests: a village on a coast and a pirate island across open water, with a second island in the way, built
 * in a 160×160 basin (stone floor, water y=2..{@value #SEA_TOP}, stone coast and islands). The lane is searched on a
 * {@link SeaGrid} read from the basin's blocks (8-block cells: a cell is sea when its centre column is water at the
 * surface and three blocks down): the village's berth stands on the coast, so the lane starts in the nearest water
 * cell; it bends around the island in the way; a convoy departs on it, appears on its longest leg and sails on with the
 * wind astern. The ports are {@code gametest/lane1_…} (left alone by the scheduler and the materialiser's own check)
 * and removed at the end with their markets and lanes.
 */
public final class LaneVoyageGameTests {

    private static final String BATCH = "pirates_n_ships_config_worldsim_lane_voyage";
    private static final int SIZE = 160;
    private static final int SEA_TOP = 9;
    private static final int CELL = 8;
    /** The coast: x ≤ this is land. */
    private static final int COAST = 14;
    /** The island in the way: x, z ranges (relative). */
    private static final int ISLE_X0 = 60, ISLE_X1 = 95, ISLE_Z0 = 20, ISLE_Z1 = 80;
    /** The pirate island at the far side. */
    private static final int FAR_X0 = 132, FAR_X1 = 150, FAR_Z0 = 28, FAR_Z1 = 52;
    /** Berths (relative x, z): the village's on the coast's stone, the island's in the water west of it. */
    private static final int VILLAGE_X = 10, VILLAGE_Z = 40, ISLAND_X = 126, ISLAND_Z = 40;

    private LaneVoyageGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(LaneVoyageGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    private static void basin(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                boolean land = x == 0 || x == SIZE - 1 || z == 0 || z == SIZE - 1 || x <= COAST
                        || (x >= ISLE_X0 && x <= ISLE_X1 && z >= ISLE_Z0 && z <= ISLE_Z1)
                        || (x >= FAR_X0 && x <= FAR_X1 && z >= FAR_Z0 && z <= FAR_Z1);
                level.setBlock(h.absolutePos(new BlockPos(x, 1, z)), stone, Block.UPDATE_CLIENTS);
                for (int y = 2; y <= SEA_TOP + 1; y++) {
                    level.setBlock(h.absolutePos(new BlockPos(x, y, z)), land ? stone : y <= SEA_TOP ? water : air, Block.UPDATE_CLIENTS);
                }
                // the framework's barrier ceiling: the materialiser wants water open to the sky
                for (int y = 36; y <= 44; y++) {
                    BlockPos p = h.absolutePos(new BlockPos(x, y, z));
                    if (level.getBlockState(p).is(Blocks.BARRIER)) level.setBlock(p, air, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** The basin as lane cells: water at the surface and three blocks down at the cell's centre column; land outside. */
    private static SeaGrid grid(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos origin = h.absolutePos(BlockPos.ZERO);
        int surface = h.absolutePos(new BlockPos(0, SEA_TOP, 0)).getY();
        return SeaGrid.of(CELL, (cx, cz) -> water(level, origin, surface, cx * CELL + CELL / 2, cz * CELL + CELL / 2));
    }

    private static boolean water(ServerLevel level, BlockPos origin, int surface, int x, int z) {
        int rx = x - origin.getX(), rz = z - origin.getZ();
        if (rx < 1 || rz < 1 || rx >= SIZE - 1 || rz >= SIZE - 1) return false;
        return level.getFluidState(new BlockPos(x, surface, z)).is(FluidTags.WATER)
                && level.getFluidState(new BlockPos(x, surface - 3, z)).is(FluidTags.WATER);
    }

    private static Port port(GameTestHelper h, String name, PortKind kind, int x, int z) {
        BlockPos berth = h.absolutePos(new BlockPos(x, SEA_TOP, z));
        ResourceLocation id = Constants.id("gametest/lane1_" + name + "_" + UUID.randomUUID().toString().substring(0, 8));
        return new Port(id, kind, h.getLevel().dimension(), berth, BoundingBox.fromCorners(berth.offset(-4, -4, -4), berth.offset(4, 4, 4)),
                Climate.TEMPERATE, List.of(new Berth(berth, Direction.NORTH)));
    }

    private static PortProfile profile(PortKind kind, ResourceLocation produces, ResourceLocation demands) {
        Map<ResourceLocation, GoodRole> roles = new HashMap<>();
        for (ResourceLocation g : TradeService.goods(false).tradeable().ids()) roles.put(g, GoodRole.NEUTRAL);
        roles.put(produces, GoodRole.PRODUCES);
        roles.put(demands, GoodRole.DEMANDS);
        return new PortProfile(kind, Climate.TEMPERATE, 0L, roles);
    }

    private static void cleanUp(MinecraftServer server, Port... ports) {
        for (Port p : ports) {
            for (Voyage v : Voyages.active(server)) {
                if (v.from().equals(p.id()) || v.to().equals(p.id())) {
                    Voyages.end(server, v.id(), VoyageEnd.CANCELLED);
                    VoyageCrew.discard(server.overworld(), v.id());
                }
            }
            PortRegistry.get(server).remove(p.id());
            TradeData.get(server).removeMarket(p.id());
            Lanes.forgetPort(server, p.id());
        }
    }

    /** Every block along every leg is water (sampled each block). */
    private static void assertAtSea(GameTestHelper h, List<Lane.Point> points) {
        BlockPos origin = h.absolutePos(BlockPos.ZERO);
        int surface = h.absolutePos(new BlockPos(0, SEA_TOP, 0)).getY();
        for (int i = 1; i < points.size(); i++) {
            Lane.Point a = points.get(i - 1), b = points.get(i);
            double len = a.distanceTo(b);
            for (double t = 0; t <= len; t += 1.0) {
                int x = (int) Math.floor(a.x() + (b.x() - a.x()) * t / Math.max(1e-9, len));
                int z = (int) Math.floor(a.z() + (b.z() - a.z()) * t / Math.max(1e-9, len));
                h.assertTrue(water(h.getLevel(), origin, surface, x, z), "leg " + i + " crosses land at relative "
                        + (x - origin.getX()) + " " + (z - origin.getZ()) + ": " + points);
            }
        }
    }

    // ------------------------------------------------------------------ tests

    /**
     * The village's berth stands on the coast's stone: the lane starts in the nearest water cell (within
     * {@code endpoint_radius_cells}), bends around the island in the way and reaches the pirate island's berth; every
     * block of it is water. A convoy departs on the cached lane, appears on the middle of its longest leg, and with the
     * wind astern it sails on along the lane: its record's progress grows by several blocks.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_160_DEEP, timeoutTicks = 400, batch = BATCH)
    public static void villageLaneToAnIslandCarriesAConvoy(GameTestHelper h) {
        ConfigOverrides.during(h, MaterializeConfig.LINGER_TICKS, 2_000); // no player near: keep the ship real for the test
        basin(h);
        MinecraftServer server = h.getLevel().getServer();
        Port village = port(h, "village", PortKind.SEAFARER_VILLAGE, VILLAGE_X, VILLAGE_Z);
        Port island = port(h, "island", PortKind.PIRATE_ISLAND, ISLAND_X, ISLAND_Z);
        PortRegistry.get(server).add(village);
        PortRegistry.get(server).add(island);
        TradeService.openMarket(server, village.id(), () -> profile(PortKind.SEAFARER_VILLAGE, TradeGoods.SUGAR, TradeGoods.IRON));
        TradeService.openMarket(server, island.id(), () -> profile(PortKind.PIRATE_ISLAND, TradeGoods.IRON, TradeGoods.SUGAR));
        Runnable clean = () -> cleanUp(server, village, island);
        Voyage v = guarded(clean, () -> {
            List<Lane.Point> pts = checkLane(h, server, village, island);
            Voyage departed = Voyages.spawnConvoy(server, village.id(), island.id(), RandomSource.create(11)).orElseThrow();
            h.assertTrue(departed.waypoints().equals(pts), "the convoy does not sail the cached lane: " + departed.waypoints());
            h.assertTrue(departed.cargo().containsKey(TradeGoods.SUGAR), "cargo " + departed.cargo());
            return departed;
        });
        List<Lane.Point> pts = v.waypoints();
        // the middle of the longest leg, where the sloop has room
        int leg = 0;
        double best = -1, at = 0, sum = 0;
        for (int i = 1; i < pts.size(); i++) {
            double len = pts.get(i - 1).distanceTo(pts.get(i));
            if (len > best) {
                best = len;
                leg = i - 1;
                at = sum + len / 2;
            }
            sum += len;
        }
        double longest = best;
        guard(clean, () -> h.assertTrue(longest >= 40, "longest leg only " + longest + " blocks: " + pts));
        Voyage placed = v.withProgress(at);
        VoyageData.get(server).put(placed);
        double heading = Lane.heading(pts.get(leg), pts.get(leg + 1));
        String dim = h.getLevel().dimension().location().toString();
        WindOverride.set(dim, heading + 180.0, 8.0, h.getLevel().getGameTime() + 320);
        double[] start = new double[1];
        ShipBody[] ship = new ShipBody[1];
        h.runAtTickTime(5, () -> guard(clean, () -> {
            Materializer.Outcome o = Materializer.materialize(server, v.id());
            Voyage m = Voyages.get(server, v.id()).orElseThrow();
            m.shipId().ifPresent(id -> ShipTestCleanup.track(h, id));
            h.assertTrue(o == Materializer.Outcome.SPAWNED, "materialize: " + o + " at " + placed.position());
            ship[0] = SableShips.byId(h.getLevel(), m.shipId().orElseThrow());
            h.assertTrue(ship[0] != null, "no ship");
            h.assertTrue(HelmCourses.course(ship[0].id()) != null, "no course along the lane");
            start[0] = m.progress();
        }));
        h.onEachTick(() -> {
            if (ship[0] != null && h.getTick() > 5 && h.getTick() % 10 == 0) Materializer.update(server, v.id());
        });
        h.runAtTickTime(300, () -> {
            guard(clean, () -> {
                Voyage m = Voyages.get(server, v.id()).orElseThrow();
                h.assertTrue(m.state() == Voyage.State.MATERIALISED, "state " + m.state());
                Vec3 c = Materializer.centre(ship[0]);
                double sailed = m.progress() - start[0];
                h.assertTrue(sailed >= 4.0, "sailed only " + sailed + " blocks along the lane in 295 ticks (centre " + c + ")");
            });
            WindOverride.clear(dim);
            clean.run();
            h.succeed();
        });
    }

    /**
     * NAME3: the lane replies name ports by their proper names: "the sea around Port Marlow (...) is closed and never
     * reaches Fort St. Anne", and a voyage's line in {@code /pirates world voyages} reads "Port Marlow -> Fort St. Anne".
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_3, batch = "pirates_n_ships_worldsim_lane_names")
    public static void laneRepliesNamePortsByName(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Port village = port(h, "names_village", PortKind.SEAFARER_VILLAGE, 0, 0);
        Port outpost = port(h, "names_outpost", PortKind.NAVY_OUTPOST, 2, 2);
        PortRegistry.get(server).add(village);
        PortRegistry.get(server).add(outpost);
        String from = PortRegistry.get(server).index().byId(village.id()).orElseThrow().name();
        String to = PortRegistry.get(server).index().byId(outpost.id()).orElseThrow().name();
        Runnable clean = () -> cleanUp(server, village, outpost);
        guard(clean, () -> {
            var closed = new Lanes.Computed(java.util.Optional.empty(), new LanePathfinder.Result(LanePathfinder.Status.NO_PATH, List.of(),
                    40, 0.0, 1000, null, null, new LanePathfinder.Bounds(0, 0, 4, 4)), 1_000_000L, 0L, 300.0);
            String reply = com.richardsenger.piratesnships.worldsim.voyage.VoyageCommands.noLane(village.id(), outpost.id(), closed, 1).getString();
            h.assertTrue(reply.contains("the sea around " + from) && reply.contains("never reaches " + to), "the reply: " + reply);
            h.assertFalse(reply.contains(village.id().toString()) || reply.contains(outpost.id().getPath()), "no ids: " + reply);
            var budget = new Lanes.Computed(java.util.Optional.empty(), new LanePathfinder.Result(LanePathfinder.Status.BUDGET, List.of(),
                    1000, 0.0, 1000, null, null, null), 0L, 0L, 300.0);
            String ran = com.richardsenger.piratesnships.worldsim.voyage.VoyageCommands.noLane(village.id(), outpost.id(), budget, 1).getString();
            h.assertTrue(ran.startsWith("No lane from " + from + " to " + to), "the budget reply: " + ran);
        });
        clean.run();
        h.succeed();
    }

    /** Searches the lane on the basin's blocks and checks its start, its course around the island and its end. */
    private static List<Lane.Point> checkLane(GameTestHelper h, MinecraftServer server, Port village, Port island) {
        SeaGrid sea = grid(h);
        BlockPos vb = village.berths().get(0).pos();
        h.assertFalse(sea.isSea(sea.cellOf(vb.getX()), sea.cellOf(vb.getZ())), "the village berth's cell should be land");
        Lanes.Computed computed = Lanes.compute(server, village, island, sea);
        LanePathfinder.Result r = computed.result();
        h.assertTrue(computed.lane().isPresent(), "no lane: " + r.status() + " after " + r.expanded() + " of " + r.budget() + " cells");
        List<Lane.Point> pts = computed.lane().get().waypoints();
        LanePathfinder.Cell start = r.start();
        double startCells = Math.hypot(start.x() - sea.cellOf(vb.getX()), start.z() - sea.cellOf(vb.getZ()));
        h.assertTrue(startCells >= 1 && startCells <= VoyageConfig.ENDPOINT_RADIUS_CELLS.get(),
                "start cell " + start + " " + startCells + " cells from the berth");
        assertAtSea(h, pts);
        BlockPos origin = h.absolutePos(BlockPos.ZERO);
        boolean around = pts.stream().anyMatch(p -> p.z() - origin.getZ() < ISLE_Z0 || p.z() - origin.getZ() > ISLE_Z1);
        h.assertTrue(around && pts.size() > 2, "the lane does not bend around the island in the way: " + pts);
        Lane.Point last = pts.get(pts.size() - 1);
        BlockPos ib = island.berths().get(0).pos();
        h.assertTrue(Math.hypot(last.x() - ib.getX(), last.z() - ib.getZ()) <= CELL, "does not end at the island's berth: " + last);
        h.assertTrue(Lanes.between(server, village.id(), island.id()).isPresent(), "the lane was not cached");
        return pts;
    }

    /** Runs {@code body}; on a failure cleans up first (ports, markets, lanes, voyages), then fails. */
    private static void guard(Runnable cleanUp, Runnable body) {
        guarded(cleanUp, () -> {
            body.run();
            return null;
        });
    }

    private static <T> T guarded(Runnable cleanUp, java.util.function.Supplier<T> body) {
        try {
            return body.get();
        } catch (RuntimeException | Error e) {
            cleanUp.run();
            throw e;
        }
    }
}
