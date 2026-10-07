package com.richardsenger.piratesnships.world.wreck;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fences, panes and iron bars of a template connect to their template neighbours; everything else is untouched. */
class WreckConnectionsTest {

    @BeforeAll
    static void setUp() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final BlockPos AT = new BlockPos(1, 0, 1);

    /** Fence-to-fence needs the block tags, which JUnit does not load; the stern's GameTest covers it in a server. */
    @Test
    void fenceConnectsToSolidBlocksOnly() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        BlockState fence = Blocks.SPRUCE_FENCE.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
        blocks.put(AT, fence);
        blocks.put(AT.north(), Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        blocks.put(AT.east(), Blocks.SPRUCE_PLANKS.defaultBlockState());
        blocks.put(AT.south(), Blocks.CHAIN.defaultBlockState());
        // west: nothing in the template (water)
        BlockState connected = WreckConnectionsProcessor.connect(new WreckConnectionsProcessor.TemplateGetter(blocks), AT, fence);
        assertTrue(connected.getValue(CrossCollisionBlock.NORTH));
        assertTrue(connected.getValue(CrossCollisionBlock.EAST));
        assertFalse(connected.getValue(CrossCollisionBlock.SOUTH));
        assertFalse(connected.getValue(CrossCollisionBlock.WEST));
        assertTrue(connected.getValue(BlockStateProperties.WATERLOGGED), "keeps its water");
    }

    @Test
    void panesAndBarsConnectToEachOther() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
        blocks.put(AT, pane);
        blocks.put(AT.west(), Blocks.IRON_BARS.defaultBlockState());
        blocks.put(AT.east(), Blocks.GLASS_PANE.defaultBlockState());
        BlockState connected = WreckConnectionsProcessor.connect(new WreckConnectionsProcessor.TemplateGetter(blocks), AT, pane);
        assertTrue(connected.getValue(CrossCollisionBlock.WEST));
        assertTrue(connected.getValue(CrossCollisionBlock.EAST));
        assertFalse(connected.getValue(CrossCollisionBlock.NORTH));
        assertFalse(connected.getValue(CrossCollisionBlock.SOUTH));
    }

    @Test
    void otherBlocksAreUntouched() {
        BlockState stairs = Blocks.SPRUCE_STAIRS.defaultBlockState();
        Map<BlockPos, BlockState> blocks = Map.of(AT, stairs, AT.north(), Blocks.SPRUCE_PLANKS.defaultBlockState());
        assertSame(stairs, WreckConnectionsProcessor.connect(new WreckConnectionsProcessor.TemplateGetter(blocks), AT, stairs));
        assertEquals(Blocks.WATER.defaultBlockState(), new WreckConnectionsProcessor.TemplateGetter(blocks).getBlockState(AT.above()));
    }
}
