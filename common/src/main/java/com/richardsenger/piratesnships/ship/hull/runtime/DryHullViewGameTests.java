package com.richardsenger.piratesnships.ship.hull.runtime;

import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.ship.hull.HullTags;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.WaterRegions;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;

/**
 * Dry hull view (HV1, docs/design.md §4.4): a camera in a deck opening counts as inside the ship, and the water plants
 * that the client hides inside dry regions are tagged. Fixtures from {@link DryHullGameTests} (5×4×5 plank hull, hold
 * y 6..7, deck y 8, in a basin with water up to y 7).
 */
public final class DryHullViewGameTests {

    private DryHullViewGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(DryHullViewGameTests.class);
    }

    private static Vec3 worldAt(DryHullGameTests.Fixture f, BlockPos plot, double ox, double oy, double oz) {
        return f.ship().toWorld(new Vec3(plot.getX() + ox, plot.getY() + oy, plot.getZ() + oz));
    }

    private static String where(DryHullGameTests.Fixture f, ServerLevel level, BlockPos plot) {
        Vec3 w = worldAt(f, plot, 0.5, 0.5, 0.5);
        BlockPos b = BlockPos.containing(w);
        return plot + " (world " + w + ", fluid " + level.getFluidState(b).getType() + " h" + level.getFluidState(b).getHeight(level, b)
                + ", in region " + f.runtime().inRegion(plot) + ", sea " + f.runtime().seaShipFrame() + ")";
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void openDeckHatchIsInsideTheShip(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        DryHullGameTests.Fixture f = DryHullGameTests.assemble(h, DryHullGameTests.hull(h, 9, true));
        ServerLevel level = h.getLevel();
        BlockPos hatch = f.hold(-1, -1, -1), sky = hatch.above(), below = hatch.below();
        h.assertTrue(level.getBlockState(hatch).is(Blocks.OAK_TRAPDOOR), "no hatch in the plot at " + hatch);
        h.runAfterDelay(5, () -> level.setBlock(hatch, level.getBlockState(hatch).setValue(TrapDoorBlock.OPEN, true), Block.UPDATE_ALL));
        h.runAfterDelay(10, () -> h.succeedWhen(() -> {
            h.assertTrue(f.runtime().regionCount() == 1, "no dry region yet");
            h.assertTrue(f.runtime().inRegion(below), "the hold under the hatch is not dry: " + where(f, level, below));
            h.assertTrue(f.runtime().inRegion(hatch), "the open deck hatch is not in the dry region: " + where(f, level, hatch));
            for (double y : new double[] {0.05, 0.5, 0.95}) {
                h.assertTrue(WaterRegions.isOccluded(level, worldAt(f, hatch, 0.5, y, 0.5)),
                        "a camera at height " + y + " in the hatch is not occluded: " + where(f, level, hatch));
            }
            h.assertFalse(f.runtime().inRegion(sky), "the air above the deck is in the region");
            // outside the hull: the basin water beside the west wall at hold height stays water
            BlockPos besideWall = f.hold(-3, -2, 0);
            h.assertFalse(WaterRegions.isOccluded(level, worldAt(f, besideWall, 0.5, 0.5, 0.5)),
                    "the sea beside the hull is occluded: " + where(f, level, besideWall));
            SableShips.remove(f.ship());
        }));
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void trapdoorUnderADeckOpeningIsInsideTheShip(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        BlockPos helm = DryHullGameTests.hull(h, 9, false);
        // a deck opening at (12, 8, 12) closed from below by an open trapdoor hung under the deck at (12, 7, 12)
        h.setBlock(new BlockPos(12, 8, 12), Blocks.AIR);
        h.setBlock(new BlockPos(12, 7, 12), Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.HALF, Half.TOP).setValue(TrapDoorBlock.OPEN, true));
        DryHullGameTests.Fixture f = DryHullGameTests.assemble(h, helm);
        ServerLevel level = h.getLevel();
        BlockPos trapdoor = f.hold(1, -2, 1), opening = trapdoor.above();
        h.assertTrue(level.getBlockState(trapdoor).is(Blocks.SPRUCE_TRAPDOOR), "no trapdoor in the plot at " + trapdoor);
        h.succeedWhen(() -> {
            h.assertTrue(f.runtime().regionCount() >= 1, "no dry region yet");
            h.assertTrue(f.runtime().inRegion(trapdoor), "the open trapdoor is not in the dry region: " + where(f, level, trapdoor));
            h.assertTrue(WaterRegions.isOccluded(level, worldAt(f, trapdoor, 0.5, 0.5, 0.5)), "a camera in the trapdoor cell is not occluded");
            h.assertTrue(f.runtime().inRegion(opening), "the deck opening above the trapdoor is not in the dry region: " + where(f, level, opening));
            h.assertFalse(f.runtime().inRegion(opening.above()), "the air above the deck is in the region");
            SableShips.remove(f.ship());
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_3)
    public static void waterPlantsAreHiddenInDryHulls(GameTestHelper h) {
        for (Block b : new Block[] {Blocks.SEAGRASS, Blocks.TALL_SEAGRASS, Blocks.KELP, Blocks.KELP_PLANT, Blocks.SEA_PICKLE,
                Blocks.BUBBLE_COLUMN}) {
            h.assertTrue(b.defaultBlockState().is(HullTags.HIDDEN_IN_DRY_HULL), b + " is not in #hidden_in_dry_hull");
        }
        for (Block b : new Block[] {Blocks.WATER, Blocks.OAK_PLANKS, Blocks.OAK_TRAPDOOR, Blocks.TUBE_CORAL}) {
            h.assertFalse(b.defaultBlockState().is(HullTags.HIDDEN_IN_DRY_HULL), b + " is in #hidden_in_dry_hull");
        }
        h.succeed();
    }
}
