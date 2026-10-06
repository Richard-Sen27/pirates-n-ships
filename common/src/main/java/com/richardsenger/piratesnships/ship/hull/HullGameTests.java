package com.richardsenger.piratesnships.ship.hull;

import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.ship.hull.world.HullGridSnapshotter;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;

/** World-adapter GameTests: real blocks → cell kinds → compartments. */
public final class HullGameTests {

    // Hull occupies relative x 1..5, y 0..4, z 1..5; grid (0,0,0) = relative (1,0,1).
    private static final BlockPos MIN = new BlockPos(1, 0, 1);
    private static final BlockPos MAX = new BlockPos(5, 4, 5);

    private HullGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(HullGameTests.class);
    }

    /** Planks box with a slab and a stair in the walls and a closed trapdoor hatch in the roof. */
    private static void buildBox(GameTestHelper helper) {
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        for (int x = 1; x <= 5; x++)
            for (int y = 0; y <= 4; y++)
                for (int z = 1; z <= 5; z++) {
                    boolean shell = x == 1 || x == 5 || y == 0 || y == 4 || z == 1 || z == 5;
                    helper.setBlock(new BlockPos(x, y, z), shell ? planks : Blocks.AIR.defaultBlockState());
                }
        helper.setBlock(new BlockPos(3, 1, 1), Blocks.OAK_SLAB.defaultBlockState());
        helper.setBlock(new BlockPos(1, 2, 3), Blocks.OAK_STAIRS.defaultBlockState());
        helper.setBlock(new BlockPos(3, 4, 3), Blocks.OAK_TRAPDOOR.defaultBlockState());
    }

    private static HullGrid snapshot(GameTestHelper helper) {
        return HullGridSnapshotter.snapshot(helper.getLevel(), helper.absolutePos(MIN), helper.absolutePos(MAX));
    }

    private static CellKind kindAt(HullGrid g, int rx, int ry, int rz) {
        return g.kind(rx - MIN.getX(), ry - MIN.getY(), rz - MIN.getZ());
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void classifiesRealBlocks(GameTestHelper helper) {
        buildBox(helper);
        helper.setBlock(new BlockPos(5, 2, 3), Blocks.OAK_FENCE.defaultBlockState());
        helper.setBlock(new BlockPos(3, 2, 5), Blocks.TORCH.defaultBlockState());
        HullGrid g = snapshot(helper);
        helper.assertTrue(kindAt(g, 2, 0, 2) == CellKind.SOLID, "planks should be solid");
        helper.assertTrue(kindAt(g, 3, 1, 1) == CellKind.SOLID, "slab should be watertight");
        helper.assertTrue(kindAt(g, 1, 2, 3) == CellKind.SOLID, "stair should be watertight");
        helper.assertTrue(kindAt(g, 3, 4, 3) == CellKind.OPENING, "trapdoor should be an opening");
        helper.assertFalse(g.isOpen(2, 4, 2), "closed trapdoor should be closed");
        helper.assertTrue(kindAt(g, 5, 2, 3) == CellKind.AIR, "fence should not be watertight");
        helper.assertTrue(kindAt(g, 3, 2, 5) == CellKind.AIR, "torch should not be watertight");
        helper.assertTrue(kindAt(g, 3, 2, 3) == CellKind.AIR, "interior should be air");

        helper.setBlock(new BlockPos(3, 4, 3), Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, true));
        HullGrid opened = snapshot(helper);
        helper.assertTrue(opened.kind(2, 4, 2) == CellKind.OPENING && opened.isOpen(2, 4, 2), "open trapdoor should be an open opening");
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void analyzesRealHull(GameTestHelper helper) {
        buildBox(helper);
        HullAnalysis closed = HullAnalyzer.analyze(snapshot(helper));
        helper.assertTrue(closed.compartments().size() == 1, "closed box: one compartment, got " + closed.compartments().size());
        Compartment room = closed.compartments().get(0);
        helper.assertTrue(room.volume() == 27, "closed box: 27 interior cells, got " + room.volume());
        helper.assertTrue(room.ports().size() == 1 && !room.ports().get(0).isPourPoint(),
                "closed box: only the roof hatch connects to outside, got " + room.ports());

        // A fence high in a wall is a hole: the top interior layer becomes outside air, the rest a basin below it.
        helper.setBlock(new BlockPos(3, 3, 5), Blocks.OAK_FENCE.defaultBlockState());
        HullAnalysis holed = HullAnalyzer.analyze(snapshot(helper));
        helper.assertTrue(holed.compartments().size() == 1, "holed box: one compartment");
        Compartment basin = holed.compartments().get(0);
        helper.assertTrue(basin.volume() == 18, "holed box: 18 cells below the hole, got " + basin.volume());
        helper.assertTrue(basin.ports().stream().anyMatch(p -> p.isPourPoint() && p.area() == 9),
                "holed box: pour point of area 9 at the hole layer, got " + basin.ports());
        helper.succeed();
    }
}
