package com.richardsenger.piratesnships.world.structure;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.WallSide;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fences, panes, iron bars and walls of a template connect to their template neighbours; sides facing outside the
 * template keep their value; everything else is untouched. Block-to-same-kind connections that need block tags
 * (fence to fence, wall to wall) are not loaded in JUnit; {@code ConnectionsGameTests} covers them in a server.
 */
class ConnectionsTest {

    @BeforeAll
    static void setUp() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final BlockPos AT = new BlockPos(1, 0, 1);

    private static ConnectionsProcessor.TemplateGetter template(Map<BlockPos, BlockState> blocks) {
        return new ConnectionsProcessor.TemplateGetter(blocks);
    }

    private static Map<BlockPos, BlockState> airAround(BlockState centre) {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (BlockPos p : BlockPos.betweenClosed(AT.offset(-1, -1, -1), AT.offset(1, 1, 1))) blocks.put(p.immutable(), Blocks.AIR.defaultBlockState());
        blocks.put(AT, centre);
        return blocks;
    }

    @Test
    void fenceConnectsToSolidBlocksOnly() {
        BlockState fence = Blocks.SPRUCE_FENCE.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
        Map<BlockPos, BlockState> blocks = airAround(fence);
        blocks.put(AT.north(), Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        blocks.put(AT.east(), Blocks.SPRUCE_PLANKS.defaultBlockState());
        blocks.put(AT.south(), Blocks.CHAIN.defaultBlockState());
        BlockState connected = ConnectionsProcessor.connect(template(blocks), AT, fence);
        assertTrue(connected.getValue(CrossCollisionBlock.NORTH));
        assertTrue(connected.getValue(CrossCollisionBlock.EAST));
        assertFalse(connected.getValue(CrossCollisionBlock.SOUTH));
        assertFalse(connected.getValue(CrossCollisionBlock.WEST), "air");
        assertTrue(connected.getValue(BlockStateProperties.WATERLOGGED), "keeps its water");
    }

    @Test
    void panesAndBarsConnectToEachOtherAndToSolidBlocks() {
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
        Map<BlockPos, BlockState> blocks = airAround(pane);
        blocks.put(AT.west(), Blocks.IRON_BARS.defaultBlockState());
        blocks.put(AT.east(), Blocks.GLASS_PANE.defaultBlockState());
        blocks.put(AT.north(), Blocks.STONE_BRICKS.defaultBlockState());
        BlockState connected = ConnectionsProcessor.connect(template(blocks), AT, pane);
        assertTrue(connected.getValue(CrossCollisionBlock.WEST));
        assertTrue(connected.getValue(CrossCollisionBlock.EAST));
        assertTrue(connected.getValue(CrossCollisionBlock.NORTH));
        assertFalse(connected.getValue(CrossCollisionBlock.SOUTH));
    }

    /** A side towards a position outside the template keeps the template's value (no connection to the terrain). */
    @Test
    void sidesFacingOutsideTheTemplateKeepTheirValue() {
        BlockState bars = Blocks.IRON_BARS.defaultBlockState().setValue(CrossCollisionBlock.SOUTH, true).setValue(CrossCollisionBlock.NORTH, true);
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        blocks.put(AT, bars);
        blocks.put(AT.north(), Blocks.AIR.defaultBlockState());
        blocks.put(AT.east(), Blocks.IRON_BARS.defaultBlockState());
        // south and west: not in the template
        BlockState connected = ConnectionsProcessor.connect(template(blocks), AT, bars);
        assertFalse(connected.getValue(CrossCollisionBlock.NORTH), "air inside the template disconnects");
        assertTrue(connected.getValue(CrossCollisionBlock.EAST));
        assertTrue(connected.getValue(CrossCollisionBlock.SOUTH), "outside: as the template says");
        assertFalse(connected.getValue(CrossCollisionBlock.WEST), "outside: as the template says");
    }

    /** A wall between two solid blocks under air: low sides, no post (it runs straight through). */
    @Test
    void wallRunsLowBetweenSolidBlocks() {
        BlockState wall = Blocks.STONE_BRICK_WALL.defaultBlockState();
        Map<BlockPos, BlockState> blocks = airAround(wall);
        blocks.put(AT.east(), Blocks.STONE_BRICKS.defaultBlockState());
        blocks.put(AT.west(), Blocks.STONE_BRICKS.defaultBlockState());
        BlockState connected = ConnectionsProcessor.connect(template(blocks), AT, wall);
        assertEquals(WallSide.LOW, connected.getValue(WallBlock.EAST_WALL));
        assertEquals(WallSide.LOW, connected.getValue(WallBlock.WEST_WALL));
        assertEquals(WallSide.NONE, connected.getValue(WallBlock.NORTH_WALL));
        assertEquals(WallSide.NONE, connected.getValue(WallBlock.SOUTH_WALL));
        assertFalse(connected.getValue(WallBlock.UP), "straight run, nothing above");
    }

    /** Under a full block the connected sides are tall and a straight tall run has no post. */
    @Test
    void wallUnderAFullBlockIsTall() {
        BlockState wall = Blocks.STONE_BRICK_WALL.defaultBlockState();
        Map<BlockPos, BlockState> blocks = airAround(wall);
        blocks.put(AT.north(), Blocks.STONE_BRICKS.defaultBlockState());
        blocks.put(AT.south(), Blocks.IRON_BARS.defaultBlockState());
        blocks.put(AT.above(), Blocks.STONE_BRICKS.defaultBlockState());
        BlockState connected = ConnectionsProcessor.connect(template(blocks), AT, wall);
        assertEquals(WallSide.TALL, connected.getValue(WallBlock.NORTH_WALL));
        assertEquals(WallSide.TALL, connected.getValue(WallBlock.SOUTH_WALL), "walls connect to iron bars");
        assertEquals(WallSide.NONE, connected.getValue(WallBlock.EAST_WALL));
        assertFalse(connected.getValue(WallBlock.UP));
    }

    /** A wall end (one side connected) and a lone wall raise their post. */
    @Test
    void wallEndsAndLoneWallsHaveAPost() {
        BlockState wall = Blocks.STONE_BRICK_WALL.defaultBlockState().setValue(WallBlock.UP, false);
        Map<BlockPos, BlockState> end = airAround(wall);
        end.put(AT.east(), Blocks.STONE_BRICKS.defaultBlockState());
        BlockState connected = ConnectionsProcessor.connect(template(end), AT, wall);
        assertEquals(WallSide.LOW, connected.getValue(WallBlock.EAST_WALL));
        assertTrue(connected.getValue(WallBlock.UP), "end");
        assertTrue(ConnectionsProcessor.connect(template(airAround(wall)), AT, wall).getValue(WallBlock.UP), "alone");
    }

    @Test
    void otherBlocksAreUntouched() {
        BlockState stairs = Blocks.SPRUCE_STAIRS.defaultBlockState();
        Map<BlockPos, BlockState> blocks = Map.of(AT, stairs, AT.north(), Blocks.SPRUCE_PLANKS.defaultBlockState());
        assertSame(stairs, ConnectionsProcessor.connect(template(blocks), AT, stairs));
        assertEquals(Blocks.AIR.defaultBlockState(), template(blocks).getBlockState(AT.above()));
        assertFalse(template(blocks).contains(AT.above()));
    }
}
