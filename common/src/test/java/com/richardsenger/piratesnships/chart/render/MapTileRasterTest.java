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
}
