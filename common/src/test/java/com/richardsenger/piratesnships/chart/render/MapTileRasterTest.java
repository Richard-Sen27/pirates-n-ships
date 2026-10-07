package com.richardsenger.piratesnships.chart.render;

import com.richardsenger.piratesnships.chart.data.CellClass;
import com.richardsenger.piratesnships.chart.data.ChartCells;
import com.richardsenger.piratesnships.chart.data.ChartMarker;
import com.richardsenger.piratesnships.chart.data.MapTileDrawing;
import com.richardsenger.piratesnships.chart.data.MarkerIcon;
import com.richardsenger.piratesnships.chart.data.TileMarker;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The map tile's maths (MAP2): palette and quantisation, drawing, marker stamping, the selection clamp, the picture. */
class MapTileRasterTest {

    /** Every colour {@link ChartRaster} can put on a chart. */
    private static final int[] RASTER_COLOURS = {
            ChartRaster.INK, ChartRaster.RIPPLE, ChartRaster.DEEP, ChartRaster.DEEP_WAVE, ChartRaster.SHALLOW, ChartRaster.SHALLOW_HATCH,
            ChartRaster.BEACH, ChartRaster.BEACH_DOT, ChartRaster.LAND, ChartRaster.LAND_DOT, ChartRaster.SNOW, ChartRaster.SNOW_DOT,
    };
    /** Squared RGB distance allowed between a raster colour over parchment and its palette entry. */
    private static final long TOLERANCE = 3 * 2 * 2;

    @Test
    void paletteFitsInSixtyFourOpaqueColours() {
        assertTrue(MapTileRaster.paletteSize() <= MapTileRaster.MAX_PALETTE);
        assertEquals(MapTileRaster.PARCHMENT, MapTileRaster.colour(0), "entry 0 is blank parchment");
        assertEquals(MapTileRaster.Kind.PARCHMENT, MapTileRaster.kind(0));
        Set<Integer> distinct = new HashSet<>();
        for (int i = 0; i < MapTileRaster.paletteSize(); i++) {
            assertEquals(0xFF, MapTileRaster.colour(i) >>> 24, "entry " + i + " is opaque");
            distinct.add(MapTileRaster.colour(i));
        }
        assertEquals(MapTileRaster.paletteSize(), distinct.size(), "no two entries alike");
        assertEquals(MapTileRaster.PARCHMENT, MapTileRaster.colour(200), "unknown indices read as parchment");
    }

    @Test
    void everyChartColourHasAPaletteEntryWithinTolerance() {
        for (int c : RASTER_COLOURS) {
            int index = MapTileRaster.quantise(c);
            long d = MapTileRaster.distance(MapTileRaster.over(c, MapTileRaster.PARCHMENT), MapTileRaster.colour(index));
            assertTrue(d <= TOLERANCE, String.format("%08X maps to entry %d at distance %d", c, index, d));
            assertTrue(index != 0, String.format("%08X is not parchment", c));
        }
        assertEquals(0, MapTileRaster.quantise(0), "transparent is parchment");
        assertEquals(MapTileRaster.Kind.INK, MapTileRaster.kind(MapTileRaster.quantise(ChartRaster.INK)));
        assertEquals(MapTileRaster.Kind.WATER, MapTileRaster.kind(MapTileRaster.quantise(ChartRaster.DEEP)));
        assertEquals(MapTileRaster.Kind.WATER, MapTileRaster.kind(MapTileRaster.quantise(ChartRaster.SHALLOW_HATCH)));
        assertEquals(MapTileRaster.Kind.LAND, MapTileRaster.kind(MapTileRaster.quantise(ChartRaster.LAND_DOT)));
        assertEquals(MapTileRaster.Kind.LAND, MapTileRaster.kind(MapTileRaster.quantise(ChartRaster.SNOW)));
    }

    @Test
    void quantisationIsDeterministicAndPicksTheNearest() {
        Random r = new Random(7);
        for (int i = 0; i < 2000; i++) {
            int c = r.nextInt();
            int a = MapTileRaster.quantise(c);
            assertEquals(a, MapTileRaster.quantise(c), "same input, same index");
            int over = MapTileRaster.over(c, MapTileRaster.PARCHMENT);
            long best = MapTileRaster.distance(over, MapTileRaster.colour(a));
            for (int j = 0; j < MapTileRaster.paletteSize(); j++) {
                assertTrue(best <= MapTileRaster.distance(over, MapTileRaster.colour(j)), "entry " + j + " is not nearer");
            }
        }
    }

