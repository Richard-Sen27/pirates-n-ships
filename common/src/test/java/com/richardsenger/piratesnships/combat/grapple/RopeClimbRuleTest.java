package com.richardsenger.piratesnships.combat.grapple;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** GR6: a hand-held rope pulls its thrower only as a climb ({@link RopeSlideService#isClimb}), and the hint's throttle. */
class RopeClimbRuleTest {

    @Test
    void aHookHighEnoughAboveTheFeetIsAClimb() {
        assertTrue(RopeSlideService.isClimb(14.0, 10.0, 2.0), "4 blocks up a wall");
        assertTrue(RopeSlideService.isClimb(12.0, 10.0, 2.0), "exactly the minimum rise");
        assertTrue(RopeSlideService.isClimb(8.5, 5.0, 2.0), "a deck above a swimmer");
    }

    @Test
    void aLevelLowOrSlightlyRaisedHookIsNoClimb() {
        assertFalse(RopeSlideService.isClimb(11.0, 10.0, 2.0), "1 block up across a gap");
        assertFalse(RopeSlideService.isClimb(11.99, 10.0, 2.0), "just short of the minimum");
        assertFalse(RopeSlideService.isClimb(10.0, 10.0, 2.0), "level");
        assertFalse(RopeSlideService.isClimb(3.0, 10.0, 2.0), "below, e.g. from the crow's nest");
    }

    @Test
    void aZeroRiseRestoresTheGr5Pull() {
        assertTrue(RopeSlideService.isClimb(3.0, 10.0, 0.0));
        assertTrue(RopeSlideService.isClimb(10.0, 10.0, 0.0));
    }

    @Test
    void theHintShowsAtMostOncePerSecond() {
        long t = RopeSlideService.HINT_INTERVAL_TICKS;
        assertEquals(20, t, "once per second");
        assertTrue(RopeSlideService.hintDue(100, Long.MIN_VALUE), "never shown");
        assertFalse(RopeSlideService.hintDue(100, 100), "same tick");
        assertFalse(RopeSlideService.hintDue(100 + t - 1, 100));
        assertTrue(RopeSlideService.hintDue(100 + t, 100));
        assertTrue(RopeSlideService.hintDue(5, 100), "game time went back (a new world): shown again");
    }
}
