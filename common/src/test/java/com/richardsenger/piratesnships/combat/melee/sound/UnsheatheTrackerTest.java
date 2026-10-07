package com.richardsenger.piratesnships.combat.melee.sound;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UnsheatheTrackerTest {

    static final String CUTLASS = "cutlass", RAPIER = "rapier";
    static final int CD = 10;

    @Test
    void theFirstObservationIsOnlyTheBaseline() {
        UnsheatheTracker t = new UnsheatheTracker();
        assertFalse(t.update(CUTLASS, 0, CD), "logging in with a sword in hand draws nothing");
        assertFalse(t.update(CUTLASS, 100, CD), "holding it on draws nothing");
    }

    @Test
    void drawingASwordSoundsOnce() {
        UnsheatheTracker t = new UnsheatheTracker();
        assertFalse(t.update(null, 0, CD));
        assertTrue(t.update(CUTLASS, 1, CD));
        for (long i = 2; i < 50; i++) assertFalse(t.update(CUTLASS, i, CD));
        assertFalse(t.update(null, 50, CD), "putting it away is silent");
        assertTrue(t.update(CUTLASS, 51, CD), "drawing it again sounds");
    }

    @Test
    void scrollingAcrossTwoSwordsSoundsOnce() {
        UnsheatheTracker t = new UnsheatheTracker();
        t.update(null, 0, CD);
        assertTrue(t.update(CUTLASS, 1, CD));
        assertFalse(t.update(RAPIER, 3, CD), "second sword within the cooldown");
        assertFalse(t.update(null, 5, CD));
        assertFalse(t.update(CUTLASS, 7, CD), "still within the cooldown");
        assertTrue(t.update(RAPIER, 20, CD), "a different sword after the cooldown sounds");
    }

    @Test
    void zeroCooldownSoundsOnEveryChange() {
        UnsheatheTracker t = new UnsheatheTracker();
        t.update(null, 0, 0);
        assertTrue(t.update(CUTLASS, 1, 0));
        assertTrue(t.update(RAPIER, 1, 0));
        assertFalse(t.update(RAPIER, 2, 0));
    }
}
