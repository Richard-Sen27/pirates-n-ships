package com.richardsenger.piratesnships.crew.provisions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RumIssueTest {

    private static final ProvisionSettings S = ProvisionSettings.DEFAULTS;
    private static final long DAY = ProvisionSettings.TICKS_PER_DAY;

    @Test
    void theRationUntilThereIsRumForTheBingeDays() {
        // 4 crew drink 1 rum a day at the standard ration (0.25 each)
        assertEquals(1.0, RumIssue.rations(9.9, 4, S, 10, 2));
        assertEquals(2.0, RumIssue.rations(10, 4, S, 10, 2));
        assertEquals(2.0, RumIssue.rations(64, 4, S, 10, 2));
    }

    @Test
    void zeroBingeDaysNoCrewOrNoRationNeverBinge() {
        assertEquals(1.0, RumIssue.rations(1000, 4, S, 0, 2));
        assertEquals(1.0, RumIssue.rations(1000, 0, S, 10, 2));
        assertEquals(1.0, RumIssue.rations(1000, 4, S.toBuilder().rumRationPerCrewPerDay(0).build(), 10, 2));
        assertEquals(1.0, RumIssue.rations(1000, 4, S, 10, 0.5), "never less than the ration");
    }

    @Test
    void aBingeMakesTheCrewDrunkAndSlow() {
        ProvisionType rum = ProvisionType.rum("rum", 1, 0.5);
        ProvisionType water = ProvisionType.water("water", 1, 1.0);
        ProvisionType hardtack = ProvisionType.food("hardtack", 4, true, false, 0.25, 0);
        ProvisionStore store = ProvisionStore.of(new ProvisionLot(rum, 64, 0), new ProvisionLot(water, 20, 0),
                new ProvisionLot(hardtack, 20, 0));
        double rations = RumIssue.rations(store.totalValue(ProvisionKind.RUM), 4, S, 10, 2);
        ProvisionUpdate u = ProvisionRules.advance(store, ProvisioningState.INITIAL, CrewHeadcount.crew(4).withRum(rations), S, DAY);
        assertTrue(u.outcome().drunk());
        assertEquals(S.drunkWorkSpeed(), u.outcome().effects().workSpeedMultiplier(), 1e-9);
        ProvisionUpdate sober = ProvisionRules.advance(store, ProvisioningState.INITIAL,
                CrewHeadcount.crew(4).withRum(RumIssue.rations(5, 4, S, 10, 2)), S, DAY);
        assertFalse(sober.outcome().drunk());
    }
}
