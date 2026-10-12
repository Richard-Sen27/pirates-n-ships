package com.richardsenger.piratesnships.chart.render;

import com.richardsenger.piratesnships.chart.data.CellClass;
import com.richardsenger.piratesnships.chart.data.ChartCells;
import com.richardsenger.piratesnships.chart.data.ChartRegion;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CHART2's far tiles: a pixel is a map board's zoomed pixel, tiles group c x c regions, absent regions are blank. */
class ChartZoomOutTest {

    private static Map<Long, ChartRegion> chart(long seed, int... regionCoords) {
        Random r = new Random(seed);
        Map<Long, ChartRegion> m = new HashMap<>();
        for (int i = 0; i < regionCoords.length; i += 2) {
            byte[] cells = new byte[ChartRegion.CELLS];
            for (int k = 0; k < cells.length; k++) {
                int pick = r.nextInt(7);
                cells[k] = pick == 0 ? 0 : (byte) ChartCells.of(CellClass.values()[1 + r.nextInt(CellClass.values().length - 1)], r.nextInt(5) == 0);
            }
            ChartRegion region = ChartRegion.of(regionCoords[i], regionCoords[i + 1], cells, 1, 0);
            m.put(region.key(), region);
        }
        return m;
    }

    private static int cell(Map<Long, ChartRegion> m, int cx, int cz) {
        ChartRegion r = m.get(ChartRegion.keyOfCell(cx, cz));
        return r == null ? 0 : r.get(cx & ChartRegion.MASK, cz & ChartRegion.MASK) & 0xFF;
    }

    @Test
    void aFarPixelIsTheBoardsZoomedPixel() {
        Map<Long, ChartRegion> m = chart(7, 0, 0, -1, 0, 3, -2);
        CellLookup all = (cx, cz) -> cell(m, cx, cz);
        for (int c : new int[]{2, 4, 8, 16, 32}) {
            for (int vz = -70 / c; vz < 70 / c; vz++) {
                for (int vx = -70 / c; vx < 70 / c; vx++) {
                    assertEquals(MapTileRaster.downsample(all, vx * c, vz * c, c), ChartZoomOut.pixel(m::get, vx, vz, c),
                            "c " + c + " pixel " + vx + "," + vz);
                }
            }
        }
    }

    @Test
    void tilesGroupRegions() {
        assertEquals(0, ChartZoomOut.tileOf(0, 8));
        assertEquals(0, ChartZoomOut.tileOf(7, 8));
        assertEquals(1, ChartZoomOut.tileOf(8, 8));
        assertEquals(-1, ChartZoomOut.tileOf(-1, 8));
        assertEquals(-1, ChartZoomOut.tileOf(-8, 8));
        assertEquals(-2, ChartZoomOut.tileOf(-9, 8));
    }

    @Test
    void aTileRendersItsRegionsAndLeavesTheRestBlank() {
        Map<Long, ChartRegion> m = chart(3, 0, 0);
        int[] px = ChartZoomOut.render(m::get, 0, 0, 4);
        assertEquals(ChartZoomOut.TILE * ChartZoomOut.TILE, px.length);
        // region (0,0) at 4 cells per pixel fills the top-left 16 x 16 pixels; the rest of the tile is parchment
        int drawn = 0;
        for (int y = 0; y < ChartZoomOut.TILE; y++) {
            for (int x = 0; x < ChartZoomOut.TILE; x++) {
                boolean inside = x < 16 && y < 16;
                if (!inside) assertEquals(0, px[y * ChartZoomOut.TILE + x], "outside the region at " + x + "," + y);
                else if (px[y * ChartZoomOut.TILE + x] != 0) drawn++;
            }
        }
        assertTrue(drawn > 200, "the region is drawn: " + drawn);
        int[] empty = ChartZoomOut.render(m::get, 5, 5, 4);
        for (int v : empty) assertEquals(0, v, "a tile without regions is blank");
    }
}
