package com.richardsenger.piratesnships.sailing.ballast;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.force.ShipFrame;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.ship.decor.ShipDecor;
import com.richardsenger.piratesnships.trade.cargo.CargoContainerBlockEntity;
import com.richardsenger.piratesnships.trade.cargo.CargoLoad;
import com.richardsenger.piratesnships.trade.cargo.CargoMass;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * GameTests of the shipwright's ballast (TRM1, docs/design.md §5.2): the 7×17 test hull of the damping tests
 * ({@link SailingGameTestsShips#longHull}, bow +Z, 40×40 basin with the sea surface at y=8) with something heavy built
 * on it. Pitch is the bow's angle above the horizon (+ = bow up = stern down), roll the port side's (+ = port up).
 * Angles are means over a window after the hull has settled.
 */
public final class BallastGameTests {

    /** Window over which a settled hull is measured [ticks]. */
    private static final int FROM = 200, TO = 300;
    private static final String OFF_BATCH = "pirates_n_ships_config_ballast_off";

    private BallastGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(BallastGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    /**
     * A three-storey stern castle across the whole beam over the last three rows of the long hull (x 16..22, z 10..12,
     * y 9..17): walls on the sides and ends, floors at y=11 and 14, a roof at y=17. 80 kpg of planks on the 181 kpg
     * hull puts the centre of mass about 2.1 blocks aft of the centre of buoyancy.
     */
    private static void sternCastle(GameTestHelper h) {
        for (int x = 16; x <= 22; x++) {
            for (int z = 10; z <= 12; z++) {
                for (int y = 9; y <= 17; y++) {
                    boolean wall = x == 16 || x == 22 || z == 10 || z == 12;
                    boolean floor = (y - 8) % 3 == 0;
                    h.setBlock(new BlockPos(x, y, z), wall || floor ? Blocks.OAK_PLANKS : Blocks.AIR);
                }
            }
        }
    }

    private static SailingGameTestsShips.Fixture floatLongHull(GameTestHelper h, Runnable build) {
        SailingGameTestsShips.basin(h, true);
        BlockPos helm = SailingGameTestsShips.longHull(h);
        build.run();
        return SailingGameTestsShips.assemble(h, helm);
    }

    /** The bow's angle above the horizon [degrees]: + = bow up, stern down. */
    static double pitchDegrees(SailingGameTestsShips.Fixture f) {
        Quaterniond q = f.runtime().bow().shipToWorld(f.ship().orientation(new Quaterniond()), new Quaterniond());
        Vector3d fwd = q.transform(new Vector3d(ShipFrame.FORWARD));
        return Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, fwd.y))));
    }

    /** The port side's angle above the horizon [degrees]: + = port side up. */
    static double rollDegrees(SailingGameTestsShips.Fixture f) {
        Quaterniond q = f.runtime().bow().shipToWorld(f.ship().orientation(new Quaterniond()), new Quaterniond());
        Vector3d port = q.transform(new Vector3d(ShipFrame.PORT));
        return Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, port.y))));
    }

    /** Sums pitch and roll over ticks [from, to): {Σ pitch, Σ roll, n, min pitch, max pitch}. */
    private static double[] watch(GameTestHelper h, SailingGameTestsShips.Fixture f, long from, long to) {
        double[] acc = {0, 0, 0, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t >= from && t < to && !f.ship().isRemoved()) {
                double p = pitchDegrees(f);
                acc[0] += p;
                acc[1] += rollDegrees(f);
                acc[2]++;
                acc[3] = Math.min(acc[3], p);
                acc[4] = Math.max(acc[4], p);
            }
        });
        return acc;
    }

    private static double meanPitch(double[] acc) {
        return acc[2] == 0 ? Double.NaN : acc[0] / acc[2];
    }

    private static double meanRoll(double[] acc) {
        return acc[2] == 0 ? Double.NaN : acc[1] / acc[2];
    }

    /** Plot position of a test-relative position of the long hull (through its helm at (19, 9, 18)). */
    private static BlockPos plot(SailingGameTestsShips.Fixture f, int x, int y, int z) {
        BlockPos helm = f.runtime().helm();
        if (helm == null) {
            throw new AssertionError("the long hull lost its helm");
        }
        return helm.offset(x - 19, y - 9, z - 18);
    }

    private static String fmt(double v) {
        return String.format("%.2f", v);
    }

    // ------------------------------------------------------------------ level by design

    /** TRM1 (1): the stern-castle hull rests within 1° of level in pitch and roll with the ballast on. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = TO + 20)
    public static void sternCastleRestsLevel(GameTestHelper h) {
        SailingGameTestsShips.Fixture f = floatLongHull(h, () -> sternCastle(h));
        double[] acc = watch(h, f, FROM, TO);
        h.runAfterDelay(TO, () -> {
            BallastRule.Result r = ShipBallast.result(h.getLevel(), f.ship().id());
            double pitch = meanPitch(acc), roll = meanRoll(acc);
            Constants.LOG.info("[ballast test] stern castle, ballast on: pitch {} deg (range {}..{}), roll {} deg, ballast {}",
                    fmt(pitch), fmt(acc[3]), fmt(acc[4]), fmt(roll), r);
            h.assertTrue(r != null && !r.capped() && r.torqueX() > 0.0, "no bow-down ballast for the stern castle: " + r);
            h.assertTrue(Math.abs(pitch) < 1.0, "the stern-castle hull rests " + pitch + " deg out of level in pitch (+ = stern down)");
            h.assertTrue(Math.abs(roll) < 1.0, "the stern-castle hull rests " + roll + " deg out of level in roll");
            h.succeed();
        });
    }

    /** TRM1 (1), control: the same hull without the ballast sits clearly stern down. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = TO + 20, batch = OFF_BATCH)
    public static void sternCastleSitsSternDownWithoutBallast(GameTestHelper h) {
        ConfigOverrides.during(h, SailingConfig.AUTO_BALLAST, false);
        SailingGameTestsShips.Fixture f = floatLongHull(h, () -> sternCastle(h));
        double[] acc = watch(h, f, FROM, TO);
        h.runAfterDelay(TO, () -> {
            BallastRule.Result r = ShipBallast.result(h.getLevel(), f.ship().id());
            double pitch = meanPitch(acc);
            Constants.LOG.info("[ballast test] stern castle, ballast off: pitch {} deg (range {}..{}), roll {} deg, ballast {}",
                    fmt(pitch), fmt(acc[3]), fmt(acc[4]), fmt(meanRoll(acc)), r);
            h.assertTrue(r == null || r.torqueMagnitude() == 0.0, "a ballast while auto_ballast is off: " + r);
            h.assertTrue(pitch > 2.0, "without the ballast the stern-castle hull rests only " + pitch + " deg stern down");
            h.succeed();
        });
    }

    /**
     * TRM1 (2): a row of cobblestone along the starboard deck edge (x=22, z 11..25: 30 kpg, 0.4 blocks of offset) is
     * ballasted out: the hull rests within 1° in roll.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = TO + 20)
    public static void heavySideRestsLevelInRoll(GameTestHelper h) {
        SailingGameTestsShips.Fixture f = floatLongHull(h, () -> {
            for (int z = 11; z <= 25; z++) {
                h.setBlock(new BlockPos(22, 9, z), Blocks.COBBLESTONE);
            }
        });
        double[] acc = watch(h, f, FROM, TO);
        h.runAfterDelay(TO, () -> {
            BallastRule.Result r = ShipBallast.result(h.getLevel(), f.ship().id());
            double pitch = meanPitch(acc), roll = meanRoll(acc);
            Constants.LOG.info("[ballast test] heavy side: roll {} deg, pitch {} deg, ballast {}", fmt(roll), fmt(pitch), r);
            h.assertTrue(r != null && Math.abs(r.torqueZ()) > Math.abs(r.torqueX()), "no roll ballast for the heavy side: " + r);
            h.assertTrue(Math.abs(roll) < 1.0, "the heavy-side hull rests " + roll + " deg out of level in roll");
            h.assertTrue(Math.abs(pitch) < 1.0, "the heavy-side hull rests " + pitch + " deg out of level in pitch");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ recompute

    private static final int ADD_AT = 100, ADDED_FROM = 180, REMOVE_AT = 240, REMOVED_FROM = 320, RECOMPUTE_END = 380;

    /**
     * TRM1 (4): three iron blocks set on the after deck of the floating hull are blocks added to the ship: the ballast is
     * worked out again (a bow-down torque about 132 N × the lever to the centre of buoyancy larger) and the hull comes
     * back to level; taking them off restores the first ballast and the hull stays level. A change of state of a block
     * that weighs the same (a trapdoor opened and shut) works nothing out again.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = RECOMPUTE_END + 20)
    public static void blocksAddedAndRemovedAreBallastedAgain(GameTestHelper h) {
        SailingGameTestsShips.Fixture f = floatLongHull(h, () -> h.setBlock(new BlockPos(17, 9, 24), Blocks.OAK_TRAPDOOR));
        ServerLevel level = h.getLevel();
        double[] first = new double[2];
        int[] count = new int[2];
        double[] transient_ = {Double.NEGATIVE_INFINITY};
        h.runAfterDelay(ADD_AT - 10, () -> {
            // a state change of the same block, same mass: no recompute
            count[0] = ShipBallast.computations(level, f.ship().id());
            BlockPos door = plot(f, 17, 9, 24);
            level.setBlock(door, level.getBlockState(door).cycle(net.minecraft.world.level.block.TrapDoorBlock.OPEN), Block.UPDATE_ALL);
        });
        h.runAfterDelay(ADD_AT - 5, () -> {
            // and shut again, so the ballast after removing the iron compares with the one worked out with it shut
            BlockPos door = plot(f, 17, 9, 24);
            level.setBlock(door, level.getBlockState(door).cycle(net.minecraft.world.level.block.TrapDoorBlock.OPEN), Block.UPDATE_ALL);
        });
        h.runAfterDelay(ADD_AT, () -> {
            BallastRule.Result r = ShipBallast.result(level, f.ship().id());
            first[0] = r == null ? Double.NaN : r.torqueX();
            first[1] = r == null ? Double.NaN : r.torqueZ();
            count[1] = ShipBallast.computations(level, f.ship().id());
            for (int x = 18; x <= 20; x++) {
                level.setBlock(plot(f, x, 9, 11), Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            }
        });
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t > ADD_AT && t < ADD_AT + 40 && !f.ship().isRemoved()) {
                transient_[0] = Math.max(transient_[0], pitchDegrees(f));
            }
        });
        double[] added = watch(h, f, ADDED_FROM, REMOVE_AT);
        double[] withIron = new double[3];
        h.runAfterDelay(REMOVE_AT, () -> {
            BallastRule.Result r = ShipBallast.result(level, f.ship().id());
            withIron[0] = r == null ? Double.NaN : r.torqueX();
            withIron[1] = r == null ? Double.NaN : r.cob()[2] - (plot(f, 19, 9, 11).getZ() + 0.5 - f.ship().plotBounds()[0].getZ());
            withIron[2] = ShipBallast.computations(level, f.ship().id());
            for (int x = 18; x <= 20; x++) {
                level.setBlock(plot(f, x, 9, 11), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        });
        double[] removed = watch(h, f, REMOVED_FROM, RECOMPUTE_END);
        h.runAfterDelay(RECOMPUTE_END, () -> {
            BallastRule.Result r = ShipBallast.result(level, f.ship().id());
            double gain = withIron[0] - first[0];
            // three iron blocks: 3 × 4 kpg × 11, at the lever between their row and the centre of buoyancy
            double expected = 12.0 * 11.0 * withIron[1];
            Constants.LOG.info("[ballast test] recompute: torque x {} -> {} (expected gain {}, got {}), back to {}; pitch with iron {} deg "
                            + "(right after adding up to {}), after removing {} deg; computations {} / {} / {} / {}",
                    fmt(first[0]), fmt(withIron[0]), fmt(expected), fmt(gain), r == null ? "-" : fmt(r.torqueX()),
                    fmt(meanPitch(added)), fmt(transient_[0]), fmt(meanPitch(removed)), count[0], count[1], (int) withIron[2],
                    ShipBallast.computations(level, f.ship().id()));
            h.assertTrue(count[1] == count[0], "a trapdoor opening worked the ballast out again: " + count[0] + " -> " + count[1]);
            h.assertTrue(withIron[2] > count[1], "adding blocks did not work the ballast out again");
            h.assertTrue(gain > 0.8 * expected && gain < 1.2 * expected, "the ballast grew by " + gain + ", expected about " + expected);
            h.assertTrue(r != null && Math.abs(r.torqueX() - first[0]) < 1e-6 * Math.max(1.0, Math.abs(first[0]))
                    && Math.abs(r.torqueZ() - first[1]) < 1e-6 * Math.max(1.0, Math.abs(first[1])),
                    "removing the blocks did not restore the ballast: " + first[0] + ", " + first[1] + " -> " + r);
            h.assertTrue(Math.abs(meanPitch(added)) < 1.0, "with the iron on deck the hull rests " + meanPitch(added) + " deg out of level");
            h.assertTrue(Math.abs(meanPitch(removed)) < 1.0, "after removing the iron the hull rests " + meanPitch(removed) + " deg out of level");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ cargo and the cap

    private static final int FILL_AT = 120;
    private static final int TOWER = 20;

    /**
     * Cargo still trims: a cargo crate in the after hold is counted empty, and filling it with iron (a {@code load}
     * state change, 20.5 kpg more in Sable) works nothing out again and puts the stern down.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = TO + 20)
    public static void cargoStillTrimsABallastedShip(GameTestHelper h) {
        SailingGameTestsShips.Fixture f = floatLongHull(h, () -> h.setBlock(new BlockPos(19, 6, 11), ShipDecor.CARGO_CRATE.get()));
        ServerLevel level = h.getLevel();
        double[] before = watch(h, f, FILL_AT - 40, FILL_AT);
        double[] torque = new double[1];
        int[] count = new int[1];
        h.runAfterDelay(FILL_AT, () -> {
            BallastRule.Result r = ShipBallast.result(level, f.ship().id());
            torque[0] = r == null ? Double.NaN : r.torqueX();
            count[0] = ShipBallast.computations(level, f.ship().id());
            CargoContainerBlockEntity crate = (CargoContainerBlockEntity) level.getBlockEntity(plot(f, 19, 6, 11));
            int ingots = 1024;
            while (ingots > 0) {
                int in = crate.insert(new ItemStack(Items.IRON_INGOT, Math.min(64, ingots)));
                if (in <= 0) throw new AssertionError("the crate refused iron");
                ingots -= in;
            }
        });
        double[] after = watch(h, f, FROM, TO);
        h.runAfterDelay(TO, () -> {
            BallastRule.Result r = ShipBallast.result(level, f.ship().id());
            int load = level.getBlockState(plot(f, 19, 6, 11)).getValue(CargoLoad.LOAD);
            double trim = meanPitch(after) - meanPitch(before);
            Constants.LOG.info("[ballast test] cargo: pitch {} deg empty, {} deg with a full crate (load {}, +{} kpg), trim {} deg; ballast {}",
                    fmt(meanPitch(before)), fmt(meanPitch(after)), load, fmt(CargoMass.CRATE.fullExtraMass()), fmt(trim), r);
            h.assertValueEqual(load, CargoMass.MAX_LOAD, "crate load");
            h.assertTrue(ShipBallast.computations(level, f.ship().id()) == count[0], "the crate's load worked the ballast out again");
            h.assertTrue(r != null && r.torqueX() == torque[0], "the crate's load changed the ballast: " + torque[0] + " -> " + r);
            h.assertTrue(Math.abs(meanPitch(before)) < 1.0, "the empty hull rests " + meanPitch(before) + " deg out of level");
            h.assertTrue(trim > 1.0, "a full crate aft trims the ballasted hull only " + trim + " deg stern down");
            h.succeed();
        });
    }

    /**
     * TRM1 (5): an absurd build still lists: a one-block-wide tower of twenty iron blocks (80 kpg) on a plank boom six
     * blocks aft of the transom (x=19, z 4..9) puts the centre of mass 4.3 blocks aft of the centre of buoyancy, more
     * than the default cap of 3 can make good, so the ballast is capped and the hull rests clearly stern down (measured
     * 7.65°). Fourteen blocks (3.34 blocks of offset) were capped too, but the small remainder showed mostly as an
     * artefact of the hull runtime's dry lift: about 4.9° stern down until a re-analysis at the 5° tilt threshold moved
     * the dry lift aft, then 0.2° (docs/design.md §5.2 SH1, "the dry lift at a fixed centroid between re-analyses").
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = TO + 20)
    public static void towerAftHitsTheCapAndStillLists(GameTestHelper h) {
        SailingGameTestsShips.Fixture f = floatLongHull(h, () -> {
            for (int z = 4; z <= 9; z++) {
                h.setBlock(new BlockPos(19, 8, z), Blocks.OAK_PLANKS);
            }
            for (int y = 9; y < 9 + TOWER; y++) {
                h.setBlock(new BlockPos(19, y, 4), Blocks.IRON_BLOCK);
            }
        });
        double[] acc = watch(h, f, FROM, TO);
        h.runAfterDelay(TO, () -> {
            BallastRule.Result r = ShipBallast.result(h.getLevel(), f.ship().id());
            double pitch = meanPitch(acc);
            Constants.LOG.info("[ballast test] iron tower aft: pitch {} deg (range {}..{}), roll {} deg, offset {} blocks, ballast {}",
                    fmt(pitch), fmt(acc[3]), fmt(acc[4]), fmt(meanRoll(acc)), r == null ? "-" : fmt(r.offset()), r);
            h.assertTrue(r != null && r.capped(), "the iron tower did not reach the cap: " + r);
            h.assertTrue(pitch > 1.0, "the capped hull rests only " + pitch + " deg stern down");
            h.succeed();
        });
    }
}
