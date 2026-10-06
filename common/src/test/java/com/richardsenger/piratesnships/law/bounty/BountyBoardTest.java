package com.richardsenger.piratesnships.law.bounty;

import com.richardsenger.piratesnships.law.bounty.BountyBoard.ClaimMethod;
import com.richardsenger.piratesnships.law.bounty.BountyBoard.ClaimOutcome;
import com.richardsenger.piratesnships.law.bounty.BountyBoard.NavyChange;
import com.richardsenger.piratesnships.law.bounty.BountyBoard.PlaceOutcome;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BountyBoardTest {

    static final BountyRules RULES = BountyRules.defaults(); // threshold 50, withdraw < 25, 2/point, min 10, alive 1.5
    static final BountyTarget PIRATE = BountyTarget.player(new UUID(1, 1), "Blackbeard");
    static final BountyTarget NPC = BountyTarget.npc(new UUID(1, 2), "Pirate Captain");
    static final UUID PAYER = new UUID(2, 1);
    static final UUID HUNTER = new UUID(2, 2);

    static int ids = 0;

    static BountyBoard place(BountyBoard board, UUID payer, BountyTarget target, int amount) {
        var r = board.placePlayerBounty(new UUID(9, ids++), payer, "payer", target, amount, 0, RULES);
        assertTrue(r.placed(), r.outcome().toString());
        return r.board();
    }

    @Nested
    class Navy {
        @Test
        void placedExactlyAtThreshold() {
            assertEquals(NavyChange.UNCHANGED, BountyBoard.EMPTY.syncNavy(PIRATE, 49.9, 0, RULES).change());
            var s = BountyBoard.EMPTY.syncNavy(PIRATE, 50, 0, RULES);
            assertEquals(NavyChange.PLACED, s.change());
            assertEquals(100, s.board().navyBounty(PIRATE.id()).orElseThrow().amount());
            assertEquals(WantedNavy.id(PIRATE), s.board().navyBounty(PIRATE.id()).orElseThrow().id());
        }

        @Test
        void scalesAndRaisesWithScoreWithoutDuplicates() {
            var b = BountyBoard.EMPTY.syncNavy(PIRATE, 60, 0, RULES).board();
            var raised = b.syncNavy(PIRATE, 90, 10, RULES);
            assertEquals(NavyChange.RAISED, raised.change());
            b = raised.board();
            assertEquals(1, b.bounties().size(), "still one navy bounty");
            assertEquals(180, b.total(PIRATE.id(), 10));
            assertEquals(0, b.navyBounty(PIRATE.id()).orElseThrow().createdAt(), "keeps its creation time");
            assertEquals(NavyChange.UNCHANGED, b.syncNavy(PIRATE, 90, 20, RULES).change());
        }

        @Test
        void keptWhileDecayingThenWithdrawnBelowRatio() {
            var b = BountyBoard.EMPTY.syncNavy(PIRATE, 100, 0, RULES).board();
            var kept = b.syncNavy(PIRATE, 30, 1, RULES);
            assertEquals(NavyChange.UNCHANGED, kept.change(), "never lowered while the score decays");
            assertEquals(200, kept.board().total(PIRATE.id(), 1));
            assertEquals(NavyChange.UNCHANGED, b.syncNavy(PIRATE, 25, 1, RULES).change(), "withdraw bound is exclusive");
            var gone = b.syncNavy(PIRATE, 24.9, 2, RULES);
            assertEquals(NavyChange.WITHDRAWN, gone.change());
            assertFalse(gone.board().hasBounty(PIRATE.id(), 2));
        }

        @Test
        void hysteresisPreventsFlapping() {
            var b = BountyBoard.EMPTY.syncNavy(PIRATE, 50, 0, RULES).board();
            for (double s : new double[]{49, 51, 40, 50, 30}) {
                b = b.syncNavy(PIRATE, s, 0, RULES).board();
                assertTrue(b.navyBounty(PIRATE.id()).isPresent(), "score " + s);
            }
        }

        @Test
        void disabledNavyWithdrawsAndPlacesNothing() {
            var off = RULES.withNavy(false);
            assertEquals(NavyChange.UNCHANGED, BountyBoard.EMPTY.syncNavy(PIRATE, 500, 0, off).change());
            var b = BountyBoard.EMPTY.syncNavy(PIRATE, 500, 0, RULES).board();
            assertEquals(NavyChange.WITHDRAWN, b.syncNavy(PIRATE, 500, 0, off).change());
        }

        @Test
        void playerBountiesDoNotBlockNavyBounty() {
            var b = place(BountyBoard.EMPTY, PAYER, PIRATE, 20).syncNavy(PIRATE, 50, 0, RULES).board();
            assertEquals(120, b.total(PIRATE.id(), 0));
        }
    }

    /** Helper so the test reads clearly. */
    static final class WantedNavy {
        static UUID id(BountyTarget t) {
            return BountyBoard.navyBountyId(t.id());
        }
    }

    @Nested
    class PlayerBounties {
        @Test
        void stackOnTheSameTarget() {
            var b = place(place(place(BountyBoard.EMPTY, PAYER, NPC, 10), HUNTER, NPC, 25), PAYER, NPC, 15);
            assertEquals(50, b.total(NPC.id(), 0));
            assertEquals(3, b.forTarget(NPC.id(), 0).size());
        }

        @Test
        void refusals() {
            assertEquals(PlaceOutcome.BELOW_MINIMUM,
                    BountyBoard.EMPTY.placePlayerBounty(new UUID(0, 1), PAYER, "p", NPC, 9, 0, RULES).outcome());
            assertEquals(PlaceOutcome.SELF_TARGET,
                    BountyBoard.EMPTY.placePlayerBounty(new UUID(0, 1), PIRATE.id(), "p", PIRATE, 100, 0, RULES).outcome());
            var off = RULES.withPlayerBounties(false, 10, 0);
            var r = BountyBoard.EMPTY.placePlayerBounty(new UUID(0, 1), PAYER, "p", NPC, 100, 0, off);
            assertEquals(PlaceOutcome.DISABLED, r.outcome());
            assertSame(BountyBoard.EMPTY, r.board());
        }

        @Test
        void expiryWhenConfigured() {
            var rules = RULES.withPlayerBounties(true, 10, 1000);
            var b = BountyBoard.EMPTY.placePlayerBounty(new UUID(0, 1), PAYER, "p", NPC, 50, 100, rules).board();
            b = b.syncNavy(NPC, 60, 100, rules).board();
            assertEquals(170, b.total(NPC.id(), 1099));
            assertEquals(120, b.total(NPC.id(), 1100), "player bounty expired, navy bounty never does");
            var pruned = b.pruneExpired(1100);
            assertEquals(1, pruned.expired().size());
            assertEquals(1, pruned.board().bounties().size());
            assertSame(b, b.pruneExpired(50).board());
        }
    }

    @Nested
    class Claims {
        BountyBoard board() {
            return place(BountyBoard.EMPTY.syncNavy(PIRATE, 50, 0, RULES).board(), PAYER, PIRATE, 40);
        }

        @Test
        void deadWithProofPaysTotal() {
            var r = board().claim(PIRATE.id(), HUNTER, ClaimMethod.DEAD_WITH_PROOF, 0, RULES);
            assertEquals(ClaimOutcome.CLAIMED, r.outcome());
            assertEquals(140, r.payout());
            assertEquals(2, r.claimed().size());
            assertFalse(r.board().hasBounty(PIRATE.id(), 0));
            assertEquals(0.0, r.scoreFactor());
        }

        @Test
        void alivePaysMore() {
            var r = board().claim(PIRATE.id(), HUNTER, ClaimMethod.ALIVE, 0, RULES);
            assertEquals(210, r.payout());
            var custom = board().claim(PIRATE.id(), HUNTER, ClaimMethod.ALIVE, 0, RULES.withClaim(2.0, 0.5));
            assertEquals(280, custom.payout());
            assertEquals(0.5, custom.scoreFactor());
        }

        @Test
        void targetCannotClaimOwnBounty() {
            var b = board();
            var r = b.claim(PIRATE.id(), PIRATE.id(), ClaimMethod.ALIVE, 0, RULES);
            assertEquals(ClaimOutcome.SELF_CLAIM, r.outcome());
            assertEquals(0, r.payout());
            assertSame(b, r.board());
            assertEquals(1.0, r.scoreFactor());
        }

        @Test
        void payerIsNotPaidForOwnBountyButItIsFulfilled() {
            var r = board().claim(PIRATE.id(), PAYER, ClaimMethod.DEAD_WITH_PROOF, 0, RULES);
            assertEquals(100, r.payout(), "only the navy part");
            assertFalse(r.board().hasBounty(PIRATE.id(), 0));
        }

        @Test
        void noBounty() {
            var r = board().claim(NPC.id(), HUNTER, ClaimMethod.ALIVE, 0, RULES);
            assertEquals(ClaimOutcome.NO_BOUNTY, r.outcome());
            assertEquals(1.0, r.scoreFactor());
        }

        @Test
        void otherTargetsStayOnTheBoard() {
            var b = place(board(), PAYER, NPC, 30);
            var r = b.claim(PIRATE.id(), HUNTER, ClaimMethod.ALIVE, 0, RULES);
            assertEquals(30, r.board().total(NPC.id(), 0));
        }

        @Test
        void pirateTurnInByTier() {
            assertEquals(10, RULES.turnInReward(PirateTier.DECKHAND));
            assertEquals(150, RULES.turnInReward(PirateTier.CAPTAIN));
            int last = -1;
            for (PirateTier t : PirateTier.values()) {
                assertTrue(RULES.turnInReward(t) > last, "higher tiers pay more");
                last = RULES.turnInReward(t);
            }
        }
    }

    @Test
    void noticesSortedByTotal() {
        var a = BountyTarget.npc(new UUID(5, 1), "Anne");
        var z = BountyTarget.npc(new UUID(5, 2), "Zed");
        var b = place(place(place(BountyBoard.EMPTY, PAYER, a, 30), PAYER, z, 30), PAYER, NPC, 10);
        b = b.syncNavy(PIRATE, 100, 0, RULES).board();
        List<BountyBoard.Notice> n = b.notices(0);
        assertEquals(List.of("Blackbeard", "Anne", "Zed", "Pirate Captain"), n.stream().map(x -> x.target().name()).toList());
        assertTrue(n.get(0).navy());
        assertFalse(n.get(1).navy());
        assertEquals(200, n.get(0).total());
        assertTrue(BountyBoard.EMPTY.notices(0).isEmpty());
    }

    @Test
    void clearTarget() {
        var b = place(BountyBoard.EMPTY.syncNavy(PIRATE, 100, 0, RULES).board(), PAYER, NPC, 20).clearTarget(PIRATE.id());
        assertFalse(b.hasBounty(PIRATE.id(), 0));
        assertTrue(b.hasBounty(NPC.id(), 0));
    }

    @Test
    void navyAmountIsAtLeastOne() {
        assertEquals(1, RULES.navyAmount(0));
        assertEquals(101, RULES.navyAmount(50.4));
    }
}
