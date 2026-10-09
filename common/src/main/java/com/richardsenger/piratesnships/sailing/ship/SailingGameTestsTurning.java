package com.richardsenger.piratesnships.sailing.ship;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.wind.WindOverride;
import com.richardsenger.piratesnships.sailing.wind.WindSample;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.template.ShipTemplatePlacer;
import com.richardsenger.piratesnships.ship.template.ShipTemplates;
import java.util.Collection;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

/**
 * SH2 turning GameTests (docs/design.md §5.3, "Turning authority"): the starter sloop in a 160 × 160 basin runs up to
 * speed before the wind (bow north), then the helm goes hard to port. The wind is kept dead astern through the turn,
 * so the sail's drive does not change with the heading and the circle measures the rudder alone. Every run logs one {@code [turn table]} line: the
 * speed at the turn, the time and the advance and transfer at 90°, the time at 180° and the tactical diameter (the
 * transfer at 180°), each also in ship lengths, the steady turn rate and the radius it implies (speed / turn rate), and
 * the heel in the turn. The wind override is level-wide, so every test has its own batch.
 */
public final class SailingGameTestsTurning {

    private static final int SIZE = 160;
    private static final int SEA_TOP = 11;
    private static final int SETTLE = 80;
    /** Longest straight run to full speed before the helm goes over [ticks]. */
    private static final int RUN = 140;
    /** The helm goes over at the latest when the centre of mass has run this far north (test z), leaving room for the turn. */
    private static final double TURN_Z = 82.0;
    /** Longest turn watched [ticks]. */
    private static final int TURN = 2400;
    private static final int TIMEOUT = SETTLE + RUN + TURN + 40;
    private static final double CAPSIZE_DEGREES = 60.0;

