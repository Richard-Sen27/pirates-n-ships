package com.richardsenger.piratesnships.ship.template;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntime;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.sailing.wind.WindOverride;
import com.richardsenger.piratesnships.sailing.wind.WindSample;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntime;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntimes;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.assembly.ShipHelm;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.Collection;
import java.util.Locale;

/**
 * The brig's draught and trim (SHIP2): each of the three brig templates, placed and assembled in deep water, settles for
 * 200 ticks and then floats with 3 to 5 hull rows under the surface, within 2 degrees of upright in roll and pitch; and,
 * heeled 12 degrees by hand after settling, she rights herself to within 2 degrees again (the human's brig of 2026-10-13
 * came to rest heeled 25 to 30 degrees: her centre of mass stood so high that only the righting torque held her, and it
 * ran out before she was upright; art/README.md "The brig (SHIP2)" has the mass budget); and on a beam reach under all
 * sail she makes way with her heel within {@code stability.max_heel_degrees}, the gun deck dry, and stands up again
 * once the sails are furled (the SAIL heel bounds of {@code SailingGameTestsHeel}). Every test has its own batch.
 */
public final class BrigTrimGameTests {

    private static final String BATCH = "pirates_n_ships_ship_brig_trim_";
    /** Water y 2..SURFACE: deep enough that a brig drawing 6 rows would still float clear of the floor. */
    private static final int SURFACE = 12;
    private static final int SETTLE = 200;
    private static final int MEASURE_FROM = 160;
    private static final double HEEL_DEGREES = 12.0;
    private static final int RIGHT_TICKS = 240;
    private static final double MAX_ANGLE = 2.0;
    private static final double MIN_ROWS = 3.0, MAX_ROWS = 5.0;

    private BrigTrimGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(BrigTrimGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    /** A 48 × 48 basin: stone floor at y 1, water y 2..SURFACE, no barrier ceiling up to y 48. */
    private static void deepBasin(GameTestHelper h) {
        for (int x = 0; x < 48; x++) {
            for (int z = 0; z < 48; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                boolean wall = x == 0 || x == 47 || z == 0 || z == 47;
                for (int y = 2; y <= SURFACE + 1; y++) {
                    h.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : y <= SURFACE ? Blocks.WATER : Blocks.AIR);
                }
                for (int y = SURFACE + 2; y <= 48; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (h.getBlockState(p).is(Blocks.BARRIER)) h.setBlock(p, Blocks.AIR);
                }
            }
        }
    }

    private static ShipBody brig(GameTestHelper h, ResourceLocation id) {
        deepBasin(h);
        ShipTemplatePlacer.Result r = ShipTemplatePlacer.place(h.getLevel(), id, h.absolutePos(new BlockPos(24, SURFACE + 1, 46)),
                Direction.NORTH, true, true, null);
        AssemblyResult a = r.assembly();
        if (a == null || a.shipId() == null) throw new GameTestAssertException(id + " not placed and assembled: " + r.outcome() + " " + a);
        ShipTestCleanup.track(h, a.shipId());
        ShipBody ship = SableShips.byId(h.getLevel(), a.shipId());
        if (ship == null) throw new GameTestAssertException("no ship body");
        return ship;
    }

    /** The plot cell of template cell {@code t} on a brig placed bow north (assembly only translates). */
    private static BlockPos plot(ShipBody ship, int x, int y, int z) {
        return ShipHelm.steering(ship).offset(new BlockPos(x, y, z).subtract(ShipTemplates.BRIG_HELM));
    }

    /** Roll and pitch [degrees] from world up in the ship (plot) frame; the brig lies bow north, so plot = template axes. */
    static double[] attitude(ShipBody ship) {
        Vector3d up = ship.localUp();
        return new double[] {Math.toDegrees(Math.atan2(up.x, up.y)), Math.toDegrees(Math.atan2(-up.z, up.y))};
    }

    /** Hull rows under the surface amidships: the water's top face over the bottom of the keel at z 19. */
    private static double submergedRows(GameTestHelper h, ShipBody ship) {
        double water = h.absolutePos(new BlockPos(0, SURFACE, 0)).getY() + 1.0;
        double keel = ship.toWorld(Vec3.atLowerCornerOf(plot(ship, 4, 0, 19))).y;
        return water - keel;
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.2f", d);
    }

    // ------------------------------------------------------------------ settling

