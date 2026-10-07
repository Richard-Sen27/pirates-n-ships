package com.richardsenger.piratesnships.chart.render;

import com.richardsenger.piratesnships.chart.data.ChartData;

/** Reads the cell byte at cell coordinates (0 = unknown), e.g. {@link ChartData#cell} or the client's cache. */
@FunctionalInterface
public interface CellLookup {

    int cell(int cx, int cz);

    static CellLookup of(ChartData data) {
        return data::cell;
    }
}
