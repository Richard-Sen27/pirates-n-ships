package com.richardsenger.piratesnships.mob.captain;

import com.richardsenger.piratesnships.mob.HostilityRules;
import com.richardsenger.piratesnships.mob.MobFaction;
import com.richardsenger.piratesnships.mob.captain.DuelRules.End;
import com.richardsenger.piratesnships.mob.captain.DuelRules.Refusal;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** BOS1: who may challenge the captain, who keeps out, and when the duel ends. */
class DuelRulesTest {

    private static final DuelRules.Params P = new DuelRules.Params(true, 16, 32, 6000);
    private static final UUID ME = new UUID(1, 1);
    private static final UUID OTHER = new UUID(2, 2);

    @Test
    void aSneakingSwordsmanMayChallenge() {
        assertEquals(Refusal.NONE, DuelRules.challenge(P, true, true, false, null, ME, false));
        assertEquals(Refusal.NONE, DuelRules.challenge(P, true, true, false, ME, ME, false), "challenging again keeps the duel");
    }

    @Test
    void refusalsInOrder() {
        assertEquals(Refusal.DISABLED, DuelRules.challenge(new DuelRules.Params(false, 16, 32, 6000), true, true, false, null, ME, false));
        assertEquals(Refusal.NOT_SNEAKING, DuelRules.challenge(P, false, true, false, null, ME, false));
        assertEquals(Refusal.NO_SWORD, DuelRules.challenge(P, true, false, false, null, ME, false));
        assertEquals(Refusal.EXEMPT, DuelRules.challenge(P, true, true, true, null, ME, false));
        assertEquals(Refusal.BUSY, DuelRules.challenge(P, true, true, false, OTHER, ME, false));
        assertEquals(Refusal.GRUDGE, DuelRules.challenge(P, true, true, false, null, ME, true));
    }

    @Test
    void theCrewWithinTheTruceRangeKeepOut() {
        assertTrue(DuelRules.inTruce(P, 0));
        assertTrue(DuelRules.inTruce(P, 16 * 16), "the range is inclusive");
        assertFalse(DuelRules.inTruce(P, 16 * 16 + 0.01));
        assertFalse(DuelRules.inTruce(new DuelRules.Params(true, 0, 32, 6000), 1), "range 0: nobody keeps out");
    }

    @Test
    void trucesAreRenewedInShortStepsUpToTheDeadline() {
        long deadline = DuelRules.deadline(P, 1000);
        assertEquals(7000, deadline);
        assertEquals(1000 + DuelRules.TRUCE_RENEW_TICKS, DuelRules.truceUntil(1000, deadline));
        assertEquals(deadline, DuelRules.truceUntil(deadline - 5, deadline), "never past the deadline");
    }

    @Test
    void theDuelEndsOnADeathALeaveOrTheDeadline() {
        assertEquals(End.NONE, DuelRules.check(P, true, true, 10 * 10, 100, 200));
        assertEquals(End.CAPTAIN_DIED, DuelRules.check(P, false, false, 0, 100, 200), "his death first");
        assertEquals(End.CHALLENGER_DIED, DuelRules.check(P, true, false, 0, 100, 200));
        assertEquals(End.CHALLENGER_LEFT, DuelRules.check(P, true, true, 33 * 33, 100, 200));
        assertEquals(End.NONE, DuelRules.check(P, true, true, 32 * 32, 100, 200), "the leave range is inclusive");
        assertEquals(End.TIMED_OUT, DuelRules.check(P, true, true, 0, 200, 200));
    }

    /** The truce is part of the hostility rules: a pirate under truce doesn't attack on sight but fights back. */
    @Test
    void aTruceStopsAttacksOnSightButNotRetaliation() {
        HostilityRules.Params defaults = new HostilityRules.Params(true, true, true, false);
        HostilityRules.Target player = HostilityRules.Target.ofPlayer(false, false);
        HostilityRules.Target truce = player.withTruce(true);
        assertTrue(HostilityRules.attacksOnSight(MobFaction.PIRATE, player, defaults));
        assertFalse(HostilityRules.attacksOnSight(MobFaction.PIRATE, truce, defaults));
        assertTrue(HostilityRules.retaliates(MobFaction.PIRATE, truce, defaults));
        assertTrue(HostilityRules.keepsTarget(MobFaction.PIRATE, truce, defaults, true), "a grudge outlasts the truce");
        assertFalse(HostilityRules.keepsTarget(MobFaction.PIRATE, truce, defaults, false));
        assertTrue(truce.withLikedByPirates(true).truce() && truce.withShip(player.ship()).truce(), "the other withers keep it");
    }
}
