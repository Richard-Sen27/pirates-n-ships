package com.richardsenger.piratesnships.station.helm;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewPose;
import com.richardsenger.piratesnships.crew.npc.StationPoses;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.helm.HelmConfig;
import com.richardsenger.piratesnships.sailing.helm.HelmService;
import com.richardsenger.piratesnships.sailing.ship.RudderSteps;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips.Fixture;
import com.richardsenger.piratesnships.sailing.ship.ShipControls;
import com.richardsenger.piratesnships.sailing.wind.WindOverride;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.assembly.HelmBlock;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.StationState;
import com.richardsenger.piratesnships.station.Stations;
import com.richardsenger.piratesnships.station.jobs.JobBoard;
import com.richardsenger.piratesnships.station.seat.StationSeat;
import com.richardsenger.piratesnships.station.winch.SailOrder;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * GameTests of the NPC helmsman (WS3a): the ballasted 5×4×5 test hull of {@link SailingGameTestsShips} (helm at the
 * stern facing north, bow +Z = heading 180°) in a 40×40 basin, wind from the north (astern) at 6 blocks/s where the
 * ship sails. Every test with wind or config overrides runs in a batch of its own ({@link WindOverride} is level-wide).
 *
 * <p>Measured (WS3a probes): under the small square sail the hull makes 0.35 to 0.39 blocks/s and, at the default
 * {@code sailing.rudder_strength} 0.5, turns only 0.7°/s at full rudder (180° → 212° in 1000 ticks), far too slow for a
 * 40-block basin. The turning tests therefore raise the rudder strength (×10: the bearing 31° to starboard is reached
 * in about 230 ticks without overshoot; ×40: 120° in about 220 ticks). The arrival test sails at the defaults.
 */
public final class HelmStationGameTests {

    private static final double WIND = 6.0;
    private static final String BATCH = "pirates_n_ships_config_station_helm_";
    /** Ship center of the hull at (17, 3) in test coordinates (plot bounds x 17..21, z 3..7). */
    private static final double CX = 19.5;
    private static final double CZ = 5.5;

    private static final List<CourseEvent> EVENTS = new CopyOnWriteArrayList<>();

    static {
        HelmCourses.onEvent(EVENTS::add);
    }

