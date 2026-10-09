package com.richardsenger.piratesnships.station.lookout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The lookout calls a sighting once while it stays in sight, and again only after it was forgotten (CN1). */
class SightingMemoryTest {

    @Test
    void newSightingIsCalledOnceWhileInSight() {
        SightingMemory m = new SightingMemory();
        assertTrue(m.sight("ship:a", 100, 600));
        assertFalse(m.sight("ship:a", 160, 600));
        for (long t = 220; t <= 1000; t += 60) {
            assertFalse(m.sight("ship:a", t, 600), "kept in sight every scan, never forgotten (" + t + ")");
        }
        assertTrue(m.sight("ship:b", 1000, 600), "another ship is new");
    }

    @Test
    void sightingIsForgottenAfterTheMemoryUnseen() {
        SightingMemory m = new SightingMemory();
        assertTrue(m.sight("entity:shark", 0, 100));
        assertTrue(m.remembers("entity:shark", 100, 100));
        assertFalse(m.remembers("entity:shark", 101, 100));
        assertFalse(m.sight("entity:shark", 100, 100), "exactly the memory: still remembered");
        assertTrue(m.sight("entity:shark", 201, 100), "seen again after 101 ticks unseen");
    }

    @Test
    void forgetDropsOnlyStaleEntries() {
        SightingMemory m = new SightingMemory();
        m.sight("a", 0, 50);
        m.sight("b", 40, 50);
        m.forget(80, 50);
        assertEquals(1, m.size());
        assertTrue(m.remembers("b", 80, 50));
    }

    @Test
    void zeroMemoryCallsEverySightingOnANewTick() {
        SightingMemory m = new SightingMemory();
        assertTrue(m.sight("a", 0, 0));
        assertFalse(m.sight("a", 0, 0));
        assertTrue(m.sight("a", 60, 0));
    }
}
