package com.richardsenger.piratesnships.worldsim.captain;

import com.richardsenger.piratesnships.worldsim.captain.CaptainVoyageRules.Quarry;
import com.richardsenger.piratesnships.worldsim.captain.CaptainVoyageRules.Schedule;
import com.richardsenger.piratesnships.worldsim.captain.CaptainVoyageRules.Verdict;
import com.richardsenger.piratesnships.worldsim.lane.Lane;
import com.richardsenger.piratesnships.worldsim.lane.SeaGrid;
import com.richardsenger.piratesnships.worldsim.navy.HuntRules;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptainVoyageRulesTest {

    private static final Schedule EVERY_TWO_DAYS_HALF = new Schedule(2, 0.5);
    private static final Quarry DEFAULT_QUARRY = new Quarry(true, 1);

    // ------------------------------------------------------------------ schedule

    @Test
    void dueEveryVoyageDays() {
        assertFalse(CaptainVoyageRules.due(10, 10, 2));
        assertFalse(CaptainVoyageRules.due(11, 10, 2));
        assertTrue(CaptainVoyageRules.due(12, 10, 2));
        assertTrue(CaptainVoyageRules.due(40, 10, 2));
    }

    @Test
    void voyageDaysBelowOneCountAsOne() {
        assertFalse(CaptainVoyageRules.due(5, 5, 0));
        assertTrue(CaptainVoyageRules.due(6, 5, 0));
    }

    @Test
    void rollBelowTheChanceSails() {
        assertEquals(Verdict.SAIL, CaptainVoyageRules.decide(true, true, true, 12, 10, EVERY_TWO_DAYS_HALF, 0.49));
        assertEquals(Verdict.STAYS, CaptainVoyageRules.decide(true, true, true, 12, 10, EVERY_TWO_DAYS_HALF, 0.5));
        assertEquals(Verdict.SAIL, CaptainVoyageRules.decide(true, true, true, 12, 10, new Schedule(2, 1.0), 0.999));
        assertEquals(Verdict.STAYS, CaptainVoyageRules.decide(true, true, true, 12, 10, new Schedule(2, 0.0), 0.0));
    }

    @Test
    void verdictOrder() {
        assertEquals(Verdict.DISABLED, CaptainVoyageRules.decide(false, false, false, 12, 10, EVERY_TWO_DAYS_HALF, 0.0));
        assertEquals(Verdict.LOST, CaptainVoyageRules.decide(true, false, true, 12, 10, EVERY_TWO_DAYS_HALF, 0.0));
        assertEquals(Verdict.AWAY, CaptainVoyageRules.decide(true, true, false, 12, 10, EVERY_TWO_DAYS_HALF, 0.0));
        assertEquals(Verdict.NOT_DUE, CaptainVoyageRules.decide(true, true, true, 11, 10, EVERY_TWO_DAYS_HALF, 0.0));
    }

    @Test
    void onlySailingOrStayingUsesTheChance() {
        assertTrue(Verdict.SAIL.usesChance());
        assertTrue(Verdict.STAYS.usesChance());
        for (Verdict v : List.of(Verdict.NOT_DUE, Verdict.AWAY, Verdict.LOST, Verdict.DISABLED)) assertFalse(v.usesChance(), v.name());
    }

    @Test
    void atPostWhenUnloadedOrNearAndFree() {
        assertTrue(CaptainVoyageRules.atPost(false, false, 0, false, false), "unloaded: he stands there unseen");
        assertTrue(CaptainVoyageRules.atPost(true, true, CaptainVoyageRules.AT_POST_SLACK, false, false));
        assertFalse(CaptainVoyageRules.atPost(true, false, 0, false, false), "loaded but nowhere to be seen");
        assertFalse(CaptainVoyageRules.atPost(true, true, CaptainVoyageRules.AT_POST_SLACK + 0.1, false, false), "wandered off");
        assertFalse(CaptainVoyageRules.atPost(true, true, 0, true, false), "a prisoner");
        assertFalse(CaptainVoyageRules.atPost(true, true, 0, false, true), "dueling");
    }

    // ------------------------------------------------------------------ route

    @Test
    void openSeaCruiseGoesOutTheFullDistanceAndBack() {
        List<Lane.Point> r = CaptainVoyageRules.outAndBack(SeaGrid.allSea(32), 0.5, 0.5, 0.0, 600);
        assertEquals(3, r.size());
        assertEquals(r.get(0), r.get(2), "back to the start");
        assertEquals(new Lane.Point(1, 1), r.get(0));
        assertEquals(600, Math.hypot(r.get(1).x() - 0.5, r.get(1).z() - 0.5), 2.0);
        assertEquals(1, r.get(1).z(), 1, "east along +x");
    }

    @Test
    void landAheadTurnsTheCruiseToOpenSea() {
        // land everywhere east of x = 64 (cells 2 and up); open sea elsewhere
        SeaGrid grid = SeaGrid.of(32, (cx, cz) -> cx < 2);
        List<Lane.Point> r = CaptainVoyageRules.outAndBack(grid, 0, 0, 0.0, 600);
        Lane.Point tip = r.get(1);
        assertTrue(Math.hypot(tip.x(), tip.z()) >= 600 * CaptainVoyageRules.MIN_REACH_SHARE, "reach " + tip);
        assertTrue(tip.x() < 64, "stays off the land: " + tip);
    }

    @Test
    void enclosedSeaTakesTheFarthestReach() {
        // a sea pocket: cells -2..1 on both axes are sea, the rest land
        SeaGrid grid = SeaGrid.of(32, (cx, cz) -> cx >= -2 && cx <= 1 && cz >= -2 && cz <= 1);
        List<Lane.Point> r = CaptainVoyageRules.outAndBack(grid, 0, 0, 0.0, 600);
        double reach = Math.hypot(r.get(1).x(), r.get(1).z());
        assertTrue(reach > 0 && reach < 600 * CaptainVoyageRules.MIN_REACH_SHARE, "reach " + reach);
        for (int turn : CaptainVoyageRules.TURNS) {
            double a = Math.toRadians(turn * 45.0);
            Lane.Point other = com.richardsenger.piratesnships.worldsim.navy.PatrolRoutes.capToSea(grid, 0, 0, Math.cos(a) * 600, Math.sin(a) * 600);
            assertTrue(Math.hypot(other.x(), other.z()) <= reach + 1e-9, "a farther direction " + other);
        }
    }

    @Test
    void towardPortGoesAlongTheLaneAndBack() {
        List<Lane.Point> lane = List.of(new Lane.Point(0, 0), new Lane.Point(400, 0), new Lane.Point(400, 400));
        List<Lane.Point> r = CaptainVoyageRules.towardPort(lane, 600);
        assertEquals(new Lane.Point(0, 0), r.get(0));
        assertEquals(new Lane.Point(0, 0), r.get(r.size() - 1));
        assertTrue(r.contains(new Lane.Point(400, 200)), "turns 600 blocks out: " + r);
        assertEquals(1200, Lane.length(r), 1.0);
    }

    // ------------------------------------------------------------------ quarry

    @Test
    void huntsLetterHoldersAndProofCarriersOnly() {
        assertTrue(CaptainVoyageRules.hunted(true, true, 0, DEFAULT_QUARRY), "letter of marque");
        assertTrue(CaptainVoyageRules.hunted(true, false, 1, DEFAULT_QUARRY), "a bounty proof");
        assertFalse(CaptainVoyageRules.hunted(true, false, 0, DEFAULT_QUARRY), "an honest sailor");
        assertFalse(CaptainVoyageRules.hunted(false, true, 5, DEFAULT_QUARRY), "an NPC ship");
    }

    @Test
    void quarryConfigSwitchesTheReasons() {
        assertFalse(CaptainVoyageRules.hunted(true, true, 0, new Quarry(false, 1)), "letters ignored");
        assertFalse(CaptainVoyageRules.hunted(true, false, 2, new Quarry(true, 3)), "too few proofs");
        assertTrue(CaptainVoyageRules.hunted(true, false, 3, new Quarry(true, 3)));
        assertFalse(CaptainVoyageRules.hunted(true, false, 50, new Quarry(true, 0)), "proofs never draw him");
    }

    @Test
    void theNavyChaseFollowsOnlyHisVerdict() {
        HuntRules.Params p = CaptainVoyageRules.huntParams(192, 320, 64, 2400, 600);
        UUID hunted = UUID.randomUUID();
        UUID left = UUID.randomUUID();
        List<HuntRules.Candidate> seen = List.of(
                CaptainVoyageRules.candidate(left, 10, 0, true, false, false),
                CaptainVoyageRules.candidate(hunted, 100, 0, true, false, true),
                CaptainVoyageRules.candidate(UUID.randomUUID(), 300, 0, true, false, true));
        Optional<HuntRules.Candidate> pick = HuntRules.pick(0, 0, seen, p);
        assertEquals(Optional.of(hunted), pick.map(HuntRules.Candidate::ship), "the nearest hunted ship within the radius");
        assertFalse(HuntRules.hunted(seen.get(0), p.bountyMinimum()), "no flag or bounty draws him by itself");
    }

    @Test
    void hisQuarryStrikingItsColoursIsLeftAfterTheLinger() {
        HuntRules.Params p = CaptainVoyageRules.huntParams(192, 320, 64, 2400, 600);
        HuntRules.Candidate struck = CaptainVoyageRules.candidate(UUID.randomUUID(), 20, 0, true, true, true);
        HuntRules.Judgement j = HuntRules.judge(struck, 0, 0, 1000, 1000, 0, p);
        assertEquals(HuntRules.Step.SHADOW, j.step());
        HuntRules.Judgement later = HuntRules.judge(struck, 0, 0, 1000 + 600, j.lastContact(), j.surrenderedUntil(), p);
        assertEquals(HuntRules.Step.RESUME, later.step());
        assertEquals(HuntRules.Reason.SURRENDERED, later.reason());
    }

    @Test
    void aQuarryThatDropsItsProofsIsLetGo() {
        HuntRules.Params p = CaptainVoyageRules.huntParams(192, 320, 64, 2400, 600);
        HuntRules.Candidate innocent = CaptainVoyageRules.candidate(UUID.randomUUID(), 20, 0, true, false, false);
        HuntRules.Judgement j = HuntRules.judge(innocent, 0, 0, 1000, 1000, 0, p);
        assertEquals(HuntRules.Step.RESUME, j.step());
        assertEquals(HuntRules.Reason.NOT_HUNTED, j.reason());
    }
}
