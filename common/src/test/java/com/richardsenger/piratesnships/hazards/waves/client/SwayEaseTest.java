package com.richardsenger.piratesnships.hazards.waves.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The camera sway fades in and out instead of snapping (CAM1). */
class SwayEaseTest {

    @Test
    void reachesTheTargetInEaseTicks() {
        SwayEase e = new SwayEase();
        assertTrue(e.idle());
        assertEquals(0.0, e.weight(1f), 1e-12);
        double last = 0;
        for (int i = 0; i < 5; i++) {
            e.tick(true, 5);
            double w = e.weight(1f);
            assertTrue(w > last, "rises every tick");
            assertTrue(i == 4 || w < 1.0, "not there before 5 ticks");
            last = w;
        }
        assertEquals(1.0, e.weight(1f), 1e-12, "there after 5 ticks");
        e.tick(true, 5);
        assertEquals(1.0, e.weight(0.5f), 1e-12, "stays at the target");
        for (int i = 0; i < 5; i++) e.tick(false, 5);
        assertEquals(0.0, e.weight(1f), 1e-12);
        assertFalse(e.idle(), "the last frame of the fade still interpolates");
        e.tick(false, 5);
        assertTrue(e.idle());
    }

    @Test
    void framesBetweenTicksDoNotJump() {
        SwayEase e = new SwayEase();
        e.tick(true, 10);
        double prev = 0;
        for (int i = 0; i < 10; i++) {
            e.tick(true, 10);
            for (float pt = 0; pt <= 1f; pt += 0.125f) {
                double w = e.weight(pt);
                assertTrue(w >= prev - 1e-12 && w - prev < 0.06, "frame step " + (w - prev) + " at tick " + i);
                prev = w;
            }
        }
    }

    @Test
    void zeroIsInstant() {
        SwayEase e = new SwayEase();
        e.tick(true, 0);
        assertEquals(1.0, e.weight(0f), 1e-12);
        e.tick(false, 0);
        assertEquals(0.0, e.weight(0f), 1e-12);
        assertTrue(e.idle());
    }

    @Test
    void aShortLossOnlyDipsTheWeight() {
        SwayEase e = new SwayEase();
        for (int i = 0; i < 10; i++) e.tick(true, 5);
        e.tick(false, 5);
        e.tick(true, 5);
        assertTrue(e.weight(0f) > 0.8, "one lost tick dips to " + e.weight(0f));
    }
}
