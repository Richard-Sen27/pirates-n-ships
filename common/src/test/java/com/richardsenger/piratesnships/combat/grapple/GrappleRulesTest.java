package com.richardsenger.piratesnships.combat.grapple;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrappleRulesTest {

    private static final double EPS = 1e-9;
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    // ---- hits ----

    @Test
    void anotherShipIsALatch() {
        assertEquals(GrappleRules.HitKind.LATCH, GrappleRules.blockHit(B, A));
        assertEquals(GrappleRules.HitKind.LATCH, GrappleRules.blockHit(B, null), "a thrower on land latches onto a ship");
    }

    @Test
    void ownShipAndLandAreNoLatch() {
        assertEquals(GrappleRules.HitKind.OWN_SHIP, GrappleRules.blockHit(A, A));
        assertEquals(GrappleRules.HitKind.LAND, GrappleRules.blockHit(null, A));
        assertEquals(GrappleRules.HitKind.LAND, GrappleRules.blockHit(null, null));
    }

    @Test
    void sameShipNeedsAShip() {
        assertTrue(GrappleRules.sameShip(A, A));
        assertFalse(GrappleRules.sameShip(A, B));
        assertFalse(GrappleRules.sameShip(null, null));
    }

    // ---- release ----

    @Test
    void nothingHappensWhileAllIsWell() {
        assertEquals(GrappleRules.Release.NONE, GrappleRules.release(true, true, true, true, 10, 24));
        assertEquals(GrappleRules.Release.NONE, GrappleRules.release(true, true, true, true, 24, 24), "exactly the rope length holds");
    }

    @Test
    void walkingBeyondTheRopeSnapsItOnlyWhenLatched() {
        assertEquals(GrappleRules.Release.TOO_FAR, GrappleRules.release(true, true, true, true, 24.01, 24));
        assertEquals(GrappleRules.Release.NONE, GrappleRules.release(true, true, false, false, 30, 24),
                "a flying or retracting hook is not snapped (the rope runs out instead)");
    }

    @Test
    void releaseReasonsInOrder() {
        assertEquals(GrappleRules.Release.DISABLED, GrappleRules.release(false, false, true, false, 99, 24));
        assertEquals(GrappleRules.Release.OWNER_GONE, GrappleRules.release(true, false, true, false, 99, 24));
        assertEquals(GrappleRules.Release.SHIP_GONE, GrappleRules.release(true, true, true, false, 99, 24));
        assertEquals(GrappleRules.Release.NONE, GrappleRules.release(true, true, false, false, 1, 24), "no ship needed unless latched");
    }

    @Test
    void onlyASnappedRopeCanLoseTheHook() {
        assertTrue(GrappleRules.hookReturned(GrappleRules.Release.TOO_FAR, false));
        assertFalse(GrappleRules.hookReturned(GrappleRules.Release.TOO_FAR, true));
        for (GrappleRules.Release r : GrappleRules.Release.values()) {
            if (r != GrappleRules.Release.TOO_FAR) {
                assertTrue(GrappleRules.hookReturned(r, true), r + " lost the hook");
            }
        }
    }

    @Test
    void ropeRunsOutBeyondItsLength() {
        assertFalse(GrappleRules.ropeRunsOut(24, 24));
        assertTrue(GrappleRules.ropeRunsOut(24.5, 24));
    }

    // ---- tension ----

    @Test
    void slackRopeNeverPushes() {
        assertEquals(0.0, GrappleRules.tension(1.0, 1.5, 0, 100, 40), EPS);
        assertEquals(0.0, GrappleRules.tension(1.5, 1.5, -5, 100, 40), EPS, "at the rest length");
        assertEquals(0.0, GrappleRules.tension(Double.NaN, 1.5, 0, 100, 40), EPS);
    }

    @Test
    void tensionRampsUpOverOneBlockThenHoldsTheFullPull() {
        assertEquals(25.0, GrappleRules.tension(1.75, 1.5, 0, 100, 40), EPS);
        assertEquals(50.0, GrappleRules.tension(2.0, 1.5, 0, 100, 40), EPS);
        assertEquals(100.0, GrappleRules.tension(2.5, 1.5, 0, 100, 40), EPS);
        assertEquals(100.0, GrappleRules.tension(20.0, 1.5, 0, 100, 40), EPS, "capped at the haul force");
    }

    @Test
    void dampingEasesThePullWhileTheEndsApproach() {
        assertEquals(100.0 - 40.0 * 1.0, GrappleRules.tension(10, 1.5, -1.0, 100, 40), EPS);
        assertEquals(0.0, GrappleRules.tension(10, 1.5, -3.0, 100, 40), EPS, "closing at haul / damping or faster: no pull");
        assertEquals(100.0, GrappleRules.tension(10, 1.5, 2.0, 100, 40), EPS, "separating ends never exceed the haul force");
        assertEquals(50.0 + 40.0 * 0.5, GrappleRules.tension(2.0, 1.5, 0.5, 100, 40), EPS);
    }

    @Test
    void deadBandHoldsWithoutPullingJustBeyondTheHoldDistance() {
        double rest = GrappleRules.holdLength(1.5, 0.5);
        assertEquals(2.0, rest, EPS);
        assertEquals(1.5, GrappleRules.holdLength(1.5, -1.0), EPS, "negative slack is no slack");
        // the hulls settle a little beyond hold_distance (1.58 to 1.78 blocks measured): no pull, rope not taut
        for (double d : new double[] {1.58, 1.78, 2.0}) {
            assertEquals(0.0, GrappleRules.tension(d, rest, 0, 120, 40), EPS, "pull at " + d);
            assertEquals(0.0, GrappleRules.tension(d, rest, 1.0, 120, 40), EPS, "pull at " + d + " while separating");
            assertFalse(GrappleRules.taut(d, rest, 120), "taut at " + d);
        }
        // from farther out the ramp still pulls them together, starting at the end of the dead band
        assertEquals(60.0, GrappleRules.tension(2.5, rest, 0, 120, 40), EPS);
        assertEquals(120.0, GrappleRules.tension(3.0, rest, 0, 120, 40), EPS);
        assertEquals(120.0, GrappleRules.tension(9.0, rest, 0, 120, 40), EPS);
        assertTrue(GrappleRules.taut(2.01, rest, 120));
    }

    // ---- holding (stall detector) ----

    private static final int N = GrappleRules.STALL_SUBSTEPS;

    /** Feeds {@code count} substeps at a constant distance; returns the last result. */
    private static boolean feed(GrappleRules.Holding h, int count, double distance, double rest) {
        boolean r = false;
        for (int i = 0; i < count; i++) r = h.update(distance, rest);
        return r;
    }

    @Test
    void ropeThatStopsClosingNearTheHoldLengthHolds() {
        GrappleRules.Holding h = new GrappleRules.Holding();
        // closing from 8 to 3.05 blocks: never holding while the distance shrinks
        for (int i = 0; i < 100; i++) {
            double d = 8.0 - 0.05 * i;
            assertFalse(h.update(d, 2.0), "holding while closing at " + d);
        }
        // the hulls touch at 2.3 (beyond the 2.0 hold length): the first substep there is progress, then N without
        assertFalse(feed(h, N, 2.3, 2.0), "holding too early");
        assertTrue(h.update(2.3, 2.0), "a stalled rope near the hold length does not hold");
        assertTrue(h.holding());
    }

    @Test
    void hullsCreepingAtContactStillHold() {
        // measured in G11: at contact the distance creeps by a few thousandths per substep and jitters
        GrappleRules.Holding h = new GrappleRules.Holding();
        boolean held = false;
        for (int i = 0; i < 3 * N && !held; i++) {
            held = h.update(2.33 - 0.0002 * i + (i % 2 == 0 ? 0.004 : 0.0), 2.0);
        }
        assertTrue(held, "creeping hulls never held");
    }

    @Test
    void noStallBeforeTheRopeHasPulledForNSubsteps() {
        GrappleRules.Holding h = new GrappleRules.Holding();
        // just latched, ships at rest and not yet moving: N - 1 pulling substeps are not enough
        assertFalse(feed(h, N - 1, 2.5, 2.0));
        assertFalse(h.holding());
    }

    @Test
    void noStallFarOutOrWhileSlack() {
        GrappleRules.Holding far = new GrappleRules.Holding();
        // a heavy ship slow to start far out keeps being pulled
        assertFalse(feed(far, 5 * N, 12.0, 2.0), "holding far beyond the hold length");
        GrappleRules.Holding slack = new GrappleRules.Holding();
        assertFalse(feed(slack, 5 * N, 1.8, 2.0), "a slack rope inside the dead band is not a stall");
    }

    @Test
    void slowProgressKeepsThePullGoing() {
        GrappleRules.Holding h = new GrappleRules.Holding();
        double d = 2.9;
        for (int i = 0; i < 6 * N; i++) {
            if (i % (N / 2) == 0) d -= 0.02; // 0.02 blocks closer every N/2 substeps
            assertFalse(h.update(d, 2.0), "holding while still making progress at " + d);
        }
    }

    @Test
    void holdingEndsOnlyBeyondTheHysteresisMargin() {
        GrappleRules.Holding h = new GrappleRules.Holding();
        feed(h, 2 * N, 2.3, 2.0);
        assertTrue(h.holding());
        // drifting apart within the margin keeps holding
        assertTrue(h.update(2.9, 2.0));
        assertTrue(h.update(2.0 + GrappleRules.HOLD_HYSTERESIS, 2.0));
        // beyond it the rope pulls again, and needs N substeps of pulling before it can hold again
        assertFalse(h.update(3.2, 2.0));
        assertFalse(h.holding());
        assertFalse(feed(h, N - 2, 2.9, 2.0));
    }

    @Test
    void resetStartsOver() {
        GrappleRules.Holding h = new GrappleRules.Holding();
        feed(h, 2 * N, 2.3, 2.0);
        h.reset();
        assertFalse(h.holding());
        assertFalse(feed(h, N - 1, 2.3, 2.0));
    }

    @Test
    void noForceMeansNoPullAndNoTautRope() {
        assertEquals(0.0, GrappleRules.tension(10, 1.5, 0, 0, 40), EPS);
        assertFalse(GrappleRules.taut(10, 1.5, 0));
        assertTrue(GrappleRules.taut(10, 1.5, 30));
        assertFalse(GrappleRules.taut(1.0, 1.5, 30));
    }
}
