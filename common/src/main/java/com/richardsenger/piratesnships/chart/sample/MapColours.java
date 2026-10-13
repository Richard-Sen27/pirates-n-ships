package com.richardsenger.piratesnships.chart.sample;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;

/**
 * Vanilla's map colouring for one chart cell (CHART4), without world access: the rule of
 * {@code MapItem.update} (1.21.1) for one map pixel of {@code cellBlocks} blocks, so a chart cell shows the colour a
 * vanilla map of the same scale would (roofs, paths, sand, grass, water and wood read as on a vanilla map).
 *
 * <ul>
 *   <li><b>A column</b> ({@link Column}) is its top block with a map colour (vanilla walks down from the
 *       {@code WORLD_SURFACE} heightmap past blocks whose colour is {@code NONE}, such as glass), that block's height,
 *       and for a fluid the water depth below it. A block holding a fluid whose top face is not sturdy (a waterlogged
 *       slab, kelp) shows the fluid ({@link #shownState}).</li>
 *   <li><b>The colour</b> of a cell is the most common colour among its columns (ties: the column read first, like
 *       vanilla's insertion-ordered multiset).</li>
 *   <li><b>The brightness step</b>: for water, from the mean depth ({@code depth * 0.1 + parity * 0.2}: below 0.5
 *       {@code HIGH}, above 0.9 {@code LOW}, else {@code NORMAL}); for anything else, from the mean height against the
 *       cell to the north ({@code (h - hNorth) * 4 / (cellBlocks + 4) + (parity - 0.5) * 0.4}: above 0.6 {@code HIGH},
 *       below -0.6 {@code LOW}, else {@code NORMAL}). {@code parity} is {@code (cx + cz) & 1}, vanilla's dither.</li>
 *   <li><b>Stored</b> as vanilla's packed id ({@link MapColor#getPackedId}: id times four plus the step), 0 for none.</li>
 * </ul>
 */
public final class MapColours {

    /** One column: the {@link MapColor} id of its top coloured block, that block's y, the water depth under it (0 on land) and (CHART5) whether that block is man-made ({@link ChartBuilt}). */
    public record Column(int colourId, int height, int waterDepth, boolean built) {
        /** A column that is not built (CHART4's three values). */
        public Column(int colourId, int height, int waterDepth) {
            this(colourId, height, waterDepth, false);
        }
    }

    private MapColours() {
    }

    /** The state vanilla's map shows for {@code state}: the fluid's own block where the block's top face is not sturdy. */
    public static BlockState shownState(BlockState state, BlockGetter level, BlockPos pos) {
        FluidState fluid = state.getFluidState();
        return !fluid.isEmpty() && !state.isFaceSturdy(level, pos, Direction.UP) ? fluid.createLegacyBlock() : state;
    }

    /**
     * The packed colour of a cell from its {@code n} columns ({@code ids}, {@code heights}, {@code depths}, in reading
     * order), {@code northHeight} the mean height of the cell to the north (NaN when unknown: the step then comes from
     * the dither alone), {@code parity} {@code (cx + cz) & 1}. 0 when there are no columns or the dominant colour is
     * {@code NONE}.
     */
    public static int cellColour(int[] ids, int[] heights, int[] depths, int n, double northHeight, int parity, int cellBlocks) {
        if (n <= 0) return 0;
        int id = dominant(ids, n);
        if (id == MapColor.NONE.id) return 0;
        double height = meanHeight(heights, n);
        int depth = 0;
        for (int i = 0; i < n; i++) depth += depths[i];
        depth /= n;
        MapColor.Brightness b = brightness(id, height, Double.isNaN(northHeight) ? height : northHeight, depth, parity, cellBlocks);
        return MapColor.byId(id).getPackedId(b) & 0xFF;
    }

    /** The most common id among the first {@code n}; ties go to the id read first. */
    public static int dominant(int[] ids, int n) {
        int best = ids[0];
        int bestCount = 0;
        for (int i = 0; i < n; i++) {
            int id = ids[i];
            boolean seen = false;
            for (int j = 0; j < i; j++) {
                if (ids[j] == id) {
                    seen = true;
                    break;
                }
            }
            if (seen) continue;
            int count = 0;
            for (int j = i; j < n; j++) if (ids[j] == id) count++;
            if (count > bestCount) {
                best = id;
                bestCount = count;
            }
        }
        return best;
    }

    /** The mean of the first {@code n} heights. */
    public static double meanHeight(int[] heights, int n) {
        double sum = 0;
        for (int i = 0; i < n; i++) sum += heights[i];
        return n == 0 ? 0 : sum / n;
    }

    /** Vanilla's brightness step of a pixel of colour {@code id} (see the class comment). */
    public static MapColor.Brightness brightness(int id, double height, double northHeight, int waterDepth, int parity, int cellBlocks) {
        if (id == MapColor.WATER.id) {
            double d = waterDepth * 0.1 + parity * 0.2;
            if (d < 0.5) return MapColor.Brightness.HIGH;
            return d > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
        }
        double d = (height - northHeight) * 4.0 / (cellBlocks + 4) + (parity - 0.5) * 0.4;
        if (d > 0.6) return MapColor.Brightness.HIGH;
        return d < -0.6 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
    }

    /** The {@link MapColor} id of a packed colour. */
    public static int id(int packed) {
        return (packed & 0xFF) >>> 2;
    }

    /** The brightness step of a packed colour. */
    public static MapColor.Brightness step(int packed) {
        return MapColor.Brightness.byId(packed & 3);
    }

    /** The opaque ARGB of a packed colour as a vanilla map draws it (0 for none). */
    public static int argb(int packed) {
        if (id(packed) == 0) return 0;
        int abgr = MapColor.getColorFromPackedId(packed);
        return (abgr & 0xFF00FF00) | ((abgr >> 16) & 0xFF) | ((abgr & 0xFF) << 16);
    }
}
