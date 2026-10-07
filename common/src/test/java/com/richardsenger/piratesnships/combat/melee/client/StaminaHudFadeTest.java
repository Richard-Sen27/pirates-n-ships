package com.richardsenger.piratesnships.combat.melee.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaminaHudFadeTest {

    private static final float E = 1e-5f;

    @Test
    void curveHoldsThenFadesLinearly() {
        assertEquals(1f, StaminaHudFade.visibility(0, 20, 10), E);
        assertEquals(1f, StaminaHudFade.visibility(20, 20, 10), E);
        assertEquals(0.5f, StaminaHudFade.visibility(25, 20, 10), E);
        assertEquals(0.1f, StaminaHudFade.visibility(29, 20, 10), E);
        assertEquals(0f, StaminaHudFade.visibility(30, 20, 10), E);
        assertEquals(0f, StaminaHudFade.visibility(500, 20, 10), E);
        assertEquals(0f, StaminaHudFade.visibility(1, 0, 0), E);
    }

    @Test
    void fullStaminaFadesOutAfterTheDelay() {
        StaminaHudFade fade = new StaminaHudFade();
        for (int t = 0; t < 20; t++) {
            fade.tick(true, false);
            assertEquals(1f, fade.visibility(true, false, 0f, 20, 10), E);
        }
        float last = 1f;
        for (int t = 0; t < 10; t++) {
            fade.tick(true, false);
            float v = fade.visibility(true, false, 0f, 20, 10);
            assertTrue(v < last, "fades monotonically");
            last = v;
        }
        assertEquals(0f, last, E);
        // between ticks the partial tick moves the fade on smoothly
        StaminaHudFade mid = new StaminaHudFade();
        for (int t = 0; t < 22; t++) mid.tick(true, false);
        assertEquals(0.75f, mid.visibility(true, false, 0.5f, 20, 10), E);
    }

    @Test
    void reappearsAtOnceWhenStaminaDropsOrAFightStarts() {
        StaminaHudFade fade = new StaminaHudFade();
        for (int t = 0; t < 100; t++) fade.tick(true, false);
        assertEquals(0f, fade.visibility(true, false, 0f, 20, 10), E);
        // the very frame stamina drops, before the next tick
        assertEquals(1f, fade.visibility(false, false, 0.3f, 20, 10), E);
        // the very frame a fight state starts (guard, attack, parry, stagger, hit)
        assertEquals(1f, fade.visibility(true, true, 0.3f, 20, 10), E);
        // and the next tick restarts the delay
        fade.tick(true, true);
        assertEquals(0, fade.fullTicks());
        fade.tick(true, false);
        assertEquals(1f, fade.visibility(true, false, 0f, 20, 10), E);
    }

    @Test
    void resetShowsTheBarAgain() {
        StaminaHudFade fade = new StaminaHudFade();
        for (int t = 0; t < 100; t++) fade.tick(true, false);
        fade.reset();
        assertEquals(1f, fade.visibility(true, false, 0f, 20, 10), E);
    }
}
