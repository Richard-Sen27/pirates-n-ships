package com.richardsenger.piratesnships.sailing.ship;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.force.ShipFrame;
import com.richardsenger.piratesnships.sailing.wind.WindParams;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.hull.flooding.FloodReport;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntime;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntimes;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.template.ShipTemplatePlacer;
import com.richardsenger.piratesnships.ship.template.ShipTemplates;
import java.util.Collection;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Blocks;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * SH1 heel GameTests (docs/design.md §5.2, "Heel"): the starter sloop template and the 7×17 test hull in a beam wind,
 * measured and held to the targets. Each run settles the ship for {@link #SETTLE} ticks, sets the wind (from the east,
 * on the starboard beam of a ship heading north or south) and the trim of every sail, sails {@link #SAIL} ticks (10 s),
 * then furls and watches the ship right itself for {@link #RIGHT} ticks (5 s). Every run logs one {@code [heel table]}
 * line: rest list, steady heel (mean over the last 3 s of sailing), largest heel, time to 90 % of the steady heel,
 * capsize, time until the heel is below 2° after furling, forward speed. The wind override is level-wide and some runs
 * override the half trim factor, so every test has its own batch.
 */
public final class SailingGameTestsHeel {

    /** A moderate beam wind [blocks/s]: the middle of the clear-weather range (3..12). */
    static final double MODERATE = 6.0;
    /** A strong beam wind: the clear-weather maximum. */
    static final double STRONG = WindParams.DEFAULTS.maxStrength();
    /** The strongest wind the wind model produces: a full thunderstorm at the peak of the strongest gust. */
    static final double STRONGEST = WindParams.DEFAULTS.maxStrength() * WindParams.DEFAULTS.thunderMultiplier()
            * (1.0 + WindParams.DEFAULTS.gustStrength());

    private static final int SETTLE = 80;
    private static final int SAIL = 200;
    private static final int RIGHT = 100;
    private static final int STEADY_FROM = SETTLE + SAIL - 60;
    private static final double CAPSIZE_DEGREES = 60.0;
    private static final int TIMEOUT = SETTLE + SAIL + RIGHT + 40;

    private SailingGameTestsHeel() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(SailingGameTestsHeel.class);
    }

    // ------------------------------------------------------------------ fixtures

    /** A ship under test: the body, its sailing runtime and the absolute test origin. */
    record Rig(ShipBody ship, SailingRuntime runtime) { }

    /**
     * 48×48 stone basin (floor y=1, walls at the edges), water y=2..7, open to the sky (the barrier ceiling of the
     * 48×16×48 template is removed, the sloop's mast reaches far above it).
     */
    static void basin48(GameTestHelper h) {
        for (int x = 0; x < 48; x++) {
            for (int z = 0; z < 48; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                boolean wall = x == 0 || x == 47 || z == 0 || z == 47;
                for (int y = 2; y <= 8; y++) {
                    h.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : y <= 7 ? Blocks.WATER : Blocks.AIR);
                }
                for (int y = 12; y <= 20; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (h.getBlockState(p).is(Blocks.BARRIER)) {
                        h.setBlock(p, Blocks.AIR);
                    }
                }
            }
        }
    }

    /**
     * The starter sloop placed and assembled from its template, bow north, its stern near the south wall of a
     * {@link #basin48} so it has the most room to sail.
     */
    static Rig sloop(GameTestHelper h) {
        basin48(h);
        BlockPos feet = h.absolutePos(new BlockPos(24, 8, 47));
        ShipTemplatePlacer.Result r = ShipTemplatePlacer.place(h.getLevel(), ShipTemplates.STARTER_SLOOP_ID, feet, Direction.NORTH,
                true, true, null);
        AssemblyResult a = r.assembly();
        if (a == null || a.shipId() == null) {
            throw new AssertionError("sloop not placed and assembled: " + r.outcome() + " " + a);
        }
        ShipTestCleanup.track(h, a.shipId());
        return rig(h, a.shipId());
    }

    /** The 7×17 test hull ({@link SailingGameTestsShips#longHull}) in a 40×40 basin, its one square sail furled. */
    static Rig longHull(GameTestHelper h) {
        SailingGameTestsShips.basin(h, true);
        SailingGameTestsShips.Fixture f = SailingGameTestsShips.assemble(h, SailingGameTestsShips.longHull(h));
        return new Rig(f.ship(), f.runtime());
    }

    private static Rig rig(GameTestHelper h, java.util.UUID id) {
        ShipBody ship = SableShips.byId(h.getLevel(), id);
        SailingRuntime rt = ship == null ? null : SailingRuntimes.getOrCreate(ship);
        if (rt == null) {
            throw new AssertionError("no ship or no sailing runtime after assembly");
        }
        return new Rig(ship, rt);
    }

    /** Signed roll [degrees], + = port side up. */
    static double rollDegrees(Rig r) {
        Quaterniond q = r.runtime().bow().shipToWorld(r.ship().orientation(new Quaterniond()), new Quaterniond());
        Vector3d port = q.transform(new Vector3d(ShipFrame.PORT));
        return Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, port.y))));
    }

    /** Signed pitch [degrees], + = bow up. */
    static double pitchDegrees(Rig r) {
        Quaterniond q = r.runtime().bow().shipToWorld(r.ship().orientation(new Quaterniond()), new Quaterniond());
        Vector3d fwd = q.transform(new Vector3d(ShipFrame.FORWARD));
        return Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, fwd.y))));
    }

    /** Dry lift and its centroid relative to the COM (ship frame), the weight, the last applied force torque (ship frame). */
    static String diagnostics(GameTestHelper h, Rig r) {
        StringBuilder sb = new StringBuilder();
        Vector3d com = new Vector3d();
        r.ship().centerOfMass(com);
        sb.append(String.format("weight %.0f", r.ship().mass() * 11.0));
        HullRuntime hull = HullRuntimes.get(h.getLevel(), r.ship().id());
        FloodReport rep = hull == null ? null : hull.lastReport();
        if (rep != null && rep.dryCentroid() != null) {
            Vector3d c = new Vector3d(rep.dryCentroid().x(), rep.dryCentroid().y(), rep.dryCentroid().z()).sub(com);
            Vector3d s = r.runtime().bow().toShip(c, new Vector3d());
            sb.append(String.format(" dryLift %.0f at %.2f/%.2f/%.2f", 10.5 * rep.submergedDryVolume(), s.x, s.y, s.z));
        }
        if (rep != null) {
            sb.append(String.format(" flood %.1f sea-com %.2f", rep.floodVolume(),
                    hull.seaWorldY() - SailingGameTestsShips.comWorld(new SailingGameTestsShips.Fixture(r.ship(), r.runtime())).y));
        }
        var fb = r.runtime().lastBreakdown();
        if (fb != null) {
            sb.append(String.format(" torque %.1f/%.1f/%.1f", fb.torque().x(), fb.torque().y(), fb.torque().z()));
            for (var c : fb.contributions()) {
                sb.append(String.format(" %s=%.1f/%.1f/%.1f@%.1f/%.1f/%.1f", c.source(), c.torque().x(), c.torque().y(), c.torque().z(),
                        c.point().x(), c.point().y(), c.point().z()));
            }
        }
        int[] b = r.runtime().bounds();
        sb.append(" box ").append(b[3] - b[0] + 1).append('x').append(b[4] - b[1] + 1).append('x').append(b[5] - b[2] + 1)
                .append(String.format(" comY-minY %.2f", com.y - b[1]));
        return sb.toString();
    }

    private static void trimAll(GameTestHelper h, Rig r, SailTrim trim) {
        for (BlockPos p : r.runtime().sailPositions()) {
            SailingRuntimes.setTrim(h.getLevel(), p, trim);
        }
    }

    /** What one run measured. Angles in degrees, times in ticks after the event (-1 = never). */
    record Result(String label, double rest, double restPitch, double steady, double max, int t90, boolean capsized,
                  int rightedAfter, double finalHeel, double speed, double mass, int sails, double restClearance,
                  double minClearance) {

        String row() {
            return String.format("%s | rest %.2f (pitch %.2f) | steady %.2f | max %.2f | t90 %s | capsized %s | <2deg after furl %s | "
                            + "heel 5 s after furl %.2f | fwd %.2f m/s | mass %.0f | sails %d | clearance at rest %.2f, least %.2f",
                    label, rest, restPitch, steady, max, t90 < 0 ? "-" : (t90 / 20.0) + " s", capsized,
                    rightedAfter < 0 ? "never" : (rightedAfter / 20.0) + " s", finalHeel, speed, mass, sails, restClearance,
                    minClearance);
        }
    }

    /** Height of the hull's lowest corner above the basin floor's top face (relative y=2 in every heel basin) [blocks]. */
    static double clearance(GameTestHelper h, Rig r) {
        return SailingGameTestsShips.hullBottomY(r.ship()) - (h.absolutePos(BlockPos.ZERO).getY() + 2.0);
    }

    /**
     * Runs the schedule described in the class comment and hands the result to {@code check} (which asserts and
     * succeeds). {@code halfFactor} overrides {@code half_trim_factor} when positive (a quarter trim is HALF at 0.25).
     */
    static void run(GameTestHelper h, String label, Function<GameTestHelper, Rig> build, double wind, SailTrim trim, double halfFactor,
                    java.util.function.Consumer<Result> check) {
        if (halfFactor > 0) {
            ConfigOverrides.during(h, SailingConfig.HALF_TRIM_FACTOR, halfFactor);
        }
        Rig r = build.apply(h);
        if (r.runtime().sailCount() == 0) {
            throw new AssertionError("the ship has no sails");
        }
        double[] rest = new double[3];
        double[] acc = new double[4]; // sum heel over steady window, samples, max |heel|, sum forward speed
        int[] t90 = {-1};
        int[] righted = {-1};
        boolean[] capsized = {false};
        double[] heel = new double[SAIL];
        double[] clear = {Double.NaN, Double.POSITIVE_INFINITY}; // at the end of the settling, least from then on
        h.onEachTick(() -> {
            long t = h.getTick();
            if (r.ship().isRemoved()) {
                capsized[0] = true;
                return;
            }
            if (t >= SETTLE && t % 5 == 0) {
                double c = clearance(h, r);
                if (t == SETTLE) {
                    clear[0] = c;
                }
                clear[1] = Math.min(clear[1], c);
            }
            double roll = rollDegrees(r);
            if (Math.abs(roll) > CAPSIZE_DEGREES) {
                capsized[0] = true;
            }
            if (t >= SETTLE - 20 && t < SETTLE) {
                rest[0] += roll;
                rest[1] += pitchDegrees(r);
                rest[2]++;
            } else if (t == SETTLE) {
                SailingGameTestsShips.fixWind(h, 90.0, wind);
                trimAll(h, r, trim);
            } else if (t > SETTLE && t <= SETTLE + SAIL) {
                double d = roll - rest[0] / rest[2];
                heel[(int) (t - SETTLE - 1)] = d;
                acc[2] = Math.max(acc[2], Math.abs(d));
                if (t > STEADY_FROM) {
                    acc[0] += d;
                    acc[1]++;
                    acc[3] += r.runtime().shipFrameVelocity(r.ship(), new Vector3d()).z;
                }
                if (t == SETTLE + SAIL) {
                    trimAll(h, r, SailTrim.FURLED);
                }
            } else if (t > SETTLE + SAIL && t <= SETTLE + SAIL + RIGHT) {
                if (righted[0] < 0 && Math.abs(roll) < 2.0) {
                    righted[0] = (int) (t - SETTLE - SAIL);
                }
            }
            if (t % 20 == 0) {
                Constants.LOG.info("[heel test] {} t={} roll {} pitch {} fwd {} clearance {} com {} {}", label, t, String.format("%.2f", roll),
                        String.format("%.2f", pitchDegrees(r)),
                        String.format("%.2f", r.runtime().shipFrameVelocity(r.ship(), new Vector3d()).z),
                        String.format("%.2f", clearance(h, r)),
                        SailingGameTestsShips.comWorld(new SailingGameTestsShips.Fixture(r.ship(), r.runtime())), diagnostics(h, r));
            }
        });
        h.runAfterDelay(SETTLE + SAIL + RIGHT + 1, () -> {
            SailingGameTestsShips.clearWind(h);
            double steady = acc[1] == 0 ? 0 : acc[0] / acc[1];
            for (int i = 0; i < SAIL; i++) {
                if (Math.abs(heel[i]) >= 0.9 * Math.abs(steady) && Math.abs(steady) > 0.05) {
                    t90[0] = i + 1;
                    break;
                }
            }
            Result res = new Result(label, rest[0] / rest[2], rest[1] / rest[2], Math.abs(steady), acc[2], t90[0], capsized[0],
                    righted[0], r.ship().isRemoved() ? Double.NaN : Math.abs(rollDegrees(r)),
                    acc[1] == 0 ? 0 : acc[3] / acc[1], r.ship().isRemoved() ? 0 : r.ship().mass(), r.runtime().sailCount(),
                    clear[0], clear[1]);
            Constants.LOG.info("[heel table] {}", res.row());
            check.accept(res);
        });
    }

    /**
     * Every run: the hull floats free of the basin floor, no capsize, never past {@code stability.max_heel_degrees} under
     * sail. PHY1 measured the clearance because SH2 suspected the sloop's stern touched the floor of this 6-deep basin:
     * its lowest block (the stern's keel log) sits 1.73 blocks above the floor at rest (draught 4.27 at 4.2 degrees bow
     * up), and heel only lifts it (least 1.72 in every run, 33-36 degrees with stability off included). A ship that came
     * within a block of the floor would measure the seabed, not the water.
     */
    private static void sane(GameTestHelper h, Result r) {
        h.assertTrue(r.minClearance() > 1.0, r.label() + ": the hull came within a block of the basin floor; " + r.row());
        double maxHeel = SailingConfig.MAX_HEEL_DEGREES.get();
        h.assertTrue(!r.capsized(), r.label() + ": capsized; " + r.row());
        h.assertTrue(r.max() <= maxHeel, r.label() + ": heeled " + r.max() + " deg, past max_heel_degrees " + maxHeel + "; " + r.row());
    }

    /** {@link #sane}, and back below 2 degrees within 5 s of furling. */
    private static void rights(GameTestHelper h, Result r) {
        sane(h, r);
        h.assertTrue(r.rightedAfter() >= 0, r.label() + ": not below 2 deg within 5 s of furling; " + r.row());
    }

    /** {@link #rights}, and a steady heel of at most {@code maxSteady} degrees. Succeeds. */
    private static void target(GameTestHelper h, Result r, double maxSteady) {
        rights(h, r);
        h.assertTrue(r.steady() <= maxSteady, r.label() + ": steady heel " + r.steady() + " deg, target <= " + maxSteady + "; " + r.row());
        h.succeed();
    }

    // ------------------------------------------------------------------ the sweep

    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = TIMEOUT, batch = "pirates_n_ships_config_sailing_heel_sloop_quarter_moderate")
    public static void sloopQuarterModerate(GameTestHelper h) {
        run(h, "sloop quarter 6", SailingGameTestsHeel::sloop, MODERATE, SailTrim.HALF, 0.25, r -> target(h, r, 6.0));
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = TIMEOUT, batch = "pirates_n_ships_config_sailing_heel_sloop_half_moderate")
    public static void sloopHalfModerate(GameTestHelper h) {
        run(h, "sloop half 6", SailingGameTestsHeel::sloop, MODERATE, SailTrim.HALF, 0.5, r -> target(h, r, 6.0));
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = TIMEOUT, batch = "pirates_n_ships_config_sailing_heel_sloop_full_moderate")
    public static void sloopFullModerate(GameTestHelper h) {
        run(h, "sloop full 6", SailingGameTestsHeel::sloop, MODERATE, SailTrim.FULL, -1, r -> target(h, r, 6.0));
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = TIMEOUT, batch = "pirates_n_ships_config_sailing_heel_sloop_quarter_strong")
    public static void sloopQuarterStrong(GameTestHelper h) {
        run(h, "sloop quarter 12", SailingGameTestsHeel::sloop, STRONG, SailTrim.HALF, 0.25, r -> target(h, r, 12.0));
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = TIMEOUT, batch = "pirates_n_ships_config_sailing_heel_sloop_half_strong")
    public static void sloopHalfStrong(GameTestHelper h) {
        run(h, "sloop half 12", SailingGameTestsHeel::sloop, STRONG, SailTrim.HALF, 0.5, r -> target(h, r, 12.0));
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = TIMEOUT, batch = "pirates_n_ships_config_sailing_heel_sloop_full_strong")
    public static void sloopFullStrong(GameTestHelper h) {
        run(h, "sloop full 12", SailingGameTestsHeel::sloop, STRONG, SailTrim.FULL, -1, r -> target(h, r, 12.0));
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = TIMEOUT, batch = "pirates_n_ships_config_sailing_heel_sloop_full_strongest")
    public static void sloopFullStrongest(GameTestHelper h) {
        // SH1 had to relax this to 5 degrees: the template's hold was open to the sea at the stern (y=1, z=25). SH1b
        // closed it, so the 2 degree rule of every other run holds here too.
        run(h, "sloop full 37", SailingGameTestsHeel::sloop, STRONGEST, SailTrim.FULL, -1, r -> {
            rights(h, r);
            h.succeed();
        });
    }

    /**
     * With {@code stability.enabled} off the sloop is as tender as before SH1 (measured 14.2 degrees at full sail in the
     * strong wind, against 4.6 with it on).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = TIMEOUT, batch = "pirates_n_ships_config_sailing_heel_stability_off")
    public static void sloopWithoutStabilityHeelsFar(GameTestHelper h) {
        ConfigOverrides.during(h, SailingConfig.STABILITY_ENABLED, false);
        run(h, "sloop full 12 stability off", SailingGameTestsHeel::sloop, STRONG, SailTrim.FULL, -1, r -> {
            h.assertTrue(r.minClearance() > 1.0, "the hull came within a block of the basin floor: " + r.row());
            h.assertTrue(r.steady() > 8.0, "without the righting torque the sloop should heel far: " + r.row());
            h.succeed();
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = TIMEOUT, batch = "pirates_n_ships_config_sailing_heel_long_full_moderate")
    public static void longHullFullModerate(GameTestHelper h) {
        run(h, "7x17 full 6", SailingGameTestsHeel::longHull, MODERATE, SailTrim.FULL, -1, r -> target(h, r, 6.0));
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = TIMEOUT, batch = "pirates_n_ships_config_sailing_heel_long_full_strong")
    public static void longHullFullStrong(GameTestHelper h) {
        run(h, "7x17 full 12", SailingGameTestsHeel::longHull, STRONG, SailTrim.FULL, -1, r -> target(h, r, 12.0));
    }
}
