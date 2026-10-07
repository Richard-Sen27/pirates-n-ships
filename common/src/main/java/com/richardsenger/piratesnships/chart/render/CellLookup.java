package com.richardsenger.piratesnships.chart.render;

import com.richardsenger.piratesnships.chart.data.ChartData;
import com.richardsenger.piratesnships.chart.data.ChartRegion;

/** Reads the cell byte at cell coordinates (0 = unknown), e.g. {@link ChartData#cell} or the client's cache. */
@FunctionalInterface
public interface CellLookup {

    int cell(int cx, int cz);

    static CellLookup of(ChartData data) {
        return data::cell;
    }

    /**
     * {@link #of} that remembers the last region it read: drawing a board at a high zoom reads millions of cells in
     * runs inside one region, so this saves a map lookup per cell (MAP3). Not thread-safe; use one per drawing.
     */
    static CellLookup cached(ChartData data) {
        return new CellLookup() {
            private long lastKey;
            private ChartRegion last;
            private boolean any;

            @Override
            public int cell(int cx, int cz) {
                long key = ChartRegion.keyOfCell(cx, cz);
                if (!any || key != lastKey) {
                    any = true;
                    lastKey = key;
                    last = data.regions().get(key);
                }
                return last == null ? 0 : last.get(cx & ChartRegion.MASK, cz & ChartRegion.MASK);
            }
        };
    }
}
