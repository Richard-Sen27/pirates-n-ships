package com.richardsenger.piratesnships.sailing.sail;

/**
 * What the yard rule needs to know about one block position (pure: the game adapter is {@code YardSails}, JUnit
 * tests use a map).
 */
@FunctionalInterface
public interface YardLookup {

    /** The kinds of cells the rule tells apart. */
    enum Cell {
        /** Air: allowed between the two yards of a sail. */
        AIR,
        /** A mast block ({@code #pirates_n_ships:masts}): allowed between the two yards of a sail. */
        MAST,
        /** A yard block running along the x axis. */
        YARD_X,
        /** A yard block running along the z axis. */
        YARD_Z,
        /** Anything else: it blocks a sail. */
        OTHER;

        public boolean isYard() {
            return this == YARD_X || this == YARD_Z;
        }

        public boolean isOpen() {
            return this == AIR || this == MAST;
        }
    }

    Cell at(int x, int y, int z);
}
