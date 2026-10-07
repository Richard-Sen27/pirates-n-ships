package com.richardsenger.piratesnships.mob.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChaseRulesTest {

    private static final double REACH = 2.6;
    private static final double MARGIN = 0.3;
    private static final double SELF = 0.26;

    @Test
    void gapIsMeasuredToTheBoxFootprint() {
        // player box 0.6 wide centred on (3, 0): the near edge is 2.7 from the origin
        assertEquals(2.7, ChaseRules.gap(0, 0, 2.7, -0.3, 3.3, 0.3), 1e-9);
        assertEquals(0.0, ChaseRules.gap(3, 0, 2.7, -0.3, 3.3, 0.3), 1e-9, "inside the footprint");
        assertEquals(Math.sqrt(2), ChaseRules.gap(0, 0, 1, 1, 2, 2), 1e-9, "to the corner");
    }

    @Test
    void closingSpeedIsSmoothedAndIgnoresJumps() {
        double c = ChaseRules.closing(0, 3.0, 2.8);
        assertEquals(0.1, c, 1e-9);
        assertEquals(c, ChaseRules.closing(c, 3.0, 1.0), 1e-9, "a 2-block jump (teleport, knockback) is noise");
        assertTrue(ChaseRules.closing(0, 2.0, 2.2) < 0, "the gap grows: negative closing");
    }

    @Test
    void attacksOnlyWhenTheFirstHitFrameIsInsideReach() {
        // standing still, both: in reach with the margin, not at the edge
        assertTrue(ChaseRules.attackReaches(2.2, 0, 5, REACH, MARGIN, SELF));
        assertFalse(ChaseRules.attackReaches(2.5, 0, 5, REACH, MARGIN, SELF), "at the edge of reach");
        // the target walks away at 0.05 blocks/tick faster than we close: it would be out of reach when the hit comes
        assertFalse(ChaseRules.attackReaches(2.2, -0.05, 5, REACH, MARGIN, SELF));
        // walking in at 0.2 blocks/tick: start early, the swing lands as we arrive
        assertTrue(ChaseRules.attackReaches(3.2, 0.2, 5, REACH, MARGIN, SELF));
        // but never from far off, whatever the closing speed says
        assertFalse(ChaseRules.attackReaches(5.0, 0.6, 5, REACH, MARGIN, SELF));
    }

    @Test
    void approachHasHysteresis() {
        double stop = ChaseRules.stopGap(REACH, 1.0);
        assertEquals(1.6, stop, 1e-9);
        assertTrue(ChaseRules.approach(1.7, stop, true));
        assertFalse(ChaseRules.approach(1.6, stop, true), "walking: stops at the stop gap");
        assertFalse(ChaseRules.approach(1.9, stop, false), "standing: resumes only beyond the hysteresis");
        assertTrue(ChaseRules.approach(2.1, stop, false));
        assertEquals(0.25, ChaseRules.stopGap(0.5, 1.0), 1e-9, "never closer than a quarter block");
    }

    @Test
    void pathCadenceIsLikeVanillas() {
        assertFalse(ChaseRules.repath(1, true, 100, 0), "never before the delay ran out");
        assertTrue(ChaseRules.repath(0, true, 0, 0.9), "no path");
        assertTrue(ChaseRules.repath(0, false, 1.0, 0.9), "the target moved a block");
        assertFalse(ChaseRules.repath(0, false, 0.5, 0.9), "the target barely moved");
        assertTrue(ChaseRules.repath(0, false, 0.5, 0.01), "now and then anyway");
        assertEquals(ChaseRules.MIN_REPATH_TICKS, ChaseRules.nextRepathDelay(0, true));
        assertEquals(ChaseRules.MIN_REPATH_TICKS + ChaseRules.REPATH_JITTER - 1, ChaseRules.nextRepathDelay(1, true));
        assertEquals(ChaseRules.MIN_REPATH_TICKS + ChaseRules.FAILED_PATH_PENALTY, ChaseRules.nextRepathDelay(0, false));
        // at most 20 / MIN_REPATH_TICKS = 5 recalculations per second
        assertTrue(20.0 / ChaseRules.MIN_REPATH_TICKS <= 5.0);
    }
}
