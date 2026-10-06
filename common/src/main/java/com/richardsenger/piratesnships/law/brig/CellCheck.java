package com.richardsenger.piratesnships.law.brig;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Whether a position is inside a brig cell (design.md §13.3): a small space enclosed on all six sides by walls,
 * brig bars and at least one closed, locked brig door. Pure: works on an abstract {@link Grid}. A bounded flood fill
 * over passable cells (6-connected, mobs can't squeeze through corners) from the start; if it finds more than
 * {@code maxCells} passable cells the space is open or too large.
 */
public final class CellCheck {

    /** What one block of the grid is to a prisoner. */
    public enum Cell {
        /** Air, plants, unlocked or open doors, gates, trapdoors: a prisoner can get through. */
        PASSABLE,
        /** Anything solid that a prisoner can't pass. */
        WALL,
        /** Brig bars. */
        BARS,
        /** A closed, locked brig door. */
        LOCKED_DOOR;

        public boolean passable() {
            return this == PASSABLE;
        }
    }

    @FunctionalInterface
    public interface Grid {
        Cell at(int x, int y, int z);
    }

    public enum Outcome { CELL, START_BLOCKED, NOT_ENCLOSED, NO_LOCKED_DOOR }

    /** {@code size} = passable cells found (for {@link Outcome#NOT_ENCLOSED} the search stopped there). */
    public record Result(Outcome outcome, int size, int bars, int lockedDoors) {
        public boolean isCell() {
            return outcome == Outcome.CELL;
        }
    }

    private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private CellCheck() {
    }

    public static Result check(Grid grid, int x, int y, int z, int maxCells) {
        if (!grid.at(x, y, z).passable()) return new Result(Outcome.START_BLOCKED, 0, 0, 0);
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        seen.add(key(0, 0, 0));
        queue.add(new int[]{0, 0, 0});
        int size = 0, bars = 0, doors = 0;
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            if (++size > maxCells) return new Result(Outcome.NOT_ENCLOSED, size, bars, doors);
            for (int[] d : DIRS) {
                int dx = p[0] + d[0], dy = p[1] + d[1], dz = p[2] + d[2];
                if (!seen.add(key(dx, dy, dz))) continue;
                switch (grid.at(x + dx, y + dy, z + dz)) {
                    case PASSABLE -> queue.add(new int[]{dx, dy, dz});
                    case BARS -> bars++;
                    case LOCKED_DOOR -> doors++;
                    case WALL -> { }
                }
            }
        }
        return new Result(doors > 0 ? Outcome.CELL : Outcome.NO_LOCKED_DOOR, size, bars, doors);
    }

    /** Offsets stay within ±maxCells (well below 2^20), so 21 bits per axis are enough. */
    private static long key(int dx, int dy, int dz) {
        return ((long) (dx & 0x1FFFFF) << 42) | ((long) (dy & 0x1FFFFF) << 21) | (dz & 0x1FFFFF);
    }
}