    /** West half land, east half deep water, the last rows unknown. */
    private static CellLookup coast(int coastCx, int unknownFromCz) {
        return (cx, cz) -> {
            if (cz >= unknownFromCz) return 0;
            CellClass cls = cx < coastCx ? CellClass.LAND : CellClass.DEEP_WATER;
            boolean coastFlag = cx == coastCx - 1;
            return ChartCells.of(cls, coastFlag);
        };
    }

    @Test
    void drawingIsTheChartRasterQuantised() {
        CellLookup cells = coast(10, 20);
        byte[] px = MapTileRaster.draw(cells, -6, -4, 32);
        assertEquals(32 * 32, px.length);
        int[] argb = ChartRaster.render(cells, -6, -4, 32, 32, 1);
        for (int i = 0; i < px.length; i++) {
            assertEquals(MapTileRaster.quantise(argb[i]), px[i] & 0xFF, "pixel " + i);
        }
        // cell (cx, cz) is pixel (cx + 6, cz + 4)
        assertEquals(MapTileRaster.Kind.LAND, MapTileRaster.kind(px[(4 + 0) * 32 + 6 + 2]));
        assertEquals(MapTileRaster.Kind.INK, MapTileRaster.kind(px[(4 + 0) * 32 + 6 + 9]));
        assertEquals(MapTileRaster.Kind.WATER, MapTileRaster.kind(px[(4 + 0) * 32 + 6 + 12]));
        assertEquals(0, px[(4 + 22) * 32 + 6 + 12], "unknown stays parchment");
        assertArrayEquals(px, MapTileRaster.draw(cells, -6, -4, 32), "deterministic");
    }

    @Test
    void markersInsideTheAreaAreStampedAtTheirCell() {
        List<ChartMarker> markers = List.of(
                new ChartMarker(1, 0, 0, MarkerIcon.X, "origin"),
                new ChartMarker(2, -1, -1, MarkerIcon.SKULL, "just west and north"),
                new ChartMarker(3, 4 * 31 + 3, 4 * 31, MarkerIcon.PORT, "last cell"),
                new ChartMarker(4, 4 * 32, 0, MarkerIcon.ANCHOR, "one cell too far east"),
                new ChartMarker(5, -4 * 8, -4 * 8, MarkerIcon.DANGER, ""));
        // area: cells -8..23 on both axes, 4 blocks per cell
        List<TileMarker> stamped = MapTileRaster.stamp(markers, -8, -8, 32, 4, 64);
        assertEquals(List.of(
                new TileMarker(MarkerIcon.X, 8, 8, "origin"),
                new TileMarker(MarkerIcon.SKULL, 7, 7, "just west and north"),
                new TileMarker(MarkerIcon.DANGER, 0, 0, "")), stamped);
        assertEquals(List.of(new TileMarker(MarkerIcon.PORT, 31, 31, "last cell")), MapTileRaster.stamp(markers, 0, 0, 32, 4, 64).subList(1, 2));
        assertEquals(1, MapTileRaster.stamp(markers, -8, -8, 32, 4, 1).size(), "capped");
    }

    @Test
    void selectionStaysOnTheChartedArea() {
        // charted cells 0..299, a selection of 128
        assertEquals(50, MapTileRaster.clampAxis(50, 128, 0, 299));
        assertEquals(0, MapTileRaster.clampAxis(-40, 128, 0, 299), "not past the west edge");
        assertEquals(172, MapTileRaster.clampAxis(250, 128, 0, 299), "not past the east edge");
        // charted cells 10..59 (less than the selection): it must cover them all
        assertEquals(-68, MapTileRaster.clampAxis(-200, 128, 10, 59), "at most so far west that 59 is the last cell");
        assertEquals(10, MapTileRaster.clampAxis(200, 128, 10, 59), "at most so far east that 10 is the first cell");
        assertEquals(-20, MapTileRaster.clampAxis(-20, 128, 10, 59), "free in between");
        // exactly as wide: one place only
        assertEquals(5, MapTileRaster.clampAxis(99, 128, 5, 132));
    }

    @Test
    void centringAndTheWorldEdge() {
        assertEquals(-64, MapTileRaster.centredOn(0.5, 4, 128));
        assertEquals(-65, MapTileRaster.centredOn(-0.5, 4, 128));
        assertEquals(25 - 64, MapTileRaster.centredOn(103.9, 4, 128));
        assertTrue(MapTileRaster.inWorld(-64, -64, 128, 4, 30_000_000));
        assertTrue(MapTileRaster.inWorld(7_500_000 - 128, 0, 128, 4, 30_000_000), "touching the edge");
        assertFalse(MapTileRaster.inWorld(7_500_000 - 127, 0, 128, 4, 30_000_000));
        assertFalse(MapTileRaster.inWorld(0, -7_500_001, 128, 4, 30_000_000));
        assertFalse(MapTileRaster.inWorld(Integer.MAX_VALUE - 10, 0, 128, 16, 30_000_000), "no overflow");
    }

