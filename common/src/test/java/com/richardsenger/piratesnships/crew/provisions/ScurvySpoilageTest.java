package com.richardsenger.piratesnships.crew.provisions;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.richardsenger.piratesnships.crew.provisions.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ScurvySpoilageTest {

    private static final long ONSET = Math.round(S.scurvyOnsetDays() * DAY);

    @Test
    void preservedDietLeadsToScurvyAtOnset() {
        ProvisionStore s = store(KELP, 10_000, WATER, 1000);
        ProvisionUpdate before = run(s, crew(1), S, ONSET - 1);
        assertFalse(before.outcome().effects().scurvy());
        ProvisionUpdate at = run(s, crew(1), S, ONSET);
        assertTrue(at.outcome().effects().scurvy());
        assertEquals(0, at.outcome().effects().moraleChange(), 1e-9, "no scurvy time yet, so no morale loss");

        ProvisionUpdate dayAfter = run(s, crew(1), S, ONSET + DAY);
        assertEquals(-S.scurvyMoralePerDay(), dayAfter.outcome().effects().moraleChange(), 1e-6);
        assertEquals(1.0, dayAfter.outcome().effects().workSpeedMultiplier(), "scurvy is weakness, not a work speed factor");
    }

    @Test
    void citrusCuresScurvy() {
        ProvisionUpdate sick = run(store(KELP, 10_000, WATER, 1000), crew(1), S, ONSET + DAY);
        assertTrue(sick.outcome().effects().scurvy());
        // An apple is fresh, so it is eaten before the kelp: the next unit taken cures the crew.
        ProvisionUpdate cured = ProvisionRules.advance(sick.store().add(APPLE, 1), sick.state(), crew(1), S, 100);
        assertFalse(cured.outcome().effects().scurvy());
        assertEquals(100, cured.state().ticksSinceAntiScurvy(), 1e-6, "apple eaten right away (credit was used up)");
    }

    @Test
    void preservedCitrusCountsWhenFreshFoodDoesNot() {
        ProvisionSettings citrusOnly = S.toBuilder().freshFoodPreventsScurvy(false).build();
        assertTrue(ProvisionRules.preventsScurvy(LEMON, citrusOnly));
        assertTrue(ProvisionRules.preventsScurvy(APPLE, citrusOnly));
        assertFalse(ProvisionRules.preventsScurvy(BEEF, citrusOnly));
        assertTrue(ProvisionRules.preventsScurvy(BEEF, S));
        assertFalse(ProvisionRules.preventsScurvy(KELP, S));
        assertFalse(ProvisionRules.preventsScurvy(WATER, S));

        // A beef-only diet gives scurvy when only citrus counts, and doesn't by default.
        ProvisionSettings noSpoil = citrusOnly.toBuilder().spoilageEnabled(false).build();
        ProvisionStore beef = store(BEEF, 1000, WATER, 1000);
        assertTrue(run(beef, crew(1), noSpoil, ONSET).outcome().effects().scurvy());
        assertFalse(run(beef, crew(1), S.toBuilder().spoilageEnabled(false).build(), ONSET).outcome().effects().scurvy());

        // Lemons in the diet keep it away.
        ProvisionStore lemons = store(LEMON, 1000, WATER, 1000);
        assertFalse(run(lemons, crew(1), citrusOnly, ONSET * 2).outcome().effects().scurvy());
    }

    @Test
    void scurvyToggleOff() {
        ProvisionSettings off = S.toBuilder().scurvyEnabled(false).build();
        ProvisionUpdate u = run(store(KELP, 10_000, WATER, 1000), crew(1), off, ONSET * 3);
        assertFalse(u.outcome().effects().scurvy());
        assertEquals(0, u.state().ticksSinceAntiScurvy());
        assertEquals(0, u.outcome().effects().moraleChange(), 1e-9);
    }

    @Test
    void scurvyOnsetIsConfigurable() {
        ProvisionSettings quick = S.toBuilder().scurvyOnsetDays(1).build();
        assertTrue(run(store(KELP, 1000, WATER, 100), crew(1), quick, DAY).outcome().effects().scurvy());
    }

    @Test
    void scurvyClockStopsWithoutCrew() {
        ProvisionUpdate u = run(store(KELP, 1000), CrewHeadcount.NOBODY, S, ONSET * 2);
        assertFalse(u.outcome().effects().scurvy());
    }

    @Test
    void freshFoodSpoilsAfterItsShelfLife() {
        ProvisionStore s = store(BEEF, 10, BREAD, 10);
        ProvisionUpdate before = run(s, CrewHeadcount.NOBODY, S, BEEF.shelfLifeTicks() - 1);
        assertEquals(10, before.store().units("beef"));
        ProvisionUpdate after = run(s, CrewHeadcount.NOBODY, S, BEEF.shelfLifeTicks());
        assertEquals(0, after.store().units("beef"));
        assertEquals(10, after.store().units("bread"), "preserved food never spoils");
        assertEquals(Map.of("beef", 10), after.outcome().spoiled());
        assertEquals(Map.of("beef", 10), after.outcome().removed());
        assertEquals(10, run(s, CrewHeadcount.NOBODY, S, 1000 * DAY).store().units("bread"));
    }

    @Test
    void olderLotsSpoilFirst() {
        ProvisionStore s = ProvisionStore.of(new ProvisionLot(BEEF, 3, 4 * DAY), new ProvisionLot(BEEF, 5, 0));
        ProvisionUpdate u = run(s, CrewHeadcount.NOBODY, S, DAY);
        assertEquals(5, u.store().units("beef"));
        assertEquals(Map.of("beef", 3), u.outcome().spoiled());
    }

    @Test
    void spoilageToggleOff() {
        ProvisionSettings off = S.toBuilder().spoilageEnabled(false).build();
        ProvisionStore s = store(BEEF, 10);
        ProvisionUpdate u = run(s, CrewHeadcount.NOBODY, off, 100 * DAY);
        assertEquals(s, u.store(), "nothing spoils and ages don't advance");
        assertTrue(u.outcome().spoiled().isEmpty());
    }

    @Test
    void eatenBeforeSpoiledCountsAsEaten() {
        // One sailor eats 6 a day; beef (8) spoils after 5 days. Day 1..5: 30 nutrition → 4 beef eaten, the rest spoils.
        ProvisionUpdate u = run(store(BEEF, 10, WATER, 100), crew(1), S, BEEF.shelfLifeTicks());
        assertEquals(Map.of("beef", 4, "water", 5), u.outcome().consumed());
        assertEquals(Map.of("beef", 6), u.outcome().spoiled());
        assertFalse(u.outcome().hungry(), "the last beef's credit still covers the demand");
    }
}
