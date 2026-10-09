package com.richardsenger.piratesnships.combat.cannon.npc;

import com.richardsenger.piratesnships.combat.cannon.ShotKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** CAN3: which shot a gun crew firing by itself wants. */
class ShotChoiceTest {

    private static final ShotChoice.Rules RULES = new ShotChoice.Rules(true, 0.5, 3, 24, true, true);

    private static ShotKind wanted(GunneryState.Mode mode, double speed, int fighters, double distance) {
        return ShotChoice.wanted(new ShotChoice.Situation(mode, speed, fighters, distance), RULES);
    }

    @Test
    void firingAtWillAtAnyHostileShipTakesTheBall() {
        assertEquals(ShotKind.BALL, wanted(GunneryState.Mode.AT_WILL, 4.0, 0, 30));
        assertEquals(ShotKind.BALL, wanted(GunneryState.Mode.AT_WILL, 4.0, 2, 10));
    }

    @Test
    void aChasedQuarryUnderWayGetsChainShot() {
        assertEquals(ShotKind.CHAIN, wanted(GunneryState.Mode.TARGET, 4.0, 0, 40));
        assertEquals(ShotKind.CHAIN, wanted(GunneryState.Mode.TARGET, 0.5, 0, 40));
        // stopped already: sink her
        assertEquals(ShotKind.BALL, wanted(GunneryState.Mode.TARGET, 0.4, 0, 40));
        ShotChoice.Rules noChain = new ShotChoice.Rules(false, 0.5, 3, 24, true, true);
        assertEquals(ShotKind.BALL, ShotChoice.wanted(new ShotChoice.Situation(GunneryState.Mode.TARGET, 4.0, 0, 40), noChain));
        ShotChoice.Rules chainOff = new ShotChoice.Rules(true, 0.5, 3, 24, false, true);
        assertEquals(ShotKind.BALL, ShotChoice.wanted(new ShotChoice.Situation(GunneryState.Mode.TARGET, 4.0, 0, 40), chainOff));
    }

    @Test
    void aCrowdedDeckInRangeGetsGrapeshotBeforeAnythingElse() {
        assertEquals(ShotKind.GRAPE, wanted(GunneryState.Mode.AT_WILL, 0.0, 3, 24));
        assertEquals(ShotKind.GRAPE, wanted(GunneryState.Mode.TARGET, 4.0, 5, 10));
        // too few, or too far
        assertEquals(ShotKind.CHAIN, wanted(GunneryState.Mode.TARGET, 4.0, 2, 10));
        assertEquals(ShotKind.CHAIN, wanted(GunneryState.Mode.TARGET, 4.0, 5, 24.5));
        ShotChoice.Rules grapeOff = new ShotChoice.Rules(true, 0.5, 3, 24, true, false);
        assertEquals(ShotKind.BALL, ShotChoice.wanted(new ShotChoice.Situation(GunneryState.Mode.AT_WILL, 0, 9, 5), grapeOff));
    }
}