    private SailingGameTestsTurning() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(SailingGameTestsTurning.class);
    }

    // ------------------------------------------------------------------ fixtures

    /**
     * 160×160 stone basin (floor y=1, walls at the edges), water y=2..{@value #SEA_TOP}: 10 blocks deep. The starter sloop draws
     * about 5.7 blocks at its stern (it floats 4° bow up), so the 6 blocks of the heel tests' basin ground it and hold it
     * to a few tenths of a block per second.
     */
    private static void basin(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                boolean wall = x == 0 || x == SIZE - 1 || z == 0 || z == SIZE - 1;
                level.setBlock(h.absolutePos(new BlockPos(x, 1, z)), stone, Block.UPDATE_CLIENTS);
                for (int y = 2; y <= SEA_TOP + 1; y++) {
                    level.setBlock(h.absolutePos(new BlockPos(x, y, z)), wall ? stone : y <= SEA_TOP ? water : Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** The starter sloop, bow north, its stern near the south wall at {@code x}. */
    private static SailingGameTestsHeel.Rig sloop(GameTestHelper h, int x) {
        basin(h);
        BlockPos feet = h.absolutePos(new BlockPos(x, SEA_TOP + 1, SIZE - 2));
        ShipTemplatePlacer.Result r = ShipTemplatePlacer.place(h.getLevel(), ShipTemplates.STARTER_SLOOP_ID, feet, Direction.NORTH,
                true, true, null);
        AssemblyResult a = r.assembly();
        if (a == null || a.shipId() == null) {
            throw new AssertionError("sloop not placed and assembled: " + r.outcome() + " " + a);
        }
        ShipTestCleanup.track(h, a.shipId());
        ShipBody ship = SableShips.byId(h.getLevel(), a.shipId());
        SailingRuntime rt = ship == null ? null : SailingRuntimes.getOrCreate(ship);
        if (rt == null || rt.helm() == null) {
            throw new AssertionError("no ship, sailing runtime or helm after assembly");
        }
        return new SailingGameTestsHeel.Rig(ship, rt);
    }

    /** Forward speed [blocks per tick]. */
    private static double speedEstimate(SailingGameTestsHeel.Rig r) {
        return Math.max(0.0, r.runtime().shipFrameVelocity(r.ship(), new Vector3d()).z) / 20.0;
    }

    private static double length(SailingRuntime rt) {
        int[] b = rt.bounds();
        return rt.bow().lengthOf(b[3] - b[0] + 1, b[5] - b[2] + 1);
    }

    private static Vector3d com(SailingGameTestsHeel.Rig r) {
        return SailingGameTestsShips.comWorld(new SailingGameTestsShips.Fixture(r.ship(), r.runtime()));
    }

    // ------------------------------------------------------------------ the run

    /** What one run measured. Times in ticks after the helm went over (-1 = never), distances in blocks. */
    record Turn(String label, double length, double speed, int t90, double advance90, double transfer90, int t180,
                double diameter, double rate, double turnSpeed, double heelBefore, double heelMax, double heelMean,
                boolean capsized, String stop) {

        double diameterLengths() {
            return diameter / length;
        }

        /** Steady radius from speed and turn rate, in ship lengths, as a diameter (2 v / ω). */
        double steadyDiameterLengths() {
            return rate <= 1e-6 ? Double.POSITIVE_INFINITY : 2.0 * turnSpeed / Math.toRadians(rate) / length;
        }

        String row() {
            return String.format(java.util.Locale.ROOT, "%s | L %.1f | v %.2f m/s | 90deg %s, advance %.1f (%.2f L) transfer %.1f (%.2f L) | 180deg %s, "
                            + "diameter %.1f (%.2f L) | rate %.2f deg/s at %.2f m/s, 2v/w %.2f L | heel before %.2f, max %.2f, "
                            + "mean %.2f (+ = port up) | capsized %s | %s",
                    label, length, speed, t90 < 0 ? "-" : (t90 / 20.0) + " s", advance90, advance90 / length, transfer90,
                    transfer90 / length, t180 < 0 ? "-" : (t180 / 20.0) + " s", diameter, diameterLengths(), rate, turnSpeed,
                    steadyDiameterLengths(), heelBefore, heelMax, heelMean, capsized, stop);
        }
    }

    /**
     * Runs the schedule of the class comment: the helm hard to port ({@code rudder} degrees, clamped to the maximum) after
     * the run-up in a wind of {@code wind} blocks/s from the east with every sail at {@code trim}.
     */
    static void run(GameTestHelper h, String label, double wind, SailTrim trim, double windowFrom, double windowTo, boolean to180,
                    Consumer<Turn> check) {
        SailingGameTestsHeel.Rig r = sloop(h, SIZE - 22);
        String dim = h.getLevel().dimension().location().toString();
        double len = length(r.runtime());
        double[] start = new double[6]; // x, z, heading, speed, heel before (sum), samples
        double[] at = new double[8]; // advance90, transfer90, diameter, rate sum, speed sum, samples, heel max, heel sum
        int[] times = {-1, -1, 0}; // t90, t180, heel samples
        double[] heading = {0.0, 0.0}; // last heading, unwrapped change
        boolean[] capsized = {false};
        String[] stop = {"timeout"};
        boolean[] done = {false};
        long[] turnAt = {SETTLE + RUN};
        h.onEachTick(() -> {
            long t = h.getTick();
            if (done[0]) {
                return;
            }
            if (r.ship().isRemoved()) {
                capsized[0] = true;
                stop[0] = "ship removed";
                done[0] = true;
                return;
            }
            double roll = SailingGameTestsHeel.rollDegrees(r);
            if (Math.abs(roll) > CAPSIZE_DEGREES) {
                capsized[0] = true;
            }
            Vector3d c = com(r);
            net.minecraft.world.phys.Vec3 rel = h.relativeVec(new net.minecraft.world.phys.Vec3(c.x, c.y, c.z));
            if (t > SETTLE && t + 20 < turnAt[0] && rel.z < TURN_Z + 20 * speedEstimate(r)) {
                turnAt[0] = t + 20; // 1 s of heel samples before the helm goes over
            }
            double speed = r.runtime().shipFrameVelocity(r.ship(), new Vector3d()).z;
            double hd = r.runtime().headingDegrees(r.ship());
            if (t >= SETTLE) {
                // the wind stays dead astern, also through the turn: the sail's drive is the same on every heading, so
                // the circle measures the rudder alone (a fixed wind would stall a square-rigged sloop head to wind)
                WindOverride.set(dim, WindSample.normalizeDegrees(hd + 180.0), wind, h.getLevel().getGameTime() + 40);
            }
            if (t == SETTLE) {
                for (BlockPos p : r.runtime().sailPositions()) {
                    SailingRuntimes.setTrim(h.getLevel(), p, trim);
                }
            } else if (t > turnAt[0] - 20 && t <= turnAt[0]) {
                start[4] += roll;
                start[5]++;
                if (t == turnAt[0]) {
                    start[0] = c.x;
                    start[1] = c.z;
                    start[2] = hd;
                    start[3] = speed;
                    heading[0] = hd;
                    ShipControls.setRudderAngle(h.getLevel(), r.runtime().helm(), -360.0);
                }
            } else if (t > turnAt[0]) {
                int dt = (int) (t - turnAt[0]);
                heading[1] += WindSample.normalizeDegrees(hd - heading[0] + 180.0) - 180.0;
                heading[0] = hd;
                double turned = -heading[1]; // to port
                double h0 = Math.toRadians(start[2]);
                double fx = Math.sin(h0), fz = -Math.cos(h0); // initial bow direction (compass: 0 = north = -z)
                double dx = c.x - start[0], dz = c.z - start[1];
                double advance = dx * fx + dz * fz;
                double transfer = -(dx * -fz + dz * fx); // to port of the initial heading = positive
                double heel = roll - start[4] / start[5];
                at[6] = Math.max(at[6], Math.abs(heel));
                at[7] += heel;
                times[2]++;
                if (turned >= windowFrom && turned < windowTo) {
                    at[4] += speed;
                    at[5]++;
                }
                if (times[0] < 0 && turned >= 90.0) {
                    times[0] = dt;
                    at[0] = advance;
                    at[1] = transfer;
                }
                if (times[1] < 0 && turned >= 180.0) {
                    times[1] = dt;
                    at[2] = transfer;
                    stop[0] = "180 reached";
                    done[0] = true;
                }
                if (!to180 && turned >= windowTo) {
                    stop[0] = "window done";
                    done[0] = true;
                }
                // the hull must not touch a wall: keep its centre half a length plus 3 blocks inside
                double margin = len * 0.5 + 3.0;
                if (!done[0] && (rel.x < margin || rel.x > SIZE - margin || rel.z < margin || rel.z > SIZE - margin)) {
                    stop[0] = String.format(java.util.Locale.ROOT, "near the wall after %.0f deg", turned);
                    done[0] = true;
                }
                if (dt >= TURN) {
                    done[0] = true;
                }
                if (done[0]) {
                    at[3] = turned / (dt / 20.0); // overall mean rate, replaced by the 45..135 window below when known
                }
            }
            if (t % 100 == 0) {
                Constants.LOG.info("[turn test] {} t={} heading {} turned {} fwd {} roll {} rudder {} com {}/{}", label, t,
                        String.format(java.util.Locale.ROOT, "%.1f", hd), String.format(java.util.Locale.ROOT, "%.1f", -heading[1]), String.format(java.util.Locale.ROOT, "%.2f", speed),
                        String.format(java.util.Locale.ROOT, "%.2f", roll), r.runtime().rudderAngle(), String.format(java.util.Locale.ROOT, "%.1f", rel.x), String.format(java.util.Locale.ROOT, "%.1f", rel.z));
            }
        });
        // the steady turn rate: degrees per second between 45 and 135 degrees of turn
        int[] window = {-1, -1};
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t <= turnAt[0] || window[1] >= 0) return;
            double turned = -heading[1];
            if (window[0] < 0 && turned >= windowFrom) window[0] = (int) t;
            if (window[0] >= 0 && turned >= windowTo) window[1] = (int) t;
        });
        h.runAfterDelay(TIMEOUT - 20, () -> {
            WindOverride.clear(dim);
            double rate = window[1] > window[0] && window[0] >= 0 ? (windowTo - windowFrom) / ((window[1] - window[0]) / 20.0) : at[3];
            Turn res = new Turn(label, len, start[3], times[0], at[0], at[1], times[1], at[2], rate,
                    at[5] == 0 ? start[3] : at[4] / at[5], start[4] / Math.max(1, start[5]), at[6],
                    times[2] == 0 ? 0 : at[7] / times[2], capsized[0], stop[0]);
            Constants.LOG.info("[turn table] {}", res.row());
            check.accept(res);
        });
    }

    // ------------------------------------------------------------------ the pinned turns

    /** Tactical diameter and steady circle at full speed, in ship lengths (design: "about three to four"). */
    static final double MIN_CIRCLE = 3.0;
    static final double MAX_CIRCLE = 4.0;

    private static void sane(GameTestHelper h, Turn t) {
        h.assertTrue(!t.capsized(), t.label() + ": capsized; " + t.row());
        h.assertTrue(t.heelMax() <= Math.min(5.0, SailingConfig.MAX_HEEL_DEGREES.get()), t.label() + ": heeled " + t.heelMax()
                + " deg in the turn; " + t.row());
    }

    /*
     * Measured on the starter sloop (L = 29 blocks), wind 12 blocks/s dead astern, full sail: 1.33 blocks/s, helm hard
     * over (35 deg). rudder_force_factor 1 (before SH2): 0.51 deg/s, a steady circle of 10.2 ship lengths (296 blocks;
     * a 90 deg turn would take about 3 minutes). 2: 1.03 deg/s, 5.1 L. 3: 1.54 deg/s, 3.4 L. 4: 2.05 deg/s, 2.55 L, 90 deg
     * in 43.9 s. max_rudder_angle 45 at factor 1: 0.63 deg/s, 8.3 L (the angle helps a fifth, the factor in proportion).
     * Half sail (0.69 blocks/s) at factor 1: 10.3 L, a moderate wind (0.65 blocks/s) at factor 3: 3.44 L: the circle does
     * not depend on the speed, only the time to sail it.
     */

    /**
     * Full speed, helm hard over: the sloop turns 90 degrees in 45 to 75 s and 180 degrees on a tactical diameter of
     * {@link #MIN_CIRCLE} to {@link #MAX_CIRCLE} ship lengths, heels less than 5 degrees and never capsizes.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_160_DEEP, timeoutTicks = TIMEOUT, batch = "pirates_n_ships_sailing_turn_full")
    public static void sloopTurnsACircleOfThreeToFourLengthsAtFullSpeed(GameTestHelper h) {
        run(h, "full sail", SailingGameTestsHeel.STRONG, SailTrim.FULL, 45.0, 135.0, true, t -> {
            sane(h, t);
            h.assertTrue(t.speed() > 1.0, "the sloop did not gather way: " + t.row());
            h.assertTrue(t.t90() >= 45 * 20 && t.t90() <= 75 * 20, "90 deg not in 45..75 s: " + t.row());
            h.assertTrue(t.t180() > 0, "did not turn 180 deg: " + t.row());
            h.assertTrue(t.diameterLengths() >= MIN_CIRCLE && t.diameterLengths() <= MAX_CIRCLE,
                    "tactical diameter " + t.diameterLengths() + " L not in " + MIN_CIRCLE + ".." + MAX_CIRCLE + ": " + t.row());
            h.assertTrue(t.steadyDiameterLengths() >= MIN_CIRCLE && t.steadyDiameterLengths() <= MAX_CIRCLE,
                    "steady circle " + t.steadyDiameterLengths() + " L not in " + MIN_CIRCLE + ".." + MAX_CIRCLE + ": " + t.row());
            h.succeed();
        });
    }

    /**
     * Half sail, about half the speed: the rudder still answers, on the same circle (the force grows linearly with the
     * speed), only half as fast. Measured between 20 and 60 degrees of turn to keep the test short.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_160_DEEP, timeoutTicks = TIMEOUT, batch = "pirates_n_ships_sailing_turn_half")
    public static void sloopAnswersTheHelmOnTheSameCircleAtHalfSpeed(GameTestHelper h) {
        run(h, "half sail", SailingGameTestsHeel.STRONG, SailTrim.HALF, 20.0, 60.0, false, t -> {
            sane(h, t);
            h.assertTrue(t.speed() > 0.3 && t.speed() < 1.0, "not about half speed: " + t.row());
            h.assertTrue(t.rate() > 0.4, "the rudder hardly answers at half speed: " + t.row());
            h.assertTrue(t.steadyDiameterLengths() >= MIN_CIRCLE && t.steadyDiameterLengths() <= MAX_CIRCLE + 0.5,
                    "circle at half speed " + t.steadyDiameterLengths() + " L: " + t.row());
            h.succeed();
        });
    }
}
