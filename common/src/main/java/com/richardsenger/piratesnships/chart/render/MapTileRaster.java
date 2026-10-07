package com.richardsenger.piratesnships.chart.render;

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
     * The {@code size x size} cells from cell {@code (minCx, minCz)} as palette bytes, one pixel per cell, drawn by
     * {@link ChartRaster} (unknown cells stay parchment).
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
            out[i] = (byte) lastOut;
        }
        return out;
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
        long cb = Math.max(1, cellBlocks);
        return (long) minCx * cb >= -limit && (long) minCz * cb >= -limit
                && ((long) minCx + size) * cb <= limit && ((long) minCz + size) * cb <= limit;
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
     */
    public static int[] picture(MapTileDrawing d, int scale, int[] sheet, int sheetW, int sheetH) {
        int size = d.size();
        int w = size * scale;
        int[] out = new int[w * w];
        for (int y = 0; y < w; y++) {
            for (int x = 0; x < w; x++) {
                int edge = Math.min(Math.min(x, y), Math.min(w - 1 - x, w - 1 - y));
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
            ChartSheet.Part rose = ChartSheet.SMALL_ROSE;
            blit(out, w, w, sheet, sheetW, sheetH, rose, w - rose.w() - 3, 3);
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
