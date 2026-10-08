package com.richardsenger.piratesnships.worldsim.navy;

import com.richardsenger.piratesnships.law.flag.FlagKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HuntRulesTest {

    private static final HuntRules.Params P = new HuntRules.Params(256, 50, 384, 64, 2400, 600);

    private static HuntRules.Candidate ship(double x, FlagKind flag) {
        return new HuntRules.Candidate(UUID.randomUUID(), x, 0, true, flag, false, false, 0, false);
    }

    private static HuntRules.Candidate with(HuntRules.Candidate c, boolean playerShip, boolean struck, boolean coverBlown, long bounty, boolean wanted) {
        return new HuntRules.Candidate(c.ship(), c.x(), c.z(), playerShip, c.shown(), struck, coverBlown, bounty, wanted);
    }

    @Test
    void jollyRogerIsHunted() {
        assertTrue(HuntRules.hunted(ship(0, FlagKind.JOLLY_ROGER), 50));
    }

    @Test
    void cleanMerchantAndNavyAndUnflaggedShipsAreNot() {
        assertFalse(HuntRules.hunted(ship(0, FlagKind.MERCHANT), 50));
        assertFalse(HuntRules.hunted(ship(0, FlagKind.NAVY), 50));
        assertFalse(HuntRules.hunted(ship(0, FlagKind.NONE), 50));
    }

    @Test
    void bountyCountsFromTheMinimum() {
        HuntRules.Candidate m = ship(0, FlagKind.MERCHANT);
        assertFalse(HuntRules.hunted(with(m, true, false, false, 49, false), 50));
        assertTrue(HuntRules.hunted(with(m, true, false, false, 50, false), 50));
    }

    @Test
    void wantedOwnerOrBlownCoverIsHuntedWhateverTheFlag() {
        HuntRules.Candidate m = ship(0, FlagKind.NAVY);
        assertTrue(HuntRules.hunted(with(m, true, false, false, 0, true), 50));
        assertTrue(HuntRules.hunted(with(m, true, false, true, 0, false), 50));
    }

    @Test
    void npcShipsAndStruckColoursAreNeverHunted() {
        HuntRules.Candidate jr = ship(0, FlagKind.JOLLY_ROGER);
        assertFalse(HuntRules.hunted(with(jr, false, false, false, 1000, true), 50));
        assertFalse(HuntRules.hunted(with(jr, true, true, true, 1000, true), 50));
    }

    @Test
    void pickTakesTheNearestHuntedShipWithinTheRadius() {
        HuntRules.Candidate far = ship(200, FlagKind.JOLLY_ROGER);
        HuntRules.Candidate near = ship(100, FlagKind.JOLLY_ROGER);
        HuntRules.Candidate nearer = ship(50, FlagKind.MERCHANT);
        HuntRules.Candidate outside = ship(300, FlagKind.JOLLY_ROGER);
        assertEquals(Optional.of(near), HuntRules.pick(0, 0, List.of(far, outside, nearer, near), P));
        assertEquals(Optional.empty(), HuntRules.pick(0, 0, List.of(outside, nearer), P));
        assertEquals(Optional.of(far), HuntRules.pick(0, 0, List.of(far), P), "256 radius includes 200");
    }

    @Test
    void chaseGoesOnWhileInContact() {
        HuntRules.Candidate t = ship(30, FlagKind.JOLLY_ROGER);
        HuntRules.Judgement j = HuntRules.judge(t, 0, 0, 5000, 0, 0, P);
        assertEquals(HuntRules.Step.CHASE, j.step());
        assertEquals(5000, j.lastContact(), "contact within 64 blocks resets the timer");
    }

    @Test
    void chaseEndsAfterGiveUpTicksWithoutContact() {
        HuntRules.Candidate t = ship(100, FlagKind.JOLLY_ROGER);
        assertEquals(HuntRules.Step.CHASE, HuntRules.judge(t, 0, 0, 2399, 0, 0, P).step());
        HuntRules.Judgement j = HuntRules.judge(t, 0, 0, 2400, 0, 0, P);
        assertEquals(HuntRules.Step.RESUME, j.step());
        assertEquals(HuntRules.Reason.GAVE_UP, j.reason());
    }

    @Test
    void lostOrFarOrNoLongerHuntedEndsTheChase() {
        assertEquals(HuntRules.Reason.LOST, HuntRules.judge(null, 0, 0, 10, 0, 0, P).reason());
        assertEquals(HuntRules.Reason.OUT_OF_RANGE, HuntRules.judge(ship(400, FlagKind.JOLLY_ROGER), 0, 0, 10, 0, 0, P).reason());
        assertEquals(HuntRules.Reason.NOT_HUNTED, HuntRules.judge(ship(30, FlagKind.MERCHANT), 0, 0, 10, 0, 0, P).reason());
    }

    @Test
    void struckColoursAreShadowedForTheLingerThenLeft() {
        HuntRules.Candidate struck = with(ship(30, FlagKind.JOLLY_ROGER), true, true, false, 0, false);
        HuntRules.Judgement first = HuntRules.judge(struck, 0, 0, 1000, 1000, 0, P);
        assertEquals(HuntRules.Step.SHADOW, first.step());
        assertEquals(1600, first.surrenderedUntil());
        assertEquals(HuntRules.Step.SHADOW, HuntRules.judge(struck, 0, 0, 1599, 1000, 1600, P).step());
        HuntRules.Judgement done = HuntRules.judge(struck, 0, 0, 1600, 1000, 1600, P);
        assertEquals(HuntRules.Step.RESUME, done.step());
        assertEquals(HuntRules.Reason.SURRENDERED, done.reason());
    }

    @Test
    void raisingTheColoursAgainRestartsTheChase() {
        HuntRules.Candidate flying = ship(30, FlagKind.JOLLY_ROGER);
        HuntRules.Judgement j = HuntRules.judge(flying, 0, 0, 1200, 1000, 1600, P);
        assertEquals(HuntRules.Step.CHASE, j.step());
        assertEquals(0, j.surrenderedUntil());
    }

    @Test
    void zeroLingerLeavesAtOnce() {
        HuntRules.Params p = new HuntRules.Params(256, 50, 384, 64, 2400, 0);
        HuntRules.Candidate struck = with(ship(30, FlagKind.JOLLY_ROGER), true, true, false, 0, false);
        assertEquals(HuntRules.Step.RESUME, HuntRules.judge(struck, 0, 0, 1000, 1000, 0, p).step());
    }
}
