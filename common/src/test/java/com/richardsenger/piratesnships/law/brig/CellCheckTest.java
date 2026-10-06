package com.richardsenger.piratesnships.law.brig;

import com.richardsenger.piratesnships.law.brig.CellCheck.Cell;
import com.richardsenger.piratesnships.law.brig.CellCheck.Outcome;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CellCheckTest {

    /** Sparse grid: everything is air except what is set. */
    private static final class Grid implements CellCheck.Grid {
        private final Map<String, Cell> cells = new HashMap<>();

        Grid set(int x, int y, int z, Cell c) {
            cells.put(x + "," + y + "," + z, c);
            return this;
        }

        Grid box(int x0, int y0, int z0, int x1, int y1, int z1, Cell c) {
            for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) set(x, y, z, c);
            return this;
        }

        @Override
        public Cell at(int x, int y, int z) {
            return cells.getOrDefault(x + "," + y + "," + z, Cell.PASSABLE);
        }
    }

    /** Solid shell around an interior from (0,0,0) to (w-1,h-1,d-1), interior air, one locked door in the wall. */
    private static Grid room(int w, int h, int d) {
        Grid g = new Grid().box(-1, -1, -1, w, h, d, Cell.WALL).box(0, 0, 0, w - 1, h - 1, d - 1, Cell.PASSABLE);
        return g.set(0, 0, -1, Cell.LOCKED_DOOR).set(0, 1, -1, Cell.LOCKED_DOOR);
    }

    @Test
    void enclosedRoomWithLockedDoorIsCell() {
        CellCheck.Result r = CellCheck.check(room(2, 2, 2), 0, 0, 0, 64);
        assertEquals(Outcome.CELL, r.outcome());
        assertEquals(8, r.size());
        assertEquals(2, r.lockedDoors());
    }

    @Test
    void barsCountAsWalls() {
        Grid g = room(1, 2, 1).set(1, 0, 0, Cell.BARS).set(1, 1, 0, Cell.BARS);
        CellCheck.Result r = CellCheck.check(g, 0, 0, 0, 64);
        assertTrue(r.isCell());
        assertEquals(2, r.bars());
    }

    @Test
    void oneMissingBlockLeaks() {
        Grid g = room(3, 2, 3).set(3, 1, 1, Cell.PASSABLE);
        assertEquals(Outcome.NOT_ENCLOSED, CellCheck.check(g, 1, 0, 1, 64).outcome());
    }

    @Test
    void openCeilingLeaks() {
        Grid g = room(2, 2, 2).box(0, 2, 0, 1, 2, 1, Cell.PASSABLE);
        assertEquals(Outcome.NOT_ENCLOSED, CellCheck.check(g, 0, 0, 0, 500).outcome());
    }

    @Test
    void unlockedDoorIsAGap() {
        // An unlocked or open brig door is classified as passable by the world adapter: same as a hole
        Grid g = room(2, 2, 2).set(0, 0, -1, Cell.PASSABLE).set(0, 1, -1, Cell.PASSABLE);
        assertEquals(Outcome.NOT_ENCLOSED, CellCheck.check(g, 0, 0, 0, 64).outcome());
    }

    @Test
    void enclosureWithoutLockedDoorIsNoCell() {
        Grid g = new Grid().box(-1, -1, -1, 2, 2, 2, Cell.WALL).box(0, 0, 0, 1, 1, 1, Cell.PASSABLE);
        assertEquals(Outcome.NO_LOCKED_DOOR, CellCheck.check(g, 0, 0, 0, 64).outcome());
    }

    @Test
    void sizeLimitIsInclusive() {
        // 4 x 2 x 4 = 32 cells
        assertTrue(CellCheck.check(room(4, 2, 4), 0, 0, 0, 32).isCell());
        assertEquals(Outcome.NOT_ENCLOSED, CellCheck.check(room(4, 2, 4), 0, 0, 0, 31).outcome());
    }

    @Test
    void diagonalGapDoesNotLeak() {
        // Two walls meeting only at a corner still close the room in 6-connectivity: remove an edge block of the shell
        Grid g = room(2, 2, 2).set(-1, 0, -1, Cell.PASSABLE).set(-1, 1, -1, Cell.PASSABLE);
        assertTrue(CellCheck.check(g, 0, 0, 0, 64).isCell());
    }

    @Test
    void solidStartIsBlocked() {
        assertEquals(Outcome.START_BLOCKED, CellCheck.check(room(2, 2, 2), -1, 0, 0, 64).outcome());
    }

    @Test
    void openSpaceStopsAtLimit() {
        CellCheck.Result r = CellCheck.check(new Grid(), 0, 0, 0, 100);
        assertEquals(Outcome.NOT_ENCLOSED, r.outcome());
        assertEquals(101, r.size());
    }
}
