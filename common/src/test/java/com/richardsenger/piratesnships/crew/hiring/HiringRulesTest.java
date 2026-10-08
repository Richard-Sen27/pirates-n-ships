package com.richardsenger.piratesnships.crew.hiring;

import com.mojang.serialization.JsonOps;
import com.richardsenger.piratesnships.rpg.career.InfamyRank;
import com.richardsenger.piratesnships.rpg.career.NavyRank;
import com.richardsenger.piratesnships.trade.market.PortKind;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.richardsenger.piratesnships.crew.hiring.HiringRules.Verdict.NOT_ENLISTED;
import static com.richardsenger.piratesnships.crew.hiring.HiringRules.Verdict.OK;
import static com.richardsenger.piratesnships.crew.hiring.HiringRules.Verdict.PIRATES_DISTRUST;
import static com.richardsenger.piratesnships.crew.hiring.HiringRules.Verdict.VILLAGERS_REFUSE;
import static com.richardsenger.piratesnships.crew.hiring.HiringRules.Verdict.WRONG_PORT;
import static org.junit.jupiter.api.Assertions.*;

/** The pure hiring rules (CRW1): candidates per day, the eligibility matrix, the crew cap and dismissal rights. */
class HiringRulesTest {

    private static final HiringRules.Settings DEFAULTS = new HiringRules.Settings(InfamyRank.BUCCANEER, true);

    private static HiringRules.Standing standing(boolean villagersRefuse, boolean piratesFriendly, InfamyRank infamy, NavyRank navy) {
        return new HiringRules.Standing(villagersRefuse, piratesFriendly, infamy, navy, true, true);
    }

    private static final HiringRules.Standing NOBODY = standing(false, false, InfamyRank.DECKHAND, NavyRank.NONE);

    // ------------------------------------------------------------------ candidates

    @Test
    void candidatesAreDeterministicPerSeed() {
        long seed = HiringRules.seed("pirates_n_ships:village_10_20", 42);
        List<Candidate> a = HiringRules.candidates(CandidateKind.SAILOR, seed, 3, 10);
        List<Candidate> b = HiringRules.candidates(CandidateKind.SAILOR, seed, 3, 10);
        assertEquals(a, b);
        assertEquals(3, a.size());
        assertEquals(3, new HashSet<>(a.stream().map(Candidate::name).toList()).size(), "distinct names");
        assertEquals(3, new HashSet<>(a.stream().map(Candidate::id).toList()).size(), "distinct ids");
        assertTrue(a.stream().allMatch(c -> c.kind() == CandidateKind.SAILOR && c.fee() == 10));
    }

    @Test
    void anotherDayOrPortGivesOtherCandidates() {
        List<Candidate> today = HiringRules.candidates(CandidateKind.SAILOR, HiringRules.seed("p:a", 5), 3, 10);
        List<Candidate> tomorrow = HiringRules.candidates(CandidateKind.SAILOR, HiringRules.seed("p:a", 6), 3, 10);
        List<Candidate> elsewhere = HiringRules.candidates(CandidateKind.SAILOR, HiringRules.seed("p:b", 5), 3, 10);
        assertNotEquals(today.stream().map(Candidate::id).toList(), tomorrow.stream().map(Candidate::id).toList());
        assertNotEquals(today.stream().map(Candidate::id).toList(), elsewhere.stream().map(Candidate::id).toList());
    }

    @Test
    void candidateCountsAndFees() {
        assertTrue(HiringRules.candidates(CandidateKind.PIRATE, 1L, 0, 20).isEmpty());
        assertEquals(12, HiringRules.candidates(CandidateKind.PIRATE, 1L, 12, 20).size());
        assertEquals(0, HiringRules.candidates(CandidateKind.NAVY, 1L, 1, -5).get(0).fee(), "a fee is never negative");
    }

