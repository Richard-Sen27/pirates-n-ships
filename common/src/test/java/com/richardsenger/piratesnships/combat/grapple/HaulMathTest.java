package com.richardsenger.piratesnships.combat.grapple;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pure maths of hauling a hooked ship by hand (GR5). */
class HaulMathTest {

    private static final double E = 1.0e-9;

    @Test
    void excessIsTheDistanceBeyondTheFrozenLengthAndNeverNegative() {
        assertEquals(2.0, HaulMath.excess(8.0, 6.0), E);
        assertEquals(0.0, HaulMath.excess(6.0, 6.0), E);
        assertEquals(0.0, HaulMath.excess(4.0, 6.0), E, "a slack rope never pushes");
    }

    @Test
    void tensionIsASpringPlusDamping() {
        assertEquals(120.0, HaulMath.tension(2.0, 0.0, 60.0, 30.0, 200.0), E);
        assertEquals(150.0, HaulMath.tension(2.0, 1.0, 60.0, 30.0, 200.0), E, "walking away pulls harder");
        assertEquals(90.0, HaulMath.tension(2.0, -1.0, 60.0, 30.0, 200.0), E, "a ship closing in is pulled less");
    }

    @Test
    void tensionIsCappedAndNeverNegative() {
        assertEquals(200.0, HaulMath.tension(10.0, 0.0, 60.0, 30.0, 200.0), E);
        assertEquals(200.0, HaulMath.tension(3.0, 5.0, 60.0, 30.0, 200.0), E);
        assertEquals(0.0, HaulMath.tension(0.5, -5.0, 60.0, 30.0, 200.0), E, "damping never turns the pull into a push");
    }

    @Test
    void noExcessOrNoStrengthMeansNoTension() {
        assertEquals(0.0, HaulMath.tension(0.0, 3.0, 60.0, 30.0, 200.0), E, "a slack rope does not pull, however fast the ends part");
        assertEquals(0.0, HaulMath.tension(2.0, 0.0, 60.0, 30.0, 0.0), E);
        assertEquals(0.0, HaulMath.tension(2.0, 0.0, 0.0, 30.0, 200.0), E);
    }

    @Test
    void theRopeSlipsSoTheSpringHoldsTheCap() {
        // cap 40 at 60 per block: at most 2/3 block of excess
        assertEquals(9.0 - 40.0 / 60.0, HaulMath.slip(9.0, 6.0, 60.0, 40.0), E);
        assertTrue(HaulMath.slips(9.0, 6.0, 60.0, 40.0));
        assertEquals(40.0, HaulMath.tension(HaulMath.excess(9.0, HaulMath.slip(9.0, 6.0, 60.0, 40.0)), 0.0, 60.0, 0.0, 40.0), E);
    }

    @Test
    void belowTheCapTheRopeHolds() {
        assertEquals(6.0, HaulMath.slip(8.0, 6.0, 60.0, 200.0), E);
        assertFalse(HaulMath.slips(8.0, 6.0, 60.0, 200.0));
        assertEquals(6.0, HaulMath.slip(5.0, 6.0, 60.0, 200.0), E, "a slip never shortens the rope");
    }

    @Test
    void withoutGripTheRopeRunsFreely() {
        assertEquals(9.0, HaulMath.slip(9.0, 6.0, 0.0, 200.0), E);
        assertEquals(9.0, HaulMath.slip(9.0, 6.0, 60.0, 0.0), E);
    }

    @Test
    void thePlayerIsPulledByTheTensionsShareOfTheCap() {
        assertEquals(0.04, HaulMath.playerPull(100.0, 200.0, 0.08), E);
        assertEquals(0.08, HaulMath.playerPull(300.0, 200.0, 0.08), E);
        assertEquals(0.0, HaulMath.playerPull(0.0, 200.0, 0.08), E);
    }

    @Test
    void onlyASneakingPlayerHoldingTheRopeOfAHookedShipHauls() {
        assertTrue(HaulMath.hauls(true, true, true, true, false, false));
        assertFalse(HaulMath.hauls(false, true, true, true, false, false), "toggle off");
        assertFalse(HaulMath.hauls(true, false, true, true, false, false), "the hook is not in a ship");
        assertFalse(HaulMath.hauls(true, true, false, true, false, false), "the rope is tied to a cleat");
        assertFalse(HaulMath.hauls(true, true, true, false, false, false), "not sneaking");
        assertFalse(HaulMath.hauls(true, true, true, true, true, false), "riding the rope");
        assertFalse(HaulMath.hauls(true, true, true, true, false, true), "aboard the hooked ship");
    }
}
