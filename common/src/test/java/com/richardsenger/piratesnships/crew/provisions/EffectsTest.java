package com.richardsenger.piratesnships.crew.provisions;

import org.junit.jupiter.api.Test;

import static com.richardsenger.piratesnships.crew.provisions.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** Hunger, thirst, desertion risk and rum. */
class EffectsTest {

    @Test
    void outOfFoodMeansHungry() {
        ProvisionUpdate u = run(store(WATER, 100), crew(2), S, DAY);
        assertTrue(u.outcome().hungry());
        assertFalse(u.outcome().thirsty());
        assertEquals(-S.hungerMoralePerDay(), u.outcome().effects().moraleChange(), 1e-9);
        assertEquals(S.hungryWorkSpeed(), u.outcome().effects().workSpeedMultiplier(), 1e-9);
        assertEquals(1.0 / S.hungerDesertionDays(), u.outcome().effects().desertionRisk(), 1e-9);
        assertFalse(u.outcome().effects().desertionCritical());
    }

    @Test
    void outOfWaterMeansThirsty() {
        ProvisionUpdate u = run(store(KELP, 100), crew(2), S, DAY);
        assertFalse(u.outcome().hungry());
        assertTrue(u.outcome().thirsty());
        assertEquals(-S.thirstMoralePerDay(), u.outcome().effects().moraleChange(), 1e-9);
        assertEquals(S.thirstyWorkSpeed(), u.outcome().effects().workSpeedMultiplier(), 1e-9);
        assertTrue(u.outcome().effects().desertionCritical(), "a day without water is critical by default");
    }

    @Test
    void thirstBitesFasterThanHunger() {
        ProvisionUpdate hungry = run(store(WATER, 100), crew(2), S, DAY / 2);
        ProvisionUpdate thirsty = run(store(KELP, 100), crew(2), S, DAY / 2);
        assertTrue(thirsty.outcome().effects().moraleChange() < hungry.outcome().effects().moraleChange());
        assertTrue(thirsty.outcome().effects().workSpeedMultiplier() < hungry.outcome().effects().workSpeedMultiplier());
        assertTrue(thirsty.outcome().effects().desertionRisk() > hungry.outcome().effects().desertionRisk());
    }

    @Test
    void bothShortagesStack() {
        ProvisionUpdate u = run(ProvisionStore.EMPTY, crew(2), S, DAY);
        assertEquals(-(S.hungerMoralePerDay() + S.thirstMoralePerDay()), u.outcome().effects().moraleChange(), 1e-9);
        assertEquals(S.hungryWorkSpeed() * S.thirstyWorkSpeed(), u.outcome().effects().workSpeedMultiplier(), 1e-9);
    }

    @Test
    void runningOutMidPeriodCountsOnlyTheShortPart() {
        // 3 kelp feed one sailor for half a day.
        ProvisionUpdate u = run(store(KELP, 3, WATER, 10), crew(1), S, DAY);
        assertTrue(u.outcome().hungry());
        assertEquals(DAY / 2.0, u.outcome().hungryTicks(), 1e-6);
        assertEquals(DAY / 2.0, u.state().hungryTicks(), 1e-6);
        assertEquals(-S.hungerMoralePerDay() / 2, u.outcome().effects().moraleChange(), 1e-6);
    }

    @Test
    void sustainedHungerRaisesTheDesertionSignalUntilCritical() {
        ProvisionStore s = store(WATER, 100);
        ProvisioningState st = ProvisioningState.INITIAL;
        double lastRisk = 0;
        for (int day = 1; day <= 3; day++) {
            ProvisionUpdate u = ProvisionRules.advance(s, st, crew(1), S, DAY);
            assertTrue(u.outcome().effects().desertionRisk() > lastRisk);
            lastRisk = u.outcome().effects().desertionRisk();
            assertEquals(day >= 3, u.outcome().effects().desertionCritical(), "day " + day);
            s = u.store();
            st = u.state();
        }
        // Food comes back: the streak and the risk reset.
        ProvisionUpdate fed = ProvisionRules.advance(s.add(KELP, 50), st, crew(1), S, 100);
        assertFalse(fed.outcome().hungry());
        assertEquals(0, fed.outcome().effects().desertionRisk());
        assertEquals(1.0, fed.outcome().effects().workSpeedMultiplier());
    }

