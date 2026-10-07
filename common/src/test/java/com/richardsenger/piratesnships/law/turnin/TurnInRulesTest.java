package com.richardsenger.piratesnships.law.turnin;

import com.richardsenger.piratesnships.law.LawService.ProofClaimOutcome;
import com.richardsenger.piratesnships.law.bounty.BountyBoard;
import com.richardsenger.piratesnships.law.bounty.BountyBoard.ClaimMethod;
import com.richardsenger.piratesnships.law.bounty.BountyRules;
import com.richardsenger.piratesnships.law.bounty.BountyTarget;
import com.richardsenger.piratesnships.law.bounty.PirateTier;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TurnInRulesTest {

    static final BountyRules RULES = BountyRules.defaults();
    static final BountyTarget TARGET = BountyTarget.npc(new UUID(1, 1), "Calico Jack");
    static final UUID PAYER = new UUID(2, 1);
    static final UUID HUNTER = new UUID(2, 2);

    @Test
    void officerDealsOnlyWhenEnabledAndNotHostile() {
        assertEquals(TurnInRules.Gate.OPEN, TurnInRules.gate(true, false));
        assertEquals(TurnInRules.Gate.HOSTILE, TurnInRules.gate(true, true));
        assertEquals(TurnInRules.Gate.DISABLED, TurnInRules.gate(false, false));
        assertEquals(TurnInRules.Gate.DISABLED, TurnInRules.gate(false, true), "switched off wins over hostility");
    }

    @Test
    void everyProofOutcomeHasItsOwnMessage() {
        assertEquals("proof.paid", TurnInRules.proofMessage(ProofClaimOutcome.CLAIMED));
        assertEquals("proof.blank", TurnInRules.proofMessage(ProofClaimOutcome.NOT_A_PROOF));
        assertEquals("proof.no_bounty", TurnInRules.proofMessage(ProofClaimOutcome.NO_BOUNTY));
        assertEquals("proof.self", TurnInRules.proofMessage(ProofClaimOutcome.SELF_CLAIM));
    }

    @Test
    void claimableProofPaysTheBountyOnce() {
        BountyBoard board = BountyBoard.EMPTY.placePlayerBounty(new UUID(9, 1), PAYER, "payer", TARGET, 40, 0, RULES).board();
        var claim = board.claim(TARGET.id(), HUNTER, ClaimMethod.DEAD_WITH_PROOF, 10, RULES, 5);
        assertTrue(claim.success());
        assertEquals(40, claim.payout());
        assertFalse(claim.board().hasBounty(TARGET.id(), 10), "a claimed bounty is gone");
        // a proof made before the bounty was placed claims nothing
        BountyBoard later = BountyBoard.EMPTY.placePlayerBounty(new UUID(9, 2), PAYER, "payer", TARGET, 40, 20, RULES).board();
        assertFalse(later.claim(TARGET.id(), HUNTER, ClaimMethod.DEAD_WITH_PROOF, 30, RULES, 10).success());
        // the target can't turn in a proof of himself
        assertEquals(BountyBoard.ClaimOutcome.SELF_CLAIM,
                board.claim(TARGET.id(), TARGET.id(), ClaimMethod.DEAD_WITH_PROOF, 10, RULES, 5).outcome());
    }

    @Test
    void aliveDeliveryPaysTheAliveFactor() {
        BountyBoard board = BountyBoard.EMPTY.placePlayerBounty(new UUID(9, 3), PAYER, "payer", TARGET, 40, 0, RULES).board();
        var claim = board.claim(TARGET.id(), HUNTER, ClaimMethod.ALIVE, 10, RULES);
        assertEquals(60, claim.payout(), "1.5 x 40");
        assertEquals(60, TurnInRules.expectedPayout(40, RULES.aliveFactor(), 0));
        assertEquals(71, TurnInRules.expectedPayout(41, 1.5, 9), "61.5 rounds up like the board, plus the reward");
    }

    @Test
    void pirateWithoutBountyPaysTheRankReward() {
        var d = TurnInRules.delivery(false, false, PirateTier.DECKHAND, 10);
        assertTrue(d.deliverable());
        assertEquals(PirateTier.DECKHAND, d.pirateTier());
        assertEquals(10, TurnInRules.expectedPayout(0, 1.5, 10));
    }

    @Test
    void zeroRewardSwitchesThePirateTurnInOff() {
        var none = TurnInRules.delivery(false, false, PirateTier.DECKHAND, 0);
        assertFalse(none.deliverable(), "no bounty and no reward: the navy refuses");
        var withBounty = TurnInRules.delivery(false, true, PirateTier.DECKHAND, 0);
        assertTrue(withBounty.deliverable(), "a bounty still pays");
        assertNull(withBounty.pirateTier(), "but no rank reward");
    }

    @Test
    void playersAndNonPiratesNeedABounty() {
        assertFalse(TurnInRules.delivery(true, false, PirateTier.CAPTAIN, 150).deliverable(), "a player is never a pirate turn-in");
        assertTrue(TurnInRules.delivery(true, true, null, 0).deliverable());
        assertNull(TurnInRules.delivery(true, true, PirateTier.CAPTAIN, 150).pirateTier());
        assertFalse(TurnInRules.delivery(false, false, null, 0).deliverable(), "a villager without a bounty");
        assertTrue(TurnInRules.delivery(false, true, null, 0).deliverable());
    }

    @Test
    void deliveryRangeIsInclusive() {
        assertTrue(TurnInRules.inRange(16.0, 4.0));
        assertFalse(TurnInRules.inRange(16.01, 4.0));
        assertTrue(TurnInRules.inRange(0.0, 4.0));
    }
}
