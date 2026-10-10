package com.richardsenger.piratesnships.ship.rigging;

import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.Point;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link RatlineNetRules}'s foot exit (RL6): the foot layer, the net's heading, the input's direction and the release. */
class RatlineFootRulesTest {

    /** Vanilla's yaws: south 0, west 90, north 180, east -90 (or 270). */
    private static final float SOUTH = 0f, WEST = 90f, EAST = -90f;
    /** A net rising east (a latch on a west rail to a mast inboard of it). */
    private static final double HX = 1, HZ = 0;

    private static boolean release(boolean onGround, boolean sneaking, boolean rising, float strafe, float forward, float yaw, double pitch) {
        double[] w = RatlineNetRules.wish(strafe, forward, yaw);
        return RatlineNetRules.footRelease(onGround, sneaking, rising, forward, pitch, w[0], w[1], HX, HZ);
    }

    @Test
    void theFootIsTheLowerFittingsLayer() {
        assertTrue(RatlineNetRules.atFoot(10, 10.0));
        assertTrue(RatlineNetRules.atFoot(10, 10.5));
        assertTrue(RatlineNetRules.atFoot(10, 10.999));
        assertFalse(RatlineNetRules.atFoot(10, 11.0));
        assertFalse(RatlineNetRules.atFoot(10, 9.99));
        assertTrue(RatlineNetRules.atFoot(-60, -59.5));
    }

    @Test
    void headingIsFromTheLowerRingTowardsTheHigherOne() {
        assertArrayEquals(new double[] {1, 0}, RatlineNetRules.heading(new Point(2, 2, 4), new Point(5, 6, 4)), 1e-9);
        // whichever ring is A
        assertArrayEquals(new double[] {1, 0}, RatlineNetRules.heading(new Point(5, 6, 4), new Point(2, 2, 4)), 1e-9);
        double[] diag = RatlineNetRules.heading(new Point(0, 0, 0), new Point(3, 5, 4));
        assertEquals(0.6, diag[0], 1e-9);
        assertEquals(0.8, diag[1], 1e-9);
        assertArrayEquals(new double[] {0, 0}, RatlineNetRules.heading(new Point(1, 1, 1), new Point(1, 6, 1)), 1e-9);
    }

    @Test
    void wishFollowsVanillasInputVector() {
        // forward facing south walks +z, facing east +x, facing west -x
        assertArrayEquals(new double[] {0, 1}, RatlineNetRules.wish(0, 1, SOUTH), 1e-6);
        assertArrayEquals(new double[] {1, 0}, RatlineNetRules.wish(0, 1, EAST), 1e-6);
        assertArrayEquals(new double[] {-1, 0}, RatlineNetRules.wish(0, 1, WEST), 1e-6);
        // strafing left (positive) facing east walks -z (north), right +z (south)
        assertArrayEquals(new double[] {0, -1}, RatlineNetRules.wish(1, 0, EAST), 1e-6);
        assertArrayEquals(new double[] {0, 1}, RatlineNetRules.wish(-1, 0, EAST), 1e-6);
        // back facing east walks west
        assertArrayEquals(new double[] {-1, 0}, RatlineNetRules.wish(0, -1, EAST), 1e-6);
    }

    @Test
    void sneakingAlwaysLetsGo() {
        assertTrue(release(true, true, false, 0, 1, EAST, 0));
        assertTrue(release(false, true, false, 0, 1, EAST, 0));
        assertTrue(release(false, true, true, 0, 0, EAST, 0));
    }

    @Test
    void aJumpClimbs() {
        assertFalse(release(true, false, true, 0, 0, EAST, 0));
        assertFalse(release(true, false, true, 0, 1, SOUTH, 0));
        assertFalse(release(true, false, true, 0, 1, EAST, 60));
    }

    @Test
    void withoutInputItStandsOnTheGroundAndHangsInTheAir() {
        assertTrue(release(true, false, false, 0, 0, EAST, 0));
        assertFalse(release(false, false, false, 0, 0, EAST, 0));
    }

    @Test
    void forwardTowardsTheNetClimbsLookingAheadOrUp() {
        assertFalse(release(true, false, false, 0, 1, EAST, 0));
        assertFalse(release(true, false, false, 0, 1, EAST, -40));
        assertFalse(release(true, false, false, 0, 1, EAST, RatlineNetRules.STEP_OFF_PITCH - 1));
        // up to 60 degrees off the heading
        assertFalse(release(true, false, false, 0, 1, EAST + 55, 0));
        assertFalse(release(true, false, false, 0, 1, EAST - 55, 0));
        // forward and strafe together, still mostly towards the net
        assertFalse(release(true, false, false, 1, 1, EAST, 0));
        // hanging in the bottom of the net too
        assertFalse(release(false, false, false, 0, 1, EAST, 0));
    }

    @Test
    void lookingDownAtTheDeckStepsOff() {
        assertTrue(release(true, false, false, 0, 1, EAST, RatlineNetRules.STEP_OFF_PITCH));
        assertTrue(release(true, false, false, 0, 1, EAST, 45));
        assertTrue(release(true, false, false, 0, 1, EAST, 90));
        // and keeps stepping off once over the rail's edge, dropping through the latch's cell
        assertTrue(release(false, false, false, 0, 1, EAST, 45));
    }

    @Test
    void alongTheRailOrAwayLetsGo() {
        // facing along the rail
        assertTrue(release(true, false, false, 0, 1, SOUTH, 0));
        assertTrue(release(true, false, false, 0, 1, 180f, 0));
        // more than 60 degrees off the heading
        assertTrue(release(true, false, false, 0, 1, EAST + 65, 0));
        // strafing along the rail while facing the net, on the rail and hanging in the bottom of the net
        assertTrue(release(true, false, false, 1, 0, EAST, 0));
        assertTrue(release(true, false, false, -1, 0, EAST, 0));
        assertTrue(release(false, false, false, -1, 0, EAST, 0));
        // facing away from the net and walking off
        assertTrue(release(true, false, false, 0, 1, WEST, 0));
    }

    @Test
    void backFacingTheNetHangsOnUntilTheFeetTouch() {
        assertFalse(release(false, false, false, 0, -1, EAST, 0));
        assertTrue(release(true, false, false, 0, -1, EAST, 0));
        // back facing away from the net walks towards it: not climbing down, let go
        assertTrue(release(false, false, false, 0, -1, WEST, 0));
    }

    @Test
    void aPlumbNetTakesAnyHeadingAsTowardsIt() {
        double[] w = RatlineNetRules.wish(0, 1, SOUTH);
        assertFalse(RatlineNetRules.footRelease(true, false, false, 1, 0, w[0], w[1], 0, 0));
        assertTrue(RatlineNetRules.footRelease(true, false, false, 1, 45, w[0], w[1], 0, 0));
        double[] s = RatlineNetRules.wish(1, 0, SOUTH);
        assertTrue(RatlineNetRules.footRelease(true, false, false, 0, 0, s[0], s[1], 0, 0));
    }
}
