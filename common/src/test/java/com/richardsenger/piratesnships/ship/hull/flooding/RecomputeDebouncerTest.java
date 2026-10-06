package com.richardsenger.piratesnships.ship.hull.flooding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecomputeDebouncerTest {

    @Test
    void coalescesABurstIntoOneRecompute() {
        RecomputeDebouncer d = new RecomputeDebouncer(5, 100);
        int fired = 0;
        for (int t = 0; t < 50; t++) {
            if (t < 10) d.markDirty();
            if (d.tick()) {
                fired++;
                assertEquals(14, t, "fires 5 quiet ticks after the last change");
            }
        }
        assertEquals(1, fired);
        assertFalse(d.isDirty());
    }

    @Test
    void constantChangesStillFireAtTheMaxDelay() {
        RecomputeDebouncer d = new RecomputeDebouncer(5, 20);
        int fired = 0;
        for (int t = 0; t < 100; t++) {
            d.markDirty();
            if (d.tick()) fired++;
        }
        assertEquals(4, fired, "at ticks 20, 41, 62 and 83");
    }

    @Test
    void idleNeverFires() {
        RecomputeDebouncer d = new RecomputeDebouncer(0, 0);
        assertFalse(d.tick());
        d.markDirty();
        assertTrue(d.tick());
        assertFalse(d.tick());
    }
}
