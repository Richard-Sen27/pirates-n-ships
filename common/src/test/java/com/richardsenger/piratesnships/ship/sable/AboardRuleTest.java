package com.richardsenger.piratesnships.ship.sable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The client's rule for the ship the player is on, shared by the HUD and the camera sway (HUD2, CAM1). */
class AboardRuleTest {

    private static final String SHIP = "sloop", OTHER = "brig";

    @Test
    void trackingGivesTheShipAtOnce() {
        AboardRule<String> r = new AboardRule<>();
        assertNull(r.ship());
        assertFalse(r.aboard());
        r.tick(SHIP, false, false);
        assertEquals(SHIP, r.ship());
        assertTrue(r.aboard());
        r.tick(OTHER, false, false);
        assertEquals(OTHER, r.ship(), "stepping onto another ship switches at once");
    }

    /** The human's report: mid-jump Sable stops tracking and the view lost the deck's tilt. */
    @Test
    void aJumpKeepsTheShip() {
        AboardRule<String> r = new AboardRule<>();
        r.tick(SHIP, false, false);
        for (int jump = 0; jump < 5; jump++) {
            for (int i = 0; i < 14; i++) {
                r.tick(null, true, false);
                assertEquals(SHIP, r.ship(), "lost mid-jump " + jump + " at tick " + i);
            }
            r.tick(SHIP, false, false);
        }
    }

    @Test
    void aLongClimbKeepsTheShip() {
        AboardRule<String> r = new AboardRule<>();
        r.tick(SHIP, false, false);
        for (int i = 0; i < 300; i++) {
            r.tick(null, true, false);
            assertEquals(SHIP, r.ship(), "lost on the ratlines at tick " + i);
        }
    }

    @Test
    void waterDropsTheShipAtOnceButTheHudKeepsItsGrace() {
        AboardRule<String> r = new AboardRule<>();
        r.tick(SHIP, false, false);
        for (int i = 0; i < 10; i++) r.tick(null, true, false); // over the rail
        assertEquals(SHIP, r.ship());
        r.tick(null, false, true); // splash
        assertNull(r.ship(), "in the water the camera lets go at once");
        assertTrue(r.aboard(), "the HUD keeps its 1.5 s grace");
        r.tick(null, true, false); // a jump out of the water does not bring the ship back
        assertNull(r.ship());
        for (int i = 0; i <= AboardRule.ABOARD_GRACE_TICKS; i++) r.tick(null, false, true);
        assertFalse(r.aboard());
        r.tick(SHIP, false, false);
        assertEquals(SHIP, r.ship(), "back aboard");
    }

    @Test
    void landingElsewhereDropsTheShip() {
        AboardRule<String> r = new AboardRule<>();
        r.tick(SHIP, false, false);
        for (int i = 0; i < 6; i++) r.tick(null, true, false); // off the gangway
        r.tick(null, false, true); // on the quay
        assertNull(r.ship());
    }

    @Test
    void aLossWithoutHoldingKeepsTheShipForTheGrace() {
        AboardRule<String> r = new AboardRule<>();
        r.tick(SHIP, false, false);
        for (int i = 0; i < AboardRule.ABOARD_GRACE_TICKS; i++) {
            r.tick(null, false, false);
            assertEquals(SHIP, r.ship(), "within the grace at tick " + i);
        }
        r.tick(null, false, false);
        assertNull(r.ship());
        assertFalse(r.aboard());
    }

    @Test
    void theHoldExpires() {
        AboardRule<String> r = new AboardRule<>();
        r.tick(SHIP, false, false);
        int kept = 0;
        for (int i = 0; i < AboardRule.MAX_HOLD_TICKS + 100; i++) {
            r.tick(null, true, false);
            if (r.ship() != null) kept++;
        }
        assertEquals(AboardRule.MAX_HOLD_TICKS + AboardRule.ABOARD_GRACE_TICKS, kept, 1);
        assertNull(r.ship());
        r.tick(null, true, false);
        assertNull(r.ship(), "holding does not bring it back");
    }

    @Test
    void resetForgetsTheShip() {
        AboardRule<String> r = new AboardRule<>();
        r.tick(SHIP, false, false);
        r.reset();
        assertNull(r.ship());
        assertFalse(r.aboard());
    }
}
