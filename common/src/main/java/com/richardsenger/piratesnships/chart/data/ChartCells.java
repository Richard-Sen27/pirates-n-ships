package com.richardsenger.piratesnships.chart.data;

/**
 * The byte of one chart cell: bits 0-2 hold the {@link CellClass} ordinal, bit 3 the coast flag (a land cell with
 * water on at least one of its four sides). Bits 4-7 are free. 0 is an unknown cell.
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
}
