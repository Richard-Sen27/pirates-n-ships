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

    @Test
    void sightingInViewIsCalledAgainWhenMuchCloser() {
        SightingMemory m = new SightingMemory();
        assertTrue(m.sight("ship:a", 0, 6000, 150, 64, 2400));
        assertFalse(m.sight("ship:a", 60, 6000, 120, 64, 2400), "30 blocks closer");
        assertFalse(m.sight("ship:a", 120, 6000, 90, 64, 2400), "60 blocks closer");
        assertTrue(m.sight("ship:a", 180, 6000, 80, 64, 2400), "70 blocks closer than at the last call");
        assertFalse(m.sight("ship:a", 240, 6000, 40, 64, 2400), "40 closer than the second call");
        assertFalse(m.sight("ship:a", 300, 6000, 140, 64, 2400), "moving away is never called");
        assertTrue(m.sight("ship:a", 360, 6000, 16, 64, 2400), "64 closer than the second call");
    }

    @Test
    void sightingInViewIsCalledAgainAfterTheRecallTime() {
        SightingMemory m = new SightingMemory();
        assertTrue(m.sight("land:1", 0, 6000, 200, 64, 2400));
        for (long t = 60; t < 2400; t += 60) {
            assertFalse(m.sight("land:1", t, 6000, 200, 64, 2400), "called again at " + t);
        }
        assertTrue(m.sight("land:1", 2400, 6000, 200, 64, 2400), "not called again after the recall time");
        assertFalse(m.sight("land:1", 2460, 6000, 200, 64, 2400), "the recall restarts from the new call");
    }

    @Test
    void zeroRecallSwitchesTheRepeatsOff() {
        SightingMemory m = new SightingMemory();
        assertTrue(m.sight("a", 0, 100000, 500, 0, 0));
        assertFalse(m.sight("a", 50000, 100000, 1, 0, 0));
    }
}
