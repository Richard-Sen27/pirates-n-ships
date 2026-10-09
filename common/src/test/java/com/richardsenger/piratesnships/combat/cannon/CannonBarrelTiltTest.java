package com.richardsenger.piratesnships.combat.cannon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CAN2: the drawn barrel angle eased between elevation steps ({@link CannonBarrelTilt}). */
class CannonBarrelTiltTest {

    @Test
    void theFirstAngleIsShownAtOnce() {
        CannonBarrelTilt tilt = new CannonBarrelTilt();
        assertEquals(15.0, tilt.angle(15.0, 1000.0, 6));
        assertEquals(15.0, tilt.angle(15.0, 1003.5, 6));
    }

    @Test
    void aNewStepIsReachedSmoothlyOverTheTiltTicks() {
        CannonBarrelTilt tilt = new CannonBarrelTilt();
        tilt.angle(0.0, 100.0, 6);
        assertEquals(0.0, tilt.angle(5.0, 100.0, 6), 1e-12, "starts where it was");
        double last = 0.0;
        for (double t = 100.5; t < 106.0; t += 0.5) {
            double a = tilt.angle(5.0, t, 6);
            assertTrue(a > last && a < 5.0, t + ": " + a);
            last = a;
        }
        assertEquals(2.5, tilt.angle(5.0, 103.0, 6), 1e-12, "half way at half time");
        assertEquals(5.0, tilt.angle(5.0, 106.0, 6), 1e-12, "there after the tilt ticks");
        assertEquals(5.0, tilt.angle(5.0, 200.0, 6), 1e-12);
        // eased: slow at both ends
        CannonBarrelTilt fresh = new CannonBarrelTilt();
        fresh.angle(0.0, 0.0, 6);
        double first = fresh.angle(6.0, 0.0, 6);
        double early = fresh.angle(6.0, 0.6, 6) - first;
        double middle = fresh.angle(6.0, 3.3, 6) - fresh.angle(6.0, 2.7, 6);
        assertTrue(early < middle, "eases in: " + early + " vs " + middle);
    }

    @Test
    void aStepDuringATiltCarriesOnFromWhereTheBarrelIs() {
        CannonBarrelTilt tilt = new CannonBarrelTilt();
        tilt.angle(0.0, 0.0, 6);
        tilt.angle(10.0, 0.0, 6);
        double mid = tilt.angle(10.0, 3.0, 6);
        assertEquals(5.0, mid, 1e-12);
        assertEquals(mid, tilt.angle(-5.0, 3.0, 6), 1e-12, "no jump when the target turns round");
        assertTrue(tilt.angle(-5.0, 4.0, 6) < mid, "heads for the new step");
        assertEquals(-5.0, tilt.angle(-5.0, 9.0, 6), 1e-12);
    }

    @Test
    void zeroTiltTicksJumpsAtOnce() {
        CannonBarrelTilt tilt = new CannonBarrelTilt();
        tilt.angle(0.0, 0.0, 0);
        assertEquals(20.0, tilt.angle(20.0, 0.0, 0));
        assertEquals(-5.0, tilt.angle(-5.0, 0.5, -1));
    }
}
