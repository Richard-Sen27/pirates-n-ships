package com.richardsenger.piratesnships.worldsim.voyage;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.trade.TradeConfig;
import com.richardsenger.piratesnships.trade.TradeData;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.contract.ContractGenerator;
import com.richardsenger.piratesnships.trade.desk.HarborDeskService;
import com.richardsenger.piratesnships.trade.good.TradeGood;
import com.richardsenger.piratesnships.trade.good.TradeGoods;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.GoodRole;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.trade.market.PortProfile;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.worldsim.WorldSimConfig;
import com.richardsenger.piratesnships.worldsim.lane.BiomeSeaGrid;
import com.richardsenger.piratesnships.worldsim.lane.Lane;
import com.richardsenger.piratesnships.worldsim.lane.LanePathfinder;
import com.richardsenger.piratesnships.worldsim.lane.Lanes;
import com.richardsenger.piratesnships.worldsim.lane.SeaGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WS2 GameTests: two fake ports far east of the test area ({@code gametest/ws2_…} ids, removed at the end) with markets
 * whose roles are fixed (A produces sugar and demands iron, B the reverse, everything else neutral) and a lane on
 * {@link SeaGrid#allSea}. Convoys trade on departure and arrival, move by speed × interval, freeze when the simulation
 * is off, respect the spawn rate and the voyage cap; the flat test world has no sea, so its lane fails and is cached;
 * contract destinations use the lane length and pirate risk; one test measures a lane on a real overworld biome map.
 */
public final class VoyageGameTests {

    private static final String BATCH = "pirates_n_ships_worldsim_voyages";
    private static final String CONFIG_SPEED = "pirates_n_ships_config_worldsim_voyage_speed";
    private static final String CONFIG_DISABLED = "pirates_n_ships_config_worldsim_voyage_disabled";
    private static final String CONFIG_RATE = "pirates_n_ships_config_worldsim_voyage_rate";
    private static final String CONFIG_CAP = "pirates_n_ships_config_worldsim_voyage_cap";
    /** Blocks between the two ports' centres. */
    private static final int DISTANCE = 600;

    /** End reasons by voyage id (listener registered once). */
    private static final Map<UUID, VoyageEnd> ENDS = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> SPAWNS = new ConcurrentHashMap<>();

    static {
        Voyages.onEnd((server, voyage, reason) -> {
            if (voyage.from().getPath().startsWith("gametest/ws2_")) ENDS.put(voyage.id(), reason);
        });
        Voyages.onSpawn((server, voyage) -> {
            if (voyage.from().getPath().startsWith("gametest/ws2_")) SPAWNS.put(voyage.id(), true);
        });
    }

    private VoyageGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(VoyageGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    private record Harbors(MinecraftServer server, Port a, Port b, List<Port> extra) {
        void cleanUp() {
            List<ResourceLocation> ids = new ArrayList<>(List.of(a.id(), b.id()));
            extra.forEach(p -> ids.add(p.id()));
            for (Voyage v : Voyages.active(server)) {
                if (ids.contains(v.from()) || ids.contains(v.to())) Voyages.end(server, v.id(), VoyageEnd.CANCELLED);
            }
            for (ResourceLocation id : ids) {
                PortRegistry.get(server).remove(id);
                TradeData.get(server).removeMarket(id);
                Lanes.forgetPort(server, id);
            }
        }
    }

    private static Port port(GameTestHelper h, String name, int dx, PortKind kind) {
        BlockPos centre = h.absolutePos(BlockPos.ZERO).offset(20_000 + dx, 0, 0).atY(63);
        ResourceLocation id = Constants.id("gametest/ws2_" + name + "_" + UUID.randomUUID().toString().substring(0, 8));
        return new Port(id, kind, h.getLevel().dimension(), centre,
                BoundingBox.fromCorners(centre.offset(-8, -8, -8), centre.offset(8, 8, 8)), Climate.TEMPERATE, List.of());
    }

    private static PortProfile profile(ResourceLocation produces, ResourceLocation demands) {
        Map<ResourceLocation, GoodRole> roles = new HashMap<>();
        for (ResourceLocation g : TradeService.goods(false).tradeable().ids()) roles.put(g, GoodRole.NEUTRAL);
        roles.put(produces, GoodRole.PRODUCES);
        roles.put(demands, GoodRole.DEMANDS);
        return new PortProfile(PortKind.SEAFARER_VILLAGE, Climate.TEMPERATE, 0L, roles);
    }

    /** Ports A (sugar → iron) and B (iron → sugar) {@value #DISTANCE} blocks apart with a lane on open sea. */
    private static Harbors harbors(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Port a = port(h, "a", 0, PortKind.SEAFARER_VILLAGE);
        Port b = port(h, "b", DISTANCE, PortKind.SEAFARER_VILLAGE);
        PortRegistry.get(server).add(a);
        PortRegistry.get(server).add(b);
        TradeService.openMarket(server, a.id(), () -> profile(TradeGoods.SUGAR, TradeGoods.IRON));
        TradeService.openMarket(server, b.id(), () -> profile(TradeGoods.IRON, TradeGoods.SUGAR));
        Lanes.Computed lane = Lanes.compute(server, a, b, SeaGrid.allSea(32));
        h.assertTrue(lane.lane().isPresent(), "lane on open sea");
        return new Harbors(server, a, b, new ArrayList<>());
    }

    private static double price(MinecraftServer server, ResourceLocation port, ResourceLocation good) {
        TradeGood def = TradeService.goods(false).tradeable().require(good);
        return TradeService.market(server, port).orElseThrow().midPrice(good, def, TradeConfig.marketParams());
    }

    private static void run(GameTestHelper h, Harbors harbors, Runnable body) {
        try {
            body.run();
        } finally {
            harbors.cleanUp();
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ tests

    /** A convoy buys sugar at A (A's price rises) and sells it at B on arrival (B's price falls). */
    @ModGameTest(batch = BATCH)
    public static void convoyBuysAtOriginAndSellsAtDestination(GameTestHelper h) {
        Harbors hb = harbors(h);
        MinecraftServer server = hb.server();
        run(h, hb, () -> {
            double originBefore = price(server, hb.a().id(), TradeGoods.SUGAR);
            double destBefore = price(server, hb.b().id(), TradeGoods.SUGAR);
            Voyage v = Voyages.spawnConvoy(server, hb.a().id(), hb.b().id(), RandomSource.create(1)).orElseThrow();
            h.assertTrue(v.cargo().keySet().equals(java.util.Set.of(TradeGoods.SUGAR)), "cargo is the demanded good: " + v.cargo());
            h.assertTrue(v.cargo().get(TradeGoods.SUGAR) > 0, "units bought");
            h.assertValueEqual(v.length(), (double) DISTANCE, "lane length");
            double originAfter = price(server, hb.a().id(), TradeGoods.SUGAR);
            h.assertTrue(originAfter > originBefore, "origin price rises: " + originBefore + " -> " + originAfter);

            Voyages.advance(server, v.id(), DISTANCE / 2.0);
            h.assertTrue(Voyages.get(server, v.id()).orElseThrow().progress() == DISTANCE / 2.0, "half way");
            h.assertTrue(price(server, hb.b().id(), TradeGoods.SUGAR) == destBefore, "no sale before arrival");
            Voyages.advance(server, v.id(), DISTANCE);
            h.assertTrue(Voyages.get(server, v.id()).isEmpty(), "ended on arrival");
            h.assertValueEqual(ENDS.get(v.id()), VoyageEnd.ARRIVED, "end reason");
            double destAfter = price(server, hb.b().id(), TradeGoods.SUGAR);
            h.assertTrue(destAfter < destBefore, "destination price falls: " + destBefore + " -> " + destAfter);
        });
    }

    /** With the wind off, a check moves a voyage speed × interval / 20 blocks; checks bring it to port. */
    @ModGameTest(batch = CONFIG_SPEED)
    public static void convoyAdvancesBySpeedAndArrives(GameTestHelper h) {
        ConfigOverrides.during(h, VoyageConfig.WIND_AFFECTS_SPEED, false);
        ConfigOverrides.during(h, VoyageConfig.SPEED, 4.0);
        ConfigOverrides.during(h, VoyageConfig.TICK_INTERVAL, 20);
        ConfigOverrides.during(h, WorldSimConfig.ENABLED, true);
        ConfigOverrides.during(h, WorldSimConfig.CONVOYS_PER_DAY, 0.0);
        Harbors hb = harbors(h);
        MinecraftServer server = hb.server();
        run(h, hb, () -> {
            double destBefore = price(server, hb.b().id(), TradeGoods.SUGAR);
            Voyage v = Voyages.spawnConvoy(server, hb.a().id(), hb.b().id(), RandomSource.create(2)).orElseThrow();
            long now = server.overworld().getGameTime();
            for (int i = 0; i < 5; i++) VoyageScheduler.check(server, now, RandomSource.create(i));
            Voyage moved = Voyages.get(server, v.id()).orElseThrow();
            h.assertTrue(Math.abs(moved.progress() - 20.0) < 1e-6, "5 checks × 20 ticks × 4 blocks/s = 20 blocks: " + moved.progress());
            h.assertTrue(Math.abs(moved.position().x() - (hb.a().centre().getX() + 20)) < 1e-6, "position along the lane");
            int checks = 5;
            while (Voyages.get(server, v.id()).isPresent() && checks < 1000) {
                VoyageScheduler.check(server, now, RandomSource.create(checks));
                checks++;
            }
            h.assertValueEqual(checks, DISTANCE / 4, "arrives after length / (4 blocks per check) checks");
            h.assertValueEqual(ENDS.get(v.id()), VoyageEnd.ARRIVED, "end reason");
            h.assertTrue(price(server, hb.b().id(), TradeGoods.SUGAR) < destBefore, "sold at the destination");
        });
    }

    /** {@code world_simulation.enabled=false}: checks do nothing and records keep their progress. */
    @ModGameTest(batch = CONFIG_DISABLED)
    public static void disabledSimulationFreezesVoyages(GameTestHelper h) {
        ConfigOverrides.during(h, WorldSimConfig.ENABLED, false);
        Harbors hb = harbors(h);
        MinecraftServer server = hb.server();
        run(h, hb, () -> {
            Voyage v = Voyages.spawnConvoy(server, hb.a().id(), hb.b().id(), RandomSource.create(3)).orElseThrow();
            for (int i = 0; i < 10; i++) {
                h.assertFalse(VoyageScheduler.check(server, 0L, RandomSource.create(i)).ran(), "check skipped");
            }
            h.assertValueEqual(Voyages.get(server, v.id()).orElseThrow().progress(), 0.0, "progress frozen");
        });
    }

    /** {@code convoys_per_day=0}: 30 checks (600 ticks) with only the test ports eligible send no convoy. */
    @ModGameTest(batch = CONFIG_RATE)
    public static void zeroConvoysPerDaySendsNone(GameTestHelper h) {
        ConfigOverrides.during(h, WorldSimConfig.ENABLED, true);
        ConfigOverrides.during(h, WorldSimConfig.CONVOYS_PER_DAY, 0.0);
        ConfigOverrides.during(h, VoyageConfig.TICK_INTERVAL, 20);
        Harbors hb = harbors(h);
        MinecraftServer server = hb.server();
        var filter = ConvoyPlanner.portFilter;
        run(h, hb, () -> {
            try {
                ConvoyPlanner.portFilter = p -> p.id().equals(hb.a().id()) || p.id().equals(hb.b().id());
                VoyageScheduler.clear();
                RandomSource rng = RandomSource.create(4);
                for (int i = 0; i < 30; i++) {
                    h.assertValueEqual(VoyageScheduler.check(server, 0L, rng).spawned(), 0, "no departure");
                }
                h.assertTrue(Voyages.active(server).stream().noneMatch(v -> v.from().equals(hb.a().id()) || v.from().equals(hb.b().id())),
                        "no convoy between the test ports");
            } finally {
                ConvoyPlanner.portFilter = filter;
            }
        });
    }

    /** {@code max_simultaneous_voyages=1} with a high rate: convoys depart, never two at once. */
    @ModGameTest(batch = CONFIG_CAP)
    public static void voyageCapIsRespected(GameTestHelper h) {
        ConfigOverrides.during(h, WorldSimConfig.ENABLED, true);
        ConfigOverrides.during(h, WorldSimConfig.CONVOYS_PER_DAY, 100.0);
        ConfigOverrides.during(h, WorldSimConfig.MAX_SIMULTANEOUS_VOYAGES, 1);
        ConfigOverrides.during(h, VoyageConfig.TICK_INTERVAL, 20);
        Harbors hb = harbors(h);
        MinecraftServer server = hb.server();
        var filter = ConvoyPlanner.portFilter;
        run(h, hb, () -> {
            try {
                ConvoyPlanner.portFilter = p -> p.id().equals(hb.a().id()) || p.id().equals(hb.b().id());
                VoyageScheduler.clear();
                int before = SPAWNS.size();
                RandomSource rng = RandomSource.create(5);
                int most = 0;
                for (int i = 0; i < 400; i++) {
                    VoyageScheduler.check(server, 0L, rng);
                    most = Math.max(most, Voyages.active(server).size());
                }
                h.assertTrue(SPAWNS.size() > before, "convoys departed");
                h.assertValueEqual(most, 1, "never more than the cap");
            } finally {
                ConvoyPlanner.portFilter = filter;
            }
        });
    }

    /** The flat test world has no ocean: the lane search fails at the start and the failure is cached, not queued. */
    @ModGameTest(batch = BATCH)
    public static void landlockedPortsHaveNoLaneAndTheFailureIsCached(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Port a = port(h, "dry_a", 0, PortKind.SEAFARER_VILLAGE);
        Port b = port(h, "dry_b", DISTANCE, PortKind.SEAFARER_VILLAGE);
        PortRegistry.get(server).add(a);
        PortRegistry.get(server).add(b);
        Harbors hb = new Harbors(server, a, b, List.of());
        run(h, hb, () -> {
            Lanes.Computed r = Lanes.compute(server, a.id(), b.id());
            h.assertTrue(r.lane().isEmpty(), "no lane on land");
            h.assertValueEqual(r.result().status(), LanePathfinder.Status.NO_START, "status");
            h.assertValueEqual(Lanes.status(server, a.id(), b.id()), Lanes.Status.FAILED, "failure cached");
            int queued = Lanes.queued();
            h.assertTrue(Lanes.between(server, a.id(), b.id()).isEmpty(), "still none");
            h.assertValueEqual(Lanes.queued(), queued, "a recent failure is not queued again");
            h.assertTrue(Voyages.spawnConvoy(server, a.id(), b.id(), RandomSource.create(6)).isEmpty(), "no convoy without a lane");
        });
    }

    /** Contract destinations get the lane's length, and a pirate island beside the lane raises the risk. */
    @ModGameTest(batch = BATCH)
    public static void contractsUseLaneLengthAndPirateRisk(GameTestHelper h) {
        Harbors hb = harbors(h);
        MinecraftServer server = hb.server();
        run(h, hb, () -> {
            ContractGenerator.Destination calm = destination(server, hb);
            h.assertValueEqual(calm.distance(), (double) DISTANCE, "lane length");
            h.assertTrue(Math.abs(calm.risk() - VoyageConfig.BASE_RISK.get()) < 1e-9, "base risk: " + calm.risk());
            Port island = new Port(Constants.id("gametest/ws2_island_" + UUID.randomUUID().toString().substring(0, 8)),
                    PortKind.PIRATE_ISLAND, h.getLevel().dimension(), hb.a().centre().offset(DISTANCE / 2, 0, 100),
                    BoundingBox.fromCorners(hb.a().centre(), hb.a().centre()), Climate.TEMPERATE, List.of());
            PortRegistry.get(server).add(island);
            hb.extra().add(island);
            ContractGenerator.Destination risky = destination(server, hb);
            h.assertTrue(risky.risk() > calm.risk() + 0.2, "pirate island near the lane: " + calm.risk() + " -> " + risky.risk());
        });
    }

    private static ContractGenerator.Destination destination(MinecraftServer server, Harbors hb) {
        return HarborDeskService.destinations(server, hb.a().id()).stream()
                .filter(d -> d.port().equals(hb.b().id())).findFirst().orElseThrow();
    }

    /**
     * Measures a first lane computation on a real overworld biome map (seed 1, cold grid) between two sea points about
     * 2000 blocks apart, and logs the cost. Not required: it depends on the seed's geography.
     */
    @ModGameTest(batch = BATCH, required = false, timeoutTicks = 400)
    public static void overworldLaneCost(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        var registries = server.registryAccess();
        MultiNoiseBiomeSource source = MultiNoiseBiomeSource.createFromPreset(registries
                .lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        RandomState random = RandomState.create(registries.asGetterLookup(), NoiseGeneratorSettings.OVERWORLD, 1L);
        BiomeSeaGrid probe = new BiomeSeaGrid(32, source, random.sampler(), 63);
        List<int[]> sea = new ArrayList<>();
        for (int x = -4000; x <= 4000; x += 256) {
            for (int z = -4000; z <= 4000; z += 256) {
                if (probe.isSea(probe.cellOf(x), probe.cellOf(z))) sea.add(new int[]{x, z});
            }
        }
        int tries = 0;
        String found = null;
        StringBuilder log = new StringBuilder();
        for (int i = 0; i < sea.size() && tries < 6 && found == null; i++) {
            for (int j = i + 1; j < sea.size() && tries < 6 && found == null; j++) {
                int[] p = sea.get(i), q = sea.get(j);
                double d = Math.hypot(p[0] - q[0], p[1] - q[1]);
                if (d < 1900 || d > 2100) continue;
                tries++;
                BiomeSeaGrid grid = new BiomeSeaGrid(32, source, random.sampler(), 63);
                long t0 = System.nanoTime();
                LanePathfinder.Result r = LanePathfinder.find(grid, p[0], p[1], q[0], q[1], LanePathfinder.Params.DEFAULT);
                long ms = (System.nanoTime() - t0) / 1_000_000;
                String line = String.format("%s (%d %d)->(%d %d) straight %.0f: %s, lane %.0f, %d waypoints, %d cells, %d biome samples, %d ms",
                        "WS2 lane cost", p[0], p[1], q[0], q[1], d, r.status(), Lane.length(r.waypoints()), r.waypoints().size(),
                        r.expanded(), grid.samples(), ms);
                Constants.LOG.info(line);
                log.append(line).append('\n');
                if (r.found()) found = line;
            }
        }
        h.assertTrue(found != null, "no 2000-block lane found in " + tries + " tries (" + sea.size() + " sea points)\n" + log);
        h.succeed();
    }
}
