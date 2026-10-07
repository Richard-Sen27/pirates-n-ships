package com.richardsenger.piratesnships.chart.data;

/**
 * What one chart cell shows (work package MAP1): the low three bits of a cell byte ({@link ChartCells}). The ordinal
 * is the stored value, so never reorder; new classes go at the end (up to 7).
 */
public enum CellClass {
    /** Not explored yet (blank parchment). */
    UNKNOWN,
    /** Water deeper than {@code chart.shallow_depth}. */
    DEEP_WATER,
    /** Water up to {@code chart.shallow_depth} deep. */
    SHALLOW_WATER,
    /** Sand, gravel, sandstone. */
    BEACH,
    /** Everything else on land: grass, forest, stone. */
    LAND,
    /** Snow and ice (also frozen sea). */
    SNOW_ICE;

    private static final CellClass[] VALUES = values();

    public static CellClass of(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : UNKNOWN;
    }

    public boolean isWater() {
        return this == DEEP_WATER || this == SHALLOW_WATER;
    }

    /** Land of any kind: beach, land, snow and ice. These cells get the coast flag next to water. */
    public boolean isLand() {
        return this == BEACH || this == LAND || this == SNOW_ICE;
    }

    public boolean known() {
        return this != UNKNOWN;
    }
}
