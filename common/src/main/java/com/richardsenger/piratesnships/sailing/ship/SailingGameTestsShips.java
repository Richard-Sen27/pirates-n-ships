package com.richardsenger.piratesnships.sailing.ship;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.block.SailBlock;
import com.richardsenger.piratesnships.sailing.block.SailWinchBlock;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.sailing.force.ForceBreakdown;
import com.richardsenger.piratesnships.sailing.force.HullDampingModel;
import com.richardsenger.piratesnships.sailing.force.ShipFrame;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.wind.WindOverride;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntime;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntimes;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Spike 3 GameTests: a closed 5×4×5 plank hull (floor y=5, deck y=8) with a helm facing north (so the bow is +Z), a
 * fence mast and one sail on top, floating in a 40×40 stone basin of water (y=2..7). The wind is fixed with
 * {@link WindOverride}, which is level-wide, so every wind test has its own batch and clears the override itself
 * (with an expiry as a safety net if a test fails).
 *
 * <p>Measurements are averages of the ship-frame velocity over ticks {@link #FROM}..{@link #TO} (100 ticks, i.e. 200
 * physics substeps), not single samples.
 */
public final class SailingGameTestsShips {

    private static final int FROM = 40;
    private static final int TO = 140;
    private static final double WIND = 6.0;
    /** Beam reach wind: at 6 blocks/s the side force (about 1 N per kpg) capsized the 5x4x5 test hull. */
    private static final double BEAM_WIND = 3.0;

