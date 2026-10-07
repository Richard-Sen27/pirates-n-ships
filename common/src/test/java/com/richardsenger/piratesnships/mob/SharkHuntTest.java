package com.richardsenger.piratesnships.mob;

import org.junit.jupiter.api.Test;

import static com.richardsenger.piratesnships.mob.SharkHunt.Action;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SharkHuntTest {

    private static final SharkHunt.Params P = new SharkHunt.Params(60, 30, 200);

    @Test
    void circlesForCircleTicksThenCharges() {
        SharkHunt hunt = new SharkHunt();
        for (int t = 1; t < 60; t++) assertEquals(Action.CIRCLE, hunt.tick(false, true, P), "tick " + t);
        assertEquals(Action.BITE, hunt.tick(false, true, P), "the charge bites when in reach");
    }

    @Test
    void chargesUntilInReachThenBitesAndCirclesAgain() {
        SharkHunt hunt = new SharkHunt();
        for (int t = 1; t < 60; t++) hunt.tick(false, false, P);
        assertEquals(Action.CHARGE, hunt.tick(false, false, P));
        assertEquals(Action.CHARGE, hunt.tick(false, false, P));
        assertEquals(Action.BITE, hunt.tick(false, true, P));
        assertEquals(SharkHunt.Phase.CIRCLE, hunt.phase());
        assertEquals(Action.CIRCLE, hunt.tick(false, true, P), "back to circling after the bite");
    }

    @Test
    void frenzySkipsCirclingButKeepsTheCooldown() {
        SharkHunt hunt = new SharkHunt();
        assertEquals(Action.BITE, hunt.tick(true, true, P), "frenzy: charge and bite at once");
        for (int t = 1; t < 30; t++) assertEquals(Action.CHARGE, hunt.tick(true, true, P), "cooldown tick " + t);
        assertEquals(Action.BITE, hunt.tick(true, true, P), "next bite after bite_cooldown_ticks");
    }

    @Test
    void frenzyStartingMidCircleChargesAtOnce() {
        SharkHunt hunt = new SharkHunt();
        for (int t = 1; t < 10; t++) hunt.tick(false, false, P);
        assertEquals(Action.CHARGE, hunt.tick(true, false, P));
    }

    @Test
    void givesUpAfterGiveUpTicksWithoutABite() {
        SharkHunt hunt = new SharkHunt();
        for (int t = 1; t <= 200; t++) {
            Action a = hunt.tick(false, false, P);
            assertEquals(t < 60 ? Action.CIRCLE : Action.CHARGE, a, "tick " + t);
        }
        assertEquals(Action.GIVE_UP, hunt.tick(false, false, P));
    }

    @Test
    void aBiteResetsTheGiveUpTimer() {
        SharkHunt hunt = new SharkHunt();
        for (int t = 1; t < 190; t++) hunt.tick(false, false, P);
        assertEquals(Action.BITE, hunt.tick(false, true, P));
        assertEquals(0, hunt.sinceBite());
        for (int t = 1; t <= 200; t++) hunt.tick(false, false, P);
        assertEquals(Action.GIVE_UP, hunt.tick(false, false, P));
    }

    @Test
    void resetStartsOver() {
        SharkHunt hunt = new SharkHunt();
        hunt.tick(true, true, P);
        hunt.reset();
        assertEquals(SharkHunt.Phase.CIRCLE, hunt.phase());
        assertEquals(0, hunt.sinceBite());
        assertEquals(Action.CIRCLE, hunt.tick(false, true, P));
    }
}
