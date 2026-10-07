package com.richardsenger.piratesnships.chart;

import com.richardsenger.piratesnships.chart.data.CellClass;
import com.richardsenger.piratesnships.chart.data.ChartData;
import com.richardsenger.piratesnships.chart.data.ChartMerge;
import com.richardsenger.piratesnships.chart.data.ChartRegion;
import com.richardsenger.piratesnships.chart.data.SampleGrid;
import com.richardsenger.piratesnships.chart.net.ChartStreaming;
import com.richardsenger.piratesnships.chart.render.CellLookup;
import com.richardsenger.piratesnships.chart.render.ChartDoodles;
import com.richardsenger.piratesnships.chart.render.ChartRaster;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pirate-style raster (shared with MAP2), the doodle placement and which regions a client still needs (MAP1). */
class ChartRenderTest {

    /** Land west of x = 10, deep water east of it, inside cells 0..19 x 0..19; unknown elsewhere. */
    private static ChartData coast() {
        SampleGrid g = SampleGrid.empty(0, 0, 20, 20);
        for (int z = 0; z < 20; z++) {
            for (int x = 0; x < 20; x++) g.set(x, z, x < 10 ? CellClass.LAND : CellClass.DEEP_WATER);
        }
        return ChartMerge.merge(ChartData.EMPTY.resetCells(4), g, 1, Long.MAX_VALUE).data();
    }

    @Test
    void unknownIsTransparentAndKnownIsNot() {
        CellLookup cells = CellLookup.of(coast());
        int[] px = ChartRaster.render(cells, 18, 0, 4, 1, 1);
        assertEquals(4, px.length);
        assertNotEquals(0, px[0] >>> 24, "deep water has a wash");
        assertNotEquals(0, px[1] >>> 24);
        assertEquals(0, px[2], "unknown cells are transparent");
        assertEquals(0, px[3]);
    }

    @Test
    void theCoastIsInkedOnTheLandSide() {
        CellLookup cells = CellLookup.of(coast());
        for (int ppc : new int[]{1, 2, 4}) {
            int[] px = ChartRaster.render(cells, 8, 5, 4, 1, ppc);
            int width = 4 * ppc;
            // cell 9 (land, coast) has ink in its east column on every row; cell 8 none
            for (int j = 0; j < ppc; j++) {
                assertEquals(ChartRaster.INK, px[j * width + 2 * ppc - 1], "ink on the coast at " + ppc + " px per cell");
                for (int i = 0; i < ppc; i++) assertNotEquals(ChartRaster.INK, px[j * width + i], "no ink inland");
            }
            if (ppc >= 2) assertNotEquals(ChartRaster.INK, px[ppc], "the coast cell's west side is plain land");
        }
    }

    @Test
    void regionsJoinSeamlessly() {
        CellLookup cells = CellLookup.of(coast());
        int ppc = 2;
        int[] big = ChartRaster.render(cells, 0, 0, 20, 20, ppc);
        int[] part = ChartRaster.render(cells, 7, 3, 6, 5, ppc);
        for (int y = 0; y < 5 * ppc; y++) {
            for (int x = 0; x < 6 * ppc; x++) {
                assertEquals(big[(3 * ppc + y) * 20 * ppc + 7 * ppc + x], part[y * 6 * ppc + x], "pixel " + x + "," + y);
            }
        }
        int[] region = ChartRaster.renderRegion(cells, 0, 0, 1);
        assertEquals(ChartRegion.CELLS, region.length);
        assertArrayEquals(region, ChartRaster.renderRegion(cells, 0, 0, 1), "deterministic");
    }

    @Test
    void doodlesOnlyInOpenDeepWater() {
        CellLookup sea = (cx, cz) -> CellClass.DEEP_WATER.ordinal();
        CellLookup land = (cx, cz) -> CellClass.LAND.ordinal();
        int placed = 0;
        for (int rx = -10; rx < 10; rx++) {
            for (int rz = -10; rz < 10; rz++) {
                Optional<ChartDoodles.Doodle> d = ChartDoodles.place(sea, rx, rz);
                assertEquals(d, ChartDoodles.place(sea, rx, rz), "deterministic");
                if (d.isPresent()) {
                    placed++;
                    ChartDoodles.Doodle doodle = d.get();
                    assertEquals(rx, doodle.cx() >> ChartRegion.SHIFT, "inside its region");
                    assertEquals(rx, (doodle.cx() + doodle.kind().w - 1) >> ChartRegion.SHIFT, "the footprint fits");
                    assertEquals(rz, (doodle.cz() + doodle.kind().h - 1) >> ChartRegion.SHIFT);
                }
                assertTrue(ChartDoodles.place(land, rx, rz).isEmpty(), "never on land");
            }
        }
        assertTrue(placed > 400 / 6 && placed < 400 / 2, "about a third of the open-sea regions: " + placed);
    }

    @Test
    void streamingSendsOnlyNewVersionsInViewNearestFirst() {
        Map<Long, ChartRegion> regions = new HashMap<>();
        for (int rx = -3; rx <= 3; rx++) {
            for (int rz = -3; rz <= 3; rz++) {
                regions.put(ChartRegion.key(rx, rz), ChartRegion.of(rx, rz, new byte[ChartRegion.CELLS], 5, 0));
            }
        }
        Map<Long, Long> sent = new HashMap<>();
        // view centred in region (0,0), 70 cells each way: cells -38..102 overlap regions -1..1
        List<ChartRegion> first = ChartStreaming.pending(regions, sent, 32, 32, 70, 100);
        assertEquals(9, first.size());
        assertEquals(ChartRegion.key(0, 0), first.get(0).key(), "the centre region first");
        assertTrue(first.stream().allMatch(r -> Math.abs(r.rx()) <= 1 && Math.abs(r.rz()) <= 1));
        for (ChartRegion r : first) sent.put(r.key(), r.version());
        assertTrue(ChartStreaming.pending(regions, sent, 32, 32, 70, 100).isEmpty(), "nothing twice");
        regions.put(ChartRegion.key(1, 1), ChartRegion.of(1, 1, new byte[ChartRegion.CELLS], 6, 0));
        List<ChartRegion> again = ChartStreaming.pending(regions, sent, 32, 32, 70, 100);
        assertEquals(1, again.size(), "only the changed region");
        assertEquals(2, ChartStreaming.pending(regions, Map.of(), 32, 32, 70, 2).size(), "the limit");
    }
}
