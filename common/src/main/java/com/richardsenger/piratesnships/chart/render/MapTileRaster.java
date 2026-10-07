package com.richardsenger.piratesnships.chart.render;

import com.richardsenger.piratesnships.chart.data.BoardMarker;
import com.richardsenger.piratesnships.chart.data.BoardSlice;
import com.richardsenger.piratesnships.chart.data.CellClass;
import com.richardsenger.piratesnships.chart.data.ChartCells;
import com.richardsenger.piratesnships.chart.data.ChartMarker;
import com.richardsenger.piratesnships.chart.data.MapTileDrawing;
import com.richardsenger.piratesnships.chart.data.TileMarker;

import java.util.ArrayList;
import java.util.List;

/**
 * The maths of the map tile (work package MAP2), pure and deterministic: the tile palette, quantising
 * {@link ChartRaster}'s pirate-style colours to it, drawing a chart area into palette bytes, stamping markers, the
 * selection rectangle's clamping, and the final picture a client uploads as the tile's texture.
 *
 * <p><b>Palette.</b> At most {@link #MAX_PALETTE} opaque colours. Entry 0 is blank parchment; every other entry is one
 * of {@link ChartRaster}'s (partly transparent) colours laid over the parchment, so a drawn tile looks exactly like the
 * chart screen at one pixel per cell. A pixel is quantised by laying it over the parchment and picking the nearest
 * entry by squared RGB distance (ties: the lower index). Never reorder entries: drawings store the indices.
 */
public final class MapTileRaster {

    public static final int MAX_PALETTE = 64;
    /** Blank parchment, the tile's ground. */
    public static final int PARCHMENT = 0xFFE6D3A6;
    /** Darker parchment: specks in the paper and the worn edge. */
    public static final int PARCHMENT_DARK = 0xFFD5BE8C;

    /** What a palette entry shows. */
    public enum Kind { PARCHMENT, INK, WATER, LAND }

    private static final int[] SOURCES = {
            PARCHMENT,
            ChartRaster.INK,
            ChartRaster.RIPPLE,
            ChartRaster.DEEP,
            ChartRaster.DEEP_WAVE,
            ChartRaster.SHALLOW,
            ChartRaster.SHALLOW_HATCH,
            ChartRaster.BEACH,
            ChartRaster.BEACH_DOT,
            ChartRaster.LAND,
            ChartRaster.LAND_DOT,
            ChartRaster.SNOW,
            ChartRaster.SNOW_DOT,
    };
    private static final Kind[] KINDS = {
            Kind.PARCHMENT, Kind.INK, Kind.WATER, Kind.WATER, Kind.WATER, Kind.WATER, Kind.WATER,
            Kind.LAND, Kind.LAND, Kind.LAND, Kind.LAND, Kind.LAND, Kind.LAND,
    };
    private static final int[] PALETTE = new int[SOURCES.length];

    static {
        if (SOURCES.length > MAX_PALETTE || KINDS.length != SOURCES.length) throw new IllegalStateException("tile palette");
        for (int i = 0; i < SOURCES.length; i++) PALETTE[i] = over(SOURCES[i], PARCHMENT);
    }

    private MapTileRaster() {
    }

    // --- palette -------------------------------------------------------------------------------------------------

    public static int paletteSize() {
        return PALETTE.length;
    }

    /** The opaque ARGB colour of palette entry {@code index}; unknown indices read as parchment. */
    public static int colour(int index) {
        return index >= 0 && index < PALETTE.length ? PALETTE[index] : PARCHMENT;
    }

    public static Kind kind(int index) {
        return index >= 0 && index < KINDS.length ? KINDS[index] : Kind.PARCHMENT;
    }

