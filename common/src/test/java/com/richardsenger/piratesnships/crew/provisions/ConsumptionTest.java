package com.richardsenger.piratesnships.crew.provisions;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.richardsenger.piratesnships.crew.provisions.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ConsumptionTest {

    @Test
    void scalesWithCrewSize() {
        ProvisionStore s = store(KELP, 100, WATER, 100);
        assertEquals(94, run(s, crew(1), S, DAY).store().units("kelp"));
        assertEquals(76, run(s, crew(4), S, DAY).store().units("kelp"));
        assertEquals(99, run(s, crew(1), S, DAY).store().units("water"));
        assertEquals(96, run(s, crew(4), S, DAY).store().units("water"));
    }

    @Test
    void scalesWithRate() {
        ProvisionStore s = store(KELP, 100);
        assertEquals(88, run(s, crew(1), S.toBuilder().consumptionRate(2).build(), DAY).store().units("kelp"));
        assertEquals(97, run(s, crew(1), S.toBuilder().consumptionRate(0.5).build(), DAY).store().units("kelp"));
        ProvisionUpdate zero = run(s, crew(1), S.toBuilder().consumptionRate(0).build(), DAY);
        assertEquals(100, zero.store().units("kelp"));
        assertFalse(zero.outcome().hungry());
    }

    @Test
    void fractionalDaysTakeWholeUnitsAndKeepCredit() {
        ProvisionStore s = store(KELP, 100);
        assertEquals(97, run(s, crew(1), S, DAY / 2).store().units("kelp"));
        ProvisionUpdate quarter = run(s, crew(1), S, DAY / 4); // 1.5 nutrition: two kelp taken, 0.5 credit left
        assertEquals(98, quarter.store().units("kelp"));
        assertEquals(0.5, quarter.state().foodCredit(), 1e-9);
        assertEquals(Map.of("kelp", 2), quarter.outcome().consumed());

        // The next quarter day uses the credit first, then takes one more unit.
        ProvisionUpdate next = ProvisionRules.advance(quarter.store(), quarter.state(), crew(1), S, DAY / 4);
        assertEquals(97, next.store().units("kelp"));
        assertEquals(0, next.state().foodCredit(), 1e-9);
    }

    @Test
    void creditCoversPartOfALargeUnit() {
        // Bread is 5 nutrition, one sailor eats 6 a day: two breads on day one, one on day two (credit 4 + 5 >= 6).
        ProvisionUpdate d1 = run(store(BREAD, 10), crew(1), S, DAY);
        assertEquals(8, d1.store().units("bread"));
        assertEquals(4, d1.state().foodCredit(), 1e-9);
        ProvisionUpdate d2 = ProvisionRules.advance(d1.store(), d1.state(), crew(1), S, DAY);
        assertEquals(7, d2.store().units("bread"));
        assertEquals(3, d2.state().foodCredit(), 1e-9);
    }

    @Test
    void prisonersTakeTheirShareButNoRum() {
        ProvisionStore s = store(KELP, 100, WATER, 100, RUM, 100);
        ProvisionUpdate onlyPrisoners = run(s, new CrewHeadcount(0, 2, 1), S, DAY);
        assertEquals(94, onlyPrisoners.store().units("kelp")); // 2 × 0.5 × 6
        assertEquals(99, onlyPrisoners.store().units("water"));
        assertEquals(100, onlyPrisoners.store().units("rum"));

        ProvisionUpdate mixed = run(s, new CrewHeadcount(2, 2, 1), S, DAY);
        assertEquals(82, mixed.store().units("kelp")); // (2 + 1) × 6
        assertEquals(97, mixed.store().units("water"));
        assertEquals(100 - 1, mixed.store().units("rum")); // 2 crew × 0.25 = 0.5 → one unit taken

        ProvisionUpdate share = run(s, new CrewHeadcount(0, 2, 0), S.toBuilder().prisonerShare(0.25).build(), DAY);
        assertEquals(97, share.store().units("kelp"));
    }

    @Test
    void consumptionDisabledChangesNothing() {
        ProvisionSettings off = S.toBuilder().consumptionEnabled(false).build();
        ProvisionStore s = store(BEEF, 10, WATER, 5);
        ProvisioningState hungry = new ProvisioningState(0, 0, 0, 5 * DAY, 5 * DAY, 100 * DAY, 100);
        ProvisionUpdate u = ProvisionRules.advance(s, hungry, CrewHeadcount.crew(10), off, 50 * DAY);
        assertEquals(s, u.store());
        assertEquals(hungry, u.state());
        assertEquals(ProvisionEffects.NONE, u.outcome().effects());
        assertFalse(u.outcome().hungry());
        assertTrue(u.outcome().removed().isEmpty());

        ProvisionUpdate empty = run(ProvisionStore.EMPTY, CrewHeadcount.crew(10), off, 10 * DAY);
        assertEquals(ProvisionEffects.NONE, empty.outcome().effects());
        assertEquals(SuppliesLeft.UNLIMITED, ProvisionRules.suppliesLeft(s, hungry, CrewHeadcount.crew(10), off));
    }

    @Test
    void emptyStoreStarvesTheCrew() {
        ProvisionUpdate u = run(ProvisionStore.EMPTY, crew(3), S, DAY);
        assertTrue(u.outcome().hungry());
        assertTrue(u.outcome().thirsty());
        assertEquals(DAY, u.outcome().hungryTicks(), 1e-9);
        assertEquals(ProvisionStore.EMPTY, u.store());
        assertTrue(u.outcome().consumed().isEmpty());
    }

    @Test
    void crewOfZeroConsumesNothingAndSuffersNothing() {
        ProvisionStore s = store(KELP, 10, WATER, 10, RUM, 10);
        ProvisionUpdate u = run(s, CrewHeadcount.NOBODY, S, 10 * DAY);
        assertEquals(10, u.store().units("kelp"));
        assertEquals(10, u.store().units("water"));
        assertEquals(10, u.store().units("rum"));
        assertEquals(ProvisionEffects.NONE, u.outcome().effects());

        ProvisionUpdate nothing = run(ProvisionStore.EMPTY, CrewHeadcount.NOBODY, S, 10 * DAY);
        assertFalse(nothing.outcome().hungry() || nothing.outcome().thirsty());
        assertEquals(ProvisionEffects.NONE, nothing.outcome().effects());
        assertEquals(SuppliesLeft.UNLIMITED, ProvisionRules.suppliesLeft(s, ProvisioningState.INITIAL, CrewHeadcount.NOBODY, S));
    }

    @Test
    void zeroOrNegativeTicksChangeNothing() {
        ProvisionStore s = store(KELP, 10);
        assertEquals(s, run(s, crew(5), S, 0).store());
        assertEquals(s, run(s, crew(5), S, -100).store());
    }

    @Test
    void weightSumsAllLots() {
        ProvisionStore s = store(KELP, 10, BEEF, 4, ProvisionType.water("barrel", 16, 16.0), 2, RUM, 3);
        assertEquals(10 * 0.25 + 4 * 0.25 + 2 * 16.0 + 3 * 0.5, s.totalWeight(), 1e-9);
        assertEquals(0, ProvisionStore.EMPTY.totalWeight());
        // Eating makes the store lighter.
        assertTrue(run(s, crew(4), S, DAY).store().totalWeight() < s.totalWeight());
    }
}
