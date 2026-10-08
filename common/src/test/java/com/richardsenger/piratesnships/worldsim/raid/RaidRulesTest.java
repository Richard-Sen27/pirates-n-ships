package com.richardsenger.piratesnships.worldsim.raid;

import com.richardsenger.piratesnships.worldsim.lane.Lane;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pure raid rules of WS5 (design.md §10.4). */
class RaidRulesTest {

    private static final double EPS = 1e-9;
    private static final RaidRules.Params P = new RaidRules.Params(0.0005, 0.02, 5.0, 1.0);

    @Test
    void chanceGrowsLinearlyWithTheMinutes() {
        assertEquals(0.0, RaidRules.chance(0, P), EPS);
        assertEquals(0.0005, RaidRules.chance(1, P), EPS);
        assertEquals(0.005, RaidRules.chance(10, P), EPS);
        assertEquals(2 * RaidRules.chance(15, P), RaidRules.chance(30, P), EPS);
    }

    @Test
    void chanceIsCapped() {
        assertEquals(0.02, RaidRules.chance(40, P), EPS);
        assertEquals(0.02, RaidRules.chance(10_000, P), EPS);
        assertEquals(0.0, RaidRules.chance(100, new RaidRules.Params(0.0005, 0.0, 5.0, 1.0)), EPS);
    }

    @Test
    void presenceCountsUpAndStartsOverWhenNobodyIsThere() {
        int m = 0;
        for (int i = 0; i < 3; i++) m = RaidRules.nextMinutes(m, true);
        assertEquals(3, m);
        assertEquals(0, RaidRules.nextMinutes(m, false));
        assertEquals(1, RaidRules.nextMinutes(0, true));
    }

    @Test
    void chanceIsZeroDuringTheCooldownAndBackAfterIt() {
        long raidDay = 10;
        assertEquals(0.0, RaidRules.chance(20, P, raidDay, 10), EPS);
        assertEquals(0.0, RaidRules.chance(20, P, raidDay, 14), EPS);
        assertEquals(0.01, RaidRules.chance(20, P, raidDay, 15), EPS);
        assertEquals(0.01, RaidRules.chance(20, P, RaidRules.NO_DAY, 0), EPS);
        assertTrue(RaidRules.onCooldown(raidDay, 12, 5.0));
        assertFalse(RaidRules.onCooldown(RaidRules.NO_DAY, 12, 5.0));
        assertEquals(3.0, RaidRules.cooldownLeft(raidDay, 12, 5.0), EPS);
        assertEquals(0.0, RaidRules.cooldownLeft(raidDay, 20, 5.0), EPS);
        assertEquals(0.0, RaidRules.cooldownLeft(RaidRules.NO_DAY, 20, 5.0), EPS);
    }

    @Test
    void afterARaidTheCountStartsOver() {
        // the raid sets the presence count to 0: the next minute is the first again
        assertEquals(RaidRules.chance(1, P), RaidRules.chance(RaidRules.nextMinutes(0, true), P), EPS);
    }

    @Test
    void tensionMultipliesTheGrowthOnlyWithRetaliation() {
        assertEquals(1.0, RaidRules.multiplier(false, 1.0, 0.8), EPS);
        assertEquals(1.8, RaidRules.multiplier(true, 1.0, 0.8), EPS);
        assertEquals(2.6, RaidRules.multiplier(true, 2.0, 0.8), EPS);
        assertEquals(2.0, RaidRules.multiplier(true, 1.0, 5.0), EPS); // tension clamped to 1
        assertEquals(1.0, RaidRules.multiplier(true, 1.0, -1.0), EPS);
        RaidRules.Params tense = new RaidRules.Params(0.0005, 0.02, 5.0, RaidRules.multiplier(true, 1.0, 0.8));
        assertEquals(0.0005 * 1.8 * 10, RaidRules.chance(10, tense), EPS);
        assertEquals(0.02, RaidRules.chance(30, tense), EPS); // the cap still holds
    }

    @Test
    void rollHitsBelowTheChance() {
        assertTrue(RaidRules.rolls(0.02, 0.01));
        assertFalse(RaidRules.rolls(0.02, 0.02));
        assertFalse(RaidRules.rolls(0.0, 0.0));
    }

    @Test
    void outcomeNeedsAFight() {
        assertEquals(RaidRules.Outcome.NONE, RaidRules.outcome(false, true));
        assertEquals(RaidRules.Outcome.REPELLED, RaidRules.outcome(true, false));
        assertEquals(RaidRules.Outcome.SUCCEEDED, RaidRules.outcome(true, true));
    }

    @Test
    void landingDistance() {
        assertTrue(RaidRules.withinLanding(10, 0, 0, 0, 24));
        assertTrue(RaidRules.withinLanding(24, 0, 0, 0, 24));
        assertFalse(RaidRules.withinLanding(20, 20, 0, 0, 24));
    }

    @Test
    void approachIsTheLastStretchOfTheLane() {
        List<Lane.Point> lane = List.of(new Lane.Point(0, 0), new Lane.Point(400, 0), new Lane.Point(400, 300));
        List<Lane.Point> a = RaidRules.approach(lane, 320);
        assertEquals(new Lane.Point(380, 0), a.get(0));
        assertEquals(List.of(new Lane.Point(380, 0), new Lane.Point(400, 0), new Lane.Point(400, 300)), a);
        assertEquals(320, Lane.length(a), 1e-6);
        assertEquals(List.of(new Lane.Point(400, 20), new Lane.Point(400, 300)), RaidRules.approach(lane, 280));
        assertEquals(lane, RaidRules.approach(lane, 5000));
    }

    @Test
    void straightApproachComesInFromTheSea() {
        List<Lane.Point> s = RaidRules.straight(new Lane.Point(100, 100), 0, 10, 320);
        assertEquals(List.of(new Lane.Point(100, 420), new Lane.Point(100, 100)), s);
        assertEquals(new Lane.Point(100, -220), RaidRules.straight(new Lane.Point(100, 100), 0, 0, 320).get(0));
    }

    @Test
    void homeIsTheApproachReversedFromTheShip() {
        List<Lane.Point> a = List.of(new Lane.Point(380, 0), new Lane.Point(400, 0), new Lane.Point(400, 300));
        assertEquals(List.of(new Lane.Point(398, 280), new Lane.Point(400, 0), new Lane.Point(380, 0)),
                RaidRules.home(a, 398.4, 280.9));
        assertEquals(List.of(new Lane.Point(5, 5), new Lane.Point(0, 0)),
                RaidRules.home(List.of(new Lane.Point(0, 0), new Lane.Point(10, 10)), 5.5, 5.5));
    }
}
