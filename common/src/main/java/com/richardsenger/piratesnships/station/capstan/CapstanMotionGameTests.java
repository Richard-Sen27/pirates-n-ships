package com.richardsenger.piratesnships.station.capstan;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewPose;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.sailing.force.AnchorState;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips.Fixture;
import com.richardsenger.piratesnships.sailing.ship.ShipAnchor;
import com.richardsenger.piratesnships.sailing.ship.ShipControls;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.Stations;
import com.richardsenger.piratesnships.station.seat.StationSeat;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The capstan in motion (CAP2, docs/design.md §6): while a crew member works the capstan its block entity says which way
 * the bars turn ({@link CapstanBlockEntity#turning()}, synced to clients), and the crew member's station seat walks round
 * the drum and comes back to its spot afterwards, the crew member pushing all the while. The ship and the capstan are
 * {@link CapstanGameTests}'s (the capstan on the test hull's deck at relative (19, 9, 6), the crew member's spot west of
 * it); crew members stand without AI.
 */
public final class CapstanMotionGameTests {

    private static final String BATCH = "pirates_n_ships_station_capstan_motion_";
    private static final String CONFIG_BATCH = "pirates_n_ships_config_station_capstan_motion_";
    /** Ticks a change of the work may take to show: the block entity ticks after the station service. */
    private static final int LAG = 2;
    /** The way back along the shorter arc takes at most half a turn at the default 6 degrees per tick, plus the step in. */
    private static final int RETURN_TICKS = 45;

    private CapstanMotionGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(CapstanMotionGameTests.class);
    }

    private record Rig(Fixture f, BlockPos capstan) {
        StationRef ref() {
            return new StationRef(f.ship().id(), capstan);
        }

        @Nullable AnchorState.Phase phase() {
            ShipAnchor a = f.runtime().anchor();
            return a == null ? null : a.state().phase();
        }
    }

    private static Rig rig(GameTestHelper h) {
        SailingGameTestsShips.basin(h, true);
        BlockPos helm = SailingGameTestsShips.squareHull(h, 17, 3, SailTrim.FURLED);
        h.setBlock(new BlockPos(19, 9, 6), SailingBlocks.CAPSTAN.get());
        Fixture f = SailingGameTestsShips.assemble(h, helm);
        for (BlockPos p : f.ship().plotBlocks()) {
            if (f.ship().level().getBlockState(p).is(SailingBlocks.CAPSTAN.get())) return new Rig(f, p);
        }
        throw new AssertionError("no capstan on the ship");
    }

    /** A crew member without AI seated at the capstan (its spot west of it). */
    private static CrewMember manned(GameTestHelper h, Rig r) {
        CrewMember c = StationContent.CREW_MEMBER.get().create(h.getLevel());
        if (c == null) throw new AssertionError("no crew member");
        Vec3 p = r.f().ship().toWorld(Vec3.atBottomCenterOf(r.capstan().west()));
        c.moveTo(p.x, p.y, p.z, 0, 0);
        c.setNoAi(true);
        h.getLevel().addFreshEntity(c);
        CrewStations.AssignResult a = CrewStations.assign(h.getLevel(), c, r.capstan());
        h.assertTrue(a == CrewStations.AssignResult.ASSIGNED, "assign: " + a);
        h.assertTrue(c.isAtStation(), "the crew member does not ride the capstan's seat");
        return c;
    }

    private static CapstanBlockEntity capstan(GameTestHelper h, Rig r) {
        if (h.getLevel().getBlockEntity(r.capstan()) instanceof CapstanBlockEntity be) return be;
        throw new AssertionError("the capstan has no block entity");
    }

    private static StationSeat seat(CrewMember c) {
        if (c.getVehicle() instanceof StationSeat s) return s;
        throw new AssertionError("the crew member left its seat: " + c.getVehicle());
    }

    /** The octant (0..7) of the seat round the capstan's axis, in the plot. */
    private static int octant(Rig r, StationSeat s) {
        Vec3 c = Vec3.atBottomCenterOf(r.capstan());
        double deg = Math.toDegrees(Math.atan2(s.getZ() - c.z, s.getX() - c.x));
        return (int) Math.floor((deg + 360.0 + 22.5) / 45.0) % 8;
    }

    private static double fromHome(StationSeat s) {
        return s.position().distanceTo(s.home());
    }

    /** Whether the anchor let go by hand holds on the ground. */
    private static boolean anchorDown(Rig r) {
        return r.phase() == AnchorState.Phase.HOLDING;
    }

    /**
     * Weighing anchor: the bars turn {@code RAISING} while the crew member works the capstan, it walks round the drum
     * (the seat passes through at least three octants of the circle round the axis, at the walking radius) pushing the
     * bars all the while, and afterwards the turning is {@code NONE} and the seat is back on its spot.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 600, batch = BATCH + "raise")
    public static void raisingTurnsTheBarsAndWalksTheCrewRound(GameTestHelper h) {
        Rig r = rig(h);
        ServerLevel level = h.getLevel();
        CrewMember c = manned(h, r);
        StationRef ref = r.ref();
        int[] stage = {0};
        long[] since = {0};
        Set<Integer> octants = new HashSet<>();
        double[] farthest = {0};
        int[] walking = {0};
        h.onEachTick(() -> {
            long t = h.getTick();
            switch (stage[0]) {
                case 0 -> {
                    if (t < 3) return;
                    h.assertTrue(capstan(h, r).turning() == CapstanTurning.NONE, "the bars turn before any work");
                    h.assertTrue(ShipControls.dropAnchor(level, r.capstan()).result() == ShipControls.AnchorResult.DROPPING, "drop by hand");
                    since[0] = t;
                    stage[0] = 1;
                }
                case 1 -> {
                    h.assertTrue(capstan(h, r).turning() == CapstanTurning.NONE, "a drop by hand turned the bars");
                    h.assertTrue(fromHome(seat(c)) < 1e-6, "the seat moved without work");
                    if (!anchorDown(r)) {
                        h.assertTrue(t - since[0] < 100, "the anchor did not land: " + r.phase());
                        return;
                    }
                    h.assertTrue(CrewStations.order(level, c, AnchorOrder.RAISE_ANCHOR) == Stations.OrderResult.STARTED, "raise not started");
                    since[0] = t;
                    stage[0] = 2;
                }
                case 2 -> {
                    long dt = t - since[0];
                    CapstanBlockEntity be = capstan(h, r);
                    StationSeat s = seat(c);
                    if (Stations.isWorking(level, ref)) {
                        if (dt >= LAG) {
                            h.assertTrue(be.turning() == CapstanTurning.RAISING, "not RAISING while raising at " + dt + ": " + be.turning());
                            h.assertTrue(c.stationPose() == CrewPose.CAPSTAN_PUSH, "not pushing during the walk at " + dt + ": " + c.stationPose());
                            octants.add(octant(r, s));
                            Vec3 axis = Vec3.atBottomCenterOf(r.capstan());
                            farthest[0] = Math.max(farthest[0], Math.hypot(s.getX() - axis.x, s.getZ() - axis.z));
                            if (fromHome(s) > 0.2) walking[0]++;
                        }
                        h.assertTrue(dt < 500, "the raise does not end");
                        return;
                    }
                    since[0] = t;
                    stage[0] = 3;
                }
                case 3 -> {
                    long dt = t - since[0];
                    CapstanBlockEntity be = capstan(h, r);
                    if (dt >= LAG) {
                        h.assertTrue(be.turning() == CapstanTurning.NONE, "still turning after the work: " + be.turning());
                        h.assertTrue(c.stationPose() == null, "still pushing after the work: " + c.stationPose());
                    }
                    StationSeat s = seat(c);
                    if (fromHome(s) > 1e-6) {
                        h.assertTrue(dt < RETURN_TICKS, "the seat is not back on its spot after " + dt + " ticks: " + fromHome(s));
                        return;
                    }
                    h.assertTrue(octants.size() >= 3, "the seat passed through only " + octants.size() + " octants: " + octants);
                    h.assertTrue(Math.abs(farthest[0] - CapstanConfig.WALK_RADIUS.get()) < 0.05,
                            "the walk did not reach walk_radius: " + farthest[0]);
                    h.assertTrue(walking[0] > 0, "the seat never left its spot");
                    h.assertTrue(r.phase() == null, "the anchor is not stowed: " + r.phase());
                    h.assertTrue(c.isAtStation(), "the crew member left the capstan");
                    stage[0] = 4;
                    c.discard();
                    h.succeed();
                }
                default -> { }
            }
        });
    }

    /** Letting the anchor go: the bars turn {@code DROPPING} (the other way) for the work, the crew member walks the other way round. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300, batch = BATCH + "drop")
    public static void droppingTurnsTheOtherWay(GameTestHelper h) {
        Rig r = rig(h);
        ServerLevel level = h.getLevel();
        CrewMember c = manned(h, r);
        StationRef ref = r.ref();
        int[] stage = {0};
        long[] since = {0};
        boolean[] seen = {false};
        double[] last = {0};
        h.onEachTick(() -> {
            long t = h.getTick();
            switch (stage[0]) {
                case 0 -> {
                    if (t < 3) return;
                    h.assertTrue(CrewStations.order(level, c, AnchorOrder.DROP_ANCHOR) == Stations.OrderResult.STARTED, "drop not started");
                    since[0] = t;
                    stage[0] = 1;
                }
                case 1 -> {
                    long dt = t - since[0];
                    CapstanBlockEntity be = capstan(h, r);
                    if (Stations.isWorking(level, ref)) {
                        if (dt > LAG) {
                            h.assertTrue(be.turning() == CapstanTurning.DROPPING, "not DROPPING at " + dt + ": " + be.turning());
                            double step = net.minecraft.util.Mth.wrapDegrees(be.walk().angle() - last[0]);
                            h.assertTrue(step > 0.0, "the crew member does not walk anticlockwise: " + last[0] + " to " + be.walk().angle());
                            seen[0] = true;
                        }
                        last[0] = be.walk().angle();
                        return;
                    }
                    h.assertTrue(seen[0], "the drop never turned the bars");
                    since[0] = t;
                    stage[0] = 2;
                }
                case 2 -> {
                    if (t - since[0] < LAG) return;
                    h.assertTrue(capstan(h, r).turning() == CapstanTurning.NONE, "still turning after the drop");
                    h.assertTrue(r.phase() != null, "the anchor did not go");
                    stage[0] = 3;
                    c.discard();
                    h.succeed();
                }
                default -> { }
            }
        });
    }

    /** With {@code crew_stations.capstan.animation} off the work goes on as before: the bars stand still, so does the seat. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = CONFIG_BATCH + "off")
    public static void animationOffKeepsTheCapstanStill(GameTestHelper h) {
        ConfigOverrides.during(h, CapstanConfig.ANIMATION, false);
        Rig r = rig(h);
        ServerLevel level = h.getLevel();
        CrewMember c = manned(h, r);
        StationRef ref = r.ref();
        int[] stage = {0};
        int[] worked = {0};
        h.onEachTick(() -> {
            long t = h.getTick();
            if (stage[0] == 0) {
                if (t < 3) return;
                h.assertTrue(CrewStations.order(level, c, AnchorOrder.DROP_ANCHOR) == Stations.OrderResult.STARTED, "drop not started");
                stage[0] = 1;
                return;
            }
            if (stage[0] != 1) return;
            h.assertTrue(capstan(h, r).turning() == CapstanTurning.NONE, "the bars turn with the animation off");
            h.assertTrue(fromHome(seat(c)) < 1e-6, "the seat moved with the animation off");
            if (Stations.isWorking(level, ref)) {
                if (worked[0]++ > LAG) {
                    h.assertTrue(c.stationPose() == CrewPose.CAPSTAN_PUSH, "not pushing on the spot: " + c.stationPose());
                }
                return;
            }
            h.assertTrue(worked[0] > 10, "the drop did not work long enough to tell: " + worked[0]);
            stage[0] = 2;
            c.discard();
            h.succeed();
        });
    }
}