    @Test
    void desertionThresholdsAreConfigurable() {
        ProvisionSettings slow = S.toBuilder().thirstDesertionDays(4).build();
        ProvisionUpdate u = run(store(KELP, 100), crew(1), slow, DAY);
        assertEquals(0.25, u.outcome().effects().desertionRisk(), 1e-9);
    }

    @Test
    void rumRationRaisesMorale() {
        ProvisionUpdate u = run(store(KELP, 100, WATER, 100, RUM, 10), CrewHeadcount.crew(4), S, DAY);
        assertTrue(u.outcome().rumIssued());
        assertFalse(u.outcome().drunk());
        assertEquals(9, u.store().units("rum")); // 4 × 0.25
        assertEquals(S.rumMoralePerDay(), u.outcome().effects().moraleChange(), 1e-9);
        assertEquals(1.0, u.outcome().effects().workSpeedMultiplier());
    }

    @Test
    void halfRationGivesHalfTheBoost() {
        ProvisionUpdate u = run(store(KELP, 100, WATER, 100, RUM, 10), CrewHeadcount.crew(4).withRum(0.5), S, DAY);
        assertEquals(S.rumMoralePerDay() / 2, u.outcome().effects().moraleChange(), 1e-9);
    }

    @Test
    void noRumNoBoost() {
        ProvisionUpdate u = run(store(KELP, 100, WATER, 100), CrewHeadcount.crew(4), S, DAY);
        assertFalse(u.outcome().rumIssued());
        assertEquals(0, u.outcome().effects().moraleChange(), 1e-9);
    }

    @Test
    void tooMuchRumSlowsWorkForAWhile() {
        ProvisionStore s = store(KELP, 1000, WATER, 1000, RUM, 100);
        ProvisionUpdate drunk = run(s, CrewHeadcount.crew(4).withRum(2), S, DAY);
        assertTrue(drunk.outcome().drunk());
        assertEquals(S.drunkWorkSpeed(), drunk.outcome().effects().workSpeedMultiplier(), 1e-9);
        assertEquals(98, drunk.store().units("rum"));
        assertEquals(S.rumMoralePerDay(), drunk.outcome().effects().moraleChange(), 1e-9, "no extra morale for over-ration");

        // Back to the normal ration: still drunk until the timer runs out.
        long d = S.drunkDurationTicks();
        ProvisionUpdate half = ProvisionRules.advance(drunk.store(), drunk.state(), CrewHeadcount.crew(4), S, d / 2);
        assertTrue(half.outcome().drunk());
        ProvisionUpdate sober = ProvisionRules.advance(half.store(), half.state(), CrewHeadcount.crew(4), S, d - d / 2);
        assertFalse(sober.outcome().drunk());
        assertEquals(1.0, sober.outcome().effects().workSpeedMultiplier());
    }

    @Test
    void drunkTimerStartsWhenTheRumRunsOut() {
        // 4 crew at a double ration drink 2 rum a day: 1 rum lasts half a day, then the timer counts down.
        long d = S.drunkDurationTicks();
        ProvisionStore s = store(KELP, 1000, WATER, 1000, RUM, 1);
        ProvisionUpdate u = run(s, CrewHeadcount.crew(4).withRum(2), S, DAY / 2 + d / 2);
        assertTrue(u.outcome().drunk());
        assertFalse(u.outcome().rumIssued());
        assertEquals(d / 2.0, u.state().drunkTicks(), 1e-6);
        ProvisionUpdate v = run(s, CrewHeadcount.crew(4).withRum(2), S, DAY / 2 + d);
        assertFalse(v.outcome().drunk());
    }

    @Test
    void drunkDurationIsConfigurable() {
        ProvisionSettings none = S.toBuilder().drunkDurationTicks(0).build();
        ProvisionUpdate u = run(store(RUM, 100), CrewHeadcount.crew(4).withRum(3), none, DAY);
        assertFalse(u.outcome().drunk());
    }
}
