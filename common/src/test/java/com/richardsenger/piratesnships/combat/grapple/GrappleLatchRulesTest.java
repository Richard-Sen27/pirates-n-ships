package com.richardsenger.piratesnships.combat.grapple;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RAIL3: a railing ({@code #grapple_latches}) holds a hook like a ring, and a hard haul tears it out. */
class GrappleLatchRulesTest {

    @Test
    void aStrongPointHoldsLikeARingWhileLatchesHold() {
        assertTrue(GrappleRules.holdsLikeRing(true, false, false), "a ring or cleat always holds like one");
        assertTrue(GrappleRules.holdsLikeRing(false, true, true), "a railing holds like a ring");
        assertFalse(GrappleRules.holdsLikeRing(false, true, false), "latches_hold off: a railing is a plain block");
        assertFalse(GrappleRules.holdsLikeRing(false, false, true), "a plain block never");
        // holding like a ring doubles the break length at the default multiplier
        assertEquals(64.0, GrappleRules.breakLength(32.0, GrappleRules.holdsLikeRing(false, true, true), 2.0), 1e-9);
    }

    @Test
    void aHaulAboveTheBreakForceTearsTheRailingOut() {
        assertFalse(GrappleRules.tornOut(120.0, 180.0), "a ship-to-ship haul at its cap does not");
        assertTrue(GrappleRules.tornOut(200.0, 180.0), "a hand haul at its cap does");
        assertFalse(GrappleRules.tornOut(180.0, 180.0), "at the force exactly it holds");
        assertFalse(GrappleRules.tornOut(1.0e6, 0.0), "0 = never");
        assertTrue(GrappleRules.hookReturned(GrappleRules.Release.TORN_OUT, true), "a torn-out railing gives the hook back");
    }
}
