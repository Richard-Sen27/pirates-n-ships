package com.richardsenger.piratesnships.sailing.sail;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CAN3: the holes chain shot tears into a square sail's cloth. */
class ClothTearsTest {

    /** Two 7-wide yards 3 apart: 21 cells, columns −3..3, rows 0..2. */
    private static final ClothGeometry RECT = new ClothGeometry(true, 3.5f, 3.5f, 3.5f, 3.5f, 3);
    /** A 3-wide upper and a 7-wide lower yard 4 apart: half widths 1.75, 2.25, 2.75, 3.25 at the row centres. */
    private static final ClothGeometry TRAPEZOID = new ClothGeometry(false, 1.5f, 1.5f, 3.5f, 3.5f, 4);

    @Test
    void packingRoundTripsNegativeColumns() {
        for (int c = -9; c <= 9; c++) {
            for (int r = 0; r < 9; r++) {
                int p = ClothTears.pack(c, r);
                assertEquals(c, ClothTears.column(p));
                assertEquals(r, ClothTears.row(p));
            }
        }
    }

    @Test
    void cellsFollowTheTrapezoid() {
        assertEquals(21, ClothTears.cellCount(RECT));
        assertTrue(ClothTears.inCloth(RECT, -3, 0) && ClothTears.inCloth(RECT, 3, 2));
        assertFalse(ClothTears.inCloth(RECT, 4, 0) || ClothTears.inCloth(RECT, 0, 3) || ClothTears.inCloth(RECT, 0, -1));
        // half widths at the row centres: 1.75, 2.25, 2.75, 3.25 -> 3, 5, 5, 7 cells
        assertEquals(3 + 5 + 5 + 7, ClothTears.cellCount(TRAPEZOID));
        assertFalse(ClothTears.inCloth(TRAPEZOID, 3, 0));
        assertTrue(ClothTears.inCloth(TRAPEZOID, 3, 3));
    }

    @Test
    void theRadiusPicksTheCellsWhoseCentresItReaches() {
        int[] cells = ClothTears.cellsWithin(RECT, 2.0, 1.5, 1.5);
        assertEquals(9, cells.length, () -> "cells " + ClothTears.of(cells));
        for (int p : cells) {
            assertTrue(ClothTears.column(p) >= 1 && ClothTears.column(p) <= 3, "column " + ClothTears.column(p));
        }
        // at the edge only the cloth's own cells count
        assertEquals(3, ClothTears.cellsWithin(RECT, 3.4, 0.2, 1.5).length, "corner cells");
        assertEquals(0, ClothTears.cellsWithin(RECT, 9, 1.5, 1.5).length);
        assertEquals(1, ClothTears.cellsWithin(RECT, 0, 1.5, 0.0).length, "a zero radius at a cell's centre");
    }

    @Test
    void theWholeShareScalesWithTheTornCellsOfTheCloth() {
        ClothTears t = ClothTears.of(ClothTears.cellsWithin(RECT, 2.0, 1.5, 1.5));
        assertEquals(12.0 / 21.0, t.intactFraction(RECT), 1.0e-9);
        assertEquals(1.0, ClothTears.NONE.intactFraction(RECT), 1.0e-9);
        // a hole outside a smaller cloth does not count, and inside() drops it
        ClothTears far = ClothTears.of(new int[]{ClothTears.pack(9, 0), ClothTears.pack(0, 0)});
        assertEquals(20.0 / 21.0, far.intactFraction(RECT), 1.0e-9);
        assertEquals(1, far.inside(RECT).size());
        ClothTears all = ClothTears.of(ClothTears.cellsWithin(RECT, 0, 1.5, 10));
        assertEquals(0.0, all.intactFraction(RECT), 1.0e-9);
    }

    @Test
    void tearsAddUpOnceAndMendFromTheYardDown() {
        ClothTears t = ClothTears.NONE.with(new int[]{ClothTears.pack(1, 2), ClothTears.pack(-1, 0)}).with(new int[]{ClothTears.pack(1, 2)});
        assertEquals(2, t.size());
        assertTrue(t.torn(-1, 0) && t.torn(1, 2) && !t.torn(0, 0));
        ClothTears once = t.mendOne();
        assertFalse(once.torn(-1, 0), "the row at the yard mends first");
        assertTrue(once.torn(1, 2));
        assertTrue(once.mendOne().isEmpty());
        assertTrue(ClothTears.NONE.mendOne().isEmpty());
        assertArrayEquals(t.packed(), ClothTears.of(t.packed()).packed());
        assertEquals(t, ClothTears.of(new int[]{ClothTears.pack(-1, 0), ClothTears.pack(1, 2), ClothTears.pack(-1, 0)}));
    }

    @Test
    void theCellAboveAHoleIsFrayed() {
        ClothTears t = ClothTears.of(new int[]{ClothTears.pack(0, 2)});
        assertTrue(t.frayedAbove(0, 1));
        assertFalse(t.frayedAbove(0, 0));
        assertFalse(t.frayedAbove(0, 2), "a hole itself is not drawn");
        assertFalse(t.frayedAbove(1, 1));
    }

    @Test
    void aStepThroughTheDrawnClothCrossesAtTheAxis() {
        // straight through at right angles: u 2, v 1.5
        double[] hit = ClothTears.crossing(RECT, 3.0, new double[]{2, -4, 1.5}, new double[]{2, 4, 1.5}, 1.2);
        assertNotNull(hit);
        assertEquals(2.0, hit[0], 1.0e-9);
        assertEquals(1.5, hit[1], 1.0e-9);
        // slanting: crosses where across is 0
        hit = ClothTears.crossing(RECT, 3.0, new double[]{0, -1, 0.5}, new double[]{2, 3, 2.5}, 1.2);
        assertNotNull(hit);
        assertEquals(0.5, hit[0], 1.0e-9);
        assertEquals(1.0, hit[1], 1.0e-9);
        // a step that ends inside the slab without crossing counts at its nearer end
        hit = ClothTears.crossing(RECT, 3.0, new double[]{1, -3, 1}, new double[]{1, -0.5, 1}, 1.2);
        assertNotNull(hit);
        assertEquals(1.0, hit[0], 1.0e-9);
        // missing: beside the cloth, below the drawn part, furled, or never near the slab
        assertNull(ClothTears.crossing(RECT, 3.0, new double[]{5, -4, 1.5}, new double[]{5, 4, 1.5}, 1.2));
        assertNull(ClothTears.crossing(RECT, 1.5, new double[]{0, -4, 2.0}, new double[]{0, 4, 2.0}, 1.2), "below a reefed sail");
        assertNull(ClothTears.crossing(RECT, 0.0, new double[]{0, -4, 0.5}, new double[]{0, 4, 0.5}, 1.2), "furled");
        assertNull(ClothTears.crossing(RECT, 3.0, new double[]{0, -4, 0.5}, new double[]{0, -2, 0.5}, 1.2));
        assertNull(ClothTears.crossing(RECT, 3.0, new double[]{0, -4, -1}, new double[]{0, 4, -1}, 1.2), "above the yard");
    }

    @Test
    void cellCoordinatesRoundToTheBlockColumns() {
        assertEquals(0, ClothTears.columnAt(0.49));
        assertEquals(1, ClothTears.columnAt(0.5));
        assertEquals(-1, ClothTears.columnAt(-0.51));
        assertEquals(0, ClothTears.rowAt(0.99));
        assertEquals(2, ClothTears.rowAt(2.0));
        assertTrue(Arrays.stream(ClothTears.cellsWithin(TRAPEZOID, 0, 0.5, 0.6)).allMatch(p -> ClothTears.row(p) == 0));
    }
}