    @Test
    void candidateCodecRoundTrips() {
        Candidate c = new Candidate(UUID.randomUUID(), "Tom Barker", CandidateKind.NAVY, 15);
        var json = Candidate.CODEC.encodeStart(JsonOps.INSTANCE, c).getOrThrow();
        assertEquals(c, Candidate.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    // ------------------------------------------------------------------ eligibility

    @Test
    void eachPortOffersOneKind() {
        assertEquals(CandidateKind.SAILOR, CandidateKind.at(PortKind.SEAFARER_VILLAGE));
        assertEquals(CandidateKind.PIRATE, CandidateKind.at(PortKind.PIRATE_ISLAND));
        assertEquals(CandidateKind.NAVY, CandidateKind.at(PortKind.NAVY_OUTPOST));
        assertEquals(WRONG_PORT, HiringRules.eligible(PortKind.SEAFARER_VILLAGE, CandidateKind.PIRATE, NOBODY, DEFAULTS));
        assertEquals(WRONG_PORT, HiringRules.eligible(PortKind.NAVY_OUTPOST, CandidateKind.SAILOR, NOBODY, DEFAULTS));
    }

    @Test
    void villagesHireUnlessTheVillagersRefuse() {
        assertEquals(OK, HiringRules.eligible(PortKind.SEAFARER_VILLAGE, CandidateKind.SAILOR, NOBODY, DEFAULTS));
        assertEquals(VILLAGERS_REFUSE, HiringRules.eligible(PortKind.SEAFARER_VILLAGE, CandidateKind.SAILOR,
                standing(true, false, InfamyRank.DECKHAND, NavyRank.NONE), DEFAULTS));
    }

    @Test
    void islandsHireFriendsOrTheInfamous() {
        assertEquals(PIRATES_DISTRUST, HiringRules.eligible(PortKind.PIRATE_ISLAND, CandidateKind.PIRATE, NOBODY, DEFAULTS));
        assertEquals(OK, HiringRules.eligible(PortKind.PIRATE_ISLAND, CandidateKind.PIRATE,
                standing(false, true, InfamyRank.DECKHAND, NavyRank.NONE), DEFAULTS));
        assertEquals(OK, HiringRules.eligible(PortKind.PIRATE_ISLAND, CandidateKind.PIRATE,
                standing(false, false, InfamyRank.BUCCANEER, NavyRank.NONE), DEFAULTS));
        assertEquals(PIRATES_DISTRUST, HiringRules.eligible(PortKind.PIRATE_ISLAND, CandidateKind.PIRATE,
                standing(false, false, InfamyRank.BUCCANEER, NavyRank.NONE), new HiringRules.Settings(InfamyRank.DREAD_CAPTAIN, true)));
        // careers off: no infamy route
        HiringRules.Standing noCareers = new HiringRules.Standing(false, false, InfamyRank.PIRATE_LORD, NavyRank.NONE, false, true);
        assertEquals(PIRATES_DISTRUST, HiringRules.eligible(PortKind.PIRATE_ISLAND, CandidateKind.PIRATE, noCareers, DEFAULTS));
        // careers and reputation off: pirates sign on with anyone
        HiringRules.Standing nothing = new HiringRules.Standing(false, false, InfamyRank.DECKHAND, NavyRank.NONE, false, false);
        assertEquals(OK, HiringRules.eligible(PortKind.PIRATE_ISLAND, CandidateKind.PIRATE, nothing, DEFAULTS));
    }

    @Test
    void outpostsHireTheEnlisted() {
        assertEquals(NOT_ENLISTED, HiringRules.eligible(PortKind.NAVY_OUTPOST, CandidateKind.NAVY, NOBODY, DEFAULTS));
        for (NavyRank r : NavyRank.values()) {
            if (r == NavyRank.NONE) continue;
            assertEquals(OK, HiringRules.eligible(PortKind.NAVY_OUTPOST, CandidateKind.NAVY,
                    standing(false, false, InfamyRank.DECKHAND, r), DEFAULTS), r.name());
        }
        assertEquals(OK, HiringRules.eligible(PortKind.NAVY_OUTPOST, CandidateKind.NAVY, NOBODY,
                new HiringRules.Settings(InfamyRank.BUCCANEER, false)), "enlistment not required");
        HiringRules.Standing noCareers = new HiringRules.Standing(false, false, InfamyRank.DECKHAND, NavyRank.NONE, false, true);
        assertEquals(OK, HiringRules.eligible(PortKind.NAVY_OUTPOST, CandidateKind.NAVY, noCareers, DEFAULTS), "careers off");
    }

    // ------------------------------------------------------------------ cap and dismissal

    @Test
    void bunksCapTheCrew() {
        assertTrue(HiringRules.hasRoom(0, 1, true, 2));
        assertFalse(HiringRules.hasRoom(1, 1, true, 2));
        assertFalse(HiringRules.hasRoom(0, 0, true, 2), "no hammock, no crew");
        assertTrue(HiringRules.hasRoom(1, 0, false, 2), "without bunks required: max_without_bunks");
        assertFalse(HiringRules.hasRoom(2, 0, false, 2));
        assertTrue(HiringRules.hasRoom(3, 4, false, 2), "bunks still count above max_without_bunks");
        assertEquals(4, HiringRules.crewCap(4, false, 2));
    }

    @Test
    void dismissalRights() {
        UUID hirer = UUID.randomUUID(), owner = UUID.randomUUID(), stranger = UUID.randomUUID();
        assertTrue(HiringRules.mayDismiss(hirer, Optional.of(hirer), Optional.of(owner)));
        assertTrue(HiringRules.mayDismiss(owner, Optional.of(hirer), Optional.of(owner)));
        assertFalse(HiringRules.mayDismiss(stranger, Optional.of(hirer), Optional.of(owner)));
        assertFalse(HiringRules.mayDismiss(stranger, Optional.empty(), Optional.of(owner)));
        assertTrue(HiringRules.mayDismiss(stranger, Optional.of(hirer), Optional.empty()), "ownerless ship: anyone");
    }

    @Test
    void deckHeightIsTheMostCommonColumnTop() {
        assertEquals(9, HireSpots.deckHeight(List.of(9, 9, 9, 14, 12, 9)));
        assertEquals(5, HireSpots.deckHeight(List.of(5, 9)), "a tie takes the lower");
    }
}
