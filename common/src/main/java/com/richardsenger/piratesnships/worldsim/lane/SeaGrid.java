package com.richardsenger.piratesnships.worldsim.lane;

/**
 * The sea as a grid of square cells for lane finding (design.md §10.4, WS2). Cell {@code (cx, cz)} covers the blocks
 * {@code [cx·cell, (cx+1)·cell)} on each axis; {@link #isSea} says whether its centre is open sea. Implementations
 * must not load chunks ({@link BiomeSeaGrid} reads the biome source).
 */
public interface SeaGrid {

    /** Side of one cell in blocks. */
    int cellBlocks();

    /** Whether the cell is open sea. */
    boolean isSea(int cx, int cz);

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

    @FunctionalInterface
    interface CellPredicate {
        boolean test(int cx, int cz);
    }
}
