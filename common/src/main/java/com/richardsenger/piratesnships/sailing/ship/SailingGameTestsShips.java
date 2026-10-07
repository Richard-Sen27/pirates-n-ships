package com.richardsenger.piratesnships.sailing.ship;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.block.CleatBlock;
import com.richardsenger.piratesnships.sailing.block.SailWinchBlock;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.sailing.block.YardBlock;
import com.richardsenger.piratesnships.sailing.block.YardBlockEntity;
import com.richardsenger.piratesnships.sailing.sail.ClothGeometry;
import com.richardsenger.piratesnships.sailing.sail.SquareSail;
import com.richardsenger.piratesnships.sailing.sail.TriangularSailContent;
import com.richardsenger.piratesnships.sailing.sail.TriangularSails;
import com.richardsenger.piratesnships.sailing.sail.YardSails;
import org.jetbrains.annotations.Nullable;
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
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Spike 3 and F5a GameTests: a closed 5×4×5 plank hull (floor y=5, deck y=8) with a helm facing north (so the bow is
 * +Z), a fence mast and one sail (a square sail between two yards, or a triangular sail on a stay), floating in a
 * 40×40 stone basin of water (y=2..7). The wind is fixed with
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

    /**
     * 40×40 stone basin, water up to y=7 (or stone up to y=4), open to the sky: the GameTest framework puts a barrier
     * ceiling one block above every test template ({@code GameTestInfo#prepareTestStructure} →
     * {@code StructureUtils#encaseStructure}; at y=13 here, since y=0 is the structure block's layer). A floating test
     * ship rises about 1.4 blocks after assembly, so a rig that reaches y=12 runs into it (measured: a two-yard rig
     * slowed the test hull to a stop after 5 blocks, kept a kicked hull from rolling, and held the ballasted test hull
     * down), so the ceiling over the basin is removed.
     */
    public static void basin(GameTestHelper h, boolean water) {
        openSky(h, 40);
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

    /**
     * Hull at x in [x0, x0+4], z in [z0, z0+4]; helm at the stern (z0+1) facing north, and amidships a square sail of
     * two 3-wide yards across the ship (along x), the lower lying on the deck (y=9) and the upper at y=12 with a fence
     * mast between: area 9 (rule F5a), as the one-block small square sail of spike 3. The cloth's centroid at full sail
     * is at y=11, a block lower than that sail's center of effort, and the rig weighs 1.1 kpg where the sail block and
     * its fence weighed 0.5: the hollow test hull is so tender that heavier or higher rigs capsized it in the anchor
     * test (measured with 0.25 kpg yards at y=10 and 13). The rig reaches above the template, so use it in a
     * {@link #basin}. Returns the helm.
     */
    public static BlockPos squareHull(GameTestHelper h, int x0, int z0, SailTrim trim) {
        BlockPos helm = bareHull(h, x0, z0);
        rig(h, x0 + 2, z0 + 2, 1, 3, trim);
        return helm;
    }

    /**
     * A square sail in column (mx, mz): a lower yard lying on the deck (y=9), a fence mast above it and an upper yard
     * {@code drop} blocks higher, both along x and {@code 2 * halfWidth + 1} long; the upper yard gets {@code trim}.
     * Returns the head (test-relative). The rig reaches the GameTest barrier ceiling, so build it in a {@link #basin}.
     */
    public static BlockPos rig(GameTestHelper h, int mx, int mz, int halfWidth, int drop, SailTrim trim) {
        yard(h, mx, 9, mz, halfWidth, SailTrim.FURLED);
        for (int y = 10; y < 9 + drop; y++) {
            h.setBlock(new BlockPos(mx, y, mz), Blocks.OAK_FENCE);
        }
        yard(h, mx, 9 + drop, mz, halfWidth, trim);
        return new BlockPos(mx, 9 + drop, mz);
    }

    /** A yard along x centered on (mx, y, mz). */
    public static void yard(GameTestHelper h, int mx, int y, int mz, int halfWidth, SailTrim trim) {
        for (int x = mx - halfWidth; x <= mx + halfWidth; x++) {
            h.setBlock(new BlockPos(x, y, mz), SailingBlocks.YARD.get().defaultBlockState()
                    .setValue(YardBlock.AXIS, Direction.Axis.X).setValue(YardBlock.TRIM, trim));
        }
    }

    /** Removes the GameTest barrier ceiling (y=13) over a {@code size}×{@code size} template (see {@link #basin}). */
    public static void openSky(GameTestHelper h, int size) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                BlockPos p = new BlockPos(x, 13, z);
                if (h.getBlockState(p).is(Blocks.BARRIER)) {
                    h.setBlock(p, Blocks.AIR);
                }
            }
        }
    }

    /**
     * Hull at x in [x0, x0+4], z in [z0, z0+4] with a triangular (fore-and-aft) sail (rule F5b): a fence mast amidships
     * (y 9..14), a bowsprit of four planks (y=8, z0+5..z0+8), the head cleat A on the mast's forward face at y=14, the
     * clew cleat C on the deck straight below it (y=9) and the tack cleat B on the bowsprit's end, with a stay from A
     * to B. Area 0.5 × 5 (drop A-C) × 5 (B forward of A) = 12.5. The mast reaches above the template: build it in a
     * {@link #basin}. Returns the helm.
     */
    public static BlockPos foreAndAftHull(GameTestHelper h, int x0, int z0, SailTrim trim) {
        BlockPos helm = bareHull(h, x0, z0);
        int mx = x0 + 2, mz = z0 + 2;
        for (int y = 9; y <= 14; y++) {
            h.setBlock(new BlockPos(mx, y, mz), Blocks.OAK_FENCE);
        }
        for (int z = z0 + 5; z <= z0 + 8; z++) {
            h.setBlock(new BlockPos(mx, 8, z), Blocks.OAK_PLANKS);
        }
        BlockPos head = new BlockPos(mx, 14, mz + 1);
        BlockPos tack = new BlockPos(mx, 9, z0 + 8);
        h.setBlock(head, cleat(AttachFace.WALL, Direction.SOUTH).setValue(CleatBlock.TRIM, trim));
        h.setBlock(new BlockPos(mx, 9, mz + 1), cleat(AttachFace.FLOOR, Direction.NORTH));
        h.setBlock(tack, cleat(AttachFace.FLOOR, Direction.NORTH));
        TriangularSails.rig(h.getLevel(), h.absolutePos(head), h.absolutePos(tack));
        return helm;
    }

    /** A cleat state ({@code facing} points away from the supporting block for a wall cleat). */
    public static BlockState cleat(AttachFace face, Direction facing) {
        return TriangularSailContent.CLEAT.get().defaultBlockState().setValue(CleatBlock.FACE, face).setValue(CleatBlock.FACING, facing);
    }

    /** The 5×4×5 plank hull with its helm and nothing on deck. Returns the helm. */
    private static BlockPos bareHull(GameTestHelper h, int x0, int z0) {
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
        return helm;
    }

    /**
     * Turns the bottom layer of a test hull into cobblestone ballast: a ship block (not {@code pirates_n_ships:terrain},
     * so it is gathered) in Sable's {@code #sable:heavy} (through {@code #c:cobblestones}, 2 kpg against the planks' 1).
     * The plain hull is a hollow, top-heavy plank box with a metacentric height of about 0.1 blocks: it lists about 22°
     * at rest from the helm's weight alone, runs downwind 35 to 46° bow down under the small sail, and, heeled a few
     * degrees, sheers off its course by up to 4° in 10 s, to port or starboard with the sign of the heel (D5, measured).
     * Ballasted (81.6 kpg with the square rig) it runs about 3.4° bow down at 0.38 m/s and holds its course (F5b,
     * measured).
     *
     * <p>Until F5b this was stone, which is terrain ({@code ShipBlockRule.TERRAIN}): it was never gathered and stayed
     * behind as a submerged plate, so the D5 numbers were measured on a floorless 30.6 kpg hull.
     */
    public static void ballast(GameTestHelper h, int x0, int z0) {
        for (int x = x0; x <= x0 + 4; x++) {
            for (int z = z0; z <= z0 + 4; z++) {
                h.setBlock(new BlockPos(x, 5, z), Blocks.COBBLESTONE);
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
        Fixture f = assemble(h, squareHull(h, 17, 3, SailTrim.FULL));
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
        Fixture f = assemble(h, squareHull(h, 17, 3, SailTrim.FURLED));
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
        Fixture f = assemble(h, squareHull(h, 17, 17, SailTrim.FULL));
        double[] a = measure(h, f);
        h.runAfterDelay(TO + 1, () -> {
            clearWind(h);
            log("headwind", f, a);
            h.assertTrue(fwd(a) < 0.02, "square-rigged ship made headway into the wind: " + fwd(a));
            h.succeed();
        });
    }

    /*
     * Beam reach tolerances (F5b, triangular sail of 12.5 blocks² on the unballasted hull, wind 3 blocks/s, measured in
     * four full suite runs): with the keel 0.371 to 0.373 m/s forward and 0.108 to 0.111 m/s to leeward (leeway ratio
     * 0.29 to 0.30), without it 0.359 to 0.363 and 0.162 to 0.166 (0.45 to 0.46). The square hull's own water drag is the same in all directions, so without the keel the
     * leeway follows the sail's side-to-drive ratio. LEEWAY_RATIO (0.37) lies between the two. Spike 3's one-block sail
     * (16 blocks², center of effort 2 blocks above the block) made 0.425 / 0.113 with the keel and 0.362 / 0.299
     * without, against a ratio of 0.5.
     */
    private static final double LEEWAY_RATIO = 0.37;

    /** Wind from the east (on the starboard beam) with a triangular fore-and-aft sail: mostly forward, the keel holds. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300, batch = "pirates_n_ships_sail_beam")
    public static void beamReachForeAndAftSailsForward(GameTestHelper h) {
        fixWind(h, 90.0, BEAM_WIND);
        basin(h, true);
        Fixture f = assemble(h, foreAndAftHull(h, 22, 3, SailTrim.FULL));
        double[] a = measure(h, f);
        h.runAfterDelay(TO + 1, () -> {
            clearWind(h);
            log("beam reach keel", f, a);
            h.assertTrue(fwd(a) > 0.3, "beam reach too slow: " + fwd(a));
            h.assertTrue(Math.abs(port(a)) < LEEWAY_RATIO * fwd(a), "beam reach drifts sideways: fwd " + fwd(a) + ", port " + port(a));
            h.succeed();
        });
    }

    /** As above with the keel switched off: the ship drifts downwind (to port, −X) much more. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300, batch = "pirates_n_ships_sail_beam_no_keel")
    public static void beamReachWithoutKeelDrifts(GameTestHelper h) {
        ConfigOverrides.during(h, SailingConfig.KEEL_ENABLED, false);
        fixWind(h, 90.0, BEAM_WIND);
        basin(h, true);
        Fixture f = assemble(h, foreAndAftHull(h, 22, 3, SailTrim.FULL));
        double[] a = measure(h, f);
        h.runAfterDelay(TO + 1, () -> {
            clearWind(h);
            log("beam reach no keel", f, a);
            // the wind blows toward −X, which is the ship's starboard→port... port is +X, so leeward is −X = starboard side
            h.assertTrue(Math.abs(port(a)) > LEEWAY_RATIO * Math.abs(fwd(a)), "without keel the ship should drift: fwd " + fwd(a) + ", port " + port(a));
            h.succeed();
        });
    }

    /** On dry land with a full sail and a fixed wind: no keel force, no sail force, no sliding. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300, batch = "pirates_n_ships_sail_dry")
    public static void shipOnLandIsNotBlownAway(GameTestHelper h) {
        fixWind(h, 0.0);
        basin(h, false);
        BlockPos helm = bareHull(h, 17, 17);
        rig(h, 19, 19, 2, 5, SailTrim.FULL); // 5 wide, 5 deep: 25 blocks², as the large square sail of spike 3
        Fixture f = assemble(h, helm);
        net.minecraft.world.phys.Vec3[] start = new net.minecraft.world.phys.Vec3[1];
        h.runAfterDelay(FROM, () -> start[0] = f.ship().worldBounds().getCenter());
        h.runAfterDelay(TO + 1, () -> {
            clearWind(h);
            ForceBreakdown fb = f.runtime().lastBreakdown();
            h.assertTrue(f.runtime().sailCount() == 1 && f.runtime().areaAt(f.runtime().sailPositions().get(0)) == 25.0,
                    "expected one 25 block² sail, got " + f.runtime().sailCount());
            h.assertTrue(f.runtime().lastSubmerged() == 0.0, "a ship on land counts as submerged: " + f.runtime().lastSubmerged());
            h.assertTrue(fb == null || fb.force().length() < 1e-9, "a ship on land got sailing forces: " + (fb == null ? null : fb.force()));
            // 0.2 blocks in 100 ticks: resting contact jitter, far below the metres a 25-block sail would push it
            double moved = f.ship().worldBounds().getCenter().distanceTo(start[0]);
            h.assertTrue(moved < 0.2, "the ship on land moved " + moved + " blocks");
            h.succeed();
        });
    }

    /** The winch sets the trim of its own ship's sails (the whole upper yard of a square sail), and of no other ship's. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200)
    public static void winchCyclesOnlyItsOwnShip(GameTestHelper h) {
        basin(h, true);
        BlockPos helmA = squareHull(h, 5, 17, SailTrim.FURLED);
        h.setBlock(new BlockPos(6, 9, 18), SailingBlocks.SAIL_WINCH.get());
        BlockPos helmB = foreAndAftHull(h, 28, 17, SailTrim.FURLED);
        Fixture a = assemble(h, helmA);
        Fixture b = assemble(h, helmB);
        BlockPos winch = a.ship().plotBlocks().stream()
                .filter(p -> h.getLevel().getBlockState(p).is(SailingBlocks.SAIL_WINCH.get())).findFirst().orElseThrow();
        h.assertTrue(a.runtime().sailCount() == 1, "ship A should have one square sail, has " + a.runtime().sailCount());
        BlockPos sailA = a.runtime().sailPositions().get(0), sailB = b.runtime().sailPositions().get(0);
        h.assertTrue(h.getLevel().getBlockState(sailA).getBlock() instanceof YardBlock, "the square sail's position is not a yard");
        SailTrim[] expected = {SailTrim.HALF, SailTrim.FULL, SailTrim.FURLED};
        for (SailTrim e : expected) {
            SailWinchBlock.use(h.getLevel(), winch);
            for (int dx = -1; dx <= 1; dx++) {
                BlockState s = h.getLevel().getBlockState(sailA.offset(dx, 0, 0));
                h.assertTrue(s.getValue(YardBlock.TRIM) == e, "upper yard trim " + s.getValue(YardBlock.TRIM) + " at " + dx + ", expected " + e);
            }
            h.assertTrue(h.getLevel().getBlockState(sailA.below(3)).getValue(YardBlock.TRIM) == SailTrim.FURLED, "the lower yard got a trim");
            h.assertTrue(a.runtime().trimAt(sailA) == e, "runtime trim " + a.runtime().trimAt(sailA) + ", expected " + e);
            h.assertTrue(h.getLevel().getBlockState(sailB).getValue(CleatBlock.TRIM) == SailTrim.FURLED, "the other ship's sail changed");
            h.assertTrue(b.runtime().unfurledCount() == 0, "the other ship's runtime changed");
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ square sails from yards (F5a)

    private static @Nullable ClothGeometry cloth(GameTestHelper h, BlockPos plotPos) {
        return h.getLevel().getBlockEntity(plotPos) instanceof YardBlockEntity be ? be.geometry() : null;
    }

    /**
     * A ship with a mast and two yards has one sail of the expected area, headed by the upper yard's middle block, whose
     * block entity holds the cloth; breaking the lower yard dissolves the sail (it stops pushing), and a new lower yard
     * brings it back with the head's trim.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200)
    public static void yardSailDissolvesAndReturnsOnAssembledShip(GameTestHelper h) {
        basin(h, true);
        Fixture f = assemble(h, squareHull(h, 17, 17, SailTrim.FULL));
        SailingRuntime rt = f.runtime();
        h.assertTrue(rt.sailCount() == 1 && rt.unfurledCount() == 1, "expected one set sail, got " + rt.sailCount());
        BlockPos head = rt.sailPositions().get(0);
        h.assertTrue(rt.areaAt(head) == 9.0, "area " + rt.areaAt(head) + ", expected 9 (3 wide, 3 deep)");
        SquareSail sq = rt.squareSailAt(head);
        h.assertTrue(sq != null && sq.drop() == 3 && sq.upper().y() == head.getY(), "unexpected sail geometry: " + sq);
        ClothGeometry expected = new ClothGeometry(true, 1.5f, 1.5f, 1.5f, 1.5f, 3);
        h.assertTrue(expected.equals(cloth(h, head)), "head block entity cloth " + cloth(h, head) + ", expected " + expected);
        h.assertTrue(cloth(h, head.east()) == null && cloth(h, head.below(3)) == null, "a non-head yard block has cloth");
        BlockPos lowerMiddle = head.below(3);
        h.runAfterDelay(20, () -> {
            ForceBreakdown fb = rt.lastBreakdown();
            h.assertTrue(fb != null && fb.contributions().stream().anyMatch(c -> c.source().startsWith("sail[")),
                    "the yard sail is not evaluated");
            h.getLevel().destroyBlock(lowerMiddle, false);
            h.assertTrue(rt.sailCount() == 0 && rt.unfurledCount() == 0, "the sail survived its lower yard: " + rt.sailCount());
            h.assertTrue(cloth(h, head) == null, "the head still has cloth without a lower yard");
        });
        h.runAfterDelay(40, () -> {
            ForceBreakdown fb = rt.lastBreakdown();
            h.assertTrue(fb == null || fb.contributions().stream().noneMatch(c -> c.source().startsWith("sail[")),
                    "the dissolved sail still pushes");
            h.getLevel().setBlock(lowerMiddle, SailingBlocks.YARD.get().defaultBlockState().setValue(YardBlock.AXIS, Direction.Axis.X),
                    Block.UPDATE_ALL);
            h.assertTrue(rt.sailCount() == 1 && rt.areaAt(head) == 9.0, "the mended lower yard did not bring the sail back");
            h.assertTrue(rt.trimAt(head) == SailTrim.FULL, "the sail lost the head's trim: " + rt.trimAt(head));
            h.assertTrue(expected.equals(cloth(h, head)), "no cloth after mending: " + cloth(h, head));
            h.succeed();
        });
    }

    /**
     * A third yard placed between the two yards of a sail on an assembled ship splits it into two sails (the middle yard
     * is the foot of the upper one and the head of the lower one); taking the middle yard's center block out joins them
     * again. Yards 3 (top), 5 (middle), 7 (bottom) wide, 3 + 3 blocks apart.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100)
    public static void thirdYardSplitsTheSail(GameTestHelper h) {
        basin(h, false);
        BlockPos helm = bareHull(h, 17, 17);
        rig(h, 19, 19, 1, 6, SailTrim.FURLED);
        yard(h, 19, 9, 19, 3, SailTrim.FURLED); // widen the lower yard to 7
        Fixture f = assemble(h, helm);
        SailingRuntime rt = f.runtime();
        h.assertTrue(rt.sailCount() == 1, "expected one sail before, got " + rt.sailCount());
        BlockPos top = rt.sailPositions().get(0);
        h.assertTrue(rt.areaAt(top) == (3 + 7) / 2.0 * 6, "area " + rt.areaAt(top) + ", expected 30");
        BlockPos mid = top.below(3);
        for (int dx = -2; dx <= 2; dx++) {
            h.getLevel().setBlock(mid.offset(dx, 0, 0), SailingBlocks.YARD.get().defaultBlockState()
                    .setValue(YardBlock.AXIS, Direction.Axis.X), Block.UPDATE_ALL);
        }
        h.assertTrue(rt.sailCount() == 2, "expected two sails with the middle yard, got " + rt.sailCount() + " at " + rt.sailPositions());
        h.assertTrue(rt.areaAt(top) == (3 + 5) / 2.0 * 3, "upper sail area " + rt.areaAt(top) + ", expected 12");
        h.assertTrue(rt.areaAt(mid) == (5 + 7) / 2.0 * 3, "lower sail area " + rt.areaAt(mid) + ", expected 18");
        h.assertTrue(cloth(h, top) != null && cloth(h, top).drop() == 3 && cloth(h, mid) != null && cloth(h, mid).drop() == 3,
                "cloth of the two sails: " + cloth(h, top) + ", " + cloth(h, mid));
        h.getLevel().setBlock(mid, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        h.assertTrue(rt.sailCount() == 1 && rt.areaAt(top) == 30.0, "the sails did not join again: " + rt.sailCount() + " " + rt.areaAt(top));
        h.assertTrue(cloth(h, top) != null && cloth(h, top).drop() == 6, "joined cloth " + cloth(h, top));
        h.succeed();
    }

    /**
     * On land (no ship) the head's block entity gets its cloth at once when the yards are placed; a block put into the
     * gap is noticed by the yard's periodic check, and clicking a yard of the upper yard cycles the sail's trim.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 60, batch = "pirates_n_ships_config_sailing_yard_refresh")
    public static void yardsOnLandShowCloth(GameTestHelper h) {
        ConfigOverrides.during(h, SailingConfig.YARD_REFRESH_TICKS, 5);
        for (int x = 8; x <= 14; x++) for (int z = 8; z <= 14; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        h.setBlock(new BlockPos(11, 2, 11), Blocks.OAK_LOG);
        yard(h, 11, 3, 11, 1, SailTrim.FURLED);
        h.setBlock(new BlockPos(11, 4, 11), Blocks.OAK_LOG);
        h.setBlock(new BlockPos(11, 5, 11), Blocks.OAK_FENCE);
        yard(h, 11, 6, 11, 1, SailTrim.FURLED);
        BlockPos rel = new BlockPos(11, 6, 11);
        BlockPos head = h.absolutePos(rel);
        ClothGeometry expected = new ClothGeometry(true, 1.5f, 1.5f, 1.5f, 1.5f, 3);
        h.assertTrue(expected.equals(cloth(h, head)), "cloth on land " + cloth(h, head));
        SailTrim t = YardSails.cycle(h.getLevel(), head.east());
        h.assertTrue(t == SailTrim.HALF && h.getLevel().getBlockState(head).getValue(YardBlock.TRIM) == SailTrim.HALF,
                "clicking the upper yard did not hoist: " + t);
        h.assertTrue(YardSails.cycle(h.getLevel(), head.below(3)) == null, "the lower yard heads a sail");
        h.setBlock(rel.below(), Blocks.STONE);
        h.runAfterDelay(10, () -> {
            h.assertTrue(cloth(h, head) == null, "a stone in the gap did not take the cloth away");
            h.setBlock(rel.below(), Blocks.OAK_FENCE);
        });
        h.runAfterDelay(20, () -> {
            h.assertTrue(expected.equals(cloth(h, head)), "the cloth did not come back with the mast: " + cloth(h, head));
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
        BlockPos helm = squareHull(h, 17, 17, SailTrim.FURLED);
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
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 320, batch = "pirates_n_ships_config_sailing_damping_on_rolling")
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
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 300, batch = "pirates_n_ships_config_sailing_damping_on_at_rest")
    public static void shipAtRestStaysAtRest(GameTestHelper h) {
        ConfigOverrides.during(h, SailingConfig.HULL_DAMPING_ENABLED, true);
        basin(h, true);
        BlockPos helm = squareHull(h, 17, 17, SailTrim.FURLED);
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