    private SailingGameTestsShips() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(SailingGameTestsShips.class);
    }

    // ------------------------------------------------------------------ fixtures

    public static void basin(GameTestHelper h, boolean water) {
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 40; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                boolean wall = x == 0 || x == 39 || z == 0 || z == 39;
                for (int y = 2; y <= 8; y++) {
                    boolean solid = wall || !water && y <= 4;
                    h.setBlock(new BlockPos(x, y, z), solid ? Blocks.STONE : y <= 7 && water ? Blocks.WATER : Blocks.AIR);
                }
            }
        }
    }

    /** Hull at x in [x0, x0+4], z in [z0, z0+4]; helm at the stern (z0+1) facing north, mast and sail amidships. Returns the helm. */
    public static BlockPos hull(GameTestHelper h, int x0, int z0, Block sail, Direction sailFacing, SailTrim trim) {
        for (int x = x0; x <= x0 + 4; x++) {
            for (int z = z0; z <= z0 + 4; z++) {
                for (int y = 5; y <= 8; y++) {
                    boolean shell = y == 5 || y == 8 || x == x0 || x == x0 + 4 || z == z0 || z == z0 + 4;
                    h.setBlock(new BlockPos(x, y, z), shell ? Blocks.OAK_PLANKS : Blocks.AIR);
                }
            }
        }
        BlockPos helm = new BlockPos(x0 + 2, 9, z0 + 1);
        h.setBlock(helm, AssemblyContent.HELM.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        h.setBlock(new BlockPos(x0 + 2, 9, z0 + 2), Blocks.OAK_FENCE);
        h.setBlock(new BlockPos(x0 + 2, 10, z0 + 2), sail.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, sailFacing).setValue(SailBlock.TRIM, trim));
        return helm;
    }

    /**
     * Turns the bottom layer of a {@link #hull} into stone ballast. The plain hull is a hollow, top-heavy plank box
     * with a metacentric height of about 0.1 blocks: it lists about 22° at rest from the helm's weight alone, runs
     * downwind 35 to 46° bow down under the small sail, and, heeled a few degrees, sheers off its course by up to 4° in
     * 10 s, to port or starboard with the sign of the heel (D5, measured). Ballasted it runs about 16° bow down and
     * holds its course.
     */
    public static void ballast(GameTestHelper h, int x0, int z0) {
        for (int x = x0; x <= x0 + 4; x++) {
            for (int z = z0; z <= z0 + 4; z++) {
                h.setBlock(new BlockPos(x, 5, z), Blocks.STONE);
            }
        }
    }

    public record Fixture(ShipBody ship, SailingRuntime runtime) { }

    public static Fixture assemble(GameTestHelper h, BlockPos helm) {
        AssemblyResult r = ShipTestCleanup.assemble(h, helm);
        if (r.shipId() == null) {
            throw new AssertionError("assembly failed: " + r);
        }
        ShipBody ship = SableShips.byId(h.getLevel(), r.shipId());
        SailingRuntime rt = ship == null ? null : SailingRuntimes.getOrCreate(ship);
        if (rt == null) {
            throw new AssertionError("no ship or no sailing runtime after assembly");
        }
        return new Fixture(ship, rt);
    }

    static void fixWind(GameTestHelper h, double fromDegrees) {
        fixWind(h, fromDegrees, WIND);
    }

    /** Fixes the wind for 400 ticks. Config values are the defaults: every GameTest run starts from a fresh world. */
    static void fixWind(GameTestHelper h, double fromDegrees, double strength) {
        WindOverride.set(h.getLevel().dimension().location().toString(), fromDegrees, strength, h.getLevel().getGameTime() + 400);
    }

    static void clearWind(GameTestHelper h) {
        WindOverride.clear(h.getLevel().dimension().location().toString());
    }

    /** Sums ship-frame velocity (x port, z forward) over FROM..TO; [0]=sum fwd, [1]=sum port, [2]=samples. */
    private static double[] measure(GameTestHelper h, Fixture f) {
        double[] acc = new double[3];
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t >= FROM && t < TO && !f.ship().isRemoved()) {
                Vector3d v = f.runtime().shipFrameVelocity(f.ship(), new Vector3d());
                acc[0] += v.z;
                acc[1] += v.x;
                acc[2]++;
            }
            if (t % 20 == 0 && !f.ship().isRemoved()) {
                Vector3d v = f.runtime().shipFrameVelocity(f.ship(), new Vector3d());
                HullRuntime hull = HullRuntimes.get(h.getLevel(), f.ship().id());
                Constants.LOG.info("[sailing test] t={} fwd {} port {} pos {} sea {} submerged {}", t, String.format("%.2f", v.z),
                        String.format("%.2f", v.x), comWorld(f), hull == null ? "no hull" : hull.seaWorldY(),
                        f.runtime().lastSubmerged());
            }
        });
        return acc;
    }

    static Vector3d comWorld(Fixture f) {
        Vector3d c = new Vector3d();
        f.ship().centerOfMass(c);
        return f.ship().toWorld(c, new Vector3d());
    }

    private static double fwd(double[] a) {
        return a[2] == 0 ? 0 : a[0] / a[2];
    }

    private static double port(double[] a) {
        return a[2] == 0 ? 0 : a[1] / a[2];
    }

    private static void log(String test, Fixture f, double[] a) {
        ForceBreakdown fb = f.runtime().lastBreakdown();
        Constants.LOG.info("[sailing test] {}: mean forward {} m/s, mean port {} m/s over {} ticks; mass {}, submerged {}, last force {}",
                test, String.format("%.3f", fwd(a)), String.format("%.3f", port(a)), (int) a[2],
                String.format("%.1f", f.ship().mass()), String.format("%.2f", f.runtime().lastSubmerged()),
                fb == null ? "none" : fb.contributions().stream().map(c -> c.source() + "=" + c.force()).toList());
    }

    // ------------------------------------------------------------------ tests

    /** Wind from the north (astern, ship bow +Z) with a full small square sail: the ship sails forward. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300, batch = "pirates_n_ships_sail_astern")
    public static void fullSquareSailDownwindGainsSpeed(GameTestHelper h) {
        fixWind(h, 0.0);
        basin(h, true);
        Fixture f = assemble(h, hull(h, 17, 3, SailingBlocks.SMALL_SQUARE_SAIL.get(), Direction.SOUTH, SailTrim.FULL));
        double[] a = measure(h, f);
        h.runAfterDelay(TO + 1, () -> {
            clearWind(h);
            log("downwind full", f, a);
            // >0.3 m/s forward on average (the sail drives the ship), and mostly along the bow
            h.assertTrue(fwd(a) > 0.3, "downwind ship too slow: mean forward " + fwd(a));
            h.assertTrue(Math.abs(port(a)) < 0.5 * fwd(a), "downwind ship drifts sideways: " + port(a));
            h.succeed();
        });
    }

    /** Same wind, sail furled: no drive (only hull windage, which this model does not apply). */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300, batch = "pirates_n_ships_sail_astern")
    public static void furledSailDownwindStays(GameTestHelper h) {
        fixWind(h, 0.0);
        basin(h, true);
        Fixture f = assemble(h, hull(h, 17, 3, SailingBlocks.SMALL_SQUARE_SAIL.get(), Direction.SOUTH, SailTrim.FURLED));
        double[] a = measure(h, f);
        h.runAfterDelay(TO + 1, () -> {
            clearWind(h);
            log("downwind furled", f, a);
            // 0.15: the spike-2 test hull drifts about 0.1 m/s by itself without any sailing force; the full sail does > 0.3
            h.assertTrue(Math.abs(fwd(a)) < 0.15, "furled ship moved: mean forward " + fwd(a));
            h.succeed();
        });
    }

    /** Wind from the south (dead ahead): a square sail gives no forward drive (it is taken aback). */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300, batch = "pirates_n_ships_sail_ahead")
    public static void headwindSquareSailMakesNoHeadway(GameTestHelper h) {
        fixWind(h, 180.0);
        basin(h, true);
        Fixture f = assemble(h, hull(h, 17, 17, SailingBlocks.SMALL_SQUARE_SAIL.get(), Direction.SOUTH, SailTrim.FULL));
        double[] a = measure(h, f);
        h.runAfterDelay(TO + 1, () -> {
            clearWind(h);
            log("headwind", f, a);
            h.assertTrue(fwd(a) < 0.02, "square-rigged ship made headway into the wind: " + fwd(a));
            h.succeed();
        });
    }

    /** Wind from the east (on the starboard beam) with a fore-and-aft sail: mostly forward, the keel holds. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300, batch = "pirates_n_ships_sail_beam")
    public static void beamReachForeAndAftSailsForward(GameTestHelper h) {
        fixWind(h, 90.0, BEAM_WIND);
        basin(h, true);
        Fixture f = assemble(h, hull(h, 22, 3, SailingBlocks.FORE_AND_AFT_SAIL.get(), Direction.EAST, SailTrim.FULL));
        double[] a = measure(h, f);
        h.runAfterDelay(TO + 1, () -> {
            clearWind(h);
            log("beam reach keel", f, a);
            h.assertTrue(fwd(a) > 0.3, "beam reach too slow: " + fwd(a));
            h.assertTrue(Math.abs(port(a)) < 0.5 * fwd(a), "beam reach drifts sideways: fwd " + fwd(a) + ", port " + port(a));
            h.succeed();
        });
    }

    /** As above with the keel switched off: the ship drifts downwind (to port, −X) much more. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300, batch = "pirates_n_ships_sail_beam_no_keel")
    public static void beamReachWithoutKeelDrifts(GameTestHelper h) {
        ConfigOverrides.during(h, SailingConfig.KEEL_ENABLED, false);
        fixWind(h, 90.0, BEAM_WIND);
        basin(h, true);
        Fixture f = assemble(h, hull(h, 22, 3, SailingBlocks.FORE_AND_AFT_SAIL.get(), Direction.EAST, SailTrim.FULL));
        double[] a = measure(h, f);
        h.runAfterDelay(TO + 1, () -> {
            clearWind(h);
            log("beam reach no keel", f, a);
            // the wind blows toward −X, which is the ship's starboard→port... port is +X, so leeward is −X = starboard side
            h.assertTrue(Math.abs(port(a)) > 0.5 * Math.abs(fwd(a)), "without keel the ship should drift: fwd " + fwd(a) + ", port " + port(a));
            h.succeed();
        });
    }

    /** On dry land with a full sail and a fixed wind: no keel force, no sail force, no sliding. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300, batch = "pirates_n_ships_sail_dry")
    public static void shipOnLandIsNotBlownAway(GameTestHelper h) {
        fixWind(h, 0.0);
        basin(h, false);
        Fixture f = assemble(h, hull(h, 17, 17, SailingBlocks.LARGE_SQUARE_SAIL.get(), Direction.SOUTH, SailTrim.FULL));
        net.minecraft.world.phys.Vec3[] start = new net.minecraft.world.phys.Vec3[1];
        h.runAfterDelay(FROM, () -> start[0] = f.ship().worldBounds().getCenter());
        h.runAfterDelay(TO + 1, () -> {
            clearWind(h);
            ForceBreakdown fb = f.runtime().lastBreakdown();
            h.assertTrue(f.runtime().lastSubmerged() == 0.0, "a ship on land counts as submerged: " + f.runtime().lastSubmerged());
            h.assertTrue(fb == null || fb.force().length() < 1e-9, "a ship on land got sailing forces: " + (fb == null ? null : fb.force()));
            // 0.2 blocks in 100 ticks: resting contact jitter, far below the metres a 25-block sail would push it
            double moved = f.ship().worldBounds().getCenter().distanceTo(start[0]);
            h.assertTrue(moved < 0.2, "the ship on land moved " + moved + " blocks");
            h.succeed();
        });
    }

    /** The winch sets the trim of its own ship's sails, and of no other ship's. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200)
    public static void winchCyclesOnlyItsOwnShip(GameTestHelper h) {
        basin(h, true);
        BlockPos helmA = hull(h, 5, 17, SailingBlocks.SMALL_SQUARE_SAIL.get(), Direction.SOUTH, SailTrim.FURLED);
        h.setBlock(new BlockPos(6, 9, 18), SailingBlocks.SAIL_WINCH.get());
        BlockPos helmB = hull(h, 28, 17, SailingBlocks.FORE_AND_AFT_SAIL.get(), Direction.EAST, SailTrim.FURLED);
        Fixture a = assemble(h, helmA);
        Fixture b = assemble(h, helmB);
        BlockPos winch = a.ship().plotBlocks().stream()
                .filter(p -> h.getLevel().getBlockState(p).is(SailingBlocks.SAIL_WINCH.get())).findFirst().orElseThrow();
        BlockPos sailA = a.runtime().sailPositions().get(0), sailB = b.runtime().sailPositions().get(0);
        SailTrim[] expected = {SailTrim.HALF, SailTrim.FULL, SailTrim.FURLED};
        for (SailTrim e : expected) {
            SailWinchBlock.use(h.getLevel(), winch);
            BlockState s = h.getLevel().getBlockState(sailA);
            h.assertTrue(s.getValue(SailBlock.TRIM) == e, "sail block trim " + s.getValue(SailBlock.TRIM) + ", expected " + e);
            h.assertTrue(a.runtime().trimAt(sailA) == e, "runtime trim " + a.runtime().trimAt(sailA) + ", expected " + e);
            h.assertTrue(h.getLevel().getBlockState(sailB).getValue(SailBlock.TRIM) == SailTrim.FURLED, "the other ship's sail changed");
            h.assertTrue(b.runtime().unfurledCount() == 0, "the other ship's runtime changed");
        }
        h.succeed();
    }

    /** A sail placed on an assembled ship is picked up, and once broken it no longer pushes. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200)
    public static void sailAddedAndBrokenOnAssembledShip(GameTestHelper h) {
        basin(h, true);
        BlockPos helm = hull(h, 17, 17, SailingBlocks.SMALL_SQUARE_SAIL.get(), Direction.SOUTH, SailTrim.FURLED);
        h.setBlock(new BlockPos(19, 10, 19), Blocks.AIR); // no sail at assembly
        Fixture f = assemble(h, helm);
        h.assertTrue(f.runtime().sailCount() == 0, "a sail was found before one was placed");
        BlockPos mast = f.ship().plotBlocks().stream()
                .filter(p -> h.getLevel().getBlockState(p).is(Blocks.OAK_FENCE)).findFirst().orElseThrow();
        BlockPos sail = mast.above();
        h.getLevel().setBlock(sail, SailingBlocks.LARGE_SQUARE_SAIL.get().defaultBlockState().setValue(SailBlock.TRIM, SailTrim.FULL), Block.UPDATE_ALL);
        h.assertTrue(f.runtime().sailCount() == 1 && f.runtime().unfurledCount() == 1, "the placed sail was not picked up");
        h.runAfterDelay(20, () -> {
            ForceBreakdown fb = f.runtime().lastBreakdown();
            h.assertTrue(fb != null && fb.contributions().stream().anyMatch(c -> c.source().startsWith("sail[")),
                    "the placed sail is not evaluated");
            h.getLevel().destroyBlock(sail, false);
            h.assertTrue(f.runtime().sailCount() == 0 && f.runtime().unfurledCount() == 0, "the broken sail is still listed");
        });
        h.runAfterDelay(40, () -> {
            ForceBreakdown fb = f.runtime().lastBreakdown();
            h.assertTrue(fb == null || fb.contributions().stream().noneMatch(c -> c.source().startsWith("sail[")),
                    "the broken sail still pushes");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ hull damping (F1)

    private static final int KICK_AT = 60;
    private static final double KICK = 0.8; // rad/s about the bow axis
    private static final int WINDOW = 10;
    private static final int WINDOWS = 20;

    /**
     * A 7×17 plank hull, 4 high (x 16..22, z 10..26), around the small {@link #hull} at (17, 17), whose walls stay as
     * a bulkhead compartment with the helm and the furled sail on top: 180 kpg. The small hull alone is too stiff and
     * too strongly damped by Sable's own water drag to show rolling; this one rolls a few times after a kick.
     */
    public static BlockPos longHull(GameTestHelper h) {
        BlockPos helm = hull(h, 17, 17, SailingBlocks.SMALL_SQUARE_SAIL.get(), Direction.SOUTH, SailTrim.FURLED);
        for (int x = 16; x <= 22; x++) {
            for (int z = 10; z <= 26; z++) {
                for (int y = 5; y <= 8; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState old = h.getBlockState(p);
                    if (old.isAir() || old.is(Blocks.WATER)) {
                        boolean shell = y == 5 || y == 8 || x == 16 || x == 22 || z == 10 || z == 26;
                        h.setBlock(p, shell ? Blocks.OAK_PLANKS : Blocks.AIR);
                    }
                }
            }
        }
        return helm;
    }

    /** Signed roll angle [rad]: the tilt of the ship's port axis out of the horizontal (+ = port side up). */
    static double rollAngle(Fixture f) {
        Quaterniond q = f.runtime().bow().shipToWorld(f.ship().orientation(new Quaterniond()), new Quaterniond());
        Vector3d port = q.transform(new Vector3d(ShipFrame.PORT));
        return Math.asin(Math.max(-1.0, Math.min(1.0, port.y)));
    }

    /** Roll rate [rad/s] about the ship's bow axis. */
    static double rollRate(Fixture f) {
        Vector3d lin = new Vector3d(), ang = new Vector3d();
        f.ship().velocities(lin, ang);
        Quaterniond q = f.runtime().bow().shipToWorld(f.ship().orientation(new Quaterniond()), new Quaterniond());
        return q.transformInverse(ang).dot(ShipFrame.FORWARD);
    }

    /**
     * Floats the {@link #longHull}, kicks it about its bow axis at {@link #KICK_AT} and records, per window of
     * {@link #WINDOW} ticks after the kick, the largest roll rate [rad/s] (the rate rather than the angle, because a hull
     * with little metacentric height can also take a slow new list, which is not rocking). Logs the rates and the roll
     * range per window.
     */
    private static double[] rollAfterKick(GameTestHelper h, String label) {
        basin(h, true);
        Fixture f = assemble(h, longHull(h));
        double[] amp = new double[WINDOWS];
        double[] range = new double[2 * WINDOWS];
        h.onEachTick(() -> {
            long t = h.getTick();
            if (f.ship().isRemoved()) return;
            if (t == KICK_AT) {
                Quaterniond q = f.runtime().bow().shipToWorld(f.ship().orientation(new Quaterniond()), new Quaterniond());
                f.ship().addVelocity(new Vector3d(), q.transform(new Vector3d(ShipFrame.FORWARD)).mul(KICK));
            } else if (t > KICK_AT && t <= KICK_AT + (long) WINDOWS * WINDOW) {
                int w = (int) ((t - KICK_AT - 1) / WINDOW);
                amp[w] = Math.max(amp[w], Math.abs(rollRate(f)));
                double r = Math.toDegrees(rollAngle(f));
                if ((t - KICK_AT - 1) % WINDOW == 0) {
                    range[2 * w] = r;
                    range[2 * w + 1] = r;
                }
                range[2 * w] = Math.min(range[2 * w], r);
                range[2 * w + 1] = Math.max(range[2 * w + 1], r);
            }
        });
        h.runAfterDelay(KICK_AT + (long) WINDOWS * WINDOW + 1, () -> {
            StringBuilder sb = new StringBuilder();
            for (double a : amp) sb.append(String.format(" %.2f", a));
            StringBuilder rb = new StringBuilder();
            for (int i = 0; i < WINDOWS; i++) rb.append(String.format(" %.0f..%.0f", range[2 * i], range[2 * i + 1]));
            Constants.LOG.info("[damping test] {}: max roll rate per {} ticks [rad/s]:{}; roll range [deg]:{}; mass {}",
                    label, WINDOW, sb, rb, String.format("%.1f", f.ship().mass()));
        });
        return amp;
    }

    /** Largest value of {@code amp} in windows {@code from..to} (inclusive). */
    private static double maxOf(double[] amp, int from, int to) {
        double m = 0.0;
        for (int i = from; i <= to; i++) m = Math.max(m, amp[i]);
        return m;
    }

    /**
     * With hull damping on, the kicked long hull settles: 3.5 to 5 s after the kick its roll rate is small (measured
     * 0.01 rad/s, against about 0.11 without damping and 0.7 right after the kick).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 320, batch = "pirates_n_ships_config_sailing_damping_on")
    public static void rollingShipSettlesWithDamping(GameTestHelper h) {
        ConfigOverrides.during(h, SailingConfig.HULL_DAMPING_ENABLED, true);
        ConfigOverrides.during(h, SailingConfig.ROLL_DAMPING, HullDampingModel.Params.DEFAULTS.roll());
        ConfigOverrides.during(h, SailingConfig.PITCH_DAMPING, HullDampingModel.Params.DEFAULTS.pitch());
        double[] amp = rollAfterKick(h, "damping on");
        h.runAfterDelay(KICK_AT + (long) WINDOWS * WINDOW + 2, () -> {
            h.assertTrue(amp[0] > 0.3, "the kick did not roll the ship: " + amp[0] + " rad/s");
            double late = maxOf(amp, 7, 9);
            h.assertTrue(late < 0.04, "still rolling 3.5 to 5 s after the kick with damping on: " + late + " rad/s");
            h.succeed();
        });
    }

    /** With hull damping off, the same kick still rolls the ship clearly at the same time. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 320, batch = "pirates_n_ships_config_sailing_damping_off")
    public static void rollingShipKeepsRollingWithoutDamping(GameTestHelper h) {
        ConfigOverrides.during(h, SailingConfig.HULL_DAMPING_ENABLED, false);
        double[] amp = rollAfterKick(h, "damping off");
        h.runAfterDelay(KICK_AT + (long) WINDOWS * WINDOW + 2, () -> {
            h.assertTrue(amp[0] > 0.3, "the kick did not roll the ship: " + amp[0] + " rad/s");
            double late = maxOf(amp, 7, 9);
            h.assertTrue(late > 0.06, "the undamped ship stopped rolling 3.5 to 5 s after the kick: " + late + " rad/s");
            h.succeed();
        });
    }

    /** A floating ship at rest stays at rest with damping on (the damping adds no motion). */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300, batch = "pirates_n_ships_config_sailing_damping_on")
    public static void shipAtRestStaysAtRest(GameTestHelper h) {
        ConfigOverrides.during(h, SailingConfig.HULL_DAMPING_ENABLED, true);
        basin(h, true);
        BlockPos helm = hull(h, 17, 17, SailingBlocks.SMALL_SQUARE_SAIL.get(), Direction.SOUTH, SailTrim.FURLED);
        ballast(h, 17, 17);
        Fixture f = assemble(h, helm);
        double[] max = new double[1];
        h.onEachTick(() -> {
            if (h.getTick() >= 100 && h.getTick() < 200 && !f.ship().isRemoved()) {
                max[0] = Math.max(max[0], Math.abs(rollRate(f)));
            }
        });
        double[] start = new double[1];
        h.runAfterDelay(100, () -> start[0] = rollAngle(f));
        h.runAfterDelay(200, () -> {
            double drift = Math.abs(rollAngle(f) - start[0]);
            Constants.LOG.info("[damping test] at rest: max roll rate {} rad/s, roll drift {} deg", String.format("%.4f", max[0]),
                    String.format("%.2f", Math.toDegrees(drift)));
            h.assertTrue(max[0] < 0.05, "a ship at rest rolls: " + max[0] + " rad/s");
            h.assertTrue(drift < Math.toRadians(1), "a ship at rest changed its roll by " + Math.toDegrees(drift) + " deg");
            h.succeed();
        });
    }
}