    @Test
    void pictureHasBorderParchmentRasterAndSprites() {
        CellLookup cells = coast(16, 1000);
        MapTileDrawing d = new MapTileDrawing(32, MapTileRaster.draw(cells, 0, 0, 32), 0, 0, 4, "Anne", 3,
                List.of(new TileMarker(MarkerIcon.X, 20, 20, "x")));
        int scale = MapTileRaster.pictureScale(32);
        assertEquals(4, scale, "small tiles are scaled up to 128 pixels");
        int w = 32 * scale;
        int[] plain = MapTileRaster.picture(d, scale, null, 0, 0);
        assertEquals(w * w, plain.length);
        assertEquals(ChartRaster.INK, plain[0], "ink border");
        assertEquals(ChartRaster.INK, plain[w * w - 1], "ink border");
        int landPixel = plain[(10 * scale) * w + 5 * scale];
        assertEquals(MapTileRaster.colour(d.pixel(5, 10)), landPixel, "raster pixels scaled up");
        for (int c : plain) assertEquals(0xFF, c >>> 24, "opaque");
        // a sheet of solid red: the rose and the marker icon land where they belong
        int[] sheet = new int[ChartSheet.WIDTH * ChartSheet.HEIGHT];
        java.util.Arrays.fill(sheet, 0xFFFF0000);
        int[] withSprites = MapTileRaster.picture(d, scale, sheet, ChartSheet.WIDTH, ChartSheet.HEIGHT);
        int mx = 20 * scale + scale / 2;
        assertEquals(0xFFFF0000, withSprites[mx * w + mx], "marker icon at its pixel");
        assertEquals(0xFFFF0000, withSprites[(3 + 8) * w + (w - 3 - 8)], "rose in the top-right corner");
        assertEquals(plain[(w / 2) * w + 2], withSprites[(w / 2) * w + 2], "elsewhere unchanged");
        assertArrayEquals(withSprites, MapTileRaster.picture(d, scale, sheet, ChartSheet.WIDTH, ChartSheet.HEIGHT), "deterministic");
    }

    @Test
    void anyKnownFindsASingleChartedCell() {
        CellLookup one = (cx, cz) -> cx == 70 && cz == -3 ? ChartCells.of(CellClass.DEEP_WATER, false) : 0;
        assertTrue(MapTileRaster.anyKnown(one, 0, -10, 128), "the cell lies inside");
        assertFalse(MapTileRaster.anyKnown(one, 71, -10, 128), "the area starts east of it");
        assertFalse(MapTileRaster.anyKnown(one, 0, -2, 128), "the area starts south of it");
        assertFalse(MapTileRaster.anyKnown((cx, cz) -> 0, 0, 0, 32), "nothing charted");
    }

