package com.richardsenger.piratesnships.chart;

import com.richardsenger.piratesnships.chart.sample.MapColours;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.MapColor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * CHART4: a cell's colour follows vanilla's map rule ({@code MapItem.update}, 1.21.1) at a scale of 4 blocks per
 * pixel: the most common colour of the cell's columns, its brightness step from the height against the cell to the
 * north (land) or from the water depth (water), with vanilla's checkerboard dither.
 */
class MapColoursTest {

    private static final int CB = 4;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static int id(BlockState state) {
        return MapColours.shownState(state, EmptyBlockGetter.INSTANCE, BlockPos.ZERO).getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).id;
    }

    /** A cell of 16 columns, all of colour {@code id} at {@code height} over {@code depth} water. */
    private static int uniform(int id, int height, int depth, double north, int parity) {
        int[] ids = new int[16];
        int[] heights = new int[16];
        int[] depths = new int[16];
        java.util.Arrays.fill(ids, id);
        java.util.Arrays.fill(heights, height);
        java.util.Arrays.fill(depths, depth);
        return MapColours.cellColour(ids, heights, depths, 16, north, parity, CB);
    }

    @Test
    void columnsTakeVanillasColourIds() {
        assertEquals(MapColor.GRASS.id, id(Blocks.GRASS_BLOCK.defaultBlockState()));
        assertEquals(MapColor.SAND.id, id(Blocks.SAND.defaultBlockState()));
        assertEquals(MapColor.WOOD.id, id(Blocks.OAK_PLANKS.defaultBlockState()));
        assertEquals(MapColor.WATER.id, id(Blocks.WATER.defaultBlockState()));
        assertEquals(MapColor.STONE.id, id(Blocks.STONE.defaultBlockState()));
        assertEquals(MapColor.DIRT.id, id(Blocks.DIRT_PATH.defaultBlockState()), "village paths");
        assertEquals(MapColor.NONE.id, id(Blocks.GLASS.defaultBlockState()), "glass is skipped by the column walk");
        // a block holding water whose top face is not sturdy shows the water; a sturdy one shows itself
        BlockState wetSlab = Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
        assertEquals(MapColor.WATER.id, id(wetSlab), "a waterlogged bottom slab shows water");
        assertEquals(MapColor.WATER.id, id(Blocks.KELP.defaultBlockState()), "kelp shows water");
        assertEquals(MapColor.WOOD.id, id(Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE)
                .setValue(BlockStateProperties.WATERLOGGED, false)));
    }

    /** Flat land is NORMAL on both dither squares; a step of one block up is HIGH on the odd square only; two blocks up is HIGH on both. */
    @Test
    void landBrightnessFollowsTheHeightToTheNorth() {
        int grass = MapColor.GRASS.id;
        for (int parity = 0; parity <= 1; parity++) {
            assertEquals(MapColor.GRASS.getPackedId(MapColor.Brightness.NORMAL) & 0xFF, uniform(grass, 64, 0, 64, parity), "flat, parity " + parity);
            assertEquals(MapColor.GRASS.getPackedId(MapColor.Brightness.HIGH) & 0xFF, uniform(grass, 66, 0, 64, parity), "two up, parity " + parity);
            assertEquals(MapColor.GRASS.getPackedId(MapColor.Brightness.LOW) & 0xFF, uniform(grass, 62, 0, 64, parity), "two down, parity " + parity);
        }
        // one block up: (1 * 4 / 8) + (parity - 0.5) * 0.4 = 0.3 or 0.7
        assertEquals(MapColor.GRASS.getPackedId(MapColor.Brightness.NORMAL) & 0xFF, uniform(grass, 65, 0, 64, 0));
        assertEquals(MapColor.GRASS.getPackedId(MapColor.Brightness.HIGH) & 0xFF, uniform(grass, 65, 0, 64, 1));
        // the north cell unknown: the dither alone, always NORMAL
        assertEquals(MapColor.STONE.getPackedId(MapColor.Brightness.NORMAL) & 0xFF, uniform(MapColor.STONE.id, 90, 0, Double.NaN, 1));
        // sand and planks: their own ids with the same steps
        assertEquals(MapColor.SAND.getPackedId(MapColor.Brightness.HIGH) & 0xFF, uniform(MapColor.SAND.id, 63, 0, 61, 0));
        assertEquals(MapColor.WOOD.getPackedId(MapColor.Brightness.LOW) & 0xFF, uniform(MapColor.WOOD.id, 60, 0, 70, 1));
    }

    /** Water: shallow is HIGH, deep is LOW, between them the dither alternates (vanilla's sea shading). */
    @Test
    void waterBrightnessFollowsTheDepth() {
        int water = MapColor.WATER.id;
        assertEquals(MapColor.WATER.getPackedId(MapColor.Brightness.HIGH) & 0xFF, uniform(water, 62, 2, 62, 0), "2 deep: 0.2");
        assertEquals(MapColor.WATER.getPackedId(MapColor.Brightness.HIGH) & 0xFF, uniform(water, 62, 2, 62, 1), "2 deep: 0.4");
        assertEquals(MapColor.WATER.getPackedId(MapColor.Brightness.NORMAL) & 0xFF, uniform(water, 62, 5, 62, 0), "5 deep: 0.5");
        assertEquals(MapColor.WATER.getPackedId(MapColor.Brightness.LOW) & 0xFF, uniform(water, 62, 8, 62, 1), "8 deep: 1.0");
        assertEquals(MapColor.WATER.getPackedId(MapColor.Brightness.LOW) & 0xFF, uniform(water, 62, 20, 30, 0), "deep sea, height ignored");
    }

    /** The most common colour wins; ties go to the column read first; NONE is no colour. */
    @Test
    void theDominantColourOfTheColumns() {
        int[] ids = {MapColor.GRASS.id, MapColor.WOOD.id, MapColor.WOOD.id, MapColor.DIRT.id};
        int[] heights = {64, 65, 65, 64};
        int[] depths = new int[4];
        assertSame(MapColor.WOOD, MapColor.byId(MapColours.id(MapColours.cellColour(ids, heights, depths, 4, 64.5, 0, CB))));
        int[] tie = {MapColor.SAND.id, MapColor.GRASS.id, MapColor.GRASS.id, MapColor.SAND.id};
        assertEquals(MapColor.SAND.id, MapColours.dominant(tie, 4), "tie: the first read");
        assertEquals(0, MapColours.cellColour(new int[]{0, 0}, new int[]{1, 1}, new int[2], 2, 1, 0, CB), "NONE: no colour");
        assertEquals(0, MapColours.cellColour(new int[0], new int[0], new int[0], 0, 1, 0, CB), "no columns");
        // the water depth is the columns' mean, rounded down like vanilla's
        int[] mixed = {MapColor.WATER.id, MapColor.WATER.id, MapColor.WATER.id, MapColor.SAND.id};
        assertEquals(MapColor.WATER.getPackedId(MapColor.Brightness.NORMAL) & 0xFF,
                MapColours.cellColour(mixed, new int[]{62, 62, 62, 63}, new int[]{7, 7, 7, 0}, 4, 62, 0, CB), "(7+7+7+0)/4 = 5: 0.5");
    }

    @Test
    void packedColoursDrawAsVanillaMaps() {
        int packed = MapColor.WOOD.getPackedId(MapColor.Brightness.HIGH) & 0xFF;
        assertEquals(MapColor.WOOD.id, MapColours.id(packed));
        assertSame(MapColor.Brightness.HIGH, MapColours.step(packed));
        assertEquals(0xFF000000 | MapColor.WOOD.col, MapColours.argb(packed), "HIGH is the colour itself");
        assertEquals(0, MapColours.argb(0));
    }
}
