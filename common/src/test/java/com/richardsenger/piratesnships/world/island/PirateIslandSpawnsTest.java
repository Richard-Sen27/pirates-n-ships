package com.richardsenger.piratesnships.world.island;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PirateIslandSpawnsTest {

    @Test
    void naturalSpawnsNeedWeightTheTypeEnabledAndRoomUnderTheCap() {
        assertTrue(PirateIslandSpawns.naturalAllowed(10, true, 0, 8));
        assertTrue(PirateIslandSpawns.naturalAllowed(10, true, 7, 8));
        assertFalse(PirateIslandSpawns.naturalAllowed(10, true, 8, 8), "at the cap");
        assertFalse(PirateIslandSpawns.naturalAllowed(10, true, 0, 0), "cap 0 stops spawns");
        assertFalse(PirateIslandSpawns.naturalAllowed(0, true, 0, 8), "weight 0 stops spawns");
        assertFalse(PirateIslandSpawns.naturalAllowed(10, false, 0, 8), "pirates disabled");
    }
}
