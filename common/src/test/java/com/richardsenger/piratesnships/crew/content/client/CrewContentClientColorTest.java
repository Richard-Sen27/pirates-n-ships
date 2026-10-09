package com.richardsenger.piratesnships.crew.content.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** ITC1: the water barrel item's water faces get vanilla's default water colour, opaque; other faces stay white. */
class CrewContentClientColorTest {

    @Test
    void waterFacesAreOpaqueDefaultWater() {
        assertEquals(0xFF3F76E4, CrewContentClient.itemColor(CrewContentClient.WATER_TINT_INDEX));
        assertEquals(0xFF, CrewContentClient.itemColor(CrewContentClient.WATER_TINT_INDEX) >>> 24, "alpha: the item renderer multiplies by it");
        assertEquals(-1, CrewContentClient.itemColor(1), "untinted");
        assertEquals(-1, CrewContentClient.itemColor(-1), "untinted");
    }
}
