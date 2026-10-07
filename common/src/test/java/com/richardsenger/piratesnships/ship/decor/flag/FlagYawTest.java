package com.richardsenger.piratesnships.ship.decor.flag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Wrapping, the shortest turn and the smooth approach of the drawn flag yaw (FL1). */
class FlagYawTest {

    private static final float EPS = 1.0e-3f;

    @Test
    void wrapMapsIntoZeroTo360() {
        assertEquals(0f, FlagYaw.wrap(0f));
        assertEquals(0f, FlagYaw.wrap(360f));
        assertEquals(350f, FlagYaw.wrap(-10f), EPS);
        assertEquals(10f, FlagYaw.wrap(730f), EPS);
        assertEquals(359.5f, FlagYaw.wrap(-0.5f), EPS);
        float tiny = FlagYaw.wrap(-1.0e-7f);
        assertTrue(tiny >= 0f && tiny < 360f, "tiny negative stays in range: " + tiny);
    }

    @Test
    void deltaTakesTheShortWayRound() {
        assertEquals(20f, FlagYaw.delta(350f, 10f), EPS);
        assertEquals(-20f, FlagYaw.delta(10f, 350f), EPS);
        assertEquals(90f, FlagYaw.delta(0f, 90f), EPS);
        assertEquals(-90f, FlagYaw.delta(0f, 270f), EPS);
        assertEquals(180f, FlagYaw.delta(0f, 180f), EPS);
        assertEquals(0f, FlagYaw.delta(725f, 5f), EPS);
    }

    @Test
    void approachStartsAtTheTargetWhenNothingWasShown() {
        assertEquals(123f, FlagYaw.approach(Float.NaN, 123f, 1.0, FlagYaw.SMOOTHING_TICKS));
        assertEquals(10f, FlagYaw.approach(Float.NaN, 370f, 0.0, FlagYaw.SMOOTHING_TICKS), EPS);
    }

    @Test
    void approachMovesPartWayAndConverges() {
        float shown = 0f;
        assertEquals(0f, FlagYaw.approach(shown, 90f, 0.0, 4f), EPS, "no time, no movement");
        float step = FlagYaw.approach(shown, 90f, 4.0, 4f);
        assertEquals(90f * (1f - (float) Math.exp(-1.0)), step, 0.01f, "one time constant: about 63 %");
        float prev = shown;
        for (int tick = 0; tick < 40; tick++) {
            shown = FlagYaw.approach(shown, 90f, 1.0, 4f);
            assertTrue(shown >= prev && shown <= 90f, "monotonic, no overshoot: " + shown);
            prev = shown;
        }
        assertEquals(90f, shown, 0.1f);
    }

    @Test
    void approachIsFrameRateIndependent() {
        float coarse = FlagYaw.approach(0f, 100f, 4.0, 4f);
        float fine = 0f;
        for (int i = 0; i < 16; i++) fine = FlagYaw.approach(fine, 100f, 0.25, 4f);
        assertEquals(coarse, fine, 0.01f);
    }

    @Test
    void approachCrossesNorthTheShortWay() {
        float shown = 350f;
        for (int tick = 0; tick < 30; tick++) {
            shown = FlagYaw.approach(shown, 10f, 1.0, 4f);
            assertTrue(shown >= 350f || shown <= 10f + EPS, "went the long way round: " + shown);
        }
        assertEquals(0f, FlagYaw.delta(shown, 10f), 0.1f);
        float back = FlagYaw.approach(10f, 350f, 4.0, 4f);
        assertTrue(back < 10f && back > 0f || back > 350f, "short way back: " + back);
    }

    @Test
    void approachJumpsAfterALongPauseOrWithoutSmoothing() {
        assertEquals(200f, FlagYaw.approach(10f, 200f, 1000.0, 4f), EPS);
        assertEquals(200f, FlagYaw.approach(10f, 200f, 1.0, 0f), EPS);
        assertEquals(10f, FlagYaw.approach(10f, 200f, -5.0, 4f), EPS, "time going backwards does not move it");
    }
}
