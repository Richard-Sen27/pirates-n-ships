package com.richardsenger.piratesnships.chart.render;

/**
 * How the chart draws land (CHART4, config {@code chart.style}). The sea, the coast strokes, the doodles and every
 * mark look the same in both.
 */
public enum ChartStyle {
    /** The pirate look of MAP1: land in parchment inks by its class (sand, land, snow), cell colours ignored. */
    INK,
    /** Land shows the vanilla map colour of each cell, softened onto the parchment; cells without a colour draw in ink. */
    COLOURED;

    private static final ChartStyle[] VALUES = values();

    public static ChartStyle of(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : INK;
    }
}
