package com.richardsenger.piratesnships.rpg.career;

import com.richardsenger.piratesnships.rpg.deeds.Deed;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** The pure career rules: promotions on both ladders, their exclusion, enlisting, desertion, the letter and prizes. */
class CareerRulesTest {

    private static final CareerThresholds T = CareerThresholds.defaults();

    private static CareerRecord enlisted(NavyRank rank) {
        return CareerRecord.EMPTY.withNavy(rank);
    }

    @Test
    void defaultsMatchThePlan() {
        assertEquals(new CareerThresholds.NavyStep(10, 0, 0), T.step(NavyRank.MIDSHIPMAN));
        assertEquals(new CareerThresholds.NavyStep(25, 5, 1), T.step(NavyRank.LIEUTENANT));
        assertEquals(new CareerThresholds.NavyStep(45, 15, 3), T.step(NavyRank.CAPTAIN));
        assertEquals(new CareerThresholds.NavyStep(65, 30, 6), T.step(NavyRank.COMMODORE));
        assertEquals(new CareerThresholds.NavyStep(85, 60, 10), T.step(NavyRank.ADMIRAL));
        assertEquals(0, T.maxPirateRepToEnlist());
        assertEquals(5, T.captainWeight());
        assertEquals(200, T.letter().fee());
        assertEquals(20, T.letter().minNavyRep());
        assertTrue(T.desertionDeeds().contains("attack_navy"));
    }

    @Test
    void navyPromotionNeedsEveryRequirement() {
        CareerRecord r = enlisted(NavyRank.MIDSHIPMAN).plus(CareerCounter.PIRATES_KILLED, 4).plus(CareerCounter.NAVY_QUESTS, 1);
        assertEquals(Optional.empty(), CareerRules.nextNavyRank(r, 30, 0, T), "one pirate short");
        CareerRecord turnedIn = r.plus(CareerCounter.PIRATES_TURNED_IN, 1);
        assertEquals(Optional.of(NavyRank.LIEUTENANT), CareerRules.nextNavyRank(turnedIn, 30, 0, T), "turn-ins count");
        assertEquals(Optional.empty(), CareerRules.nextNavyRank(turnedIn, 24, 0, T), "reputation short");
        assertEquals(Optional.empty(), CareerRules.nextNavyRank(turnedIn, 30, 1, T), "liked by pirates");
        assertEquals(Optional.empty(), CareerRules.nextNavyRank(CareerRecord.EMPTY.plus(CareerCounter.PIRATES_KILLED, 100)
                .plus(CareerCounter.NAVY_QUESTS, 100), 100, -100, T), "not enlisted: no promotion");
        assertEquals(Optional.empty(), CareerRules.nextNavyRank(enlisted(NavyRank.ADMIRAL), 100, -100, T), "the top");
        // Requirements list what is missing
        var reqs = CareerRules.requirements(NavyRank.LIEUTENANT, r, 30, T);
        assertEquals(3, reqs.size());
        assertTrue(reqs.get(0).met());
        assertFalse(reqs.get(1).met());
        assertEquals(4, reqs.get(1).have());
    }

    @Test
    void infamyNeverRisesInService() {
        CareerRecord rich = CareerRecord.EMPTY.plus(CareerCounter.PLUNDER_COINS, 100);
        assertEquals(Optional.of(InfamyRank.BUCCANEER), CareerRules.nextInfamyRank(rich, 15, T));
        assertEquals(Optional.empty(), CareerRules.nextInfamyRank(rich, 14, T), "reputation short");
        assertEquals(Optional.empty(), CareerRules.nextInfamyRank(rich.withNavy(NavyRank.MIDSHIPMAN), 100, T), "in service");
        CareerRecord dread = rich.withInfamy(InfamyRank.BUCCANEER).plus(CareerCounter.PLUNDER_COINS, 900)
                .plus(CareerCounter.MERCHANTS_PLUNDERED, 1).plus(CareerCounter.SHIPS_CAPTURED, 1);
        assertEquals(Optional.empty(), CareerRules.nextInfamyRank(dread, 40, T), "two captures of three");
        assertEquals(Optional.of(InfamyRank.DREAD_CAPTAIN), CareerRules.nextInfamyRank(dread.plus(CareerCounter.OFFICERS_KILLED, 1), 40, T));
        assertTrue(CareerRules.turnsPirate(enlisted(NavyRank.CAPTAIN), InfamyRank.BUCCANEER));
        assertFalse(CareerRules.turnsPirate(enlisted(NavyRank.CAPTAIN), InfamyRank.DECKHAND));
        assertFalse(CareerRules.turnsPirate(CareerRecord.EMPTY, InfamyRank.PIRATE_LORD));
    }

    @Test
    void enlistingNeedsACleanName() {
        CareerRecord r = CareerRecord.EMPTY;
        assertEquals(CareerRules.EnlistVerdict.OK, CareerRules.enlist(r, 10, 0, false, false, true, T));
        assertEquals(CareerRules.EnlistVerdict.DISABLED, CareerRules.enlist(r, 10, 0, false, false, false, T));
        assertEquals(CareerRules.EnlistVerdict.LOW_STANDING, CareerRules.enlist(r, 9, 0, false, false, true, T));
        assertEquals(CareerRules.EnlistVerdict.PIRATE_FRIEND, CareerRules.enlist(r, 50, 1, false, false, true, T));
        assertEquals(CareerRules.EnlistVerdict.WANTED, CareerRules.enlist(r, 50, 0, true, false, true, T));
        assertEquals(CareerRules.EnlistVerdict.HOSTILE, CareerRules.enlist(r, 50, 0, false, true, true, T));
        assertEquals(CareerRules.EnlistVerdict.INFAMOUS, CareerRules.enlist(r.withInfamy(InfamyRank.BUCCANEER), 50, 0, false, false, true, T));
        assertEquals(CareerRules.EnlistVerdict.ALREADY_ENLISTED, CareerRules.enlist(enlisted(NavyRank.MIDSHIPMAN), 50, 0, false, false, true, T));
    }

