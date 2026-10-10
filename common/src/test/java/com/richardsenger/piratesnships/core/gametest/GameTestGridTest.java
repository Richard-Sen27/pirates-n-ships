package com.richardsenger.piratesnships.core.gametest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameTestGridTest {

    private static List<int[]> squares(int size, int count) {
        List<int[]> sizes = new ArrayList<>();
        for (int i = 0; i < count; i++) sizes.add(new int[]{size, size});
        return sizes;
    }

    /** The suite's template mix at TST1 (1,324 tests), shuffled with a fixed seed. */
    private static List<int[]> suite() {
        List<int[]> sizes = new ArrayList<>();
        sizes.addAll(squares(3, 234));
        sizes.addAll(squares(9, 401));
        sizes.addAll(squares(24, 301));
        sizes.addAll(squares(40, 279));
        sizes.addAll(squares(48, 94));
        sizes.addAll(squares(160, 15));
        Collections.shuffle(sizes, new Random(7));
        return sizes;
    }

    @Test
    void extentFollowsVanillasGridSpawner() {
        // a row of 8: 8 × 40 + 7 gaps of 5; two rows: 40 + 6 + 40
        int[] e = GameTestGrid.extent(squares(40, 9), 8);
        assertEquals(355, e[0]);
        assertEquals(86, e[1]);
        // the deepest test of a row sets where the next row starts
        List<int[]> mixed = new ArrayList<>(List.of(new int[]{9, 9}, new int[]{48, 48}, new int[]{3, 3}));
        e = GameTestGrid.extent(mixed, 2);
        assertEquals(9 + 5 + 48, e[0]);
        assertEquals(48 + 6 + 3, e[1]);
    }

    @Test
    void vanillasRowsOfEightRanPastTheSafeRange() {
        // why TST1 happened: 8 per row, the corner drawn up to z 4,096, the last rows beyond z 8,192
        int[] e = GameTestGrid.extent(suite(), 8);
        assertTrue(e[1] > 4_000, "8-wide grid depth " + e[1]);
        assertFalse(GameTestGrid.inSafeRange(0, 4_096 + e[1]));
    }

    @Test
    void planIsASquareCentredOnTheOrigin() {
        GameTestGrid.Plan p = GameTestGrid.plan(suite());
        assertTrue(p.perRow() > 8, "per row " + p.perRow());
        assertTrue(Math.abs(p.width() - p.depth()) < Math.max(p.width(), p.depth()) / 3, "not square: " + p);
        assertEquals(-p.width() / 2, p.startX());
        assertEquals(-p.depth() / 2, p.startZ());
        // every test of the suite, with room for twice as many tests, stays well inside the safe range
        assertTrue(Math.max(p.width(), p.depth()) / 2 < 1_500, "half size " + p);
        List<int[]> doubled = new ArrayList<>(suite());
        doubled.addAll(suite());
        GameTestGrid.Plan q = GameTestGrid.plan(doubled);
        assertTrue(GameTestGrid.inSafeRange(q.startX(), q.startZ()), "corner " + q);
        assertTrue(GameTestGrid.inSafeRange(q.startX() + q.width(), q.startZ() + q.depth()), "far corner " + q);
    }

    @Test
    void smallRunsKeepVanillasRowLength() {
        GameTestGrid.Plan p = GameTestGrid.plan(squares(24, 3));
        assertEquals(8, p.perRow());
        assertEquals(3 * 24 + 2 * 5, p.width());
        assertEquals(24, p.depth());
        assertEquals(-41, p.startX());
        assertEquals(-12, p.startZ());
    }

    @Test
    void safeRangeBounds() {
        assertTrue(GameTestGrid.inSafeRange(0, 0));
        assertTrue(GameTestGrid.inSafeRange(-GameTestGrid.SAFE_RANGE, GameTestGrid.SAFE_RANGE));
        assertFalse(GameTestGrid.inSafeRange(GameTestGrid.SAFE_RANGE + 1, 0));
        assertFalse(GameTestGrid.inSafeRange(0, -GameTestGrid.SAFE_RANGE - 1));
        assertFalse(GameTestGrid.inSafeRange(0, 8_250));
    }
}
