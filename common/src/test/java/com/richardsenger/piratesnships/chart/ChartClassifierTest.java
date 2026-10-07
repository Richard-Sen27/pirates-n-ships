package com.richardsenger.piratesnships.chart;

import com.richardsenger.piratesnships.chart.data.CellClass;
import com.richardsenger.piratesnships.chart.sample.ChartClassifier;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Block to cell class (MAP1): water by depth, sand and gravel as beach, snow and ice, everything else land. */
class ChartClassifierTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static BlockState air() {
        return Blocks.AIR.defaultBlockState();
    }

    private static CellClass top(Block block, int depth) {
        return ChartClassifier.classify(block.defaultBlockState(), air(), depth, 4);
    }

    @Test
    void waterIsShallowUpToTheDepthLimitThenDeep() {
        assertEquals(CellClass.SHALLOW_WATER, top(Blocks.WATER, 1));
        assertEquals(CellClass.SHALLOW_WATER, top(Blocks.WATER, 4));
        assertEquals(CellClass.DEEP_WATER, top(Blocks.WATER, 5));
        assertEquals(CellClass.DEEP_WATER, top(Blocks.WATER, 40));
        assertEquals(CellClass.DEEP_WATER, ChartClassifier.classify(Blocks.WATER.defaultBlockState(), air(), 3, 2), "the limit is a parameter");
    }

    @Test
    void plantsHoldingWaterCountAsWater() {
        assertEquals(CellClass.DEEP_WATER, top(Blocks.KELP, 12));
        assertEquals(CellClass.SHALLOW_WATER, top(Blocks.SEAGRASS, 2));
        BlockState wetSlab = Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
        assertEquals(CellClass.SHALLOW_WATER, ChartClassifier.classify(wetSlab, air(), 1, 4));
    }

    @Test
    void sandAndGravelAreBeach() {
        for (Block b : new Block[]{Blocks.SAND, Blocks.RED_SAND, Blocks.GRAVEL, Blocks.SANDSTONE, Blocks.SUSPICIOUS_SAND}) {
            assertEquals(CellClass.BEACH, top(b, 0), b.toString());
        }
    }

    @Test
    void snowAndIceIncludingASnowLayerOnTop() {
        for (Block b : new Block[]{Blocks.SNOW_BLOCK, Blocks.ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.POWDER_SNOW}) {
            assertEquals(CellClass.SNOW_ICE, top(b, 0), b.toString());
        }
        // a thin snow layer is not motion blocking: the heightmap stops on the grass below it
        assertEquals(CellClass.SNOW_ICE, ChartClassifier.classify(Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.SNOW.defaultBlockState(), 0, 4));
        // but snow does not freeze open water
        assertEquals(CellClass.DEEP_WATER, ChartClassifier.classify(Blocks.WATER.defaultBlockState(), Blocks.SNOW.defaultBlockState(), 9, 4));
    }

    @Test
    void everythingElseIsLand() {
        for (Block b : new Block[]{Blocks.GRASS_BLOCK, Blocks.OAK_LEAVES, Blocks.STONE, Blocks.DIRT, Blocks.OAK_PLANKS, Blocks.LAVA}) {
            assertEquals(CellClass.LAND, top(b, 0), b.toString());
        }
    }

    @Test
    void airIsUnknown() {
        assertEquals(CellClass.UNKNOWN, top(Blocks.AIR, 0));
        assertEquals(CellClass.UNKNOWN, top(Blocks.VOID_AIR, 0));
    }
}
