package com.richardsenger.piratesnships.combat.grapple;

import com.richardsenger.piratesnships.combat.grapple.GrappleRules.NearEnd;
import com.richardsenger.piratesnships.combat.grapple.GrappleRules.Release;
import com.richardsenger.piratesnships.combat.grapple.GrappleRules.Tie;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The mooring ring's maths (GR1): catch radius, hold multiplier, near end, tying off, ring releases. */
class MooringRingRulesTest {

    private static final double EPS = 1e-9;
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    // ---- catch radius ----

    @Test
    void segmentDistanceToTheClosestPoint() {
        // a path along z passing 0.8 above the point
        assertEquals(0.8, GrappleRules.segmentDistance(0, 0.8, -2, 0, 0.8, 2, 0, 0, 0), EPS);
        // the closest point is an end
        assertEquals(1.0, GrappleRules.segmentDistance(0, 0, 1, 0, 0, 3, 0, 0, 0), EPS);
        assertEquals(5.0, GrappleRules.segmentDistance(3, 4, 0, 3, 4, 0, 0, 0, 0), EPS, "a still hook");
    }

    @Test
    void passWithinTheRadiusIsCaught() {
        assertTrue(GrappleRules.ringCatches(0.8, 1.0));
        assertTrue(GrappleRules.ringCatches(1.0, 1.0));
        assertFalse(GrappleRules.ringCatches(1.01, 1.0));
        assertFalse(GrappleRules.ringCatches(0.0, 0.0), "radius 0 catches nothing by passing (only direct hits)");
    }

    // ---- hold multiplier ----

    @Test
    void ringHoldsTheMultipleOfTheRope() {
        assertEquals(24, GrappleRules.breakLength(24, false, 2.0), EPS);
        assertEquals(48, GrappleRules.breakLength(24, true, 2.0), EPS);
        assertEquals(24, GrappleRules.breakLength(24, true, 0.5), EPS, "a ring never holds less than a plain latch");
    }

    @Test
    void ringLatchSurvivesTheDistanceThatSnapsAPlainOne() {
        double d = 30;
        assertEquals(Release.TOO_FAR, GrappleRules.release(true, true, true, true, true, d, GrappleRules.breakLength(24, false, 2)));
        assertEquals(Release.NONE, GrappleRules.release(true, true, true, true, true, d, GrappleRules.breakLength(24, true, 2)));
        assertEquals(Release.TOO_FAR, GrappleRules.release(true, true, true, true, true, 49, GrappleRules.breakLength(24, true, 2)));
    }

    // ---- releases with rings ----

    @Test
    void brokenRingReleasesAndReturnsTheHook() {
        assertEquals(Release.RING_GONE, GrappleRules.release(true, true, true, true, false, 1, 24));
        assertEquals(Release.RING_GONE, GrappleRules.release(true, true, false, false, false, 1, 24), "a tied rope with a flying hook");
        assertTrue(GrappleRules.hookReturned(Release.RING_GONE, true));
    }

    @Test
    void releaseOrderKeepsConfigOwnerAndShipFirst() {
        assertEquals(Release.DISABLED, GrappleRules.release(false, false, true, false, false, 99, 24));
        assertEquals(Release.OWNER_GONE, GrappleRules.release(true, false, true, false, false, 99, 24));
        assertEquals(Release.SHIP_GONE, GrappleRules.release(true, true, true, false, false, 99, 24));
        assertEquals(Release.NONE, GrappleRules.release(true, true, false, true, true, 99, 24), "a flying hook does not snap");
    }

    // ---- near end ----

    @Test
    void nearEndIsTheRingWhenTied() {
        assertEquals(NearEnd.THROWER, GrappleRules.nearEnd(false));
        assertEquals(NearEnd.RING, GrappleRules.nearEnd(true));
    }

    @Test
    void haulingShipFollowsTheNearEnd() {
        assertEquals(A, GrappleRules.haulingShip(NearEnd.THROWER, B, A));
        assertEquals(B, GrappleRules.haulingShip(NearEnd.RING, B, A), "the ring's ship hauls, not the player's");
        assertNull(GrappleRules.haulingShip(NearEnd.RING, null, A), "a ring on land: no ship at the near end");
        assertNull(GrappleRules.haulingShip(NearEnd.THROWER, B, null), "a thrower on land");
    }

    // ---- tying off ----

    @Test
    void tieNeedsAHookAnotherShipAndRope() {
        assertEquals(Tie.NO_HOOK, GrappleRules.tie(false, A, B, 5, 24));
        assertEquals(Tie.SAME_SHIP, GrappleRules.tie(true, B, B, 5, 24));
        assertEquals(Tie.TOO_FAR, GrappleRules.tie(true, A, B, 25, 24));
        assertEquals(Tie.OK, GrappleRules.tie(true, A, B, 24, 24));
        assertEquals(Tie.OK, GrappleRules.tie(true, null, B, 5, 24), "a ring on a quay");
        assertEquals(Tie.OK, GrappleRules.tie(true, A, null, 5, 24), "a hook still flying");
    }
}
