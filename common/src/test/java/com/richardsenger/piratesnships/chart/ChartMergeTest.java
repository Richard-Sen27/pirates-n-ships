package com.richardsenger.piratesnships.chart;

import com.richardsenger.piratesnships.chart.data.CellClass;
import com.richardsenger.piratesnships.chart.data.ChartCells;
import com.richardsenger.piratesnships.chart.data.ChartData;
import com.richardsenger.piratesnships.chart.data.ChartMerge;
import com.richardsenger.piratesnships.chart.data.ChartRegion;
import com.richardsenger.piratesnships.chart.data.SampleGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Merging samples into a chart (MAP1): classes, the coast flag, no forgetting, versions and the cap. */
class ChartMergeTest {

    private static final long NO_CAP = Long.MAX_VALUE;

    /** A grid from rows of letters: . unknown, D deep, S shallow, B beach, L land, I snow/ice. */
    private static SampleGrid grid(int minCx, int minCz, String... rows) {
        SampleGrid g = SampleGrid.empty(minCx, minCz, rows[0].length(), rows.length);
        for (int z = 0; z < rows.length; z++) {
            for (int x = 0; x < rows[z].length(); x++) {
                g.set(minCx + x, minCz + z, switch (rows[z].charAt(x)) {
                    case 'D' -> CellClass.DEEP_WATER;
                    case 'S' -> CellClass.SHALLOW_WATER;
                    case 'B' -> CellClass.BEACH;
                    case 'L' -> CellClass.LAND;
                    case 'I' -> CellClass.SNOW_ICE;
                    default -> CellClass.UNKNOWN;
                });
            }
        }
        return g;
    }

    private static CellClass cls(ChartData d, int cx, int cz) {
        return ChartCells.cellClass(d.cell(cx, cz));
    }

    private static boolean coast(ChartData d, int cx, int cz) {
        return ChartCells.coast(d.cell(cx, cz));
    }

    @Test
    void landNextToWaterIsCoastAndNothingElse() {
        ChartData d = ChartMerge.merge(ChartData.EMPTY, grid(0, 0,
                "LLBSD",
                "LLBSD",
                "LIIII"), 10, NO_CAP).data();
        assertEquals(CellClass.LAND, cls(d, 0, 0));
        assertEquals(CellClass.BEACH, cls(d, 2, 0));
        assertEquals(CellClass.SHALLOW_WATER, cls(d, 3, 0));
        assertEquals(CellClass.DEEP_WATER, cls(d, 4, 1));
        assertTrue(coast(d, 2, 0), "beach next to shallows");
        assertTrue(coast(d, 3, 2), "ice below shallows");
        assertTrue(coast(d, 4, 2), "ice below deep water");
        assertFalse(coast(d, 1, 0), "land next to beach only");
        assertFalse(coast(d, 3, 0), "water is never coast");
        assertFalse(coast(d, 0, 2), "no water around");
    }

    @Test
    void coastFlagsUpdateWhenWaterIsChartedLater() {
        ChartData first = ChartMerge.merge(ChartData.EMPTY, grid(0, 0, "LL"), 1, NO_CAP).data();
        assertFalse(coast(first, 1, 0));
        // the next sample only sees the water east of it: the old land cell becomes coast
        ChartData second = ChartMerge.merge(first, grid(2, 0, "D"), 2, NO_CAP).data();
        assertTrue(coast(second, 1, 0));
        assertFalse(coast(second, 0, 0));
    }

    @Test
    void coastWorksAcrossRegionBorders() {
        ChartData d = ChartMerge.merge(ChartData.EMPTY, grid(-1, 63, "LD", "LD"), 1, NO_CAP).data();
        assertEquals(4, d.regions().size(), "the 2x2 grid spans four regions");
        assertTrue(coast(d, -1, 63));
        assertTrue(coast(d, -1, 64));
    }

