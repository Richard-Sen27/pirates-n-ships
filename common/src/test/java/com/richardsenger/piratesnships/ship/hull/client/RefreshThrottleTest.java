package com.richardsenger.piratesnships.ship.hull.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RefreshThrottleTest {

    @Test
    void runsOncePerInterval() {
        RefreshThrottle t = new RefreshThrottle();
        int runs = 0;
        for (long tick = 0; tick < 20; tick++) {
            if (t.ready(tick, 5)) runs++;
        }
        assertEquals(4, runs, "ticks 0, 5, 10, 15");
    }

    @Test
    void firstCallAndResetAreReady() {
        RefreshThrottle t = new RefreshThrottle();
        assertTrue(t.ready(100, 5));
        assertFalse(t.ready(101, 5));
        t.reset();
        assertTrue(t.ready(102, 5));
    }

    @Test
    void intervalBelowOneRunsEveryTick() {
        RefreshThrottle t = new RefreshThrottle();
        assertTrue(t.ready(0, 0));
        assertTrue(t.ready(1, 0));
        assertTrue(t.ready(2, -3));
    }

    @Test
    void clockGoingBackwardsDoesNotStall() {
        RefreshThrottle t = new RefreshThrottle();
        assertTrue(t.ready(50, 5));
        assertTrue(t.ready(10, 5));
    }
}
