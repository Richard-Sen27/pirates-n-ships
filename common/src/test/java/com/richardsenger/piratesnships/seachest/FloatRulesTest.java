package com.richardsenger.piratesnships.seachest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatRulesTest {

    private static final FloatRules.Params P = FloatRules.Params.of(0.45);
    private static final double H = 0.75;

    @Test
    void restsAtTheDraft() {
        assertEquals(0.0, FloatRules.verticalVelocity(0.0, 0.45 * H, H, P), 1e-12);
    }

    @Test
    void submergedRisesButNoFasterThanTheCap() {
        double v = 0.0;
        for (int i = 0; i < 100; i++) {
            v = FloatRules.verticalVelocity(v, H, H, P);
            assertTrue(v <= P.maxRise() + 1e-12);
        }
        assertTrue(v > 0.1, "keeps rising while under water: " + v);
    }

    @Test
    void outOfTheWaterItFalls() {
        assertTrue(FloatRules.verticalVelocity(0.0, 0.0, H, P) < 0.0);
    }

    @Test
    void settlesAtTheWaterlineFromBelow() {
        // Simulate: surface at y = 3, chest bottom starts at 0 (fully under)
        double y = 0.0;
        double v = 0.0;
        for (int i = 0; i < 200; i++) {
            double submerged = 3.0 - y;
            v = FloatRules.verticalVelocity(v, submerged, H, P);
            y += v;
        }
        assertEquals(3.0 - 0.45 * H, y, 0.02, "bottom ends a draft below the surface");
        assertEquals(0.0, v, 0.005);
    }

    @Test
    void windDriftReachesTheFactorOfTheWindSpeed() {
        double[] a = FloatRules.windAcceleration(6.0, 0.0, 0.3, 3.0, true, P);
        assertEquals(0.0, a[1], 1e-12);
        assertTrue(a[0] > 0.0, "pushed toward where the wind blows");
        double steady = FloatRules.steadyDrift(a[0], P);
        assertEquals(6.0 * 0.3 / 20.0, steady, 1e-12, "steady drift = 1.8 blocks/s");
        // Simulated with the water drag
        double v = 0.0;
        for (int i = 0; i < 200; i++) v = (v + a[0]) * P.waterRetention();
        assertEquals(steady, v, 1e-6);
    }

    @Test
    void windDriftIsCapped() {
        double[] a = FloatRules.windAcceleration(0.0, -40.0, 0.5, 3.0, true, P);
        assertEquals(0.0, a[0], 1e-12);
        assertEquals(-3.0 / 20.0, FloatRules.steadyDrift(a[1], P), 1e-12, "capped at max_drift_speed");
    }

    @Test
    void noWindDriftUnderWaterOrInCalm() {
        double[] under = FloatRules.windAcceleration(6.0, 0.0, 0.3, 3.0, false, P);
        assertEquals(0.0, under[0]);
        double[] calm = FloatRules.windAcceleration(0.0, 0.0, 0.3, 3.0, true, P);
        assertEquals(0.0, calm[0]);
        double[] off = FloatRules.windAcceleration(6.0, 0.0, 0.0, 3.0, true, P);
        assertEquals(0.0, off[0]);
    }

    @Test
    void diagonalWindKeepsItsDirection() {
        double[] a = FloatRules.windAcceleration(3.0, 4.0, 0.3, 10.0, true, P);
        assertEquals(3.0 / 4.0, a[0] / a[1], 1e-12);
    }
}
