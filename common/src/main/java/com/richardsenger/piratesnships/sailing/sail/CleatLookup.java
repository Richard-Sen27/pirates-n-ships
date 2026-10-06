package com.richardsenger.piratesnships.sailing.sail;

/**
 * What the triangular sail rule needs to know about one block position (pure: the game adapter is
 * {@link TriangularSails#lookup}, JUnit tests use a map).
 */
@FunctionalInterface
public interface CleatLookup {

    /** The kinds of cells the rule tells apart. */
    enum Cell {
        /** Air: allowed between the head cleat and the clew cleat. */
        AIR,
        /** A mast block ({@code #pirates_n_ships:masts}): allowed between the head cleat and the clew cleat. */
        MAST,
        /** A cleat. */
        CLEAT,
        /** Anything else: it ends the search for a clew cleat. */
        OTHER;

        public boolean isOpen() {
            return this == AIR || this == MAST;
        }
    }

    Cell at(int x, int y, int z);
}
