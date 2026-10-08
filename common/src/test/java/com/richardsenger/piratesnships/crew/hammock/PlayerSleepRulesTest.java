package com.richardsenger.piratesnships.crew.hammock;

import com.richardsenger.piratesnships.crew.hammock.PlayerSleepRules.Check;
import com.richardsenger.piratesnships.crew.hammock.PlayerSleepRules.Refusal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SLP1: when a player may lie down in a bunk, in vanilla's order. */
class PlayerSleepRulesTest {

    /** A free bunk in reach at night, nothing in the way. */
    private static Check ok() {
        return new Check(false, false, false, true, true, false, false, false, false);
    }

    @Test
    void aFreeBunkInReachAtNightIsFine() {
        assertEquals(Refusal.NONE, PlayerSleepRules.refusal(ok()));
    }

    @Test
    void eachProblemAloneIsReported() {
        assertEquals(Refusal.OTHER, PlayerSleepRules.refusal(new Check(true, false, false, true, true, false, false, false, false)));
        assertEquals(Refusal.CREW_IN_IT, PlayerSleepRules.refusal(new Check(false, true, false, true, true, false, false, false, false)));
        assertEquals(Refusal.OCCUPIED, PlayerSleepRules.refusal(new Check(false, false, true, true, true, false, false, false, false)));
        assertEquals(Refusal.NOT_HERE, PlayerSleepRules.refusal(new Check(false, false, false, false, true, false, false, false, false)));
        assertEquals(Refusal.TOO_FAR_AWAY, PlayerSleepRules.refusal(new Check(false, false, false, true, false, false, false, false, false)));
        assertEquals(Refusal.OBSTRUCTED, PlayerSleepRules.refusal(new Check(false, false, false, true, true, true, false, false, false)));
        assertEquals(Refusal.NOT_NOW, PlayerSleepRules.refusal(new Check(false, false, false, true, true, false, true, false, false)));
        assertEquals(Refusal.NOT_SAFE, PlayerSleepRules.refusal(new Check(false, false, false, true, true, false, false, true, false)));
    }

    @Test
    void creativePlayersIgnoreMonsters() {
        assertEquals(Refusal.NONE, PlayerSleepRules.refusal(new Check(false, false, false, true, true, false, false, true, true)));
    }

    /** Vanilla: the bed's own occupied check comes first, then reach and headroom, then the clock, then monsters. */
    @Test
    void vanillaOrder() {
        assertEquals(Refusal.CREW_IN_IT, PlayerSleepRules.refusal(new Check(true, true, true, false, false, true, true, true, false)));
        assertEquals(Refusal.OCCUPIED, PlayerSleepRules.refusal(new Check(true, false, true, false, false, true, true, true, false)));
        assertEquals(Refusal.TOO_FAR_AWAY, PlayerSleepRules.refusal(new Check(false, false, false, true, false, true, true, true, false)));
        assertEquals(Refusal.OBSTRUCTED, PlayerSleepRules.refusal(new Check(false, false, false, true, true, true, true, true, false)));
        assertEquals(Refusal.NOT_NOW, PlayerSleepRules.refusal(new Check(false, false, false, true, true, false, true, true, false)));
    }

    /** As at a bed: the spawn is set once the bunk is free, reachable and clear, even by day or with monsters about. */
    @Test
    void spawnIsSetLikeAtABed() {
        for (Refusal r : Refusal.values()) {
            boolean expected = r == Refusal.NONE || r == Refusal.NOT_NOW || r == Refusal.NOT_SAFE;
            assertEquals(expected, PlayerSleepRules.setsSpawn(r), r.name());
        }
    }

    @Test
    void reachIsThreeAcrossAndTwoUpOrDown() {
        assertTrue(PlayerSleepRules.inReach(0, 0, 0));
        assertTrue(PlayerSleepRules.inReach(3, 2, -3));
        assertTrue(PlayerSleepRules.inReach(-3, -2, 3));
        assertFalse(PlayerSleepRules.inReach(3.01, 0, 0));
        assertFalse(PlayerSleepRules.inReach(0, 2.01, 0));
        assertFalse(PlayerSleepRules.inReach(0, 0, -3.01));
    }
}
