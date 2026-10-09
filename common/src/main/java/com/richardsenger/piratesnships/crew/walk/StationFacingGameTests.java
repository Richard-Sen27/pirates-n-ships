package com.richardsenger.piratesnships.crew.walk;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.cannon.CannonBlock;
import com.richardsenger.piratesnships.combat.cannon.CannonContent;
import com.richardsenger.piratesnships.combat.cannon.CannonRules;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.crew.npc.StationFacing;
import com.richardsenger.piratesnships.crew.npc.StationPoses;
import com.richardsenger.piratesnships.ship.hull.pump.HullRepairContent;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationConfig;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.StationGameTests;
import com.richardsenger.piratesnships.station.StationState;
import com.richardsenger.piratesnships.station.Stations;
import com.richardsenger.piratesnships.station.seat.StationSeat;
import com.richardsenger.piratesnships.station.winch.SailOrder;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

/**
 * ST5 (docs/design.md §6 "Facing the station"): a crew member that takes its place at a station turns to face it
 * smoothly at {@code crew_stations.turn_degrees_per_tick} and then stays facing it, also while the ship turns. Each test
 * puts the crew member on its seat facing away from the station (or lets it walk in from the far side) and checks that
 * its body and view are within {@link #TOLERANCE} of the station's facing within {@link #TURN_WITHIN} ticks, did not
 * snap round on the first tick, and hold it for {@link #HOLD} more ticks with the head within 45 degrees of the body.
 * Every test runs in a config batch of its own.
 */
public final class StationFacingGameTests {

    private static final String BATCH = "pirates_n_ships_config_crew_facing_";
    private static final float TOLERANCE = 5f;
    private static final int TURN_WITHIN = 20;
    private static final int HOLD = 100;

