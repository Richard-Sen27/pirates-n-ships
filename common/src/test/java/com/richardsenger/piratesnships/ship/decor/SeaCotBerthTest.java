package com.richardsenger.piratesnships.ship.decor;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which sea cots join into one berth (ART20, pure). */
class SeaCotBerthTest {

    @Test
    void sidesAreSeenFromTheFoot() {
        assertEquals(Direction.WEST, SeaCotBerth.left(Direction.NORTH));
        assertEquals(Direction.EAST, SeaCotBerth.right(Direction.NORTH));
        assertEquals(Direction.NORTH, SeaCotBerth.left(Direction.EAST));
        assertEquals(Direction.SOUTH, SeaCotBerth.right(Direction.EAST));
        assertEquals(Direction.EAST, SeaCotBerth.left(Direction.SOUTH));
        assertEquals(Direction.SOUTH, SeaCotBerth.left(Direction.WEST));
        for (Direction d : Direction.Plane.HORIZONTAL) {
            assertEquals(SeaCotBerth.left(d).getOpposite(), SeaCotBerth.right(d), d.toString());
        }
    }

    @Test
    void sameFacingAndPartJoin() {
        assertTrue(SeaCotBerth.joins(Direction.NORTH, BedPart.FOOT, Direction.NORTH, BedPart.FOOT));
        assertTrue(SeaCotBerth.joins(Direction.EAST, BedPart.HEAD, Direction.EAST, BedPart.HEAD));
    }

    @Test
    void crosswiseShiftedOrNoCotDoesNotJoin() {
        assertFalse(SeaCotBerth.joins(Direction.NORTH, BedPart.FOOT, Direction.EAST, BedPart.FOOT));
        assertFalse(SeaCotBerth.joins(Direction.NORTH, BedPart.FOOT, Direction.SOUTH, BedPart.FOOT));
        assertFalse(SeaCotBerth.joins(Direction.NORTH, BedPart.FOOT, Direction.NORTH, BedPart.HEAD));
        assertFalse(SeaCotBerth.joins(Direction.NORTH, BedPart.HEAD, null, null));
    }
}
