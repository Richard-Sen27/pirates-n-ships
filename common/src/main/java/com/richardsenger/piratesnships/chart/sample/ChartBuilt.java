package com.richardsenger.piratesnships.chart.sample;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What counts as built on the chart (CHART5): the block tag {@code pirates_n_ships:chart_built} (written by
 * {@link ChartBuiltData}: planks, fences, doors, wool, bricks, cobblestone, glass, terracotta, lanterns, chests,
 * and every block of this mod; never logs, leaves, stone or sand, so a forest stays natural). A cell is built when
 * at least {@code chart.built_columns} of its sampled columns have a built block as their top coloured block.
 */
public final class ChartBuilt {

    public static final TagKey<Block> TAG = TagKey.create(Registries.BLOCK, Constants.id("chart_built"));

    private ChartBuilt() {
    }

    /** Whether the colour-giving block {@code state} of a column is man-made. */
    public static boolean isBuilt(BlockState state) {
        return state.is(TAG);
    }

    /**
     * The built rule of a cell: {@code builtColumns} of its {@code columns} sampled columns are built, against the
     * threshold {@code minBuilt} (capped at the number of columns read, so a cell read through fewer columns can
     * still be built). False when nothing was read.
     */
    public static boolean cellBuilt(int builtColumns, int columns, int minBuilt) {
        return columns > 0 && builtColumns > 0 && builtColumns >= Math.min(Math.max(1, minBuilt), columns);
    }
}