    /** {@code argb} laid over the opaque colour {@code base}, rounded: an opaque colour. */
    public static int over(int argb, int base) {
        int a = argb >>> 24;
        int r = blend((argb >> 16) & 0xFF, (base >> 16) & 0xFF, a);
        int g = blend((argb >> 8) & 0xFF, (base >> 8) & 0xFF, a);
        int b = blend(argb & 0xFF, base & 0xFF, a);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int blend(int src, int dst, int a) {
        return (src * a + dst * (255 - a) + 127) / 255;
    }

    /** The palette index nearest to {@code argb} laid over the parchment (fully transparent = parchment). */
    public static int quantise(int argb) {
        if (argb >>> 24 == 0) return 0;
        int c = over(argb, PARCHMENT);
        int best = 0;
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < PALETTE.length; i++) {
            long d = distance(c, PALETTE[i]);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** Squared RGB distance of two colours (alpha ignored). */
    public static long distance(int a, int b) {
        long dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
        long dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
        long db = (a & 0xFF) - (b & 0xFF);
        return dr * dr + dg * dg + db * db;
    }

    // --- drawing -------------------------------------------------------------------------------------------------

    /**
     * Whether palette index {@code index} shows charted ground or sea. {@link #draw} guarantees that a pixel is
     * {@code known} exactly when its cell is known in the chart it was drawn from, so a later update (MAP3) can merge
     * two drawings of the same area pixel by pixel, keeping old pixels where the newer chart knows nothing.
     */
    public static boolean known(int index) {
        return index != 0;
    }

    /**
     * The {@code size x size} cells from cell {@code (minCx, minCz)} as palette bytes, one pixel per cell, drawn by
     * {@link ChartRaster}: unknown cells are always parchment (index 0), known cells never are (see {@link #known}).
     */
    public static byte[] draw(CellLookup cells, int minCx, int minCz, int size) {
        int[] argb = ChartRaster.render(cells, minCx, minCz, size, size, 1);
        byte[] out = new byte[argb.length];
        int lastIn = 0;
        int lastOut = 0;
        for (int i = 0; i < argb.length; i++) {
            int c = argb[i];
            if (c != lastIn || i == 0) {
                lastIn = c;
                lastOut = quantise(c);
            }
            boolean cellKnown = ChartCells.known(cells.cell(minCx + i % size, minCz + i / size));
            out[i] = (byte) (!cellKnown ? 0 : lastOut != 0 ? lastOut : nearestInked(c));
        }
        return out;
    }

    // --- zoom (MAP3) ---------------------------------------------------------------------------------------------

    /**
     * One pixel of a drawing at zoom {@code zoom}: the cell byte that stands for the {@code zoom x zoom} chart cells
     * from cell {@code (cx0, cz0)}. Its class is the most common class among the <b>known</b> cells of the block
     * (ties: deep water, then shallow water, then the land classes in their stored order), its coast flag is set when
     * any known cell of the block is coast, and it is unknown (0) only when no cell of the block is known. At zoom 1
     * it is the cell itself (with free bits cleared).
     */
    public static int downsample(CellLookup cells, int cx0, int cz0, int zoom) {
        if (zoom <= 1) {
            int c = cells.cell(cx0, cz0);
            return ChartCells.known(c) ? c & (ChartCells.CLASS_MASK | ChartCells.COAST) : 0;
        }
        int[] counts = new int[CellClass.values().length];
        boolean coast = false;
        boolean any = false;
        for (int j = 0; j < zoom; j++) {
            for (int i = 0; i < zoom; i++) {
                int c = cells.cell(cx0 + i, cz0 + j);
                if (!ChartCells.known(c)) continue;
                any = true;
                counts[ChartCells.cellClass(c).ordinal()]++;
                if (ChartCells.coast(c)) coast = true;
            }
        }
        if (!any) return 0;
        int best = 0;
        for (int k = 1; k < counts.length; k++) {
            // strictly greater: on a tie the lower ordinal (deeper water first, then land) stays
            if (counts[k] > (best == 0 ? 0 : counts[best])) best = k;
        }
        return ChartCells.of(CellClass.of(best), coast) & 0xFF;
    }

    /**
     * The chart seen at zoom {@code zoom}: virtual cell {@code (vx, vz)} is {@link #downsample} of the block whose
     * first chart cell is {@code (vx * zoom + anchorX, vz * zoom + anchorZ)}. Every slice of one board shares the
     * anchor ({@link #anchor}), so the decorations line up across the tiles.
     */
    public static CellLookup zoomed(CellLookup cells, int zoom, int anchorX, int anchorZ) {
        if (zoom <= 1 && anchorX == 0 && anchorZ == 0) return cells;
        return (vx, vz) -> downsample(cells, vx * zoom + anchorX, vz * zoom + anchorZ, zoom);
    }

    /** The anchor of an area starting at chart cell {@code minC} at zoom {@code zoom}: {@code floorMod(minC, zoom)}. */
    public static int anchor(int minC, int zoom) {
        return Math.floorMod(minC, Math.max(1, zoom));
    }

    /**
     * {@code size x size} pixels of {@code zoom x zoom} cells each from cell {@code (minCx, minCz)} as palette bytes
     * ({@link #draw(CellLookup, int, int, int)} on the {@link #zoomed} chart): a pixel is {@link #known} exactly when
     * at least one cell of its block is known. The zoomed cells are read once into a grid (with a one-cell rim for
     * the coast strokes), so each chart cell is read once per pixel block.
     */
    public static byte[] draw(CellLookup cells, int minCx, int minCz, int size, int zoom) {
        if (zoom <= 1) return draw(cells, minCx, minCz, size);
        int ax = anchor(minCx, zoom);
        int az = anchor(minCz, zoom);
        int vx0 = Math.floorDiv(minCx, zoom);
        int vz0 = Math.floorDiv(minCz, zoom);
        CellLookup virtual = zoomed(cells, zoom, ax, az);
        int w = size + 2;
        byte[] grid = new byte[w * w];
        for (int j = 0; j < w; j++) {
            for (int i = 0; i < w; i++) {
                grid[j * w + i] = (byte) virtual.cell(vx0 - 1 + i, vz0 - 1 + j);
            }
        }
        CellLookup cached = (vx, vz) -> {
            int i = vx - vx0 + 1;
            int j = vz - vz0 + 1;
            return i >= 0 && j >= 0 && i < w && j < w ? grid[j * w + i] & 0xFF : virtual.cell(vx, vz);
        };
        return draw(cached, vx0, vz0, size);
    }

    /** The nearest palette entry other than parchment, for a known cell whose colour is (almost) the paper's. */
    private static int nearestInked(int argb) {
        int c = over(argb, PARCHMENT);
        int best = 1;
        long bestD = Long.MAX_VALUE;
        for (int i = 1; i < PALETTE.length; i++) {
            long d = distance(c, PALETTE[i]);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /**
     * The markers inside the area of {@code size x size} cells from cell {@code (minCx, minCz)} (cells of
     * {@code cellBlocks} blocks), at their cell's pixel, in the chart's order, at most {@code max}.
     */
    public static List<TileMarker> stamp(List<ChartMarker> markers, int minCx, int minCz, int size, int cellBlocks, int max) {
        List<TileMarker> out = new ArrayList<>();
        int cb = Math.max(1, cellBlocks);
        for (ChartMarker m : markers) {
            if (out.size() >= max) break;
            long px = (long) Math.floorDiv(m.x(), cb) - minCx;
            long py = (long) Math.floorDiv(m.z(), cb) - minCz;
            if (px < 0 || py < 0 || px >= size || py >= size) continue;
            out.add(new TileMarker(m.icon(), (int) px, (int) py, m.name()));
        }
        return out;
    }

    /**
     * The markers inside a board area of {@code widthPx x heightPx} pixels of {@code zoom x zoom} cells from cell
     * {@code (minCx, minCz)} (cells of {@code cellBlocks} blocks), at their pixel in board pixels, in the chart's
     * order, at most {@code max} (work package MAP3).
     */
    public static List<BoardMarker> boardMarkers(List<ChartMarker> markers, int minCx, int minCz, int widthPx, int heightPx, int zoom,
                                                 int cellBlocks, int max) {
        List<BoardMarker> out = new ArrayList<>();
        int cb = Math.max(1, cellBlocks);
        int z = Math.max(1, zoom);
        for (ChartMarker m : markers) {
            if (out.size() >= max) break;
            long bx = Math.floorDiv((long) Math.floorDiv(m.x(), cb) - minCx, z);
            long by = Math.floorDiv((long) Math.floorDiv(m.z(), cb) - minCz, z);
            if (bx < 0 || by < 0 || bx >= widthPx || by >= heightPx) continue;
            out.add(new BoardMarker(m.icon(), (int) bx, (int) by, m.name()));
        }
        return out;
    }

    /**
     * How many raster pixels beyond its own edge a slice still stamps a marker of its neighbour, so the icon's part
     * that reaches over the seam is drawn on this tile too (an icon is {@link ChartSheet#ICON} picture pixels wide).
     */
    public static int markerMargin(int size) {
        int scale = pictureScale(size);
        return (ChartSheet.ICON / 2 + scale) / scale;
    }

    /** Whether the area of {@code size x size} cells from cell {@code (minCx, minCz)} holds at least one known cell. */
    public static boolean anyKnown(CellLookup cells, int minCx, int minCz, int size) {
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                if (ChartCells.known(cells.cell(minCx + x, minCz + z))) return true;
            }
        }
        return false;
    }

    // --- the selection -------------------------------------------------------------------------------------------

    /**
     * The first cell of a selection of {@code size} cells along one axis, as close to {@code desired} as allowed:
     * when the charted cells {@code knownMin..knownMax} (inclusive) span at least {@code size}, the selection stays
     * inside them; when they span less, it covers them all.
     */
    public static int clampAxis(int desired, int size, int knownMin, int knownMax) {
        int lo;
        int hi;
        if ((long) knownMax - knownMin + 1 >= size) {
            lo = knownMin;
            hi = knownMax - size + 1;
        } else {
            lo = knownMax - size + 1;
            hi = knownMin;
        }
        return Math.max(lo, Math.min(hi, desired));
    }

    /** The first cell of a selection of {@code size} cells centred on block {@code block}. */
    public static int centredOn(double block, int cellBlocks, int size) {
        return Math.floorDiv((int) Math.floor(block), Math.max(1, cellBlocks)) - size / 2;
    }

    /** Whether the whole area lies inside the world's coordinate limit of {@code limit} blocks either way. */
    public static boolean inWorld(int minCx, int minCz, int size, int cellBlocks, int limit) {
        return inWorld(minCx, minCz, (long) size, (long) size, cellBlocks, limit);
    }

    /** {@link #inWorld(int, int, int, int, int)} for an area of {@code w x h} cells (a board). */
    public static boolean inWorld(int minCx, int minCz, long w, long h, int cellBlocks, int limit) {
        long cb = Math.max(1, cellBlocks);
        return (long) minCx * cb >= -limit && (long) minCz * cb >= -limit
                && ((long) minCx + w) * cb <= limit && ((long) minCz + h) * cb <= limit;
    }

    // --- the picture ---------------------------------------------------------------------------------------------

    /** Pixels of the picture per raster pixel: pictures are at least 128 pixels wide, so icons keep their size. */
    public static int pictureScale(int size) {
        return Math.max(1, 128 / size);
    }

    /**
     * The tile's picture as ARGB ({@code (size * scale)^2}, row-major, opaque): the raster on speckled parchment
     * with a worn edge and a one-pixel ink border, the small compass rose in the top-right corner and the marker icons
     * at their pixels, taken from the chart sheet {@code sheet} ({@code sheetW x sheetH} ARGB, see {@link ChartSheet};
     * {@code null} leaves the sprites out).
     *
     * <p>A slice of a board (MAP3) draws the border and the worn edge only on the board's outer sides and the compass
     * rose only on the top-right tile, so the tiles read as one map; markers stamped just beyond its edge (the
     * neighbour's, see {@link #markerMargin}) show the part of their icon that reaches over the seam.
     */
    public static int[] picture(MapTileDrawing d, int scale, int[] sheet, int sheetW, int sheetH) {
        int size = d.size();
        int w = size * scale;
        BoardSlice b = d.board().orElse(null);
        boolean edgeW = b == null || b.column() == 0;
        boolean edgeE = b == null || b.column() == b.columns() - 1;
        boolean edgeN = b == null || b.row() == 0;
        boolean edgeS = b == null || b.row() == b.rows() - 1;
        int far = Integer.MAX_VALUE / 2;
        int[] out = new int[w * w];
        for (int y = 0; y < w; y++) {
            for (int x = 0; x < w; x++) {
                int edge = Math.min(Math.min(edgeW ? x : far, edgeN ? y : far), Math.min(edgeE ? w - 1 - x : far, edgeS ? w - 1 - y : far));
                int index = d.pixel(x / scale, y / scale);
                int c;
                if (edge == 0) {
                    c = ChartRaster.INK;
                } else if (index != 0) {
                    c = colour(index);
                } else if (edge <= 2 * scale || ChartRaster.noise(x, y) < 18) {
                    c = PARCHMENT_DARK;
                } else {
                    c = PARCHMENT;
                }
                out[y * w + x] = c;
            }
        }
        if (sheet != null) {
            if (edgeN && edgeE) {
                ChartSheet.Part rose = ChartSheet.SMALL_ROSE;
                blit(out, w, w, sheet, sheetW, sheetH, rose, w - rose.w() - 3, 3);
            }
            for (TileMarker m : d.markers()) {
                ChartSheet.Part icon = ChartSheet.marker(m.icon());
                int cx = m.px() * scale + scale / 2;
                int cy = m.py() * scale + scale / 2;
                blit(out, w, w, sheet, sheetW, sheetH, icon, cx - icon.w() / 2, cy - icon.h() / 2);
            }
        }
        return out;
    }

    /** Draws part {@code p} of {@code src} onto the opaque {@code dst} with its top-left at {@code (x, y)}, alpha blended, clipped. */
    public static void blit(int[] dst, int dw, int dh, int[] src, int sw, int sh, ChartSheet.Part p, int x, int y) {
        for (int j = 0; j < p.h(); j++) {
            int ty = y + j;
            int sy = p.v() + j;
            if (ty < 0 || ty >= dh || sy < 0 || sy >= sh) continue;
            for (int i = 0; i < p.w(); i++) {
                int tx = x + i;
                int sx = p.u() + i;
                if (tx < 0 || tx >= dw || sx < 0 || sx >= sw) continue;
                int c = src[sy * sw + sx];
                if (c >>> 24 == 0) continue;
                dst[ty * dw + tx] = over(c, dst[ty * dw + tx]);
            }
        }
    }
}