    @Test
    void desertionDeedsCostTheRankOnlyInService() {
        CareerRules.DeedOutcome o = CareerRules.onDeed(enlisted(NavyRank.CAPTAIN), Deed.ATTACK_NAVY, CareerRules.Victim.OTHER, 0, 0, T);
        assertTrue(o.deserted());
        assertEquals(NavyRank.NONE, o.record().navy());
        assertFalse(o.record().enlisted());
        CareerRules.DeedOutcome civilian = CareerRules.onDeed(CareerRecord.EMPTY, Deed.KILL_NAVY, CareerRules.Victim.OFFICER, 0, 0, T);
        assertFalse(civilian.deserted());
        assertEquals(1, civilian.record().count(CareerCounter.OFFICERS_KILLED));
        CareerRules.DeedOutcome kill = CareerRules.onDeed(enlisted(NavyRank.CAPTAIN), Deed.KILL_PIRATE, CareerRules.Victim.OTHER, 0, 0, T);
        assertFalse(kill.deserted());
        assertEquals(NavyRank.CAPTAIN, kill.record().navy());
    }

    @Test
    void countersFromDeeds() {
        assertEquals(Map.of(CareerCounter.PIRATES_KILLED, 5L, CareerCounter.CAPTAINS_KILLED, 1L),
                CareerRules.counters(Deed.KILL_PIRATE, CareerRules.Victim.CAPTAIN, 0, 5));
        assertEquals(Map.of(CareerCounter.PIRATES_KILLED, 1L), CareerRules.counters(Deed.KILL_PIRATE, CareerRules.Victim.OTHER, 0, 5));
        assertEquals(Map.of(CareerCounter.PLUNDER_COINS, 70L), CareerRules.counters(Deed.FENCE_PLUNDER, CareerRules.Victim.OTHER, 70, 5));
        assertEquals(Map.of(), CareerRules.counters(Deed.FENCE_PLUNDER, CareerRules.Victim.OTHER, 0, 5));
        assertEquals(Map.of(CareerCounter.PIRATES_TURNED_IN, 1L), CareerRules.counters(Deed.TURN_IN_PIRATE, CareerRules.Victim.OTHER, 0, 5));
        assertEquals(Map.of(), CareerRules.counters(Deed.PAY_FINE, CareerRules.Victim.OTHER, 100, 5));
    }

    @Test
    void letterOfMarque() {
        CareerRecord r = CareerRecord.EMPTY;
        assertEquals(CareerRules.LetterVerdict.OK, CareerRules.letter(r, 20, 0, 200, true, T));
        assertEquals(CareerRules.LetterVerdict.TOO_POOR, CareerRules.letter(r, 20, 0, 199, true, T));
        assertEquals(CareerRules.LetterVerdict.LOW_STANDING, CareerRules.letter(r, 19, 0, 200, true, T));
        assertEquals(CareerRules.LetterVerdict.ENLISTED, CareerRules.letter(enlisted(NavyRank.MIDSHIPMAN), 50, 0, 200, true, T));
        assertEquals(CareerRules.LetterVerdict.OK, CareerRules.letter(r.withInfamy(InfamyRank.BUCCANEER), 20, 0, 200, true, T));
        assertEquals(CareerRules.LetterVerdict.INFAMOUS, CareerRules.letter(r.withInfamy(InfamyRank.DREAD_CAPTAIN), 20, 0, 200, true, T));
        assertEquals(CareerRules.LetterVerdict.DISABLED, CareerRules.letter(r, 20, 0, 200, false, T));

        CareerRecord held = r.withLetter(LetterState.ACTIVE, 0);
        assertEquals(CareerRules.LetterVerdict.ALREADY_HELD, CareerRules.letter(held, 20, 0, 200, true, T));
        CareerRules.DeedOutcome kill = CareerRules.onDeed(held, Deed.KILL_PIRATE, CareerRules.Victim.OTHER, 0, 0, T);
        assertEquals(5, kill.prize());
        CareerRules.DeedOutcome captain = CareerRules.onDeed(kill.record(), Deed.KILL_PIRATE, CareerRules.Victim.CAPTAIN, 0, 0, T);
        assertEquals(55, captain.record().prizeMoney());

        long now = 1000;
        CareerRules.DeedOutcome plunder = CareerRules.onDeed(captain.record(), Deed.PLUNDER_MERCHANT, CareerRules.Victim.OTHER, 0, now, T);
        assertTrue(plunder.letterVoided());
        assertFalse(plunder.deserted());
        assertEquals(LetterState.VOIDED, plunder.record().letter());
        assertEquals(55, plunder.record().prizeMoney(), "the prize stays");
        long until = now + T.letter().voidTicks();
        assertEquals(until, plunder.record().letterBlockedUntil());
        assertEquals(CareerRules.LetterVerdict.BLOCKED, CareerRules.letter(plunder.record(), 20, until - 1, 200, true, T));
        assertEquals(CareerRules.LetterVerdict.OK, CareerRules.letter(plunder.record(), 20, until, 200, true, T));
        assertEquals(3, CareerRules.blockedDays(plunder.record(), now));
        assertEquals(0, CareerRules.blockedDays(plunder.record(), until));
        assertEquals(0, CareerRules.onDeed(plunder.record(), Deed.KILL_PIRATE, CareerRules.Victim.OTHER, 0, now, T).prize(),
                "no prize under a void letter");
    }
}