    @Test
    void unsampledCellsAreNeverForgotten() {
        ChartData first = ChartMerge.merge(ChartData.EMPTY, grid(0, 0, "LBSD"), 1, NO_CAP).data();
        // a later sample with gaps (unloaded chunks, outside the circle) keeps the old cells
        ChartData second = ChartMerge.merge(first, grid(0, 0, "..L."), 2, NO_CAP).data();
        assertEquals(CellClass.LAND, cls(second, 0, 0));
        assertEquals(CellClass.BEACH, cls(second, 1, 0));
        assertEquals(CellClass.LAND, cls(second, 2, 0), "a sampled cell takes its new class");
        assertEquals(CellClass.DEEP_WATER, cls(second, 3, 0));
        assertTrue(coast(second, 2, 0), "the new land next to deep water is coast");
        assertFalse(coast(second, 1, 0), "the beach lost its water neighbour");
    }

    @Test
    void versionsGrowOnlyWithChanges() {
        ChartMerge.Result r1 = ChartMerge.merge(ChartData.EMPTY, grid(0, 0, "LD"), 5, NO_CAP);
        assertEquals(1, r1.data().version());
        assertEquals(1, r1.changedRegions().size());
        ChartRegion region = r1.data().regions().get(ChartRegion.key(0, 0));
        assertEquals(1, region.version());
        assertEquals(5, region.touched());
        ChartMerge.Result same = ChartMerge.merge(r1.data(), grid(0, 0, "LD"), 9, NO_CAP);
        assertEquals(1, same.data().version(), "the same sample changes nothing");
        assertEquals(0, same.changedCells());
        assertTrue(same.changedRegions().isEmpty());
        ChartRegion again = same.data().regions().get(ChartRegion.key(0, 0));
        assertEquals(1, again.version());
        assertEquals(9, again.touched(), "but the region counts as visited");
        ChartMerge.Result changed = ChartMerge.merge(same.data(), grid(0, 0, "BD"), 12, NO_CAP);
        assertEquals(2, changed.data().version());
        assertEquals(2, changed.data().regions().get(ChartRegion.key(0, 0)).version());
    }

    @Test
    void anEmptySampleKeepsTheDataAsItIs() {
        ChartData d = ChartMerge.merge(ChartData.EMPTY, grid(0, 0, "LD"), 5, NO_CAP).data();
        ChartMerge.Result r = ChartMerge.merge(d, SampleGrid.empty(100, 100, 3, 3), 6, NO_CAP);
        assertEquals(d.version(), r.data().version());
        assertSame(d.regions().get(ChartRegion.key(0, 0)), r.data().regions().get(ChartRegion.key(0, 0)));
    }

    @Test
    void theCapDropsTheLeastRecentlyVisitedRegions() {
        long cap = 3L * ChartRegion.CELLS;
        ChartData d = ChartData.EMPTY;
        // visit four regions one after another, the first one again later
        d = ChartMerge.merge(d, grid(0, 0, "L"), 1, cap).data();
        d = ChartMerge.merge(d, grid(64, 0, "L"), 2, cap).data();
        d = ChartMerge.merge(d, grid(128, 0, "L"), 3, cap).data();
        d = ChartMerge.merge(d, grid(0, 0, "L"), 4, cap).data();
        assertEquals(3, d.regions().size());
        ChartMerge.Result r = ChartMerge.merge(d, grid(192, 0, "D"), 5, cap);
        assertEquals(3, r.data().regions().size(), "never more than max_cells");
        assertEquals(1, r.droppedRegions().size());
        assertEquals(ChartRegion.key(1, 0), r.droppedRegions().get(0), "region (1,0) was visited longest ago");
        assertTrue(r.data().regions().containsKey(ChartRegion.key(0, 0)), "the revisited one stays");
        assertTrue(r.data().regions().containsKey(ChartRegion.key(3, 0)), "the new one stays");
        assertEquals(3L * ChartRegion.CELLS, r.data().storedCells());
    }

    @Test
    void maxRegionsIsAtLeastOne() {
        assertEquals(1, ChartMerge.maxRegions(0));
        assertEquals(256, ChartMerge.maxRegions(1_048_576));
    }
}