    private static void floatsUpright(GameTestHelper h, ResourceLocation id) {
        ShipBody ship = brig(h, id);
        double[] max = {0, 0};
        h.onEachTick(() -> {
            if (h.getTick() < MEASURE_FROM || h.getTick() > SETTLE || ship.isRemoved()) return;
            double[] at = attitude(ship);
            max[0] = Math.max(max[0], Math.abs(at[0]));
            max[1] = Math.max(max[1], Math.abs(at[1]));
        });
        h.runAtTickTime(SETTLE + 1, () -> {
            double rows = submergedRows(h, ship);
            Vector3d com = new Vector3d();
            ship.centerOfMass(com);
            BlockPos keel = plot(ship, 4, 0, 19);
            Constants.LOG.info("[SHIP2] {} at rest after {} ticks: {} rows under water, |roll| {}°, |pitch| {}° (ticks {}..{}), "
                            + "mass {} kpg, centre of mass {} rows over the keel bottom, {} across, {} along",
                    id.getPath(), SETTLE, fmt(rows), fmt(max[0]), fmt(max[1]), MEASURE_FROM, SETTLE, fmt(ship.mass()),
                    fmt(com.y - keel.getY()), fmt(com.x - keel.getX() - 0.5), fmt(com.z - keel.getZ()));
            h.assertTrue(rows >= MIN_ROWS && rows <= MAX_ROWS, id.getPath() + ": " + rows + " rows under water, not " + MIN_ROWS + ".." + MAX_ROWS);
            h.assertTrue(max[0] <= MAX_ANGLE && max[1] <= MAX_ANGLE, id.getPath() + ": not upright: roll " + max[0] + "°, pitch " + max[1] + "°");
            h.succeed();
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = SETTLE + 40, batch = BATCH + "rest_brig")
    public static void theBrigFloatsUprightAtHerDraught(GameTestHelper h) {
        floatsUpright(h, ShipTemplates.BRIG_ID);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = SETTLE + 40, batch = BATCH + "rest_navy")
    public static void theNavyBrigFloatsUprightAtHerDraught(GameTestHelper h) {
        floatsUpright(h, ShipTemplates.NAVY_BRIG_ARMED_ID);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = SETTLE + 40, batch = BATCH + "rest_pirate")
    public static void thePirateBrigFloatsUprightAtHerDraught(GameTestHelper h) {
        floatsUpright(h, ShipTemplates.PIRATE_BRIG_ARMED_ID);
    }

    // ------------------------------------------------------------------ heeled by hand

    private static void rightsHerself(GameTestHelper h, ResourceLocation id, double heelDegrees) {
        ShipBody ship = brig(h, id);
        int heelAt = SETTLE / 2;
        double[] worst = {0};
        StringBuilder trace = new StringBuilder();
        h.onEachTick(() -> {
            long t = h.getTick();
            if (ship.isRemoved()) return;
            if (t == heelAt) {
                Quaterniond q = new Quaterniond().rotateZ(Math.toRadians(heelDegrees)).mul(ship.orientation());
                ship.setOrientation(q);
            }
            if (t > heelAt && (t - heelAt) % 20 == 0) trace.append(' ').append(fmt(attitude(ship)[0]));
            if (t >= heelAt + RIGHT_TICKS - 40 && t <= heelAt + RIGHT_TICKS) {
                worst[0] = Math.max(worst[0], Math.abs(attitude(ship)[0]));
            }
        });
        h.runAtTickTime(heelAt + RIGHT_TICKS + 1, () -> {
            Constants.LOG.info("[SHIP2] {} heeled {}° by hand: roll every 20 ticks [°]:{}; |roll| at most {}° over the last 40 ticks",
                    id.getPath(), fmt(heelDegrees), trace, fmt(worst[0]));
            h.assertTrue(worst[0] <= MAX_ANGLE, id.getPath() + ": does not right herself from " + heelDegrees + "°: roll " + worst[0]
                    + "° after " + RIGHT_TICKS + " ticks; trace" + trace);
            h.succeed();
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = SETTLE / 2 + RIGHT_TICKS + 40, batch = BATCH + "heel_brig")
    public static void theBrigRightsHerselfFromAHeel(GameTestHelper h) {
        rightsHerself(h, ShipTemplates.BRIG_ID, HEEL_DEGREES);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = SETTLE / 2 + RIGHT_TICKS + 40, batch = BATCH + "heel_navy")
    public static void theNavyBrigRightsHerselfFromAHeel(GameTestHelper h) {
        rightsHerself(h, ShipTemplates.NAVY_BRIG_ARMED_ID, -HEEL_DEGREES);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = SETTLE / 2 + RIGHT_TICKS + 40, batch = BATCH + "heel_pirate")
    public static void thePirateBrigRightsHerselfFromAHeel(GameTestHelper h) {
        rightsHerself(h, ShipTemplates.PIRATE_BRIG_ARMED_ID, HEEL_DEGREES);
    }

    // ------------------------------------------------------------------ on a reach

    /**
     * The armed brig in the 160 basin under all sail with a moderate wind on the beam (from starboard) for 400 ticks:
     * she makes way, her heel stays within {@code stability.max_heel_degrees} and under 10 degrees once steady, no water
     * comes into the hull (the lee gun ports stay out of the sea); furled, she is back within 2 degrees of upright in 5 s.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_160_DEEP, timeoutTicks = 800, batch = BATCH + "reach")
    public static void theBrigSailsOnABeamReachWithinTheHeelBounds(GameTestHelper h) {
        int seaTop = 13;
        for (int x = 0; x < 160; x++) {
            for (int z = 0; z < 160; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                boolean wall = x == 0 || x == 159 || z == 0 || z == 159;
                for (int y = 2; y <= seaTop + 1; y++) {
                    h.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : y <= seaTop ? Blocks.WATER : Blocks.AIR);
                }
            }
        }
        ShipTemplatePlacer.Result r = ShipTemplatePlacer.place(h.getLevel(), ShipTemplates.NAVY_BRIG_ARMED_ID,
                h.absolutePos(new BlockPos(30, seaTop + 1, 140)), Direction.NORTH, true, true, null);
        AssemblyResult a = r.assembly();
        if (a == null || a.shipId() == null) throw new GameTestAssertException("not assembled: " + r.outcome() + " " + a);
        ShipTestCleanup.track(h, a.shipId());
        ShipBody ship = SableShips.byId(h.getLevel(), a.shipId());
        String dim = h.getLevel().dimension().location().toString();
        int setAt = 60, furlAt = setAt + 400, steadyFrom = furlAt - 100;
        double[] heel = {0, 0, 0}; // max |roll| under sail, max |roll| when steady, |roll| at the end
        double[] speed = {0};
        int[] righted = {-1};
        StringBuilder trace = new StringBuilder();
        h.onEachTick(() -> {
            long t = h.getTick();
            SailingRuntime rt = SailingRuntimes.getOrCreate(ship);
            if (rt == null || ship.isRemoved()) return;
            double hd = rt.headingDegrees(ship);
            if (t >= setAt && t < furlAt) WindOverride.set(dim, WindSample.normalizeDegrees(hd + 90.0), 6.0, h.getLevel().getGameTime() + 40);
            if (t == setAt) for (BlockPos p : rt.sailPositions()) SailingRuntimes.setTrim(h.getLevel(), p, SailTrim.FULL);
            if (t == furlAt) {
                for (BlockPos p : rt.sailPositions()) SailingRuntimes.setTrim(h.getLevel(), p, SailTrim.FURLED);
                speed[0] = rt.shipFrameVelocity(ship, new Vector3d()).length();
                WindOverride.clear(dim);
            }
            double roll = Math.abs(attitude(ship)[0]);
            if (t > setAt && t <= furlAt) {
                heel[0] = Math.max(heel[0], roll);
                if (t >= steadyFrom) heel[1] = Math.max(heel[1], roll);
                if ((t - setAt) % 40 == 0) trace.append(' ').append(fmt(attitude(ship)[0]));
            }
            if (t > furlAt && righted[0] < 0 && roll < MAX_ANGLE) righted[0] = (int) (t - furlAt);
        });
        h.runAtTickTime(furlAt + 101, () -> {
            HullRuntime hull = HullRuntimes.get(h.getLevel(), ship.id());
            double water = hull == null ? -1 : hull.simulation().totalVolume();
            double maxHeel = SailingConfig.MAX_HEEL_DEGREES.get();
            Constants.LOG.info("[SHIP2] armed brig on a beam reach (wind 6): {} blocks/s, heel at most {}° ({}° steady), roll every 40 ticks [°]:{}; "
                    + "upright {} ticks after furling; water inside {}", fmt(speed[0]), fmt(heel[0]), fmt(heel[1]), trace, righted[0], fmt(water));
            h.assertTrue(speed[0] > 0.3, "she makes no way on the reach: " + speed[0] + " blocks/s");
            h.assertTrue(heel[0] <= maxHeel, "heeled " + heel[0] + "°, past max_heel_degrees " + maxHeel);
            h.assertTrue(heel[1] <= 10.0, "steady heel " + heel[1] + "°");
            h.assertTrue(water == 0.0, "water in the hull: " + water);
            h.assertTrue(righted[0] >= 0, "not back within 2° of upright 5 s after furling");
            h.succeed();
        });
    }
}
