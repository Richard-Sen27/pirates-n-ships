package com.richardsenger.piratesnships.hazards.waves;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.hazard.HazardConfig;
import com.richardsenger.piratesnships.sailing.force.ShipFrame;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.sailing.waves.WaveForces;
import com.richardsenger.piratesnships.sailing.waves.WaveTorqueRule;
import com.richardsenger.piratesnships.ship.hull.FloodingConfig;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * GameTests of the waves (WV1, docs/design.md §5.4). The sea state is held with {@link SeaStates#set} (what
 * {@code /pirates waves set} does), which is level-wide, so every test has its own batch, holds the sea with an expiry
 * as a safety net and clears it when it ends. The waves run abeam (toward +X, from 270°) of hulls whose bow is +Z.
 *
 * <p>Roll is measured as the half range (max − min) / 2 of the roll angle over a window, which ignores a constant
 * list.
 */
public final class WaveGameTests {

    /** Waves come from the west and run toward +X: abeam of a +Z bow. */
    private static final double FROM_WEST = 270.0;
    private static final int SETTLE = 60;
    /** Ticks from a change of the sea to the steady state the tests measure. */
    private static final int RAMP = 160;
    /**
     * Measuring window of the roll tests: one minute, one period of the wave groups (WAV2), so the half range covers a
     * set of big waves and a lull whatever phase the test starts at.
     */
    private static final int WINDOW = 1200;
    /**
     * Window of the spill tests: WV1b's beat of its two trains (480 ticks), kept since the threshold follows from the
     * crests within the window.
     */
    private static final int SPILL_WINDOW = 480;

