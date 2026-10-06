package com.richardsenger.piratesnships.sailing.ship;

import java.util.OptionalInt;
import java.util.function.IntPredicate;

/**
 * Pure anchor point search: the anchor falls straight down from the capstan (through water and air) and lands on the
 * first solid block within the chain length.
 */
public final class AnchorGround {

    private AnchorGround() {
    }

    /**
     * Y of the top surface of the first solid block below {@code startY} (exclusive) and at most {@code chainLength}
     * blocks below it, or empty when there is none in reach.
     *
     * @param startY      block y of the capstan (world)
     * @param chainLength length of the chain in blocks
     * @param solid       whether the world block at a y (same x/z column) stops the anchor
     */
    public static OptionalInt floorY(int startY, int chainLength, IntPredicate solid) {
        for (int y = startY - 1; y >= startY - chainLength; y--) {
            if (solid.test(y)) {
                return OptionalInt.of(y + 1);
            }
        }
        return OptionalInt.empty();
    }
}