    private StationFacingGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(StationFacingGameTests.class);
    }

    // ------------------------------------------------------------------ helpers

    private static float off(float a, float b) {
        return Math.abs(Mth.wrapDegrees(a - b));
    }

    /** The yaw a crew member on {@code seat} should face: towards the first of {@code blocks} beside the seat's cell. */
    private static float wanted(GameTestHelper h, ShipBody ship, StationSeat seat, Predicate<BlockPos> block) {
        BlockPos spot = seat.blockPosition();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (block.test(spot.relative(d))) return StationPoses.yaw(d, ship.orientation());
        }
        throw new GameTestAssertException("the seat at " + spot + " is not beside its station");
    }

    private static StationSeat seat(CrewMember c) {
        if (c.getVehicle() instanceof StationSeat seat) return seat;
        throw new GameTestAssertException("not on a station seat: " + c.getVehicle());
    }

    /** Turns body, head and view of {@code c} to face away from {@code yaw}, as a crew member that walked in backwards. */
    private static void faceAway(CrewMember c, float yaw) {
        float away = Mth.wrapDegrees(yaw + 180f);
        c.setYRot(away);
        c.setYBodyRot(away);
        c.yBodyRotO = away;
        c.setYHeadRot(away);
    }

    /** A crew member spawned at relative {@code at} and assigned to the station at plot position {@code station}: seated at once. */
    private static CrewMember manned(GameTestHelper h, BlockPos at, BlockPos station) {
        CrewMember c = h.spawn(StationContent.CREW_MEMBER.get(), at);
        CrewStations.AssignResult r = CrewStations.assign(h.getLevel(), c, station);
        h.assertTrue(r == CrewStations.AssignResult.ASSIGNED, "assign: " + r);
        h.assertTrue(c.isAtStation(), "the crew member does not ride the station seat");
        return c;
    }

    /**
     * From now on: after one tick the crew member has not snapped round yet ({@code instant}: it has), within
     * {@link #TURN_WITHIN} ticks it faces {@code want} (read every tick: the ship may turn), then holds it for
     * {@link #HOLD} ticks; {@code check} runs on every held tick, then the test ends.
     */
    private static void turnsAndHolds(GameTestHelper h, CrewMember c, Supplier<Float> want, boolean instant,
                                      Runnable check) {
        turnsAndHolds(h, c, want, instant, check, () -> { });
    }

    /** {@link #turnsAndHolds(GameTestHelper, CrewMember, Supplier, boolean, Runnable)}, with {@code end} run last. */
    private static void turnsAndHolds(GameTestHelper h, CrewMember c, Supplier<Float> want, boolean instant,
                                      Runnable check, Runnable end) {
        long start = h.getTick();
        float startOff = off(c.yBodyRot, want.get());
        long[] facedAt = {-1};
        h.onEachTick(() -> {
            long t = h.getTick() - start;
            if (t <= 0 || facedAt[0] >= 0 && t > facedAt[0] + HOLD) return;
            h.assertTrue(c.isAtStation(), "left the station at tick " + t);
            float target = want.get();
            float body = off(c.yBodyRot, target);
            if (t == 1) {
                if (instant) {
                    h.assertTrue(body < TOLERANCE, "the turn at the maximum rate was not instant: " + body + " off");
                } else if (startOff > 2 * StationConfig.TURN_DEGREES_PER_TICK.get()) {
                    h.assertTrue(body > TOLERANCE, "snapped round in one tick from " + startOff + " off (no smooth turn)");
                    float turned = startOff - body;
                    h.assertTrue(turned <= StationConfig.TURN_DEGREES_PER_TICK.get() + 0.5f,
                            "turned " + turned + " degrees in one tick");
                }
            }
            if (facedAt[0] < 0) {
                if (body < TOLERANCE && off(c.getYRot(), target) < TOLERANCE) {
                    facedAt[0] = t;
                    Constants.LOG.info("[ST5] faced the station {} ticks after seating, from {} degrees off", t, startOff);
                } else {
                    h.assertTrue(t < TURN_WITHIN, "does not face the station " + TURN_WITHIN + " ticks after seating: body "
                            + c.yBodyRot + ", view " + c.getYRot() + ", want " + target);
                }
                return;
            }
            h.assertTrue(body < TOLERANCE && off(c.getYRot(), target) < TOLERANCE, "turned away at tick " + t + ": body "
                    + c.yBodyRot + ", view " + c.getYRot() + ", want " + target);
            h.assertTrue(off(c.getYHeadRot(), c.yBodyRot) <= StationFacing.MAX_HEAD_DEGREES + 0.5f,
                    "the head looks " + off(c.getYHeadRot(), c.yBodyRot) + " degrees away from the body");
            check.run();
            if (t == facedAt[0] + HOLD) {
                end.run();
                if (c.getVehicle() != null) c.getVehicle().discard();
                c.discard();
                h.succeed();
            }
        });
    }

    // ------------------------------------------------------------------ the winch

    /**
     * WALK1 + ST5: a sailor walks to the winch round to its far side (north and east of the winch are walled, so the spot
     * is south of it and it arrives walking south or sideways, not facing the winch), turns to face it after it took the
     * seat, and keeps facing it while the ship turns on the water (a yaw rate of about 0.15 rad/s, driven every tick).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 400, batch = BATCH + "walk")
    public static void sailorWalksToTheWinchAndTurnsToFaceIt(GameTestHelper h) {
        CrewWalkGameTests.Ship s = CrewWalkGameTests.ship(h, true, x -> {
            for (BlockPos p : new BlockPos[] {CrewWalkGameTests.WINCH.north(), CrewWalkGameTests.WINCH.east()}) {
                x.setBlock(p, Blocks.OAK_PLANKS);
                x.setBlock(p.above(), Blocks.OAK_PLANKS);
            }
        });
        CrewMember c = CrewWalkGameTests.crew(h, s, CrewWalkGameTests.START);
        BlockPos winch = s.plot(CrewWalkGameTests.WINCH);
        boolean[] seated = {false};
        float[] shipYaw0 = {Float.NaN};
        h.onEachTick(() -> {
            if (h.getTick() == CrewWalkGameTests.SETTLE) {
                CrewStations.AssignResult r = CrewStations.assign(h.getLevel(), c, winch);
                h.assertTrue(r == CrewStations.AssignResult.ASSIGNED, "assign: " + r);
                h.assertTrue(c.walk() != null, "not walking to the winch");
            }
            if (!seated[0] && c.isAtStation()) {
                seated[0] = true;
                h.assertTrue(seat(c).blockPosition().equals(winch.south()), "the spot is not south of the winch: "
                        + seat(c).blockPosition() + " for " + winch);
                shipYaw0[0] = StationPoses.yaw(Direction.SOUTH, s.body().orientation());
                turnsAndHolds(h, c, () -> wanted(h, s.body(), seat(c), winch::equals), false, () -> { }, () -> {
                    float turned = off(StationPoses.yaw(Direction.SOUTH, s.body().orientation()), shipYaw0[0]);
                    Constants.LOG.info("[ST5] the ship turned {} degrees while the sailor held the winch", turned);
                    h.assertTrue(turned >= 10f, "the ship did not turn (" + turned + " degrees): the hold proves nothing");
                });
            }
            if (seated[0] && !s.body().isRemoved()) {
                Vector3d w = s.body().angularVelocity();
                double dy = Math.max(-0.05, Math.min(0.05, 0.15 - w.y));
                s.body().addVelocity(new Vector3d(), new Vector3d(-w.x * 0.5, dy, -w.z * 0.5));
            }
        });
    }

    /**
     * A sailor put on the winch seat facing away turns to it and keeps facing it while it hoists (the work runs for 400
     * ticks at {@code ticks_per_trim_step} 200).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH + "winch")
    public static void sailorFacesTheWinchWhileHoisting(GameTestHelper h) {
        ConfigOverrides.during(h, StationConfig.ENABLED, true);
        ConfigOverrides.during(h, StationConfig.TICKS_PER_TRIM_STEP, 200);
        StationGameTests.Fixture f = StationGameTests.ship(h, false, x -> { });
        CrewMember c = manned(h, new BlockPos(2, 9, 2), f.winch());
        Supplier<Float> want = () -> wanted(h, f.ship(), seat(c), f.winch()::equals);
        faceAway(c, want.get());
        h.assertTrue(CrewStations.order(h.getLevel(), c, SailOrder.HOIST) == Stations.OrderResult.STARTED, "hoist not started");
        turnsAndHolds(h, c, want, false, () -> {
            StationState<Object> st = Stations.state(c.assignment());
            h.assertTrue(st != null && st.phase() == StationState.Phase.OPERATING, "the winch stopped working");
            h.assertTrue(c.isWorking(), "the sailor does not show the work");
        });
    }

    // ------------------------------------------------------------------ pump, cannon, helm

    /** A crew member put on the bilge pump's seat in the hold facing away turns to the pump and keeps facing it. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = BATCH + "pump")
    public static void crewFacesThePump(GameTestHelper h) {
        ConfigOverrides.during(h, StationConfig.ENABLED, true);
        DryHullGameTests.basin(h, 0, 23, true);
        BlockPos helm = DryHullGameTests.hull(h, 9, false);
        h.setBlock(new BlockPos(12, 6, 12), HullRepairContent.BILGE_PUMP.get());
        DryHullGameTests.Fixture f = DryHullGameTests.assemble(h, helm);
        BlockPos pump = f.hold(1, -3, 1);
        h.assertTrue(h.getLevel().getBlockState(pump).is(HullRepairContent.BILGE_PUMP.get()), "the pump is not in the plot");
        CrewMember c = manned(h, new BlockPos(11, 10, 11), pump);
        Supplier<Float> want = () -> wanted(h, f.ship(), seat(c), pump::equals);
        faceAway(c, want.get());
        turnsAndHolds(h, c, want, false, () -> { });
    }

    /** A gunner put on the cannon's seat facing away turns to the gun (the half beside its spot) and keeps facing it. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = BATCH + "cannon")
    public static void gunnerFacesTheCannon(GameTestHelper h) {
        ConfigOverrides.during(h, StationConfig.ENABLED, true);
        DryHullGameTests.basin(h, 0, 23, true);
        BlockPos helm = DryHullGameTests.hull(h, 9, false);
        BlockState master = CannonContent.CANNON.get().defaultBlockState().setValue(CannonBlock.FACING, Direction.WEST);
        BlockPos rel = new BlockPos(10, 9, 10);
        h.setBlock(rel, master);
        h.setBlock(CannonRules.rearOf(rel, Direction.WEST), CannonContent.CANNON.get().rearState(master));
        DryHullGameTests.Fixture f = DryHullGameTests.assemble(h, helm);
        BlockPos cannon = f.hold(-1, 0, -1);
        h.assertTrue(CannonBlock.isMaster(h.getLevel().getBlockState(cannon)), "the cannon is not in the plot");
        CrewMember c = manned(h, new BlockPos(11, 10, 11), cannon);
        Supplier<Float> want = () -> wanted(h, f.ship(), seat(c),
                p -> h.getLevel().getBlockState(p).getBlock() instanceof CannonBlock);
        faceAway(c, want.get());
        turnsAndHolds(h, c, want, false, () -> { });
    }

    /** A helmsman put on the helm's seat facing away turns to the wheel (ART7's pose holds his head) and keeps facing it. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = BATCH + "helm")
    public static void helmsmanFacesTheHelm(GameTestHelper h) {
        ConfigOverrides.during(h, StationConfig.ENABLED, true);
        DryHullGameTests.basin(h, 0, 23, true);
        BlockPos helm = DryHullGameTests.hull(h, 9, false);
        DryHullGameTests.Fixture f = DryHullGameTests.assemble(h, helm);
        CrewMember c = manned(h, new BlockPos(11, 10, 13), f.helmPlot());
        Supplier<Float> want = () -> wanted(h, f.ship(), seat(c), f.helmPlot()::equals);
        faceAway(c, want.get());
        turnsAndHolds(h, c, want, false, () -> h.assertTrue(c.stationPose() != null, "no helm pose"));
    }

    /** {@code turn_degrees_per_tick} at its maximum (180): the crew member faces the winch on the first tick. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = BATCH + "instant")
    public static void theMaximumRateTurnsAtOnce(GameTestHelper h) {
        ConfigOverrides.during(h, StationConfig.ENABLED, true);
        ConfigOverrides.during(h, StationConfig.TURN_DEGREES_PER_TICK, 180.0);
        StationGameTests.Fixture f = StationGameTests.ship(h, false, x -> { });
        CrewMember c = manned(h, new BlockPos(2, 9, 2), f.winch());
        Supplier<Float> want = () -> wanted(h, f.ship(), seat(c), f.winch()::equals);
        faceAway(c, want.get());
        turnsAndHolds(h, c, want, true, () -> { });
    }
}
