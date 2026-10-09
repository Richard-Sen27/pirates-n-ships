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
     * the game time the test happens to start at and on where the runner placed the structure (the anchor term of
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
        final List<Double> y = new ArrayList<>();

        /** Half range of the roll [degrees], NaN for an empty record. */
        double rollHalf() {
            return halfRange(roll);
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
                tr.y.add(f.ship().toWorld(com, new Vector3d()).y);
            }
        });
        return tr;
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
     * degrees with WV1's two trains before SH1's righting torque, 1.85 after; WAV2 measured 4.46 (docs/playtests/waves.md).
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
            List<Double> crests = storm.crests(0.1);
            Constants.LOG.info("[wave test] 7x17 storm roll {} deg, {} crests changing {} on average, heave {} blocks; 10 s after calm {} deg; "
                            + "mass {}", f2(s), crests.size(), f2(Track.meanCrestChange(crests)), f2(storm.heave()), f2(c),
                    String.format("%.1f", f.ship().mass()));
            release(h);
            h.assertTrue(s >= 3.0 && s <= 8.0, "storm roll of the 7x17 hull outside 3..8 deg: " + s);
            h.assertTrue(c < 0.5, "the 7x17 hull still rolls " + c + " deg 10 s after the sea went calm");
            SableShips.remove(f.ship());
            h.succeed();
        });
    }

    /** A calm sea leaves the 7×17 hull nearly still: under half a degree of roll over a minute. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 1500, batch = "pirates_n_ships_waves_calm_roll")
    public static void calmSeaLeavesTheTestHullStill(GameTestHelper h) {
        hold(h, SeaState.CALM, 1600);
        SailingGameTestsShips.Fixture f = longHull(h);
        long end = SETTLE + RAMP + WINDOW;
        trace(h, "calm 7x17", f, SETTLE, end);
        Track calm = track(h, f, SETTLE + RAMP, end);
        h.runAfterDelay(end + 1, () -> {
            double c = calm.rollHalf();
            Constants.LOG.info("[wave test] 7x17 calm roll {} deg, heave {} blocks", String.format("%.3f", c), String.format("%.3f", calm.heave()));
            release(h);
            h.assertTrue(c < 0.5, "a calm sea rolls the 7x17 hull " + c + " deg");
            SableShips.remove(f.ship());
            h.succeed();
        });
    }

    /** A moderate sea rocks the 7×17 hull gently: between a tenth of a degree and 3 degrees over a minute. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 1500, batch = "pirates_n_ships_waves_moderate_roll")
    public static void moderateSeaRocksTheTestHullGently(GameTestHelper h) {
        hold(h, SeaState.MODERATE, 1600);
        SailingGameTestsShips.Fixture f = longHull(h);
        long end = SETTLE + RAMP + WINDOW;
        Track moderate = track(h, f, SETTLE + RAMP, end);
        h.runAfterDelay(end + 1, () -> {
            double m = moderate.rollHalf();
            Constants.LOG.info("[wave test] 7x17 moderate roll {} deg, heave {} blocks", f2(m), f2(moderate.heave()));
            release(h);
            h.assertTrue(m > 0.1 && m < 3.0, "a moderate sea rolls the 7x17 hull " + m + " deg");
            SableShips.remove(f.ship());
            h.succeed();
        });
    }

    /** A big hull (32×12) lies steady in a storm: under 1 degree of roll over a minute, but it moves. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 1500, batch = "pirates_n_ships_waves_big_hull")
    public static void stormBarelyRollsABigHull(GameTestHelper h) {
        hold(h, SeaState.CALM, 1600);
        SailingGameTestsShips.Fixture f = bigHull(h);
        long end = SETTLE + RAMP + WINDOW;
        h.runAfterDelay(SETTLE, () -> hold(h, SeaState.STORM, 1600));
        trace(h, "storm 32x12", f, SETTLE, end);
        Track storm = track(h, f, SETTLE + RAMP, end);
        h.runAfterDelay(end + 1, () -> {
            double s = storm.rollHalf();
            Constants.LOG.info("[wave test] 32x12 storm roll {} deg, heave {} blocks; mass {}", f2(s), f2(storm.heave()),
                    String.format("%.1f", f.ship().mass()));
            release(h);
            h.assertTrue(s < 1.0, "a storm rolls the 32x12 hull " + s + " deg");
            h.assertTrue(s > 0.05, "the storm did not move the 32x12 hull at all: " + s + " deg");
            SableShips.remove(f.ship());
            h.succeed();
        });
    }

    /** In a calm and then a moderate sea the big hull hardly moves: under 0.2 and under 0.5 degrees. */
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
     * the other storm tests), successive roll crests of the 7×17 hull differ by 15 % or more on average.
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
     * WAV2: the heave. In a storm the 7×17 hull's centre of mass rises and falls with the waves by 0.3 to 1.0 times the
     * wave amplitude (half range over one minute).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 1500, batch = "pirates_n_ships_waves_heave")
    public static void stormLiftsTheHullOnTheCrests(GameTestHelper h) {
        hold(h, SeaState.STORM, 1600);
        SailingGameTestsShips.Fixture f = longHull(h);
        long end = SETTLE + RAMP + WINDOW;
        Track storm = track(h, f, SETTLE + RAMP, end);
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t >= SETTLE + RAMP && t < end && t % 20 == 0 && !f.ship().isRemoved()) {
                Constants.LOG.info("[wave test] heave 7x17 t={} mean wave {} force {} y {}", t,
                        f2(WaveForces.meanHeight(h.getLevel(), f.ship().id())),
                        String.format("%.0f", WaveForces.heave(h.getLevel(), f.ship().id())),
                        storm.y.isEmpty() ? "-" : String.format("%.3f", storm.y.get(storm.y.size() - 1)));
            }
        });
        h.runAfterDelay(end + 1, () -> {
            double ratio = storm.heave() / amplitude(SeaState.STORM);
            Constants.LOG.info("[wave test] 7x17 storm heave {} blocks = {} x the amplitude; roll {} deg", f2(storm.heave()), f2(ratio),
                    f2(storm.rollHalf()));
            release(h);
            h.assertTrue(ratio >= 0.3 && ratio <= 1.0, "storm heave of the 7x17 hull " + ratio + " x the wave amplitude, outside 0.3..1.0");
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
     * 0.3 it is about 15). A calm sea (crest 0.1, under the sill) must put in nothing.
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
        double[] expected = {0};
        double[] maxFeed = {0};
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
            double crest = SeaStates.field(level).heightAround(centre.x, centre.z, centre.x, centre.z, level.getGameTime());
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
            Constants.LOG.info("[wave test] spill {}: water {} in {} ticks with the hatch open, estimate {}, threshold {}, "
                            + "highest crest feed {}, sill above sea at opening {}", state.id(), String.format("%.2f", water), SPILL_WINDOW,
                    String.format("%.2f", expected[0]), String.format("%.2f", threshold), String.format("%.2f", maxFeed[0]),
                    String.format("%.2f", sill[0]));
            release(h);
            var grid = f.runtime().simulation().analysis().grid();
            h.assertTrue(f.runtime().simulation().isOpen(grid.index(hatch.getX() - grid.originX(), hatch.getY() - grid.originY(),
                    hatch.getZ() - grid.originZ())), "the open hatch did not reach the simulation");
            h.assertTrue(Double.isFinite(sill[0]), "never found the sea at the hull");
            if (floods) {
                h.assertTrue(sill[0] > 0.0 && sill[0] < SeaState.STORM.amplitude(),
                        "the hatch's sill is not between the still sea and a storm crest: " + sill[0]);
                h.assertTrue(expected[0] >= 1.0, "no storm crest rose over the sill in the window: estimate " + expected[0]);
                h.assertTrue(water >= threshold, "a storm put only " + water + " blocks of water through the low hatch (threshold "
                        + threshold + " from a still-hull estimate of " + expected[0] + ")");
            } else {
                h.assertTrue(sill[0] > SeaState.CALM.amplitude(), "the hatch's sill is under a calm crest: " + sill[0]);
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

    /** In a calm sea the same hatch stays dry. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 640, batch = "pirates_n_ships_config_waves_spill_calm")
    public static void calmSeaKeepsALowHatchDry(GameTestHelper h) {
        spill(h, SeaState.CALM, false);
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
}
