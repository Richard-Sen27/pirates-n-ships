package com.richardsenger.piratesnships.crew.provisions;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.richardsenger.piratesnships.crew.provisions.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderAndDaysLeftTest {

    @Test
    void freshFoodIsEatenBeforePreservedFood() {
        ProvisionUpdate u = run(store(BREAD, 10, BEEF, 10), crew(1), S, DAY);
        assertEquals(Map.of("beef", 1), u.outcome().consumed());
    }

    @Test
    void foodClosestToSpoilingIsEatenFirst() {
        ProvisionStore s = ProvisionStore.of(
                new ProvisionLot(BEEF, 10, 0),
                new ProvisionLot(APPLE, 10, 3 * DAY),   // 2 days left
                new ProvisionLot(BEEF, 2, 4 * DAY));     // 1 day left
        ProvisionUpdate u = run(s, crew(2), S, DAY / 2); // 6 nutrition: the old beef (8) covers it
        assertEquals(Map.of("beef", 1), u.outcome().consumed());
        assertEquals(1, u.store().lots().stream().filter(l -> l.type() == BEEF && l.ageTicks() == 4 * DAY + DAY / 2)
                .mapToInt(ProvisionLot::units).sum());
        // 12 more nutrition: credit 2 + the second old beef (8) before it spoils, then one apple (next to spoil).
        ProvisionUpdate v = ProvisionRules.advance(u.store(), u.state(), crew(2), S, DAY);
        assertEquals(Map.of("beef", 1, "apple", 1), v.outcome().consumed());
        assertEquals(10, v.store().units("beef"), "the fresh beef is untouched");
    }

    @Test
    void amongPreservedFoodSmallUnitsGoFirst() {
        ProvisionUpdate u = run(store(BREAD, 10, KELP, 3), crew(1), S, DAY);
        assertEquals(Map.of("kelp", 3, "bread", 1), u.outcome().consumed());
    }

    @Test
    void daysLeftForFoodWaterAndOverall() {
        ProvisionStore s = store(KELP, 60, WATER, 4, RUM, 1);
        SuppliesLeft left = ProvisionRules.suppliesLeft(s, ProvisioningState.INITIAL, CrewHeadcount.crew(2), S);
        assertEquals(5, left.foodDays(), 1e-6);   // 60 / (2 × 6)
        assertEquals(2, left.waterDays(), 1e-6);  // 4 / (2 × 1)
        assertEquals(2, left.rumDays(), 1e-6);    // 1 / (2 × 0.25)
        assertEquals(2, left.overallDays(), 1e-6);
    }

    @Test
    void daysLeftCountsCreditAndScalesWithRate() {
        ProvisionStore s = store(KELP, 60, WATER, 100);
        ProvisioningState credit = new ProvisioningState(12, 0, 0, 0, 0, 0, 0);
        assertEquals(6, ProvisionRules.suppliesLeft(s, credit, crew(2), S).foodDays(), 1e-6);
        assertEquals(2.5, ProvisionRules.suppliesLeft(s, ProvisioningState.INITIAL, crew(2),
                S.toBuilder().consumptionRate(2).build()).foodDays(), 1e-6);
    }

    @Test
    void daysLeftIgnoresFoodThatWillSpoilFirst() {
        // 30 beef, but only 1 day of shelf life left: one beef is eaten (8 nutrition = 1.33 days), the rest spoils.
        ProvisionStore s = ProvisionStore.of(new ProvisionLot(BEEF, 30, 4 * DAY), new ProvisionLot(WATER, 100, 0));
        assertEquals(8.0 / 6.0, ProvisionRules.suppliesLeft(s, ProvisioningState.INITIAL, crew(1), S).foodDays(), 1e-6);
        ProvisionSettings noSpoil = S.toBuilder().spoilageEnabled(false).build();
        assertEquals(40, ProvisionRules.suppliesLeft(s, ProvisioningState.INITIAL, crew(1), noSpoil).foodDays(), 1e-6);
    }

    @Test
    void daysLeftWhenAlreadyOutIsZero() {
        SuppliesLeft left = ProvisionRules.suppliesLeft(ProvisionStore.EMPTY, ProvisioningState.INITIAL, crew(3), S);
        assertEquals(0, left.foodDays());
        assertEquals(0, left.waterDays());
        assertEquals(Double.POSITIVE_INFINITY, left.rumDays(), "no rum issued");
    }

    @Test
    void daysLeftDoesNotChangeTheStore() {
        ProvisionStore s = store(KELP, 60, WATER, 4);
        ProvisionRules.suppliesLeft(s, ProvisioningState.INITIAL, crew(2), S);
        assertEquals(60, s.units("kelp"));
    }
}
