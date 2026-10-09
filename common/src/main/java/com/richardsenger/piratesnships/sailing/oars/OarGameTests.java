package com.richardsenger.piratesnships.sailing.oars;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.core.text.Num;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewPose;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.oars.OarRules.Side;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntime;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.sailing.wind.WindOverride;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationConfig;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.StationGameTests;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.Stations;
import com.richardsenger.piratesnships.station.order.WhistleOrder;
import com.richardsenger.piratesnships.station.order.WhistleOrders;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/**
 * OAR1 (docs/design.md §5.3b): the Row order on the 5×4×5 ballasted test hull of {@link StationGameTests#hull} (bow
 * +z, so port is +x; helm at the stern, mast with a furled square sail amidships, a winch on the starboard side) in a
 * calm basin: three crew row from the rail and move the ship; two oar benches make it faster by the bonus; Ship oars
 * and any other order end the rowing, the crew go back to where they stood; the sails driving faster than
 * {@code oars.max_speed} ship the oars; assembly keeps the benches. Every test holds the wind in a batch of its own.
 */
public final class OarGameTests {

    private static final String BATCH = "pirates_n_ships_oars_";
    /** Ticks the crew get to land on the deck (and track the ship) before the order. */
    private static final int SETTLE = 20;
    /** The speed window: the crew have walked to their oars and the ship is up to speed. */
    private static final int FROM = 140;
    private static final int TO = 320;

