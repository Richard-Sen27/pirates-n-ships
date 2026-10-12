package com.richardsenger.piratesnships.chart.render;

import com.richardsenger.piratesnships.chart.data.CellClass;
import com.richardsenger.piratesnships.chart.data.ChartCells;
import com.richardsenger.piratesnships.chart.data.ChartRegion;
import com.richardsenger.piratesnships.chart.sample.MapColours;

/**
 * Draws chart cells as ARGB pixels in the pirate style (work package MAP1), pure: no client classes, so the screen
 * ({@code ChartTextures}) and the map tile block of MAP2 share it. Transparent pixels let the parchment show through.
 *
 * <ul>
 *   <li>Unknown: transparent (blank parchment).</li>
 *   <li>Deep water: a faint grey-blue wash with a few wave ticks (from two pixels per cell).</li>
 *   <li>Shallow water: a stronger wash hatched with diagonal blue ink lines.</li>
 *   <li>Beach: pale sand, stippled. Land: muted olive parchment, stippled. Snow and ice: pale white-grey.</li>
 *   <li>Coast: an ink stroke along the land side of every edge between land and water (the whole pixel at one pixel
 *       per cell), corners closed, and from four pixels per cell a faint ripple line in the water one pixel off.</li>
 * </ul>
 *
 * Patterns use world pixel coordinates ({@code cell * pxPerCell + offset}), so neighbouring regions or tiles join
 * seamlessly. Everything is deterministic.
 *
 * <p>CHART4, {@link ChartStyle#COLOURED}: a land cell (beach, land, snow and ice) with a colour (the cells are wide
 * cells, {@link ChartCells#wide}) is filled with vanilla's map colour of the cell, its saturation cut to
 * {@link #SATURATION} percent, mixed {@link #PAPER_MIX} percent towards the parchment and laid on at alpha
 * {@link #COLOURED_ALPHA}, stippled like the ink land; so a village's roofs, paths and fields show while the ink
 * marks, labels and icons still stand out. Water keeps the chart's wash, coasts their ink stroke; a land cell without
 * a colour (sampled before CHART4) draws in ink. {@link ChartStyle#INK} ignores the colours.
 */
public final class ChartRaster {

    public static final int INK = 0xFF2C2018;
    static final int RIPPLE = 0x6C2C2018;
    static final int DEEP = 0x384A6A78;
    static final int DEEP_WAVE = 0x7A3A5E70;
    static final int SHALLOW = 0x5A6E9AA0;
    static final int SHALLOW_HATCH = 0xA03A5E70;
    static final int BEACH = 0xD0DCC48E;
    static final int BEACH_DOT = 0xD0B0945E;
    static final int LAND = 0xC0ADA272;
    static final int LAND_DOT = 0xC0857C52;
    static final int SNOW = 0xE0F0F0E8;
    static final int SNOW_DOT = 0xE0C8D2D8;
    /** CHART4: percent of a block colour's saturation kept in the coloured style. */
    static final int SATURATION = 60;
    /** CHART4: percent a block colour is mixed towards the parchment in the coloured style. */
    static final int PAPER_MIX = 22;
    static final int COLOURED_ALPHA = 0xD8;
    static final int PAPER = 0xE6D3A6;

    private ChartRaster() {
    }

    /** The pixels of region {@code (rx, rz)} at {@code px} pixels per cell: {@code (64 * px)^2}, row-major. */
    public static int[] renderRegion(CellLookup cells, int rx, int rz, int px) {
        return renderRegion(cells, rx, rz, px, ChartStyle.INK);
    }

    /** CHART4: {@link #renderRegion(CellLookup, int, int, int)} in {@code style}. */
    public static int[] renderRegion(CellLookup cells, int rx, int rz, int px, ChartStyle style) {
        return render(cells, rx << ChartRegion.SHIFT, rz << ChartRegion.SHIFT, ChartRegion.SIZE, ChartRegion.SIZE, px, style);
    }

    /**
     * The pixels of the {@code w x h} cells starting at cell {@code (minCx, minCz)} at {@code px} pixels per cell
     * (1 or more): an array of {@code (w * px) * (h * px)} ARGB values, row-major (index {@code y * w * px + x}).
     * Neighbours outside the rectangle are read too (coast strokes).
     */
    public static int[] render(CellLookup cells, int minCx, int minCz, int w, int h, int px) {
        return render(cells, minCx, minCz, w, h, px, ChartStyle.INK);
    }

