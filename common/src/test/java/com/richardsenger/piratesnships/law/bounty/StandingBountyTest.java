package com.richardsenger.piratesnships.law.bounty;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** BOS1: the navy's standing bounty on a named NPC is listed, never touched by the score sync, and claimed like any. */
class StandingBountyTest {

    private static final BountyRules RULES = BountyRules.defaults();
    private static final BountyTarget CAPTAIN = BountyTarget.npc(new UUID(7, 7), "Black-Tooth Bartholomew Crowe");

    @Test
    void standingBountyIsANavyBountyThatTheScoreSyncNeverWithdraws() {
        BountyBoard b = BountyBoard.EMPTY.placeStandingBounty(new UUID(1, 1), CAPTAIN, 300, 5);
        assertEquals(300, b.total(CAPTAIN.id(), 10));
        assertTrue(b.forTarget(CAPTAIN.id(), 10).get(0).isNavy());
        assertTrue(b.navyBounty(CAPTAIN.id()).isEmpty(), "not the score-driven navy bounty");
        BountyBoard.NavySync sync = b.syncNavy(CAPTAIN, 0.0, 20, RULES);
        assertEquals(BountyBoard.NavyChange.UNCHANGED, sync.change());
        assertEquals(300, sync.board().total(CAPTAIN.id(), 20), "a clean score keeps the standing bounty");
        BountyBoard.Notice notice = sync.board().notices(30).get(0);
        assertEquals(CAPTAIN.name(), notice.target().name());
        assertTrue(notice.navy());
        assertEquals(300, sync.board().total(CAPTAIN.id(), Long.MAX_VALUE - 1), "never expires");
    }

    @Test
    void proofClaimsTheStandingBounty() {
        BountyBoard b = BountyBoard.EMPTY.placeStandingBounty(new UUID(1, 1), CAPTAIN, 300, 5);
        BountyBoard.ClaimResult r = b.claim(CAPTAIN.id(), new UUID(2, 2), BountyBoard.ClaimMethod.DEAD_WITH_PROOF, 10, RULES, 6);
        assertTrue(r.success());
        assertEquals(300, r.payout());
        assertEquals(0, r.board().total(CAPTAIN.id(), 10));
        BountyBoard.ClaimResult alive = b.claim(CAPTAIN.id(), new UUID(2, 2), BountyBoard.ClaimMethod.ALIVE, 10, RULES);
        assertEquals(Math.round(300 * RULES.aliveFactor()), alive.payout(), "alive pays the alive factor");
    }

    @Test
    void zeroAmountPlacesNothing() {
        assertSame(BountyBoard.EMPTY, BountyBoard.EMPTY.placeStandingBounty(new UUID(1, 1), CAPTAIN, 0, 5));
    }
}
