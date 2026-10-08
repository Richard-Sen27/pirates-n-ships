package com.richardsenger.piratesnships.ship.decor;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/** {@link DecorShapes#rotate}: the outline turns with the block state's y rotation (east = 90 degrees clockwise). */
class DecorShapesTest {

    /** A wall bracket in the north frame: the back plate against the south side (z 14..16). */
    private static final double[] BRACKET = {4, 1, 14, 12, 15, 16};

    @Test
    void northIsTheModelFrame() {
        assertArrayEquals(BRACKET, DecorShapes.rotate(BRACKET, Direction.NORTH));
    }

    @Test
    void theBackPlateFollowsTheWall() {
        // facing east: the wall is west (x 0..2); facing south: north (z 0..2); facing west: east (x 14..16)
        assertArrayEquals(new double[]{0, 1, 4, 2, 15, 12}, DecorShapes.rotate(BRACKET, Direction.EAST));
        assertArrayEquals(new double[]{4, 1, 0, 12, 15, 2}, DecorShapes.rotate(BRACKET, Direction.SOUTH));
        assertArrayEquals(new double[]{14, 1, 4, 16, 15, 12}, DecorShapes.rotate(BRACKET, Direction.WEST));
    }

    @Test
    void fourQuarterTurnsAreTheIdentity() {
        double[] box = {1, 2, 3, 9, 10, 7};
        double[] r = box;
        for (int i = 0; i < 4; i++) r = DecorShapes.rotate(r, Direction.EAST);
        assertArrayEquals(box, r, 1e-9);
    }
}
