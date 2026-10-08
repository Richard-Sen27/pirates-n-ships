package com.richardsenger.piratesnships.ship.decor.flag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The part a flagpole block shows from its neighbours, and the height limit (VIS1a). */
class FlagpolePartTest {

    @Test
    void partFollowsTheNeighbours() {
        assertEquals(FlagpolePart.SINGLE, FlagpolePart.of(false, false));
        assertEquals(FlagpolePart.BOTTOM, FlagpolePart.of(false, true));
        assertEquals(FlagpolePart.MIDDLE, FlagpolePart.of(true, true));
        assertEquals(FlagpolePart.TOP, FlagpolePart.of(true, false));
    }

    @Test
    void partRoundTripsItsNeighbours() {
        for (FlagpolePart part : FlagpolePart.values()) {
            assertEquals(part, FlagpolePart.of(part.hasBelow(), part.hasAbove()), part.name());
        }
    }

    @Test
    void serializedNamesMatchTheModels() {
        assertEquals("single", FlagpolePart.SINGLE.getSerializedName());
        assertEquals("bottom", FlagpolePart.BOTTOM.getSerializedName());
        assertEquals("middle", FlagpolePart.MIDDLE.getSerializedName());
        assertEquals("top", FlagpolePart.TOP.getSerializedName());
    }

    @Test
    void aPoleMayReachTheLimitButNotPassIt() {
        assertFalse(FlagpoleRun.tooTall(0, 0, 1));
        assertTrue(FlagpoleRun.tooTall(1, 0, 1));
        assertFalse(FlagpoleRun.tooTall(5, 0, 6));
        assertTrue(FlagpoleRun.tooTall(6, 0, 6));
        // joining two poles counts both
        assertFalse(FlagpoleRun.tooTall(2, 3, 6));
        assertTrue(FlagpoleRun.tooTall(3, 3, 6));
    }
}