    private OarGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(OarGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    private record Ship(ShipBody body, SailingRuntime runtime, BlockPos helmRel, BlockPos helmPlot) {
        BlockPos plot(BlockPos rel) {
            return helmPlot.offset(rel.subtract(helmRel));
        }
    }

    private static void calm(GameTestHelper h) {
        WindOverride.set(h.getLevel().dimension().location().toString(), 0.0, 0.0, h.getLevel().getGameTime() + 1000);
    }

    private static void clearWind(GameTestHelper h) {
        WindOverride.clear(h.getLevel().dimension().location().toString());
    }

    /** The ballasted hull at (x0, 3) with {@code benches} oar benches at the rails amidships, assembled. */
    private static Ship ship(GameTestHelper h, int x0, boolean benches) {
        BlockPos helm = StationGameTests.hull(h, x0, 3);
        SailingGameTestsShips.ballast(h, x0, 3);
        if (benches) {
            h.setBlock(new BlockPos(x0 + 4, 9, 6), bench(Direction.EAST)); // port
            h.setBlock(new BlockPos(x0, 9, 6), bench(Direction.WEST)); // starboard
        }
        AssemblyResult r = ShipTestCleanup.assemble(h, helm);
        if (r.shipId() == null) throw new AssertionError("assembly failed: " + r);
        ShipBody body = SableShips.byId(h.getLevel(), r.shipId());
        SailingRuntime rt = body == null ? null : SailingRuntimes.getOrCreate(body);
        if (rt == null) throw new AssertionError("no ship or no sailing runtime after assembly");
        return new Ship(body, rt, helm, StationGameTests.find(h, body, AssemblyContent.HELM.get()));
    }

    private static net.minecraft.world.level.block.state.BlockState bench(Direction outboard) {
        return OarsContent.BENCHES.lead().get().defaultBlockState().setValue(OarBenchBlock.FACING, outboard);
    }

    /** Three crew with AI amidships on the deck (stationary: they never stroll). */
    private static List<CrewMember> crew(GameTestHelper h, Ship s, int x0) {
        List<CrewMember> out = new ArrayList<>();
        for (BlockPos rel : List.of(new BlockPos(x0 + 2, 9, 4), new BlockPos(x0 + 1, 9, 5), new BlockPos(x0 + 3, 9, 7))) {
            CrewMember c = StationContent.CREW_MEMBER.get().create(h.getLevel());
            if (c == null) throw new AssertionError("no crew member");
            Vec3 p = s.body().toWorld(Vec3.atBottomCenterOf(s.plot(rel)).add(0, 0.1, 0));
            c.moveTo(p.x, p.y, p.z, 0, 0);
            c.setStationary(true);
            h.getLevel().addFreshEntity(c);
            out.add(c);
        }
        return out;
    }

    private static void cleanup(List<CrewMember> crew) {
        for (CrewMember c : crew) {
            if (c.getVehicle() != null) c.getVehicle().discard();
            c.discard();
        }
    }

    private static void pin(GameTestHelper h) {
        ConfigOverrides.during(h, StationConfig.ENABLED, true);
        ConfigOverrides.during(h, OarsConfig.ENABLED, true);
    }

    /** Sums the speed along the bow over FROM..TO into [0], the samples into [1]. */
    private static double[] measure(GameTestHelper h, Ship s, String label) {
        double[] acc = new double[2];
        h.onEachTick(() -> {
            long t = h.getTick();
            if (s.body().isRemoved()) return;
            Vector3d v = s.runtime().shipFrameVelocity(s.body(), new Vector3d());
            if (t >= FROM && t < TO) {
                acc[0] += v.z;
                acc[1]++;
            }
            if (t % 40 == 0) {
                OarRules.Thrust th = OarThrust.last(s.body().id());
                Constants.LOG.info("[oars test] {} t={} fwd {} rowers {} present {} thrust {}", label, t, Num.two(v.z),
                        Rowing.rowers(s.body().id()).size(), Rowing.snapshot(s.body().id()).rowers().size(),
                        th == null ? "none" : Num.one(th.force()));
            }
        });
        return acc;
    }

    private static double mean(double[] a) {
        return a[1] == 0 ? 0 : a[0] / a[1];
    }

    // ------------------------------------------------------------------ tests

    /** No bench: the three crew line the rails (two on one side, one on the other) and row the ship forward in a calm. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 420, batch = BATCH + "calm")
    public static void threeCrewRowFromTheRailInACalm(GameTestHelper h) {
        pin(h);
        calm(h);
        StationGameTests.basin(h, true);
        Ship s = ship(h, 17, false);
        List<CrewMember> crew = crew(h, s, 17);
        double[] speed = measure(h, s, "rail");
        h.runAfterDelay(SETTLE, () -> {
            Rowing.Outcome o = Rowing.toggle(h.getLevel(), s.body());
            h.assertValueEqual(o.result(), Rowing.Result.ROWING, "row");
            h.assertValueEqual(o.rowers(), 3, "rowers");
            int port = 0, stbd = 0;
            for (Rowing.Place p : Rowing.rowers(s.body().id()).values()) {
                h.assertValueEqual(p.kind(), Rowing.Kind.RAIL, "no bench: at the rail");
                if (p.side() == Side.PORT) port++;
                if (p.side() == Side.STARBOARD) stbd++;
            }
            h.assertTrue(Math.abs(port - stbd) == 1, "sides alternate: port " + port + ", starboard " + stbd);
        });
        h.runAfterDelay(FROM, () -> {
            for (CrewMember c : crew) {
                h.assertTrue(c.getVehicle() instanceof OarSeat, c.getName().getString() + " is not at its oar: " + c.getVehicle());
                h.assertTrue(c.getMainHandItem().is(OarsContent.OAR.get()), "holds no oar: " + c.getMainHandItem());
                h.assertValueEqual(c.stationPose(), CrewPose.ROW_STANDING, "pose");
            }
            h.assertValueEqual(Rowing.snapshot(s.body().id()).rowers().size(), 3, "rowers pulling");
        });
        h.runAfterDelay(TO + 1, () -> {
            clearWind(h);
            double v = mean(speed);
            Constants.LOG.info("[oars test] three rail rowers in a calm: {} m/s (mass {})", Num.two(v), Num.one(s.body().mass()));
            h.assertTrue(v > 0.15, "the ship barely moves under oars: " + v + " m/s");
            h.assertTrue(v < OarsConfig.MAX_SPEED.get(), "faster than max_speed: " + v);
            Rowing.shipOars(h.getLevel(), s.body().id());
            cleanup(crew);
            h.succeed();
        });
    }

    /** Two ships, three crew each: the one with two benches rows faster, by about the bench bonus. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 420, batch = BATCH + "benches")
    public static void benchesRowFasterByTheBonus(GameTestHelper h) {
        pin(h);
        calm(h);
        StationGameTests.basin(h, true);
        Ship rail = ship(h, 5, false);
        Ship benches = ship(h, 25, true);
        List<CrewMember> crew = new ArrayList<>(crew(h, rail, 5));
        List<CrewMember> benchCrew = crew(h, benches, 25);
        crew.addAll(benchCrew);
        double[] a = measure(h, rail, "rail");
        double[] b = measure(h, benches, "benches");
        h.runAfterDelay(SETTLE, () -> {
            Rowing.toggle(h.getLevel(), rail.body());
            Rowing.Outcome o = Rowing.toggle(h.getLevel(), benches.body());
            h.assertValueEqual(o.rowers(), 3, "rowers");
            long seated = Rowing.rowers(benches.body().id()).values().stream().filter(p -> p.kind() == Rowing.Kind.BENCH).count();
            h.assertValueEqual(seated, 2L, "two crew take the benches");
        });
        h.runAfterDelay(FROM, () -> {
            int atBench = 0;
            for (CrewMember c : benchCrew) {
                if (c.isAtStation() && c.assignment() != null
                        && h.getLevel().getBlockState(c.assignment().pos()).getBlock() instanceof OarBenchBlock) {
                    atBench++;
                    h.assertValueEqual(c.stationPose(), CrewPose.ROW_SEATED, "a bench rower sits and rows");
                    h.assertTrue(Stations.isWorking(h.getLevel(), c.assignment()), "the bench works");
                }
            }
            h.assertValueEqual(atBench, 2, "seated rowers");
        });
        h.runAfterDelay(TO + 1, () -> {
            clearWind(h);
            double vr = mean(a), vb = mean(b);
            Constants.LOG.info("[oars test] rail {} m/s, two benches + rail {} m/s, ratio {}", Num.two(vr), Num.two(vb), Num.two(vb / vr));
            h.assertTrue(vr > 0.15, "the rail ship barely moves: " + vr);
            h.assertTrue(vb > vr * 1.05, "benches not faster: " + vb + " against " + vr);
            h.assertTrue(vb < vr * 1.3, "benches faster than the bonus allows: " + vb + " against " + vr);
            Rowing.shipOars(h.getLevel(), rail.body().id());
            Rowing.shipOars(h.getLevel(), benches.body().id());
            cleanup(crew);
            h.succeed();
        });
    }

    /** The toggle again (Ship oars): the oars come in, the crew go back to where they stood, the ship slows down. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 520, batch = BATCH + "ship_oars")
    public static void shipOarsStopsTheRowingAndTheCrewGoBack(GameTestHelper h) {
        pin(h);
        calm(h);
        StationGameTests.basin(h, true);
        Ship s = ship(h, 17, false);
        List<CrewMember> crew = crew(h, s, 17);
        List<BlockPos> start = List.of(new BlockPos(19, 9, 4), new BlockPos(18, 9, 5), new BlockPos(20, 9, 7));
        double[] rowing = new double[1];
        h.runAfterDelay(SETTLE, () -> Rowing.toggle(h.getLevel(), s.body()));
        h.runAfterDelay(200, () -> {
            rowing[0] = s.runtime().shipFrameVelocity(s.body(), new Vector3d()).z;
            WhistleOrders.Issued issued = WhistleOrders.issue(h.getLevel(), s.body(), WhistleOrder.ROW, null);
            h.assertValueEqual(issued.result().outcome(), WhistleOrders.Outcome.ISSUED, "whistle");
            h.assertFalse(Rowing.isRowing(s.body().id()), "still rowing after Ship oars");
        });
        h.runAfterDelay(205, () -> {
            for (CrewMember c : crew) {
                h.assertFalse(c.getVehicle() instanceof OarSeat, "still at the oar");
                h.assertTrue(c.getMainHandItem().isEmpty(), "the oar is not put away: " + c.getMainHandItem());
            }
            h.assertTrue(OarThrust.last(s.body().id()) == null, "the oars still push");
        });
        h.runAfterDelay(480, () -> {
            clearWind(h);
            double v = s.runtime().shipFrameVelocity(s.body(), new Vector3d()).z;
            Constants.LOG.info("[oars test] rowing {} m/s, 14 s after Ship oars {} m/s", Num.two(rowing[0]), Num.two(v));
            h.assertTrue(rowing[0] > 0.15, "was not rowing: " + rowing[0]);
            h.assertTrue(Math.abs(v) < 0.5 * rowing[0], "the ship keeps its way: " + v);
            for (int i = 0; i < crew.size(); i++) {
                Vec3 local = s.body().toPlot(crew.get(i).position());
                double d = local.distanceTo(Vec3.atBottomCenterOf(s.plot(start.get(i))));
                h.assertTrue(d < 1.6, crew.get(i).getName().getString() + " did not go back to its place: " + Num.two(d) + " blocks off");
            }
            cleanup(crew);
            h.succeed();
        });
    }

    /** A sail order (or any other order) ends the rowing first: the rowers are free for it. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300, batch = BATCH + "sail_order")
    public static void aSailOrderEndsTheRowing(GameTestHelper h) {
        pin(h);
        calm(h);
        StationGameTests.basin(h, true);
        Ship s = ship(h, 17, false);
        List<CrewMember> crew = crew(h, s, 17);
        h.runAfterDelay(SETTLE, () -> Rowing.toggle(h.getLevel(), s.body()));
        h.runAfterDelay(120, () -> {
            h.assertTrue(Rowing.isRowing(s.body().id()), "not rowing");
            WhistleOrders.issue(h.getLevel(), s.body(), WhistleOrder.HOIST, null);
            h.assertFalse(Rowing.isRowing(s.body().id()), "the sail order did not end the rowing");
        });
        h.runAfterDelay(260, () -> {
            clearWind(h);
            int atWinch = 0;
            for (CrewMember c : crew) {
                h.assertFalse(c.getVehicle() instanceof OarSeat, "still at an oar");
                if (c.assignment() != null && Stations.kindAt(h.getLevel(), c.assignment()) != null
                        && Stations.state(c.assignment()) != null) atWinch++;
            }
            h.assertValueEqual(atWinch, 1, "a free hand took the winch job");
            cleanup(crew);
            h.succeed();
        });
    }

    /** Sails driving the ship faster than max_speed (lowered here): the crew ship oars by themselves. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 400, batch = BATCH + "sails_faster")
    public static void theCrewShipOarsWhenTheSailsAreFaster(GameTestHelper h) {
        pin(h);
        ConfigOverrides.during(h, OarsConfig.MAX_SPEED, 0.15);
        ConfigOverrides.during(h, OarsConfig.SHIP_OARS_TICKS, 20);
        WindOverride.set(h.getLevel().dimension().location().toString(), 0.0, 6.0, h.getLevel().getGameTime() + 1000);
        StationGameTests.basin(h, true);
        Ship s = ship(h, 17, false);
        com.richardsenger.piratesnships.sailing.ship.SailingRuntimes.setTrim(h.getLevel(), s.runtime().sailPositions().get(0), SailTrim.FULL);
        List<CrewMember> crew = crew(h, s, 17);
        h.runAfterDelay(SETTLE, () -> Rowing.toggle(h.getLevel(), s.body()));
        h.runAfterDelay(SETTLE + 2, () -> h.assertTrue(Rowing.isRowing(s.body().id()), "rows"));
        h.onEachTick(() -> {
            if (h.getTick() > SETTLE + 2 && !Rowing.isRowing(s.body().id())) {
                double v = s.runtime().shipFrameVelocity(s.body(), new Vector3d()).z;
                Constants.LOG.info("[oars test] oars shipped at t={} at {} m/s under sail", h.getTick(), Num.two(v));
                h.assertTrue(v > 0.15 * OarsConfig.SHIP_OARS_SPEED_FACTOR.get(), "shipped while slower than the margin: " + v);
                clearWind(h);
                cleanup(crew);
                h.succeed();
            }
        });
    }

    /** Assembly keeps the benches: the station, its facing and its kind on the ship. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 60, batch = BATCH + "assembly")
    public static void assemblyKeepsTheBenches(GameTestHelper h) {
        pin(h);
        calm(h);
        StationGameTests.basin(h, true);
        Ship s = ship(h, 17, true);
        h.runAfterDelay(2, () -> {
            clearWind(h);
            Map<Direction, Integer> found = new java.util.EnumMap<>(Direction.class);
            for (BlockPos p : s.body().plotBlocks()) {
                var state = h.getLevel().getBlockState(p);
                if (state.getBlock() instanceof OarBenchBlock) {
                    found.merge(state.getValue(OarBenchBlock.FACING), 1, Integer::sum);
                    StationRef ref = Stations.at(h.getLevel(), p);
                    h.assertTrue(ref != null && Stations.kindAt(h.getLevel(), ref) == OarBenchStation.INSTANCE, "the bench is no station on the ship");
                    h.assertValueEqual(p, s.plot(new BlockPos(state.getValue(OarBenchBlock.FACING) == Direction.EAST ? 21 : 17, 9, 6)), "bench cell");
                }
            }
            h.assertValueEqual(found, Map.of(Direction.EAST, 1, Direction.WEST, 1), "benches by facing");
            h.succeed();
        });
    }

    /** The whistle's one oars entry toggles: Row, then Ship oars; no crew, no rowing. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = BATCH + "toggle")
    public static void theWhistleEntryTogglesRowAndShipOars(GameTestHelper h) {
        pin(h);
        calm(h);
        StationGameTests.basin(h, true);
        Ship s = ship(h, 17, false);
        h.assertTrue(WhistleOrder.entries().contains(WhistleOrder.ROW) && WhistleOrder.ROW.isToggle(), "the wheel has the oars toggle");
        h.assertTrue(!WhistleOrder.ROW.nameKey().equals(WhistleOrder.ROW.toggledNameKey()), "two labels");
        Rowing.Outcome none = Rowing.toggle(h.getLevel(), s.body());
        h.assertValueEqual(none.result(), Rowing.Result.NO_HANDS, "no crew: nobody rows");
        h.assertFalse(Rowing.isRowing(s.body().id()), "rowing without hands");
        List<CrewMember> crew = crew(h, s, 17);
        h.runAfterDelay(SETTLE, () -> {
            h.assertValueEqual(Rowing.toggle(h.getLevel(), s.body()).result(), Rowing.Result.ROWING, "Row");
            h.assertTrue(s.body().userData(Rowing.USER_DATA_KEY).getBoolean("rowing"), "the Row is kept with the ship");
            h.assertValueEqual(Rowing.toggle(h.getLevel(), s.body()).result(), Rowing.Result.SHIPPED, "Ship oars");
            h.assertFalse(s.body().userData(Rowing.USER_DATA_KEY).getBoolean("rowing"), "the ship oars is kept with the ship");
            ConfigOverrides.during(h, OarsConfig.ENABLED, false);
            h.assertValueEqual(Rowing.toggle(h.getLevel(), s.body()).result(), Rowing.Result.DISABLED, "oars off");
            clearWind(h);
            cleanup(crew);
            h.succeed();
        });
    }
}