    /** CHART4: {@link #render(CellLookup, int, int, int, int, int)} in {@code style} (the cells may be wide cells). */
    public static int[] render(CellLookup cells, int minCx, int minCz, int w, int h, int px, ChartStyle style) {
        boolean coloured = style == ChartStyle.COLOURED;
        if (px < 1) throw new IllegalArgumentException("pixels per cell " + px);
        int width = w * px;
        int[] out = new int[width * h * px];
        for (int z = 0; z < h; z++) {
            for (int x = 0; x < w; x++) {
                int cx = minCx + x;
                int cz = minCz + z;
                int cell = cells.cell(cx, cz);
                CellClass cls = ChartCells.cellClass(cell);
                if (cls == CellClass.UNKNOWN) continue;
                int n = cells.cell(cx, cz - 1);
                int s = cells.cell(cx, cz + 1);
                int wv = cells.cell(cx - 1, cz);
                int e = cells.cell(cx + 1, cz);
                boolean land = cls.isLand();
                boolean coast = land && ChartCells.coast(cell);
                int tint = coloured && land ? ChartCells.colour(cell) : 0;
                int paint = tint == 0 ? 0 : softened(tint);
                for (int j = 0; j < px; j++) {
                    for (int i = 0; i < px; i++) {
                        int gx = cx * px + i;
                        int gz = cz * px + j;
                        int c;
                        if (coast && (px == 1 || coastPixel(cells, cx, cz, i, j, px, n, s, wv, e))) {
                            c = INK;
                        } else if (!land && px >= 4 && ripplePixel(i, j, px, n, s, wv, e)) {
                            c = RIPPLE;
                        } else if (paint != 0) {
                            c = noise(gx, gz) < 28 ? darker(paint) : paint;
                        } else {
                            c = fill(cls, gx, gz, px);
                        }
                        out[(z * px + j) * width + x * px + i] = c;
                    }
                }
            }
        }
        return out;
    }

    private static boolean water(int cell) {
        return ChartCells.cellClass(cell).isWater();
    }

    private static boolean landCell(int cell) {
        return ChartCells.cellClass(cell).isLand();
    }

    private static boolean coastPixel(CellLookup cells, int cx, int cz, int i, int j, int px, int n, int s, int w, int e) {
        int last = px - 1;
        if (j == 0 && water(n) || j == last && water(s) || i == 0 && water(w) || i == last && water(e)) return true;
        // close the stroke around a corner where only the diagonal neighbour is water
        if (i == 0 && j == 0) return water(cells.cell(cx - 1, cz - 1));
        if (i == last && j == 0) return water(cells.cell(cx + 1, cz - 1));
        if (i == 0 && j == last) return water(cells.cell(cx - 1, cz + 1));
        if (i == last && j == last) return water(cells.cell(cx + 1, cz + 1));
        return false;
    }

    private static boolean ripplePixel(int i, int j, int px, int n, int s, int w, int e) {
        int last = px - 1;
        return j == 1 && landCell(n) || j == last - 1 && landCell(s) || i == 1 && landCell(w) || i == last - 1 && landCell(e);
    }

    /** The fill of a pixel of class {@code cls} at world pixel {@code (gx, gz)}. */
    static int fill(CellClass cls, int gx, int gz, int px) {
        return switch (cls) {
            case DEEP_WATER -> px >= 2 && wave(gx, gz) ? DEEP_WAVE : DEEP;
            case SHALLOW_WATER -> Math.floorMod(gx + gz, 3) == 0 ? SHALLOW_HATCH : SHALLOW;
            case BEACH -> noise(gx, gz) < 34 ? BEACH_DOT : BEACH;
            case LAND -> noise(gx, gz) < 28 ? LAND_DOT : LAND;
            case SNOW_ICE -> noise(gx, gz) < 20 ? SNOW_DOT : SNOW;
            case UNKNOWN -> 0;
        };
    }

    /**
     * CHART4: the coloured style's fill for packed map colour {@code packed}: vanilla's colour with its saturation cut
     * to {@link #SATURATION} percent, mixed {@link #PAPER_MIX} percent towards the parchment, at alpha
     * {@link #COLOURED_ALPHA}; 0 for no colour.
     */
    public static int softened(int packed) {
        int argb = MapColours.argb(packed);
        if (argb == 0) return 0;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int luma = (r * 77 + g * 150 + b * 29) >> 8;
        r = paper(luma + (r - luma) * SATURATION / 100, (PAPER >> 16) & 0xFF);
        g = paper(luma + (g - luma) * SATURATION / 100, (PAPER >> 8) & 0xFF);
        b = paper(luma + (b - luma) * SATURATION / 100, PAPER & 0xFF);
        return COLOURED_ALPHA << 24 | r << 16 | g << 8 | b;
    }

    private static int paper(int c, int p) {
        return Math.max(0, Math.min(255, (c * (100 - PAPER_MIX) + p * PAPER_MIX + 50) / 100));
    }

    /** The stipple of the coloured style: the same colour 15 percent darker. */
    static int darker(int argb) {
        int r = ((argb >> 16) & 0xFF) * 85 / 100;
        int g = ((argb >> 8) & 0xFF) * 85 / 100;
        int b = (argb & 0xFF) * 85 / 100;
        return (argb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    /** A small wave tick "^" (three pixels) at one hashed spot in every 16 x 12 pixel tile. */
    private static boolean wave(int gx, int gz) {
        int tx = Math.floorDiv(gx, 16);
        int tz = Math.floorDiv(gz, 12);
        int h = ChartDoodles.hash(tx, tz);
        int ox = tx * 16 + Math.floorMod(h, 12) + 1;
        int oz = tz * 12 + Math.floorMod(h >> 8, 9) + 1;
        return gz == oz && (gx == ox || gx == ox + 2) || gz == oz - 1 && gx == ox + 1;
    }

    /** A fixed integer hash in 0..255. */
    static int noise(int x, int z) {
        return ChartDoodles.hash(x * 31 + 7, z * 17 + 3) >>> 24;
    }
}