    private HelmStationGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(HelmStationGameTests.class);
    }

    // ------------------------------------------------------------------ fixture

    /** The assembled test hull with its helm and (optionally) a sail winch, plot positions. */
    record Ship(Fixture f, BlockPos helm, BlockPos winch) {
        UUID id() {
            return f.ship().id();
        }
    }

    private static Ship ship(GameTestHelper h, SailTrim trim, boolean winch) {
        return ship(h, 17, 3, trim, winch);
    }

    private static Ship ship(GameTestHelper h, int x0, int z0, SailTrim trim, boolean winch) {
        SailingGameTestsShips.basin(h, true);
        BlockPos land = SailingGameTestsShips.squareHull(h, x0, z0, trim);
        SailingGameTestsShips.ballast(h, x0, z0);
        BlockPos winchRel = new BlockPos(x0 + 1, 9, z0 + 4);
        if (winch) h.setBlock(winchRel, SailingBlocks.SAIL_WINCH.get());
        BlockPos helmAbs = h.absolutePos(land);
        Fixture f = SailingGameTestsShips.assemble(h, land);
        BlockPos helm = null;
        BlockPos w = null;
        for (BlockPos p : f.ship().plotBlocks()) {
            BlockState s = h.getLevel().getBlockState(p);
            if (s.is(AssemblyContent.HELM.get())) helm = p;
            if (s.is(SailingBlocks.SAIL_WINCH.get())) w = p;
        }
        if (helm == null) throw new AssertionError("no helm on the ship (was " + helmAbs + ")");
        return new Ship(f, helm, w);
    }

    /** A crew member without AI on the deck above the plot cell {@code helm + (dx, 0, dz)}. */
    private static CrewMember onDeck(GameTestHelper h, Ship s, int dx, int dz) {
        CrewMember c = StationContent.CREW_MEMBER.get().create(h.getLevel());
        if (c == null) throw new AssertionError("no crew member");
        Vec3 p = s.f().ship().toWorld(Vec3.atBottomCenterOf(s.helm().offset(dx, 0, dz)));
        c.moveTo(p.x, p.y, p.z, 0, 0);
        c.setNoAi(true);
        h.getLevel().addFreshEntity(c);
        return c;
    }

    private static CrewMember helmsman(GameTestHelper h, Ship s) {
        CrewMember c = onDeck(h, s, 2, 0);
        CrewStations.AssignResult r = CrewStations.assign(h.getLevel(), c, s.helm());
        h.assertTrue(r == CrewStations.AssignResult.ASSIGNED, "assign to the helm: " + r);
        return c;
    }

    /** A world point {@code ahead} blocks before the ship's center (+Z) and {@code starboard} to its right (−X). */
    private static Vec3 point(GameTestHelper h, double ahead, double starboard) {
        return h.absoluteVec(new Vec3(CX - starboard, 9, CZ + ahead));
    }

    private static void wind(GameTestHelper h) {
        WindOverride.set(h.getLevel().dimension().location().toString(), 0.0, WIND, h.getLevel().getGameTime() + 2400);
    }

    private static void noWind(GameTestHelper h) {
        WindOverride.clear(h.getLevel().dimension().location().toString());
    }

    private static double headingError(Ship s, Vec3 target) {
        Vec3 c = HelmCourses.center(s.f().ship());
        return CourseKeeper.error(s.f().runtime().headingDegrees(s.f().ship()), CourseKeeper.bearing(c.x, c.z, target.x, target.z));
    }

    private static List<CourseEvent.Type> events(UUID ship) {
        return EVENTS.stream().filter(e -> e.ship().equals(ship)).map(CourseEvent::type).toList();
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.1f", d);
    }

    /** A plain use of the helm by {@code player} standing at its north side, as vanilla does it with an empty hand. */
    private static void use(GameTestHelper h, BlockPos plotHelm, Player player) {
        BlockState s = h.getLevel().getBlockState(plotHelm);
        Vec3 c = Vec3.atCenterOf(plotHelm);
        s.useWithoutItem(h.getLevel(), player, new BlockHitResult(c.add(0, 0, -0.5), Direction.NORTH, plotHelm, false));
    }

    private static void stand(Player player, Ship s) {
        Vec3 p = s.f().ship().toWorld(new Vec3(s.helm().getX() + 0.5, s.helm().getY(), s.helm().getZ() - 0.5));
        player.setPos(p.x, p.y, p.z);
    }

    // ------------------------------------------------------------------ steering under sail

    /**
     * A helmsman ordered to a point 20 blocks ahead and 12 to starboard (31° off the bow) puts the rudder to starboard
     * and brings the bow within 15° of the bearing within 200 ticks, and is settled on it (within 8°) from tick 260 on.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 400, batch = BATCH + "turn_to_bearing")
    public static void helmsmanTurnsTowardThePoint(GameTestHelper h) {
        ConfigOverrides.during(h, SailingConfig.RUDDER_STRENGTH, 5.0);
        wind(h);
        Ship s = ship(h, SailTrim.FULL, false);
        helmsman(h, s);
        Vec3 target = point(h, 20, 12);
        int[] within = {-1};
        double[] worstAfter = {0.0};
        h.runAfterDelay(2, () -> {
            HelmCourses.SetResult r = HelmCourses.set(h.getLevel(), s.f().ship(), CourseOrder.to(target));
            h.assertTrue(r == HelmCourses.SetResult.STARTED, "set: " + r);
        });
        h.runAfterDelay(12, () -> h.assertTrue(s.f().runtime().rudderAngle() > 20.0,
                "rudder not to starboard toward a point to starboard: " + s.f().runtime().rudderAngle()));
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t < 3 || t > 360) return;
            double e = headingError(s, target);
            if (within[0] < 0 && Math.abs(e) < 15.0) within[0] = (int) t;
            if (t >= 260) worstAfter[0] = Math.max(worstAfter[0], Math.abs(e)); // settled: no swing back out
        });
        h.runAfterDelay(361, () -> {
            noWind(h);
            Constants.LOG.info("[course test] bearing 31° to starboard: within 15° after {} ticks, worst error over ticks 260..360 {}°, heading {}°",
                    within[0], fmt(worstAfter[0]), fmt(s.f().runtime().headingDegrees(s.f().ship())));
            h.assertTrue(within[0] > 0 && within[0] <= 202, "heading error not under 15° within 200 ticks: " + within[0]);
            h.assertTrue(worstAfter[0] < 8.0, "not settled on the bearing (overshoot or swinging): " + worstAfter[0] + "°");
            h.succeed();
        });
    }

    /** A point dead astern: the helmsman puts the rudder hard over and the ship turns through more than 120°. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 500, batch = BATCH + "turn_astern")
    public static void pointAsternTurnsTheShipAround(GameTestHelper h) {
        ConfigOverrides.during(h, SailingConfig.RUDDER_STRENGTH, 20.0);
        wind(h);
        Ship s = ship(h, SailTrim.FULL, false);
        helmsman(h, s);
        double[] start = new double[1];
        double[] turned = {0.0};
        double[] last = new double[1];
        h.runAfterDelay(2, () -> {
            start[0] = last[0] = s.f().runtime().headingDegrees(s.f().ship());
            HelmCourses.set(h.getLevel(), s.f().ship(), CourseOrder.to(point(h, -30, 0)));
        });
        h.onEachTick(() -> {
            if (h.getTick() <= 2) return;
            double now = s.f().runtime().headingDegrees(s.f().ship());
            turned[0] += CourseKeeper.error(last[0], now);
            last[0] = now;
        });
        h.runAfterDelay(450, () -> {
            noWind(h);
            Constants.LOG.info("[course test] point astern: turned {}° in 448 ticks (from {}° to {}°)", fmt(turned[0]), fmt(start[0]), fmt(last[0]));
            h.assertTrue(Math.abs(turned[0]) > 120.0, "turned only " + turned[0] + "°");
            h.succeed();
        });
    }

    /**
     * The full flow at the default physics: sails furled, two free hands aboard; the course (two waypoints: 10 blocks
     * ahead, then 18 ahead and 2 to starboard) and a hoist are posted, the job board seats one hand at the helm and one at
     * the winch; the ship reaches the first waypoint (WAYPOINT), then the last (ARRIVED): rudder midships, course ended,
     * the helmsman stays at the helm without an order.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 1400, batch = BATCH + "arrive")
    public static void freeHandsSailTheCourseAndArrive(GameTestHelper h) {
        wind(h);
        Ship s = ship(h, SailTrim.FURLED, true);
        h.assertTrue(s.winch() != null, "no winch on the ship");
        CrewMember a = onDeck(h, s, 2, 0);
        CrewMember b = onDeck(h, s, -2, 2);
        CourseOrder course = new CourseOrder(List.of(point(h, 10, 0), point(h, 18, 2)), false);
        int[] arrivedAt = {-1};
        h.runAfterDelay(2, () -> {
            HelmCourses.SetResult r = HelmCourses.set(h.getLevel(), s.f().ship(), course);
            h.assertTrue(r == HelmCourses.SetResult.POSTED, "set without a helmsman: " + r);
            JobBoard.Posted hoist = JobBoard.post(h.getLevel(), s.f().ship(), SailOrder.HOIST);
            h.assertTrue(hoist.jobs() == 1, "hoist not posted: " + hoist);
            JobBoard.pass(h.getLevel(), s.id());
            h.assertTrue(Stations.isManned(new StationRef(s.id(), s.helm())), "nobody took the helm");
            h.assertTrue(Stations.isManned(new StationRef(s.id(), s.winch())), "nobody took the winch");
            h.assertTrue(a.assignment() != null && b.assignment() != null, "both hands should be at stations");
        });
        h.onEachTick(() -> {
            if (arrivedAt[0] < 0 && events(s.id()).contains(CourseEvent.Type.ARRIVED)) arrivedAt[0] = (int) h.getTick();
        });
        h.runAfterDelay(1300, () -> {
            noWind(h);
            List<CourseEvent.Type> ev = events(s.id());
            Constants.LOG.info("[course test] two waypoints at default physics: events {}, arrived after {} ticks", ev, arrivedAt[0]);
            h.assertTrue(ev.contains(CourseEvent.Type.WAYPOINT), "first waypoint not reached: " + ev);
            h.assertTrue(arrivedAt[0] > 0, "did not arrive: " + ev + ", left " + HelmCourses.waypointIndex(s.id()));
            h.assertTrue(HelmCourses.course(s.id()) == null, "course still set after arrival");
            h.assertTrue(Math.abs(s.f().runtime().rudderAngle()) < 1e-6, "rudder not midships: " + s.f().runtime().rudderAngle());
            StationState<Object> st = Stations.state(new StationRef(s.id(), s.helm()));
            h.assertTrue(st != null && st.occupant() != null && st.order() == null, "helmsman should stay idle at the helm");
            h.succeed();
        });
    }

    /** Click steps ({@code drag_steering=false}): the helmsman steers through the rudder step and reaches the bearing. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 400, batch = BATCH + "click_steps_sailing")
    public static void helmsmanSteersWithClickSteps(GameTestHelper h) {
        ConfigOverrides.during(h, HelmConfig.DRAG_STEERING, false);
        ConfigOverrides.during(h, SailingConfig.RUDDER_STRENGTH, 5.0);
        wind(h);
        Ship s = ship(h, SailTrim.FULL, false);
        helmsman(h, s);
        Vec3 target = point(h, 20, 12);
        int[] within = {-1};
        h.runAfterDelay(2, () -> HelmCourses.set(h.getLevel(), s.f().ship(), CourseOrder.to(target)));
        h.runAfterDelay(12, () -> {
            int step = RudderSteps.fromProperty(h.getLevel().getBlockState(s.helm()).getValue(HelmBlock.RUDDER));
            h.assertTrue(step == SailingConfig.RUDDER_STEPS.get(), "rudder step not hard to starboard: " + step);
            h.assertTrue(s.f().runtime().rudderAngle() > 30.0, "runtime rudder does not follow the step: " + s.f().runtime().rudderAngle());
        });
        h.onEachTick(() -> {
            if (h.getTick() > 2 && within[0] < 0 && Math.abs(headingError(s, target)) < 15.0) within[0] = (int) h.getTick();
        });
        h.runAfterDelay(320, () -> {
            noWind(h);
            Constants.LOG.info("[course test] click steps: within 15° after {} ticks", within[0]);
            h.assertTrue(within[0] > 0 && within[0] <= 250, "heading error not under 15° within 250 ticks: " + within[0]);
            h.succeed();
        });
    }

    /** A ship that runs into the basin wall with its sails set is reported stuck. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 600, batch = BATCH + "stuck")
    public static void shipAgainstAWallIsReportedStuck(GameTestHelper h) {
        wind(h);
        Ship s = ship(h, 17, 33, SailTrim.FULL, false);
        helmsman(h, s);
        int[] stuckAt = {-1};
        h.runAfterDelay(2, () -> HelmCourses.set(h.getLevel(), s.f().ship(), CourseOrder.to(h.absoluteVec(new Vec3(19.5, 9, 70)))));
        h.onEachTick(() -> {
            if (stuckAt[0] < 0 && events(s.id()).contains(CourseEvent.Type.STUCK)) stuckAt[0] = (int) h.getTick();
        });
        h.runAfterDelay(560, () -> {
            noWind(h);
            Constants.LOG.info("[course test] against the wall: stuck reported after {} ticks", stuckAt[0]);
            h.assertTrue(stuckAt[0] > 0, "no STUCK report: " + events(s.id()));
            h.assertTrue(HelmCourses.course(s.id()) != null, "a stuck ship keeps its course");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ players and crew (no wind, sails furled)

    /**
     * While a player holds the wheel the helmsman's rudder changes are refused and the wheel stays where the player puts
     * it; when the player lets go the helmsman takes over again.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200)
    public static void playerAtTheWheelBlocksTheHelmsman(GameTestHelper h) {
        Ship s = ship(h, SailTrim.FURLED, false);
        helmsman(h, s);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        h.onEachTick(() -> stand(player, s));
        h.runAfterDelay(2, () -> HelmCourses.set(h.getLevel(), s.f().ship(), CourseOrder.to(point(h, 20, 12))));
        h.runAfterDelay(12, () -> {
            h.assertTrue(HelmService.wheel(h.getLevel(), s.helm()) > 100.0, "helmsman did not turn the wheel: " + HelmService.wheel(h.getLevel(), s.helm()));
            stand(player, s);
            use(h, s.helm(), player);
            h.assertTrue(s.helm().equals(HelmService.session(player)), "the player did not take the wheel");
            for (int i = 0; i < 20; i++) HelmService.turn(player, s.helm(), -30.0); // one tick's budget: −30°
            h.assertTrue(ShipControls.setRudderAngle(h.getLevel(), s.helm(), 35.0) == ShipControls.RudderResult.PLAYER_AT_WHEEL,
                    "the rudder setter ignored the player");
        });
        for (int t = 13; t < 30; t++) h.runAfterDelay(t, () -> HelmService.turn(player, s.helm(), -30.0));
        h.runAfterDelay(60, () -> {
            double w = HelmService.wheel(h.getLevel(), s.helm());
            h.assertTrue(w < -200.0, "the helmsman fought the player for the wheel: " + w);
            h.assertTrue(HelmService.release(player, s.helm()), "release");
        });
        h.runAfterDelay(75, () -> {
            double w = HelmService.wheel(h.getLevel(), s.helm());
            h.assertTrue(w > 100.0, "the helmsman did not take over after the player let go: " + w);
            h.succeed();
        });
    }

    /** Releasing the helmsman ends the course: rudder and wheel midships, NO_HELMSMAN reported. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100)
    public static void releasingTheHelmsmanCentresTheRudder(GameTestHelper h) {
        Ship s = ship(h, SailTrim.FURLED, false);
        CrewMember c = helmsman(h, s);
        h.runAfterDelay(2, () -> HelmCourses.set(h.getLevel(), s.f().ship(), CourseOrder.to(point(h, 20, -12))));
        h.runAfterDelay(12, () -> {
            h.assertTrue(s.f().runtime().rudderAngle() < -20.0, "rudder not to port for a point to port: " + s.f().runtime().rudderAngle());
            CrewStations.release(h.getLevel(), c);
        });
        h.runAfterDelay(22, () -> {
            h.assertTrue(Math.abs(HelmService.wheel(h.getLevel(), s.helm())) < 1e-3, "wheel not centred: " + HelmService.wheel(h.getLevel(), s.helm()));
            h.assertTrue(Math.abs(s.f().runtime().rudderAngle()) < 1e-6, "rudder not midships: " + s.f().runtime().rudderAngle());
            h.assertTrue(HelmCourses.course(s.id()) == null, "course kept without a helmsman");
            h.assertTrue(events(s.id()).contains(CourseEvent.Type.NO_HELMSMAN), "no NO_HELMSMAN: " + events(s.id()));
            h.succeed();
        });
    }

    /** A second helm (HL1) does not steer, so its crew member refuses a course. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100)
    public static void secondHelmTakesNoCourse(GameTestHelper h) {
        SailingGameTestsShips.basin(h, true);
        BlockPos land = SailingGameTestsShips.squareHull(h, 17, 3, SailTrim.FURLED);
        SailingGameTestsShips.ballast(h, 17, 3);
        h.setBlock(new BlockPos(21, 9, 7), AssemblyContent.HELM.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        Fixture f = SailingGameTestsShips.assemble(h, land);
        BlockPos main = com.richardsenger.piratesnships.ship.assembly.ShipHelm.steering(f.ship());
        BlockPos second = f.ship().plotBlocks().stream()
                .filter(p -> h.getLevel().getBlockState(p).is(AssemblyContent.HELM.get()) && !p.equals(main)).findFirst()
                .orElseThrow(() -> new AssertionError("no second helm"));
        Ship s = new Ship(f, main, null);
        CrewMember c = onDeck(h, s, 0, 2);
        h.assertTrue(CrewStations.assign(h.getLevel(), c, second) == CrewStations.AssignResult.ASSIGNED, "assign to the second helm");
        Stations.OrderResult r = CrewStations.order(h.getLevel(), c, CourseOrder.to(point(h, 20, 0)));
        h.assertTrue(r == Stations.OrderResult.NOT_APPLICABLE, "a second helm took a course: " + r);
        h.succeed();
    }

    /** {@code /pirates crew order course ~ ~20} from the deck sets the course of the ship there. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100)
    public static void courseCommandSetsTheCourse(GameTestHelper h) {
        Ship s = ship(h, SailTrim.FURLED, false);
        helmsman(h, s);
        h.runAfterDelay(2, () -> {
            Vec3 deck = s.f().ship().toWorld(Vec3.atBottomCenterOf(s.helm().offset(1, 0, 1)));
            var source = h.getLevel().getServer().createCommandSourceStack().withLevel(h.getLevel()).withPosition(deck).withPermission(4);
            h.getLevel().getServer().getCommands().performPrefixedCommand(source, "pirates crew order course ~ ~20 ~-5 ~30 loop");
            CourseOrder o = HelmCourses.course(s.id());
            h.assertTrue(o != null && o.waypoints().size() == 2 && o.loop(), "course not set by the command: " + o);
            h.assertTrue(Math.abs(o.waypoints().get(0).z - (deck.z + 20)) < 1e-6 && Math.abs(o.waypoints().get(1).x - (deck.x - 5)) < 1e-6,
                    "waypoints not relative to the source: " + o.waypoints());
            StationState<Object> st = Stations.state(new StationRef(s.id(), s.helm()));
            h.assertTrue(st != null && o.equals(st.order()), "the helmsman does not hold the course");
            h.succeed();
        });
    }

    /** Click steps: a player's click moves the rudder; the helmsman leaves it alone for {@code manual_override_ticks}. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH + "click_override")
    public static void clickByAPlayerPausesTheHelmsman(GameTestHelper h) {
        ConfigOverrides.during(h, HelmConfig.DRAG_STEERING, false);
        ConfigOverrides.during(h, CourseConfig.MANUAL_OVERRIDE_TICKS, 60);
        Ship s = ship(h, SailTrim.FURLED, false);
        helmsman(h, s);
        h.runAfterDelay(2, () -> HelmCourses.set(h.getLevel(), s.f().ship(), CourseOrder.to(point(h, 20, 12))));
        h.runAfterDelay(12, () -> {
            h.assertTrue(step(h, s) == SailingConfig.RUDDER_STEPS.get(), "helmsman's step: " + step(h, s));
            ShipControls.setRudder(h.getLevel(), s.helm(), RudderSteps.Click.MIDSHIPS);
        });
        h.runAfterDelay(50, () -> h.assertTrue(step(h, s) == 0, "the helmsman overrode the player's click: " + step(h, s)));
        h.runAfterDelay(110, () -> {
            h.assertTrue(step(h, s) == SailingConfig.RUDDER_STEPS.get(), "the helmsman did not resume: " + step(h, s));
            h.succeed();
        });
    }

    private static int step(GameTestHelper h, Ship s) {
        return RudderSteps.fromProperty(h.getLevel().getBlockState(s.helm()).getValue(HelmBlock.RUDDER));
    }

    // ------------------------------------------------------------------ the helmsman's pose (ART7)

    /**
     * A helmsman at the helm shows the {@code helm_hold} pose (synced) and faces the helm block from his spot; a step
     * of the wheel to starboard shows {@code helm_turn_right} for one loop, a step to port {@code helm_turn_left}, and
     * then he holds the wheel again.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH + "pose")
    public static void helmsmanHoldsAndTurnsTheWheel(GameTestHelper h) {
        Ship s = ship(h, SailTrim.FURLED, false);
        CrewMember c = helmsman(h, s);
        h.runAfterDelay(3, () -> {
            h.assertTrue(c.stationPose() == CrewPose.HELM, "pose at the helm: " + c.stationPose());
            h.assertTrue(!c.mayLookAround(), "the helmsman still looks around");
            if (!(c.getVehicle() instanceof StationSeat seat)) throw new GameTestAssertException("not on the station seat");
            Direction toward = StationPoses.toward(seat.blockPosition(), s.helm());
            h.assertTrue(toward != null, "the spot " + seat.blockPosition() + " is not beside the helm " + s.helm());
            float want = StationPoses.yaw(toward, s.f().ship().orientation());
            h.assertTrue(Math.abs(Mth.wrapDegrees(c.getYRot() - want)) < 1f && Math.abs(Mth.wrapDegrees(c.yBodyRot - want)) < 1f,
                    "faces " + c.getYRot() + "/" + c.yBodyRot + " instead of the helm at " + want);
            ShipControls.setRudderAngle(h.getLevel(), s.helm(), 20.0);
        });
        h.runAfterDelay(5, () -> h.assertTrue(c.stationPose() == CrewPose.HELM_TURN_RIGHT, "turning to starboard: " + c.stationPose()));
        h.runAfterDelay(6 + HelmTurn.HOLD_TICKS, () -> {
            h.assertTrue(c.stationPose() == CrewPose.HELM, "holding after the turn: " + c.stationPose());
            ShipControls.setRudderAngle(h.getLevel(), s.helm(), -20.0);
        });
        h.runAfterDelay(8 + HelmTurn.HOLD_TICKS, () -> {
            h.assertTrue(c.stationPose() == CrewPose.HELM_TURN_LEFT, "turning to port: " + c.stationPose());
            h.assertTrue(c.pose(false) == CrewPose.HELM_TURN_LEFT, "the animation follows the synced pose: " + c.pose(false));
            h.succeed();
        });
    }

    /** With {@code crew_stations.course.enabled} off no course is set and the helm takes no course order. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = BATCH + "disabled")
    public static void disabledCoursesAreRefused(GameTestHelper h) {
        ConfigOverrides.during(h, CourseConfig.ENABLED, false);
        Ship s = ship(h, SailTrim.FURLED, false);
        CrewMember c = helmsman(h, s);
        HelmCourses.SetResult r = HelmCourses.set(h.getLevel(), s.f().ship(), CourseOrder.to(point(h, 20, 0)));
        h.assertTrue(r == HelmCourses.SetResult.DISABLED, "set: " + r);
        h.assertTrue(CrewStations.order(h.getLevel(), c, CourseOrder.to(point(h, 20, 0))) == Stations.OrderResult.NOT_APPLICABLE, "order taken");
        h.succeed();
    }
}