    @Test
    void aPixelIsKnownExactlyWhenItsCellIs() {
        // a ragged coast with unknown holes: ripples and ink must never spill onto unknown cells, and no known cell
        // may come out as blank parchment
        Random random = new Random(7);
        int size = 64;
        int[][] grid = new int[size][size];
        CellClass[] classes = {CellClass.LAND, CellClass.BEACH, CellClass.SHALLOW_WATER, CellClass.DEEP_WATER, CellClass.SNOW_ICE};
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                grid[z][x] = random.nextInt(4) == 0 ? 0 : ChartCells.of(classes[random.nextInt(classes.length)], random.nextInt(5) == 0);
            }
        }
        CellLookup cells = (cx, cz) -> cx < 0 || cz < 0 || cx >= size || cz >= size ? 0 : grid[cz][cx];
        byte[] px = MapTileRaster.draw(cells, 0, 0, size);
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                assertEquals(ChartCells.known(grid[z][x]), MapTileRaster.known(px[z * size + x] & 0xFF), "pixel " + x + "," + z);
            }
        }
    }

    // --- zoom and boards (MAP3) ---------------------------------------------------------------------------------

    private static CellLookup grid(int[][] g) {
        return (cx, cz) -> cx < 0 || cz < 0 || cz >= g.length || cx >= g[0].length ? 0 : g[cz][cx];
    }

    private static int c(CellClass cls) {
        return ChartCells.of(cls, false);
    }

    @Test
    void downsamplingTakesTheMostCommonKnownClass() {
        int land = c(CellClass.LAND);
        int deep = c(CellClass.DEEP_WATER);
        int shallow = c(CellClass.SHALLOW_WATER);
        int beach = c(CellClass.BEACH);
        assertEquals(land, MapTileRaster.downsample(grid(new int[][]{{land, land}, {land, deep}}), 0, 0, 2), "majority");
        assertEquals(deep, MapTileRaster.downsample(grid(new int[][]{{shallow, deep}, {deep, shallow}}), 0, 0, 2), "tie: deeper water first");
        assertEquals(shallow, MapTileRaster.downsample(grid(new int[][]{{land, shallow}, {shallow, land}}), 0, 0, 2), "tie: water before land");
        assertEquals(beach, MapTileRaster.downsample(grid(new int[][]{{land, beach}, {beach, land}}), 0, 0, 2), "tie: land classes in order");
        // unknown cells do not vote
        assertEquals(land, MapTileRaster.downsample(grid(new int[][]{{0, 0}, {0, land}}), 0, 0, 2), "one known cell decides");
        assertEquals(deep, MapTileRaster.downsample(grid(new int[][]{{0, deep}, {deep, land}}), 0, 0, 2));
    }

    @Test
    void downsamplingKeepsCoastsAndIsUnknownOnlyWhenAllIs() {
        int coastLand = ChartCells.of(CellClass.LAND, true);
        int deep = c(CellClass.DEEP_WATER);
        int down = MapTileRaster.downsample(grid(new int[][]{{deep, deep}, {deep, coastLand}}), 0, 0, 2);
        assertEquals(CellClass.DEEP_WATER, ChartCells.cellClass(down));
        assertTrue(ChartCells.coast(down), "any coast cell sets the flag");
        int land = MapTileRaster.downsample(grid(new int[][]{{coastLand, c(CellClass.LAND)}, {0, 0}}), 0, 0, 2);
        assertEquals(CellClass.LAND, ChartCells.cellClass(land));
        assertTrue(ChartCells.coast(land));
        assertEquals(0, MapTileRaster.downsample(grid(new int[][]{{0, 0}, {0, 0}}), 0, 0, 2), "nothing known");
        assertEquals(0, MapTileRaster.downsample(grid(new int[][]{{deep}}), 5, 5, 4), "outside the known cells");
        // zoom 1 is the cell itself
        assertEquals(coastLand, MapTileRaster.downsample(grid(new int[][]{{coastLand}}), 0, 0, 1));
    }

    @Test
    void zoomOneIsThePlainDrawing() {
        CellLookup cells = coast(16, 1000);
        assertArrayEquals(MapTileRaster.draw(cells, -5, 3, 32), MapTileRaster.draw(cells, -5, 3, 32, 1));
    }

    @Test
    void zoomFourCoversAFourTimesLargerArea() {
        // land west of cell 64: at zoom 4 the coast is pixel 16 of a tile starting at cell 0, and the tile spans 128 cells
        CellLookup cells = coast(64, 1000);
        byte[] px = MapTileRaster.draw(cells, 0, 0, 32, 4);
        assertEquals(32 * 32, px.length);
        assertEquals(MapTileRaster.Kind.LAND, MapTileRaster.kind(px[5 * 32 + 10] & 0xFF));
        assertEquals(MapTileRaster.Kind.INK, MapTileRaster.kind(px[5 * 32 + 15] & 0xFF), "the coast block is inked");
        assertEquals(MapTileRaster.Kind.WATER, MapTileRaster.kind(px[5 * 32 + 20] & 0xFF));
        // the same coast at zoom 1 lies beyond a 32-cell tile
        byte[] plain = MapTileRaster.draw(cells, 0, 0, 32);
        assertEquals(MapTileRaster.Kind.LAND, MapTileRaster.kind(plain[5 * 32 + 31] & 0xFF));
    }

    @Test
    void aZoomedPixelIsKnownExactlyWhenAnyCellOfItsBlockIs() {
        Random random = new Random(11);
        int size = 64;
        int[][] g = new int[size][size];
        CellClass[] classes = {CellClass.LAND, CellClass.BEACH, CellClass.SHALLOW_WATER, CellClass.DEEP_WATER, CellClass.SNOW_ICE};
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                // mostly unknown, so that whole blocks stay unknown
                g[z][x] = random.nextInt(12) != 0 ? 0 : ChartCells.of(classes[random.nextInt(classes.length)], random.nextInt(4) == 0);
            }
        }
        int zoom = 4;
        int tile = size / zoom;
        byte[] px = MapTileRaster.draw(grid(g), 0, 0, tile, zoom);
        int unknownBlocks = 0;
        for (int y = 0; y < tile; y++) {
            for (int x = 0; x < tile; x++) {
                boolean any = false;
                for (int j = 0; j < zoom; j++) for (int i = 0; i < zoom; i++) any |= ChartCells.known(g[y * zoom + j][x * zoom + i]);
                if (!any) unknownBlocks++;
                assertEquals(any, MapTileRaster.known(px[y * tile + x] & 0xFF), "pixel " + x + "," + y);
            }
        }
        assertTrue(unknownBlocks > 0, "the test has unknown blocks");
    }

    @Test
    void slicesOfABoardLineUpWithOneLargeDrawing() {
        // two slices side by side at zoom 2 are the two halves of one drawing twice as wide (patterns and coast strokes included)
        CellLookup cells = coast(37, 1000);
        int size = 16;
        int zoom = 2;
        int minCx = -11;
        int minCz = 5;
        byte[] left = MapTileRaster.draw(cells, minCx, minCz, size, zoom);
        byte[] right = MapTileRaster.draw(cells, minCx + size * zoom, minCz, size, zoom);
        byte[] whole = MapTileRaster.draw(cells, minCx, minCz, 2 * size, zoom);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                assertEquals(whole[y * 2 * size + x], left[y * size + x], "left " + x + "," + y);
                assertEquals(whole[y * 2 * size + size + x], right[y * size + x], "right " + x + "," + y);
            }
        }
    }

    @Test
    void boardMarkersUseBoardPixelsAtTheZoom() {
        int cb = 4;
        List<ChartMarker> markers = List.of(new ChartMarker(1, 100 * cb, 9 * cb, MarkerIcon.SKULL, "in"),
                new ChartMarker(2, -1, 0, MarkerIcon.X, "west of it"), new ChartMarker(3, 4 * 64 * cb, 0, MarkerIcon.PORT, "east of it"));
        List<com.richardsenger.piratesnships.chart.data.BoardMarker> b = MapTileRaster.boardMarkers(markers, 0, 0, 64, 32, 4, cb, 512);
        assertEquals(1, b.size());
        assertEquals(25, b.get(0).bx(), "cell 100 at zoom 4");
        assertEquals(2, b.get(0).by());
        assertTrue(MapTileRaster.markerMargin(128) >= ChartSheet.ICON / 2, "a margin of half an icon at scale 1");
        assertTrue(MapTileRaster.markerMargin(32) >= 1);
    }

    @Test
    void aSliceDrawsTheFrameOnlyOnTheBoardsOuterEdges() {
        CellLookup cells = coast(16, 1000);
        int size = 32;
        int scale = MapTileRaster.pictureScale(size);
        int w = size * scale;
        java.util.UUID id = java.util.UUID.randomUUID();
        MapTileDrawing westTile = new MapTileDrawing(size, MapTileRaster.draw(cells, 0, 0, size), 0, 0, 4, "Anne", 3, List.of(), 1,
                java.util.Optional.of(new com.richardsenger.piratesnships.chart.data.BoardSlice(id, 0, 0, 2, 1)));
        int[] pic = MapTileRaster.picture(westTile, scale, null, 0, 0);
        assertEquals(ChartRaster.INK, pic[(w / 2) * w], "the board's west edge");
        assertEquals(ChartRaster.INK, pic[w / 2], "the board's north edge");
        assertTrue(pic[(w / 2) * w + w - 1] != ChartRaster.INK, "no frame at the seam");
        int[] sheet = new int[ChartSheet.WIDTH * ChartSheet.HEIGHT];
        java.util.Arrays.fill(sheet, 0xFFFF0000);
        int[] withSprites = MapTileRaster.picture(westTile, scale, sheet, ChartSheet.WIDTH, ChartSheet.HEIGHT);
        assertTrue(withSprites[(3 + 8) * w + (w - 3 - 8)] != 0xFFFF0000, "the rose belongs to the top-right tile only");
        // a marker of the east neighbour stamped just beyond the seam shows its west half here
        MapTileDrawing seam = new MapTileDrawing(size, MapTileRaster.draw(cells, 0, 0, size), 0, 0, 4, "Anne", 3,
                List.of(new TileMarker(MarkerIcon.X, size, 10, "")), 1,
                java.util.Optional.of(new com.richardsenger.piratesnships.chart.data.BoardSlice(id, 0, 0, 2, 1)));
        int[] seamPic = MapTileRaster.picture(seam, scale, sheet, ChartSheet.WIDTH, ChartSheet.HEIGHT);
        assertEquals(0xFFFF0000, seamPic[(10 * scale) * w + w - 1], "the icon reaches over the seam");
    }
}