    private WaveGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(WaveGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    /**
     * Holds the sea at {@code state} (at once, no easing) toward +X, with the field's phases pinned to the test
     * ({@link #origin}), for {@code ticks} as a safety net.
     */
    private static void hold(GameTestHelper h, SeaState state, long ticks) {
        ServerLevel level = h.getLevel();
        SeaStates.set(level, state, FROM_WEST + 180.0, level.getGameTime() + ticks, origin(h, 0));
    }

    /**
     * The wave field's origin for test {@code h} (WV1b): phase zero at the game time the test started, minus
     * {@code phaseTicks}, and at the middle of its structure. Without it the phase the waves meet a hull at depends on
     * the game time the test happens to start at and on where the runner placed the structure (the phase k·p of the global
     * {@link WaveField}), and the spill test measured anything from 0.17 to 18 blocks of water. Every call within one
     * test gives the same origin.
     */
    private static WaveField.Origin origin(GameTestHelper h, int phaseTicks) {
        net.minecraft.world.phys.Vec3 mid = h.absoluteVec(new net.minecraft.world.phys.Vec3(11.5, 0.0, 11.5));
        return new WaveField.Origin(h.getLevel().getGameTime() - h.getTick() - phaseTicks, mid.x, mid.z);
    }

    private static void release(GameTestHelper h) {
        SeaStates.clear(h.getLevel());
    }

    /** Signed roll angle [degrees] (+ = port side up). */
    private static double rollDegrees(SailingGameTestsShips.Fixture f) {
        Quaterniond q = f.runtime().bow().shipToWorld(f.ship().orientation(new Quaterniond()), new Quaterniond());
        Vector3d port = q.transform(new Vector3d(ShipFrame.PORT));
        return Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, port.y))));
    }

    /** Signed pitch angle [degrees] (+ = bow up). */
    private static double pitchDegrees(SailingGameTestsShips.Fixture f) {
        Quaterniond q = f.runtime().bow().shipToWorld(f.ship().orientation(new Quaterniond()), new Quaterniond());
        Vector3d fwd = q.transform(new Vector3d(ShipFrame.FORWARD));
        return Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, fwd.y))));
    }

    /** Roll statistics over ticks [from, to): {min, max}. */
    private static double[] watchRoll(GameTestHelper h, SailingGameTestsShips.Fixture f, long from, long to) {
        double[] mm = {Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t >= from && t < to && !f.ship().isRemoved()) {
                double r = rollDegrees(f);
                mm[0] = Math.min(mm[0], r);
                mm[1] = Math.max(mm[1], r);
            }
        });
        return mm;
    }

    private static double half(double[] mm) {
        return Double.isFinite(mm[0]) ? (mm[1] - mm[0]) * 0.5 : Double.NaN;
    }

    /** Logs the roll every 10 ticks between {@code from} and {@code to}. */
    private static void trace(GameTestHelper h, String label, SailingGameTestsShips.Fixture f, long from, long to) {
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t >= from && t < to && t % 10 == 0 && !f.ship().isRemoved()) {
                WaveTorqueRule.Torque q = WaveForces.torque(h.getLevel(), f.ship().id());
                double[] s = WaveForces.slopes(h.getLevel(), f.ship().id());
                Constants.LOG.info("[wave test] {} t={} roll {} deg, slope roll {} pitch {}, torque roll {} pitch {}, sea {}",
                        label, t, String.format("%.2f", rollDegrees(f)), String.format("%.3f", s[0]), String.format("%.3f", s[1]),
                        String.format("%.1f", q.roll()), String.format("%.1f", q.pitch()), SeaStates.current(h.getLevel()).id());
            }
        });
    }

    /** The 7×17 test hull of the damping tests (180 kpg, bow +Z), assembled in an open 40×40 basin. */
    private static SailingGameTestsShips.Fixture longHull(GameTestHelper h) {
        SailingGameTestsShips.basin(h, true);
        return SailingGameTestsShips.assemble(h, SailingGameTestsShips.longHull(h));
    }

    /**
     * A 12-wide, 32-long plank hull, 4 high (x 14..25, z 4..35, floor y=5, deck y=8), bow +Z, helm on deck. The design
     * asks for 40×12; 32 is the longest that floats free in the 40×40 basin (inner 38) with room to drift.
     */
    private static SailingGameTestsShips.Fixture bigHull(GameTestHelper h) {
        SailingGameTestsShips.basin(h, true);
        for (int x = 14; x <= 25; x++) {
            for (int z = 4; z <= 35; z++) {
                for (int y = 5; y <= 8; y++) {
                    boolean shell = y == 5 || y == 8 || x == 14 || x == 25 || z == 4 || z == 35;
                    h.setBlock(new BlockPos(x, y, z), shell ? Blocks.OAK_PLANKS : Blocks.AIR);
                }
            }
        }
        BlockPos helm = new BlockPos(19, 9, 6);
        h.setBlock(helm, com.richardsenger.piratesnships.ship.assembly.AssemblyContent.HELM.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        return SailingGameTestsShips.assemble(h, helm);
    }

    // ------------------------------------------------------------------ roll

    /**
     * A per-tick record of a hull over a window (WAV2): its roll [degrees] and its centre of mass's world height
     * [blocks].
     */
    private static final class Track {
        final List<Double> roll = new ArrayList<>();
        final List<Double> pitch = new ArrayList<>();
        final List<Double> y = new ArrayList<>();

        /** Half range of the roll [degrees], NaN for an empty record. */
        double rollHalf() {
            return halfRange(roll);
        }

        /** Half range of the pitch [degrees]. */
        double pitchHalf() {
            return halfRange(pitch);
        }

        /** Half range of the height [blocks]: the heave amplitude. */
        double heave() {
            return halfRange(y);
        }

        private static double halfRange(List<Double> v) {
            if (v.isEmpty()) {
                return Double.NaN;
            }
            double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
            for (double d : v) {
                lo = Math.min(lo, d);
                hi = Math.max(hi, d);
            }
            return (hi - lo) * 0.5;
        }

        /**
         * The roll's crests: the highest roll above the mean between two up-crossings of the mean (with a hysteresis of
         * {@code band} degrees, so physics jitter makes no crossing).
         */
        List<Double> crests(double band) {
            double mean = 0;
            for (double d : roll) mean += d;
            mean /= Math.max(1, roll.size());
            List<Double> out = new ArrayList<>();
            boolean below = false, started = false;
            double crest = 0;
            for (double d : roll) {
                double r = d - mean;
                if (r < -band) {
                    below = true;
                } else if (r > band && below) {
                    if (started) {
                        out.add(crest);
                    }
                    started = true;
                    below = false;
                    crest = r;
                }
                crest = Math.max(crest, r);
            }
            return out;
        }

        /** Mean of {@code |cᵢ₊₁ − cᵢ| / max(cᵢ, cᵢ₊₁)} over successive crests: 0 for a clean sine. */
        static double meanCrestChange(List<Double> crests) {
            double s = 0;
            int n = 0;
            for (int i = 1; i < crests.size(); i++) {
                double a = crests.get(i - 1), b = crests.get(i);
                if (Math.max(a, b) > 0) {
                    s += Math.abs(b - a) / Math.max(a, b);
                    n++;
                }
            }
            return n == 0 ? Double.NaN : s / n;
        }
    }

    /** Records the hull's roll and height over ticks [from, to). */
    private static Track track(GameTestHelper h, SailingGameTestsShips.Fixture f, long from, long to) {
        Track tr = new Track();
        Vector3d com = new Vector3d();
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t >= from && t < to && !f.ship().isRemoved() && f.ship().centerOfMass(com)) {
                tr.roll.add(rollDegrees(f));
                tr.pitch.add(pitchDegrees(f));
                tr.y.add(f.ship().toWorld(com, new Vector3d()).y);
            }
        });
        return tr;
    }

    /**
     * Logs one row of the measured table (WAV4): roll and pitch half range [degrees], heave half range [blocks] and as a
     * share of the state's amplitude, the roll crests and how much successive crests differ.
     */
    private static void table(String hull, SeaState state, Track t) {
        List<Double> crests = t.crests(0.1);
        Constants.LOG.info("[wave table] {} {}: roll {} deg, pitch {} deg, heave {} blocks ({} x A {}), {} roll crests changing {}",
                hull, state.id(), String.format("%.3f", t.rollHalf()), String.format("%.3f", t.pitchHalf()),
                String.format("%.3f", t.heave()), f2(t.heave() / amplitude(state)), f2(amplitude(state)), crests.size(),
                f2(Track.meanCrestChange(crests)));
    }

    /** The field's amplitude of {@code state} [blocks] (× {@code waves.amplitude}). */
    private static double amplitude(SeaState state) {
        return state.amplitude() * HazardConfig.WAVE_AMPLITUDE.get();
    }

    private static String f2(double d) {
        return String.format("%.2f", d);
    }

    /**
     * The 7×17 hull at storm amplitude rolls 3 to 8 degrees (half range over one minute, one wave-group period, at
     * steady state; the target is about ±4–6°), and once the sea goes calm it settles within 10 s. Measured 4.35
     * degrees with WV1's two trains before SH1's righting torque, 1.85 after; WAV2 measured 4.46 (docs/playtests/waves.md);
     * WAV4 (storm 2.0 on a 42-block peak, {@code ship_torque} 18) 4.73, pitch 0.60, settled to 0.09 after 10 s.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 1800, batch = "pirates_n_ships_waves_storm_roll")
    public static void stormRollsTheTestHullAndCalmSettlesIt(GameTestHelper h) {
        hold(h, SeaState.CALM, 1900);
        SailingGameTestsShips.Fixture f = longHull(h);
        long stormAt = SETTLE, calmAt = SETTLE + RAMP + WINDOW;
        h.runAfterDelay(stormAt, () -> hold(h, SeaState.STORM, 1900));
        h.runAfterDelay(calmAt, () -> hold(h, SeaState.CALM, 1900));
        trace(h, "storm 7x17", f, stormAt, calmAt + 220);
        Track storm = track(h, f, stormAt + RAMP, calmAt);
        double[] settled = watchRoll(h, f, calmAt + 200, calmAt + 220);
        h.runAfterDelay(calmAt + 221, () -> {
            double s = storm.rollHalf(), c = half(settled);
            table("7x17", SeaState.STORM, storm);
            int sprays = WaveForces.sprays(h.getLevel(), f.ship().id());
            Constants.LOG.info("[wave table] 7x17 storm: {} sprays at the bow in {} ticks", sprays, calmAt - stormAt);
            List<Double> crests = storm.crests(0.1);
            Constants.LOG.info("[wave test] 7x17 storm roll {} deg, {} crests changing {} on average, heave {} blocks; 10 s after calm {} deg; "
                            + "mass {}", f2(s), crests.size(), f2(Track.meanCrestChange(crests)), f2(storm.heave()), f2(c),
                    String.format("%.1f", f.ship().mass()));
            release(h);
            h.assertTrue(s >= 3.0 && s <= 8.0, "storm roll of the 7x17 hull outside 3..8 deg: " + s);
            h.assertTrue(c < 0.5, "the 7x17 hull still rolls " + c + " deg 10 s after the sea went calm");
            // WAV4: once per crest that digs in, not every cooldown: 8 in 68 s measured (29 with the cooldown alone)
            h.assertTrue(sprays >= 2 && sprays <= 20, "the bow threw " + sprays + " sprays in a minute of storm");
            SableShips.remove(f.ship());
            h.succeed();
        });
    }

    /**
     * A calm sea leaves the 7×17 hull nearly still: under 0.4 degrees of roll over a minute. Measured 0.25° on
     * 2026-10-09 (WAV2: the calm sea is a long 56-block swell without wave groups); WAV4 (calm 0.15, {@code ship_torque}
     * 18) 0.26°, the target being 0.3 at most.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 1500, batch = "pirates_n_ships_waves_calm_roll")
    public static void calmSeaLeavesTheTestHullStill(GameTestHelper h) {
        hold(h, SeaState.CALM, 1600);
        SailingGameTestsShips.Fixture f = longHull(h);
        long end = SETTLE + RAMP + WINDOW;
        trace(h, "calm 7x17", f, SETTLE, end);
        Track calm = track(h, f, SETTLE + RAMP, end);
        h.runAfterDelay(end + 1, () -> {
            double c = calm.rollHalf();
            table("7x17", SeaState.CALM, calm);
            Constants.LOG.info("[wave test] 7x17 calm roll {} deg, heave {} blocks", String.format("%.3f", c), String.format("%.3f", calm.heave()));
            release(h);
            h.assertTrue(c < 0.4, "a calm sea rolls the 7x17 hull " + c + " deg");
            SableShips.remove(f.ship());
            h.succeed();
        });
    }

    /**
     * A moderate sea rocks the 7×17 hull gently: between a tenth of a degree and 3 degrees over a minute. Measured: WAV2
     * 1.27 (0.3 blocks), WAV4 1.76 (0.6 blocks).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 1500, batch = "pirates_n_ships_waves_moderate_roll")
    public static void moderateSeaRocksTheTestHullGently(GameTestHelper h) {
        hold(h, SeaState.MODERATE, 1600);
        SailingGameTestsShips.Fixture f = longHull(h);
        long end = SETTLE + RAMP + WINDOW;
        Track moderate = track(h, f, SETTLE + RAMP, end);
        h.runAfterDelay(end + 1, () -> {
            double m = moderate.rollHalf();
            table("7x17", SeaState.MODERATE, moderate);
            Constants.LOG.info("[wave test] 7x17 moderate roll {} deg, heave {} blocks", f2(m), f2(moderate.heave()));
            release(h);
            h.assertTrue(m > 0.1 && m < 3.0, "a moderate sea rolls the 7x17 hull " + m + " deg");
            SableShips.remove(f.ship());
            h.succeed();
        });
    }

    /**
     * A big hull (32×12) lies steady in a storm: under 1 degree of roll over a minute, but it moves; 10 s after the sea
     * goes calm it lies still again. Measured: WAV2 0.65, WAV4 0.62 (settled to 0.015).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 1800, batch = "pirates_n_ships_waves_big_hull")
    public static void stormBarelyRollsABigHull(GameTestHelper h) {
        hold(h, SeaState.CALM, 1900);
        SailingGameTestsShips.Fixture f = bigHull(h);
        long end = SETTLE + RAMP + WINDOW;
        h.runAfterDelay(SETTLE, () -> hold(h, SeaState.STORM, 1900));
        h.runAfterDelay(end, () -> hold(h, SeaState.CALM, 1900));
        trace(h, "storm 32x12", f, SETTLE, end);
        Track storm = track(h, f, SETTLE + RAMP, end);
        double[] settled = watchRoll(h, f, end + 200, end + 220);
        h.runAfterDelay(end + 221, () -> {
            double s = storm.rollHalf();
            table("32x12", SeaState.STORM, storm);
            Constants.LOG.info("[wave test] 32x12 storm roll {} deg, heave {} blocks; 10 s after calm {} deg; mass {}", f2(s),
                    f2(storm.heave()), String.format("%.3f", half(settled)), String.format("%.1f", f.ship().mass()));
            release(h);
            h.assertTrue(s < 1.0, "a storm rolls the 32x12 hull " + s + " deg");
            h.assertTrue(s > 0.05, "the storm did not move the 32x12 hull at all: " + s + " deg");
            h.assertTrue(half(settled) < 0.5, "the 32x12 hull still rolls " + half(settled) + " deg 10 s after the sea went calm");
            SableShips.remove(f.ship());
            h.succeed();
        });
    }

    /**
     * In a calm and then a moderate sea the big hull hardly moves: under 0.2 and under 0.5 degrees. Measured: WAV2 0.04
     * and 0.19, WAV4 0.04 and 0.27.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 2900, batch = "pirates_n_ships_waves_big_hull_calm")
    public static void calmAndModerateSeasBarelyMoveABigHull(GameTestHelper h) {
        hold(h, SeaState.CALM, 3000);
        SailingGameTestsShips.Fixture f = bigHull(h);
        long moderateAt = SETTLE + RAMP + WINDOW, end = moderateAt + RAMP + WINDOW;
        h.runAfterDelay(moderateAt, () -> hold(h, SeaState.MODERATE, 3000));
        Track calm = track(h, f, SETTLE + RAMP, moderateAt);
        Track moderate = track(h, f, moderateAt + RAMP, end);
        h.runAfterDelay(end + 1, () -> {
            double c = calm.rollHalf(), m = moderate.rollHalf();
            table("32x12", SeaState.CALM, calm);
            table("32x12", SeaState.MODERATE, moderate);
            Constants.LOG.info("[wave test] 32x12 calm roll {} deg, heave {}; moderate roll {} deg, heave {}", String.format("%.3f", c),
                    String.format("%.3f", calm.heave()), String.format("%.3f", m), String.format("%.3f", moderate.heave()));
            release(h);
            h.assertTrue(c < 0.2, "a calm sea rolls the 32x12 hull " + c + " deg");
            h.assertTrue(m < 0.5, "a moderate sea rolls the 32x12 hull " + m + " deg");
            SableShips.remove(f.ship());
            h.succeed();
        });
    }

    /**
     * WAV2: the storm roll has no single clean rhythm. Over one minute of storm (pinned half a group period later than
     * the other storm tests), successive roll crests of the 7×17 hull differ by 15 % or more on average. Measured: WAV2
     * 0.42, WAV4 0.52.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 1500, batch = "pirates_n_ships_waves_irregular")
    public static void stormRollHasAnIrregularRhythm(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        SeaStates.set(level, SeaState.STORM, FROM_WEST + 180.0, level.getGameTime() + 1600, origin(h, 600));
        SailingGameTestsShips.Fixture f = longHull(h);
        long end = SETTLE + RAMP + WINDOW;
        Track storm = track(h, f, SETTLE + RAMP, end);
        h.runAfterDelay(end + 1, () -> {
            List<Double> crests = storm.crests(0.1);
            double change = Track.meanCrestChange(crests);
            table("7x17 irregular", SeaState.STORM, storm);
            StringBuilder sb = new StringBuilder();
            for (double c : crests) sb.append(' ').append(f2(c));
            Constants.LOG.info("[wave test] 7x17 storm roll crests [deg]:{}; mean change {}, roll {} deg", sb, f2(change), f2(storm.rollHalf()));
            release(h);
            h.assertTrue(crests.size() >= 4, "only " + crests.size() + " roll crests in a minute of storm");
            h.assertTrue(change >= 0.15, "successive roll crests differ by only " + change + " on average: a single clean sine");
            SableShips.remove(f.ship());
            h.succeed();
        });
    }

    /**
     * WAV2: the heave. In a storm the 7×17 hull's centre of mass rises and falls with the waves by 0.5 to 0.9 times the
     * wave amplitude (half range over one minute; WAV2 asked 0.3 to 1.0 and measured 0.40 with {@code heave_strength}
     * 0.5; WAV4's target is 0.6–0.7, measured 0.67 with 0.95), and its keel stays under the surface it rides (WAV4:
     * at least 0.85 blocks deep, 1.68 at rest).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 1500, batch = "pirates_n_ships_waves_heave")
    public static void stormLiftsTheHullOnTheCrests(GameTestHelper h) {
        hold(h, SeaState.STORM, 1600);
        SailingGameTestsShips.Fixture f = longHull(h);
        long end = SETTLE + RAMP + WINDOW;
        Track storm = track(h, f, SETTLE + RAMP, end);
        // WAV4: the hull must stay in the water it rides: the depth of the middle of its keel under the drawn surface
        // there (the still sea + the mean wave height under the hull), its least over the window and its value at rest
        double[] draft = {Double.POSITIVE_INFINITY, Double.NaN};
        h.onEachTick(() -> {
            long t = h.getTick();
            var hull = com.richardsenger.piratesnships.ship.hull.runtime.HullRuntimes.get(h.getLevel(), f.ship().id());
            if (hull != null && Double.isFinite(hull.seaWorldY()) && !f.ship().isRemoved() && (t == SETTLE || t >= SETTLE + RAMP && t < end)) {
                int[] b = f.runtime().bounds();
                Vector3d keel = f.ship().toWorld(WaveTorqueRule.center(b, b[1]), new Vector3d());
                double d = hull.seaWorldY() + WaveForces.meanHeight(h.getLevel(), f.ship().id()) - keel.y;
                if (t == SETTLE) draft[1] = d;
                else draft[0] = Math.min(draft[0], d);
            }
            if (t >= SETTLE + RAMP && t < end && t % 20 == 0 && !f.ship().isRemoved()) {
                Constants.LOG.info("[wave test] heave 7x17 t={} mean wave {} force {} y {}", t,
                        f2(WaveForces.meanHeight(h.getLevel(), f.ship().id())),
                        String.format("%.0f", WaveForces.heave(h.getLevel(), f.ship().id())),
                        storm.y.isEmpty() ? "-" : String.format("%.3f", storm.y.get(storm.y.size() - 1)));
            }
        });
        h.runAfterDelay(end + 1, () -> {
            double ratio = storm.heave() / amplitude(SeaState.STORM);
            table("7x17 heave", SeaState.STORM, storm);
            Constants.LOG.info("[wave test] 7x17 storm heave {} blocks = {} x the amplitude; roll {} deg; keel under the drawn surface "
                    + "at least {} blocks (at tick {}: {})", f2(storm.heave()), f2(ratio), f2(storm.rollHalf()), f2(draft[0]), SETTLE, f2(draft[1]));
            release(h);
            h.assertTrue(ratio >= 0.5 && ratio <= 0.9, "storm heave of the 7x17 hull " + ratio + " x the wave amplitude, outside 0.5..0.9");
            h.assertTrue(draft[0] > 0.0, "the 7x17 hull's keel left the water it rides: " + draft[0] + " blocks under the surface");
            SableShips.remove(f.ship());
            h.succeed();
        });
    }

    /**
     * WAV2: Sable's buoyancy alone gives no heave. With {@code waves.heave} off, the storm only rolls and pitches the
     * hull; its centre of mass stays within a tenth of the wave amplitude of its height, since the world's water is flat
     * (docs/sable-notes.md §4.1).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 1500, batch = "pirates_n_ships_config_waves_no_heave")
    public static void withoutHeaveTheStormLeavesTheHullsHeight(GameTestHelper h) {
        ConfigOverrides.during(h, HazardConfig.HEAVE, false);
        hold(h, SeaState.STORM, 1600);
        SailingGameTestsShips.Fixture f = longHull(h);
        long end = SETTLE + RAMP + WINDOW;
        Track storm = track(h, f, SETTLE + RAMP, end);
        boolean[] lifted = {false};
        h.onEachTick(() -> {
            if (!f.ship().isRemoved() && WaveForces.heave(h.getLevel(), f.ship().id()) != 0.0) {
                lifted[0] = true;
            }
        });
        h.runAfterDelay(end + 1, () -> {
            double ratio = storm.heave() / amplitude(SeaState.STORM);
            Constants.LOG.info("[wave test] 7x17 storm without heave: y half range {} blocks = {} x the amplitude; roll {} deg",
                    String.format("%.3f", storm.heave()), String.format("%.3f", ratio), f2(storm.rollHalf()));
            release(h);
            h.assertFalse(lifted[0], "a heave force acted with waves.heave off");
            h.assertTrue(storm.rollHalf() > 0.5, "the storm did not roll the hull: " + storm.rollHalf());
            h.assertTrue(ratio < 0.1, "without heave the hull still rose and fell by " + ratio + " x the wave amplitude");
            SableShips.remove(f.ship());
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ spilling

    /**
     * The 5×4×5 hull of the dry hull tests with an open trapdoor low in its south wall, in the middle (plot cell y=7,
     * deck y=8). The waves run toward +X, so the hull rolls about the Z axis and the hatch sits on the roll axis: its
     * height follows the hull's heave and the small pitch of the crossing train, not the roll. (WV1 put the hatch in the
     * west wall; the hull rolls it up and down by more than a block there, phase-locked to the crests, so whether a crest
     * found the sill up or down, and how much water came in, depended on the wave phase at the start: WV1b.)
     */
    private static DryHullGameTests.Fixture hatchHull(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        BlockPos helm = DryHullGameTests.hull(h, 9, false);
        h.setBlock(new BlockPos(11, 7, 13), Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, Direction.SOUTH));
        DryHullGameTests.Fixture f = DryHullGameTests.assemble(h, helm);
        SailingRuntimes.getOrCreate(f.ship());
        return f;
    }

    /** Height of the hatch's sill above the still sea, in the hull's frame [blocks], or NaN. */
    private static double freeboard(DryHullGameTests.Fixture f) {
        var comps = f.runtime().simulation().analysis().compartments();
        double sill = Double.POSITIVE_INFINITY;
        for (var c : comps) {
            for (var p : c.ports()) {
                if (!p.isPourPoint()) sill = Math.min(sill, p.sill());
            }
        }
        double sea = f.runtime().seaShipFrame();
        return Double.isFinite(sill) && Math.abs(sill - sea) < 100.0 ? sill - sea : Double.NaN;
    }

    /** The hatch opens once the hull floats: during the first ticks after assembly it still sits a block deeper. */
    private static final int OPEN_AT = 60;
    /** Water the hold takes: 3×3 cells, 2 high, under the deck (the hatch's cell is the upper layer). */
    private static final double HOLD_VOLUME = 18.0;
    /**
     * Phase of the waves at the hull when the spill tests start [ticks of the field's time]; 0 in the suite. Any value
     * must pass: WV1b ran the suite with the hatch opening in a trough too.
     */
    private static final int SPILL_PHASE = 0;

    /**
     * Opens the hatch at {@link #OPEN_AT} and measures the water that comes in over one beat of the wave trains
     * ({@link #SPILL_WINDOW}), with the field pinned to the test ({@link #origin}).
     *
     * <p><b>Threshold.</b> With the hull held still, the orifice law of the flooding simulation would let in
     * {@code E = Σₜ c · min(1, eₜ) · √eₜ}, {@code eₜ = max(0, h(t) − sₜ)}, over the window, with {@code h(t)} the crest at
     * the hull's middle (the spill feed is the highest of five crests, so it is at least this), {@code sₜ} the sill's
     * height above the still sea at that tick (WAV2: the trains' directional spread pitches this 5-long boat, which
     * moves the hatch in the stern wall, so the sill at the opening no longer stands for the whole window), and {@code c = BASE_FLOW · inflow_rate} (area 1). The hull rides
     * the swell, so the test asks for half: a storm must put at least {@code min(E, HOLD_VOLUME) / 2} through the hatch,
     * and {@code E} must be at least 1 block (the window holds a crest over the sill; with a storm's 1.2 and a sill near
     * 0.3 it is about 15; WAV4's 2.0 gives over 200). A calm sea (crest 0.1, under the sill) must put in nothing.
     */
    private static void spill(GameTestHelper h, SeaState state, boolean floods) {
        double inflowRate = 10.0;
        ConfigOverrides.during(h, FloodingConfig.INFLOW_RATE, inflowRate);
        // the threshold below is a still-hull estimate, so the spill tests keep the hull as still as WV1 did: no heave
        // (WAV2, covered by the heave tests) and WV1's gentle wave torque on this 5x4x5 boat (WAV2's size exponent and
        // stronger torque roll and pitch a boat this small enough to put the hatch under the still sea)
        ConfigOverrides.during(h, HazardConfig.HEAVE, false);
        ConfigOverrides.during(h, HazardConfig.SHIP_TORQUE, 5.5);
        ConfigOverrides.during(h, HazardConfig.SIZE_EXPONENT, 0.5);
        ConfigOverrides.during(h, HazardConfig.MAX_TORQUE_PER_MASS, 2.0);
        ServerLevel level = h.getLevel();
        SeaStates.set(level, state, FROM_WEST + 180.0, level.getGameTime() + OPEN_AT + SPILL_WINDOW + 100, origin(h, SPILL_PHASE));
        DryHullGameTests.Fixture f = hatchHull(h);
        BlockPos hatch = f.hold(0, -2, 2);
        BlockPos mid = f.hold(0, -2, 0);
        double c = com.richardsenger.piratesnships.ship.hull.flooding.FloodParams.BASE_FLOW * inflowRate;
        h.runAfterDelay(OPEN_AT, () -> {
            h.assertTrue(level.getBlockState(hatch).is(Blocks.OAK_TRAPDOOR), "no hatch in the plot at " + hatch);
            level.setBlock(hatch, level.getBlockState(hatch).setValue(TrapDoorBlock.OPEN, true), net.minecraft.world.level.block.Block.UPDATE_ALL);
        });
        double[] before = {Double.NaN};
        double[] sill = {Double.NaN};
        // WAV4: the sill's mean height over the two seconds before the hatch opens, {sum, count}: the 2-block storm
        // pitches this 5-long boat by more than WV1's (at the opening tick the sill stood 0.04 below the still sea), and
        // once the hold takes water the boat settles deeper, so neither the opening tick nor the window stands for it
        double[] sillMean = {0, 0};
        double[] expected = {0};
        double[] maxFeed = {0};
        h.onEachTick(() -> {
            long t = h.getTick();
            if (!f.ship().isRemoved() && t >= OPEN_AT - 40 && t < OPEN_AT && Double.isFinite(f.runtime().seaWorldY())) {
                sillMean[0] += f.ship().toWorld(new net.minecraft.world.phys.Vec3(hatch.getX() + 0.5, hatch.getY(), hatch.getZ() + 0.5)).y
                        - f.runtime().seaWorldY();
                sillMean[1]++;
            }
        });
        h.onEachTick(() -> {
            long t = h.getTick();
            if (f.ship().isRemoved() || t < OPEN_AT || t >= OPEN_AT + SPILL_WINDOW) {
                return;
            }
            if (Double.isNaN(before[0])) {
                before[0] = f.runtime().simulation().totalVolume();
            }
            net.minecraft.world.phys.Vec3 centre = f.ship().toWorld(net.minecraft.world.phys.Vec3.atCenterOf(mid));
            double sillNow = Double.NaN;
            if (Double.isFinite(f.runtime().seaWorldY())) {
                net.minecraft.world.phys.Vec3 sillWorld = f.ship().toWorld(
                        new net.minecraft.world.phys.Vec3(hatch.getX() + 0.5, hatch.getY(), hatch.getZ() + 0.5));
                sillNow = sillWorld.y - f.runtime().seaWorldY();
                if (Double.isNaN(sill[0])) {
                    sill[0] = sillNow;
                }
            }
            double crest = SeaStates.field(level).heightAt(centre.x, centre.z, level.getGameTime());
            double feed = WaveForces.spillHeight(level, f.ship().id());
            maxFeed[0] = Math.max(maxFeed[0], feed);
            if (Double.isFinite(sillNow)) {
                double e = Math.max(0.0, crest - sillNow);
                expected[0] += c * Math.min(1.0, e) * Math.sqrt(e);
            }
            if ((t - OPEN_AT) % 20 == 0) {
                Constants.LOG.info("[wave test] spill {} t={} crest at the middle {} feed {} hatch sill above sea {} water {}", state.id(), t,
                        String.format("%.2f", crest), String.format("%.2f", feed), String.format("%.2f", freeboard(f)),
                        String.format("%.2f", f.runtime().simulation().totalVolume() - before[0]));
            }
        });
        h.runAfterDelay(OPEN_AT + SPILL_WINDOW, () -> {
            double water = f.runtime().simulation().totalVolume() - before[0];
            double threshold = Math.min(expected[0], HOLD_VOLUME) / 2.0;
            double meanSill = sillMean[1] > 0 ? sillMean[0] / sillMean[1] : Double.NaN;
            Constants.LOG.info("[wave test] spill {}: water {} in {} ticks with the hatch open, estimate {}, threshold {}, "
                            + "highest crest feed {}, sill above sea at opening {}, before it {}", state.id(), String.format("%.2f", water),
                    SPILL_WINDOW, String.format("%.2f", expected[0]), String.format("%.2f", threshold), String.format("%.2f", maxFeed[0]),
                    String.format("%.2f", sill[0]), String.format("%.2f", meanSill));
            release(h);
            var grid = f.runtime().simulation().analysis().grid();
            h.assertTrue(f.runtime().simulation().isOpen(grid.index(hatch.getX() - grid.originX(), hatch.getY() - grid.originY(),
                    hatch.getZ() - grid.originZ())), "the open hatch did not reach the simulation");
            h.assertTrue(Double.isFinite(sill[0]), "never found the sea at the hull");
            if (floods) {
                h.assertTrue(meanSill > 0.0 && meanSill < SeaState.STORM.amplitude(),
                        "the hatch's sill is not between the still sea and a storm crest before opening: " + meanSill);
                h.assertTrue(expected[0] >= 1.0, "no storm crest rose over the sill in the window: estimate " + expected[0]);
                h.assertTrue(water >= threshold, "a storm put only " + water + " blocks of water through the low hatch (threshold "
                        + threshold + " from a still-hull estimate of " + expected[0] + ")");
            } else {
                h.assertTrue(meanSill > maxFeed[0], "the hatch's sill " + meanSill + " is under the calm crests " + maxFeed[0]);
                h.assertTrue(water < 0.01, "a calm sea put " + water + " blocks of water through the low hatch");
            }
            SableShips.remove(f.ship());
            h.succeed();
        });
    }

    /** In a storm the crests spill water through an open hatch about 0.3 blocks above the still sea. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 640, batch = "pirates_n_ships_config_waves_spill_storm")
    public static void stormSpillsThroughALowHatch(GameTestHelper h) {
        spill(h, SeaState.STORM, true);
    }

    /**
     * Crests that stay under the sill put nothing in: the same hatch stays dry in a calm sea of WV1's height (0.1 blocks).
     * WAV4 raised the calm sea to 0.15, which stands at this hatch's sill (0.16 above the still sea, measured; one run
     * then took 1.0 block in 24 s, the next none), so the test scales the calm sea back to 0.1 with {@code waves.amplitude};
     * the starter sloop's open hatch in a default moderate sea ({@link #moderateSeaKeepsTheSloopsOpenHatchDry}) covers
     * the gameplay case.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 640, batch = "pirates_n_ships_config_waves_spill_calm")
    public static void calmSeaKeepsALowHatchDry(GameTestHelper h) {
        ConfigOverrides.during(h, HazardConfig.WAVE_AMPLITUDE, 0.1 / SeaState.CALM.amplitude());
        spill(h, SeaState.CALM, false);
    }

    // ------------------------------------------------------------------ the starter sloop's open hatch (WAV4)

    /** Ticks the sloop's hatch stands open in {@link #sloopHatch}: one minute. */
    private static final int SLOOP_WINDOW = 1200;
    /** The sloop's hatch opens once it floats. */
    private static final int SLOOP_OPEN_AT = 80;
    /**
     * Phase of the sloop tests' field [ticks]: the window's middle (tick 680) falls on the strongest set of the wave
     * groups, where both envelope terms peak together (field time 16,216: 1.35 × the amplitude; the default phase's
     * window reaches only 1.27), so the moderate test meets the highest crests a moderate sea brings.
     */
    private static final int SLOOP_PHASE = 16216 - 680;

    /**
     * A 48×48 stone basin, water y 2..7 (floor y 1), the barrier ceiling of the 48×16×48 template removed, with the
     * starter sloop placed and assembled from its template, bow north, in the middle.
     */
    private static java.util.UUID sloop(GameTestHelper h) {
        for (int x = 0; x < 48; x++) {
            for (int z = 0; z < 48; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                boolean wall = x == 0 || x == 47 || z == 0 || z == 47;
                for (int y = 2; y <= 8; y++) {
                    h.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : y <= 7 ? Blocks.WATER : Blocks.AIR);
                }
            }
        }
        SailingGameTestsShips.openSky(h, 48);
        BlockPos feet = h.absolutePos(new BlockPos(24, 8, 38));
        var r = com.richardsenger.piratesnships.ship.template.ShipTemplatePlacer.place(h.getLevel(),
                com.richardsenger.piratesnships.ship.template.ShipTemplates.STARTER_SLOOP_ID, feet, Direction.NORTH, true, true, null);
        var a = r.assembly();
        if (a == null || a.shipId() == null) {
            throw new AssertionError("sloop not placed and assembled: " + r.outcome() + " " + a);
        }
        com.richardsenger.piratesnships.ship.ShipTestCleanup.track(h, a.shipId());
        return a.shipId();
    }

    /**
     * The starter sloop at rest, its deck hatch (the trapdoor over the ladder into the hold) opened once it floats, in a
     * sea held at {@code state} with the default wave forces (heave on): logs the water that comes in over one minute
     * and hands it [blocks] to {@code check} (NaN when the hull was not found).
     */
    private static void sloopHatch(GameTestHelper h, SeaState state, java.util.function.BiConsumer<Double, Double> check) {
        ServerLevel level = h.getLevel();
        SeaStates.set(level, state, FROM_WEST + 180.0, level.getGameTime() + SLOOP_OPEN_AT + SLOOP_WINDOW + 100, origin(h, SLOOP_PHASE));
        java.util.UUID id = sloop(h);
        double[] before = {Double.NaN};
        int[] opened = {0};
        double[] maxFeed = {0};
        List<BlockPos> hatches = new ArrayList<>();
        // the hatch's sill above the still sea: {least, sum, count}, and the highest crest over it (feed − sill)
        double[] sill = {Double.POSITIVE_INFINITY, 0, 0};
        double[] over = {Double.NEGATIVE_INFINITY};
        h.runAfterDelay(SLOOP_OPEN_AT, () -> {
            var hull = com.richardsenger.piratesnships.ship.hull.runtime.HullRuntimes.get(level, id);
            h.assertTrue(hull != null, "the sloop has no hull runtime");
            var grid = hull.simulation().analysis().grid();
            for (int i = 0, n = grid.cellCount(); i < n; i++) {
                if (grid.kind(i) != com.richardsenger.piratesnships.ship.hull.CellKind.OPENING) continue;
                BlockPos p = new BlockPos(grid.originX() + grid.x(i), grid.originY() + grid.y(i), grid.originZ() + grid.z(i));
                BlockState s = level.getBlockState(p);
                if (s.getBlock() instanceof TrapDoorBlock) {
                    level.setBlock(p, s.setValue(TrapDoorBlock.OPEN, true), net.minecraft.world.level.block.Block.UPDATE_ALL);
                    opened[0]++;
                    hatches.add(p);
                }
            }
            before[0] = hull.simulation().totalVolume();
        });
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t > SLOOP_OPEN_AT && t < SLOOP_OPEN_AT + SLOOP_WINDOW) {
                double feed = WaveForces.spillHeight(level, id);
                maxFeed[0] = Math.max(maxFeed[0], feed);
                var hull = com.richardsenger.piratesnships.ship.hull.runtime.HullRuntimes.get(level, id);
                ShipBody ship = SableShips.byId(level, id);
                if (hull != null && ship != null && !hatches.isEmpty() && Double.isFinite(hull.seaWorldY())) {
                    BlockPos p = hatches.get(0);
                    double y = ship.toWorld(new net.minecraft.world.phys.Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5)).y - hull.seaWorldY();
                    sill[0] = Math.min(sill[0], y);
                    sill[1] += y;
                    sill[2]++;
                    over[0] = Math.max(over[0], feed - y);
                }
            }
        });
        h.runAfterDelay(SLOOP_OPEN_AT + SLOOP_WINDOW, () -> {
            var hull = com.richardsenger.piratesnships.ship.hull.runtime.HullRuntimes.get(level, id);
            double water = hull == null ? Double.NaN : hull.simulation().totalVolume() - before[0];
            Constants.LOG.info("[wave table] sloop {} sea: {} blocks of water through {} open hatches in {} ticks, highest crest feed {}, "
                    + "hatch sill above the still sea {} on average, {} at the least, highest crest over the sill {}; sprays {}", state.id(),
                    String.format("%.3f", water), opened[0], SLOOP_WINDOW, f2(maxFeed[0]), f2(sill[2] > 0 ? sill[1] / sill[2] : Double.NaN),
                    f2(sill[0]), f2(over[0]), WaveForces.sprays(level, id));
            release(h);
            h.assertTrue(opened[0] >= 1, "the sloop has no trapdoor hatch to open");
            check.accept(water, maxFeed[0]);
            ShipBody ship = SableShips.byId(level, id);
            if (ship != null) SableShips.remove(ship);
            h.succeed();
        });
    }

    /**
     * WAV4: a moderate sea (0.6 blocks, crests up to 0.81 in a set) puts no water through the starter sloop's open deck
     * hatch in a minute at rest, in the strongest set of the wave groups ({@link #SLOOP_PHASE}): the sloop rides the
     * swell and its deck stands above the crests. Measured: no water; crests at the hull up to 0.57, the hatch cell's floor
     * 0.47 to 0.83 above the still sea (a crest never rose over it; the water would have to reach the deck a block higher).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = SLOOP_OPEN_AT + SLOOP_WINDOW + 60,
            batch = "pirates_n_ships_waves_sloop_moderate")
    public static void moderateSeaKeepsTheSloopsOpenHatchDry(GameTestHelper h) {
        sloopHatch(h, SeaState.MODERATE, (water, feed) -> h.assertTrue(water < 0.01,
                "a moderate sea put " + water + " blocks of water through the sloop's open hatch"));
    }

    /**
     * WAV4: the same sloop in a held storm, measured for the table (the water logged): the hull meets crests of at least
     * half the storm's height. Measured: no water in a minute; the hatch cell's floor stands 0.5 blocks above the still
     * sea on average, the crests at the hull reach 1.2 (the hull rides up 0.67 of them), at most 0.6 over that floor, and
     * water must rise a block higher, to the deck, to run down the hatch. So an open deck hatch is safe at rest; a low
     * side hatch is not ({@link #stormSpillsThroughALowHatch}).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = SLOOP_OPEN_AT + SLOOP_WINDOW + 60,
            batch = "pirates_n_ships_waves_sloop_storm")
    public static void stormAtTheSloopsOpenHatchIsMeasured(GameTestHelper h) {
        sloopHatch(h, SeaState.STORM, (water, feed) -> h.assertTrue(Double.isFinite(water) && feed >= 0.5 * amplitude(SeaState.STORM),
                "the sloop met no storm crests: water " + water + ", highest crest " + feed));
    }

    /** WAV4: the same sloop in a held rough sea, measured for the table (no water in a minute, crests 1.1 at the hull). */
    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = SLOOP_OPEN_AT + SLOOP_WINDOW + 60,
            batch = "pirates_n_ships_waves_sloop_rough")
    public static void roughSeaAtTheSloopsOpenHatchIsMeasured(GameTestHelper h) {
        sloopHatch(h, SeaState.ROUGH, (water, feed) -> h.assertTrue(Double.isFinite(water) && feed >= 0.5 * amplitude(SeaState.ROUGH),
                "the sloop met no rough crests: water " + water + ", highest crest " + feed));
    }

    // ------------------------------------------------------------------ sync

    /**
     * The sync payload carries the held storm to a player: encoded and decoded with its stream codec, it gives the
     * client holder the storm's state and wave height. (A mock player has no connection; {@link WaveSync#syncTo} takes
     * the path of the per-player sync and records it.)
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 40, batch = "pirates_n_ships_waves_sync")
    public static void syncPayloadReachesAPlayer(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        hold(h, SeaState.STORM, 100);
        h.runAfterDelay(2, () -> {
            UUID player = UUID.randomUUID();
            List<WaveSyncPayload> sent = WaveSync.record(player);
            WaveSyncPayload[] received = new WaveSyncPayload[1];
            WaveSync.syncTo(level, player, p -> {
                RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
                WaveSyncPayload.CODEC.encode(buf, p);
                received[0] = WaveSyncPayload.CODEC.decode(buf);
                buf.release();
            });
            WaveSync.stopRecording(player);
            release(h);
            h.assertTrue(sent.size() == 1, "expected one recorded payload, got " + sent);
            h.assertValueEqual(received[0], sent.get(0), "decoded payload");
            h.assertValueEqual(received[0].seaState(), SeaState.STORM, "state");
            double expected = SeaState.STORM.amplitude() * HazardConfig.WAVE_AMPLITUDE.get();
            h.assertTrue(Math.abs(received[0].amplitude() - expected) < 1.0e-4, "amplitude " + received[0].amplitude() + " != " + expected);
            ClientWaves.accept(received[0], level.getGameTime());
            h.assertTrue(ClientWaves.hasData() && ClientWaves.state() == SeaState.STORM
                    && Math.abs(ClientWaves.field(level.getGameTime()).amplitude() - expected) < 1.0e-4, "client holder did not take the storm");
            ClientWaves.reset();
            h.succeed();
        });
    }

    /** The waves force group is registered and a ship in a storm gets a torque from it (and none on land). */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = "pirates_n_ships_waves_dry_land")
    public static void shipOnLandGetsNoWaves(GameTestHelper h) {
        hold(h, SeaState.STORM, 300);
        SailingGameTestsShips.basin(h, false);
        SailingGameTestsShips.Fixture f = SailingGameTestsShips.assemble(h, SailingGameTestsShips.longHull(h));
        ShipBody ship = f.ship();
        boolean[] seen = {false};
        h.onEachTick(() -> {
            if (!ship.isRemoved() && WaveForces.torque(h.getLevel(), ship.id()).magnitude() > 0.0) {
                seen[0] = true;
            }
        });
        h.runAfterDelay(120, () -> {
            release(h);
            h.assertFalse(seen[0], "a ship on land got wave torque");
            SableShips.remove(ship);
            h.succeed();
        });
    }

    /**
     * GR6: on the GameTest server a level nobody holds a sea for is calm and flat, whatever the game time (the clear
     * weather's calm / moderate noise does not apply); a hold wins, and clearing it returns the level to calm and flat at
     * once.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 60, batch = "pirates_n_ships_waves_plain_level")
    public static void aPlainTestLevelHasACalmFlatSea(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        h.assertTrue(SeaStates.stillByDefault(level), "the GameTest server is not recognised: " + level.getServer().getClass().getName());
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t >= 1 && t < 10 || t >= 22) {
                h.assertTrue(SeaStates.override(level) == null, "something holds the sea: " + SeaStates.override(level));
                h.assertTrue(SeaStates.current(level) == SeaState.CALM && SeaStates.target(level) == SeaState.CALM,
                        "a plain test level's sea is " + SeaStates.current(level).id() + " heading for " + SeaStates.target(level).id()
                                + " at game time " + level.getGameTime() + " (clear noise "
                                + String.format("%.2f", SeaStateModel.clearNoise(level.getSeed(), level.getGameTime())) + ")");
                h.assertTrue(SeaStates.field(level).isFlat(), "a plain test level's sea is not flat");
            }
        });
        h.runAfterDelay(10, () -> hold(h, SeaState.MODERATE, 100));
        h.runAfterDelay(15, () -> {
            h.assertTrue(SeaStates.current(level) == SeaState.MODERATE, "the hold did not win: " + SeaStates.current(level).id());
            h.assertFalse(SeaStates.field(level).isFlat(), "a held moderate sea is flat");
        });
        h.runAfterDelay(20, () -> release(h));
        h.runAfterDelay(40, h::succeed);
    }
}
