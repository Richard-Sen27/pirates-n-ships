package com.richardsenger.piratesnships.chart.render;

/**
 * How the chart draws land (CHART4, config {@code chart.style}). The sea, the coast strokes, the doodles and every
 * mark look the same in all.
 */
public enum ChartStyle {
    /**
     * The pirate look of MAP1: land in parchment inks by its class (sand, land, snow). CHART5: cells flagged built
     * ({@link com.richardsenger.piratesnships.chart.data.ChartCells#BUILT}) with a colour draw in that colour, softened
     * onto the parchment, over the stipple, so a settlement shows on an otherwise minimal chart.
     */
    INK,
    /** Land shows the vanilla map colour of each cell, softened onto the parchment; cells without a colour draw in ink. */
    COLOURED,
    /** Pure ink: cell colours and the built flag ignored (map boards, treasure maps, and a config choice). */
    PLAIN;

    private static final ChartStyle[] VALUES = values();

    public static ChartStyle of(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : INK;
    }
}
