package com.richardsenger.piratesnships.worldsim.navy;

import com.richardsenger.piratesnships.worldsim.voyage.VoyageRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatrolPlannerTest {

    @Test
    void calmNavySendsHalfAndEnragedNavyOneAndAHalfTimesThePatrols() {
        double base = VoyageRules.chancePerCheck(2.0, 20);
        assertEquals(base * 0.5, PatrolPlanner.chance(2.0, 0.0, 20), 1e-12);
        assertEquals(base * 1.5, PatrolPlanner.chance(2.0, 1.0, 20), 1e-12);
        assertTrue(PatrolPlanner.chance(2.0, 1.0, 20) > PatrolPlanner.chance(2.0, 0.0, 20));
    }

    @Test
    void aggressionIsClampedAndZeroPatrolsMeansNone() {
        assertEquals(PatrolPlanner.chance(2.0, 1.0, 20), PatrolPlanner.chance(2.0, 5.0, 20), 1e-12);
        assertEquals(PatrolPlanner.chance(2.0, 0.0, 20), PatrolPlanner.chance(2.0, -1.0, 20), 1e-12);
        assertEquals(0.0, PatrolPlanner.chance(0.0, 1.0, 20));
    }

    @Test
    void chancePerCheckIsCappedAtOne() {
        assertEquals(1.0, PatrolPlanner.chance(100.0, 1.0, 24_000));
    }
}
