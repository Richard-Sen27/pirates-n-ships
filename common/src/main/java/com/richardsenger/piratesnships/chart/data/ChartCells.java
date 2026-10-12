package com.richardsenger.piratesnships.chart.data;

/**
 * The byte of one chart cell: bits 0-2 hold the {@link CellClass} ordinal, bit 3 the coast flag (a land cell with
 * water on at least one of its four sides). Bits 4-7 are free. 0 is an unknown cell.
 *
 * <p>CHART4: a cell may also carry a colour, vanilla's packed map colour ({@code MapColor} id times four plus the
 * brightness step, 0 = none), kept beside the cell bytes ({@link ChartRegion#colour}). Readers that want both get a
 * <b>wide cell</b>: the cell byte in bits 0-7 and the colour in bits 8-15 ({@link #wide}); every check here masks
 * the low bits, so a wide cell can go wherever a cell byte goes.
 */
public final class ChartCells {

    public static final int CLASS_MASK = 0x07;
    public static final int COAST = 0x08;

    private ChartCells() {
    }

    public static CellClass cellClass(int cell) {
        return CellClass.of(cell & CLASS_MASK);
    }

    public static boolean coast(int cell) {
        return (cell & COAST) != 0;
    }

    public static boolean known(int cell) {
        return (cell & CLASS_MASK) != 0;
    }

    public static byte of(CellClass cls, boolean coast) {
        return (byte) (cls.ordinal() | (coast ? COAST : 0));
    }

    /** CHART4: a wide cell, the cell byte with the packed map colour {@code colour} (0 = none) above it. */
    public static int wide(int cell, int colour) {
        return (cell & 0xFF) | (colour & 0xFF) << 8;
    }

    /** CHART4: the packed map colour of a wide cell, 0 when it has none. */
    public static int colour(int wide) {
        return (wide >>> 8) & 0xFF;
    }

    /** CHART4: the cell byte of a wide cell (the colour dropped). */
    public static int narrow(int wide) {
        return wide & 0xFF;
    }
}
