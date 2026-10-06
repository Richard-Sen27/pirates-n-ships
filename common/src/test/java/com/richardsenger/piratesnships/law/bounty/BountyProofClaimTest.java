package com.richardsenger.piratesnships.law.bounty;

import com.richardsenger.piratesnships.law.bounty.BountyBoard.ClaimMethod;
import com.richardsenger.piratesnships.law.bounty.BountyBoard.ClaimOutcome;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A proof made at a kill only claims bounties that existed at that time. */
class BountyProofClaimTest {

    private static final BountyRules RULES = BountyRules.defaults();
    private static final BountyTarget TARGET = BountyTarget.npc(new UUID(1, 1), "Target");
    private static final UUID PAYER = new UUID(2, 2);
    private static final UUID HUNTER = new UUID(3, 3);

    @Test
    void laterBountiesStayOnTheBoard() {
        BountyBoard board = BountyBoard.EMPTY
                .placePlayerBounty(new UUID(9, 1), PAYER, "payer", TARGET, 20, 100, RULES).board()
                .placePlayerBounty(new UUID(9, 2), PAYER, "payer", TARGET, 30, 300, RULES).board();
        var claim = board.claim(TARGET.id(), HUNTER, ClaimMethod.DEAD_WITH_PROOF, 400, RULES, 200);
        assertEquals(ClaimOutcome.CLAIMED, claim.outcome());
        assertEquals(20, claim.payout());
        assertEquals(30L, claim.board().total(TARGET.id(), 400));
    }

    @Test
    void proofOlderThanEveryBountyClaimsNothing() {
        BountyBoard board = BountyBoard.EMPTY.placePlayerBounty(new UUID(9, 1), PAYER, "payer", TARGET, 20, 100, RULES).board();
        assertEquals(ClaimOutcome.NO_BOUNTY, board.claim(TARGET.id(), HUNTER, ClaimMethod.DEAD_WITH_PROOF, 400, RULES, 50).outcome());
        assertEquals(ClaimOutcome.CLAIMED, board.claim(TARGET.id(), HUNTER, ClaimMethod.DEAD_WITH_PROOF, 400, RULES, 100).outcome(),
                "a bounty placed in the same tick as the kill counts");
    }
}
