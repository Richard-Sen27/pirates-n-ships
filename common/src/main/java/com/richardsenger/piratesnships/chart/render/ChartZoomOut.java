package com.richardsenger.piratesnships.chart.render;

import com.richardsenger.piratesnships.chart.data.ChartRegion;

import java.util.function.LongFunction;

/**
 * The chart seen from far away (work package CHART2), pure: at a zoom-out step of {@code c} cells per pixel the screen
 * draws <b>far tiles</b>, each covering {@code c x c} regions in one 64 x 64 pixel texture. A far tile's pixel stands
 * for {@code c x c} cells, chosen like a map board's zoomed pixel ({@link MapTileRaster#downsample}: the most common
 * known class, coast if any cell is coast, unknown only when none is known), so far zooms stay readable: coasts stay
 * inked and small islands fold into their surroundings. {@code c} is a power of two up to 32, so a pixel's block
 * always lies inside one region; absent regions read as unknown without being walked. CHART4: a far pixel carries a
 * colour the same way ({@link MapTileRaster#downsample}: the most common colour of its land cells), drawn in the
 * chart's {@link ChartStyle}.
 */
public final class ChartZoomOut {

    /** Pixels along a far tile's side: one region's worth, like a region texture at one pixel per cell. */
    public static final int TILE = ChartRegion.SIZE;

    private ChartZoomOut() {
    }

    /** The far tile holding region {@code r} at {@code c} cells per pixel (one axis). */
    public static int tileOf(int region, int c) {
        return Math.floorDiv(region, c);
    }

    /**
     * The far tile {@code (tx, tz)}'s virtual cells and a one-cell border (coast strokes read their neighbours) as a
     * lookup: virtual cell {@code (vx, vz)} is the block of chart cells from {@code (vx * c, vz * c)}.
     */
    public static CellLookup tileLookup(LongFunction<ChartRegion> regions, int tx, int tz, int c) {
        int size = TILE + 2;
        int minVx = tx * TILE - 1;
        int minVz = tz * TILE - 1;
        int[] grid = new int[size * size];
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                grid[j * size + i] = pixel(regions, minVx + i, minVz + j, c);
            }
        }
        return (vx, vz) -> {
            int i = vx - minVx;
            int j = vz - minVz;
            return i < 0 || j < 0 || i >= size || j >= size ? 0 : grid[j * size + i];
        };
    }

    /** The cell byte of virtual cell {@code (vx, vz)} at {@code c} cells per pixel. */
    public static int pixel(LongFunction<ChartRegion> regions, int vx, int vz, int c) {
        int cx0 = vx * c;
        int cz0 = vz * c;
        ChartRegion r = regions.apply(ChartRegion.keyOfCell(cx0, cz0));
        if (r == null) return 0;
        return MapTileRaster.downsample((x, z) -> r.wide(x & ChartRegion.MASK, z & ChartRegion.MASK), cx0, cz0, c);
    }

    /** The ARGB pixels of far tile {@code (tx, tz)}: {@link #TILE} squared, row-major, in the chart's pirate style. */
    public static int[] render(LongFunction<ChartRegion> regions, int tx, int tz, int c) {
        return render(regions, tx, tz, c, ChartStyle.INK);
    }

    /** CHART4: {@link #render(LongFunction, int, int, int)} in {@code style}. */
    public static int[] render(LongFunction<ChartRegion> regions, int tx, int tz, int c, ChartStyle style) {
        return ChartRaster.render(tileLookup(regions, tx, tz, c), tx * TILE, tz * TILE, TILE, TILE, 1, style);
    }
}
