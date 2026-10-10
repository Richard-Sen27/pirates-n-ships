package com.richardsenger.piratesnships.worldsim.lane;

/**
 * The sea as a grid of square cells for lane finding (design.md §10.4, WS2). Cell {@code (cx, cz)} covers the blocks
 * {@code [cx·cell, (cx+1)·cell)} on each axis; {@link #isSea} says whether a lane may pass it, {@link #isShore} whether
 * such a cell is the coast itself (passable at a higher cost: beaches the biome map draws over shallow water).
 * Implementations must not load chunks ({@link BiomeSeaGrid} reads the biome source).
 */
public interface SeaGrid {

    /** Side of one cell in blocks. */
    int cellBlocks();

    /** Whether a lane may pass the cell (open sea or shore). */
    boolean isSea(int cx, int cz);

    /** Whether a passable cell is shore rather than open sea (none by default). */
    default boolean isShore(int cx, int cz) {
        return false;
    }

    /** The cell holding block coordinate {@code block}. */
    default int cellOf(int block) {
        return Math.floorDiv(block, cellBlocks());
    }

    /** The block coordinate of the centre of cell {@code cell}. */
    default int centreOf(int cell) {
        return cell * cellBlocks() + cellBlocks() / 2;
    }

    /** A grid where every cell is sea (tests). */
    static SeaGrid allSea(int cellBlocks) {
        return of(cellBlocks, (cx, cz) -> true);
    }

    /** A grid from a predicate over cell coordinates (tests, custom maps). */
    static SeaGrid of(int cellBlocks, CellPredicate sea) {
        if (cellBlocks < 1) throw new IllegalArgumentException("cellBlocks < 1");
        return new SeaGrid() {
            @Override
            public int cellBlocks() {
                return cellBlocks;
            }

            @Override
            public boolean isSea(int cx, int cz) {
                return sea.test(cx, cz);
            }
        };
    }

    /** A grid of open sea cells and passable shore cells from two predicates (tests, custom maps). */
    static SeaGrid of(int cellBlocks, CellPredicate sea, CellPredicate shore) {
        SeaGrid open = of(cellBlocks, sea);
        return new SeaGrid() {
            @Override
            public int cellBlocks() {
                return cellBlocks;
            }

            @Override
            public boolean isSea(int cx, int cz) {
                return open.isSea(cx, cz) || shore.test(cx, cz);
            }

            @Override
            public boolean isShore(int cx, int cz) {
                return !open.isSea(cx, cz) && shore.test(cx, cz);
            }
        };
    }

    @FunctionalInterface
    interface CellPredicate {
        boolean test(int cx, int cz);
    }
}
