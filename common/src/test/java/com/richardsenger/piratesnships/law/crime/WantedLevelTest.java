package com.richardsenger.piratesnships.law.crime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WantedLevelTest {

    final CrimeRules rules = CrimeRules.defaults(); // 10 / 50 / 200

    @Test
    void levelsAtAndAroundThresholds() {
        assertEquals(WantedLevel.CLEAN, rules.wantedLevel(0));
        assertEquals(WantedLevel.CLEAN, rules.wantedLevel(9.99));
        assertEquals(WantedLevel.SUSPECT, rules.wantedLevel(10));
        assertEquals(WantedLevel.SUSPECT, rules.wantedLevel(49.99));
        assertEquals(WantedLevel.WANTED, rules.wantedLevel(50));
        assertEquals(WantedLevel.WANTED, rules.wantedLevel(199));
        assertEquals(WantedLevel.NOTORIOUS, rules.wantedLevel(200));
        assertEquals(WantedLevel.NOTORIOUS, rules.wantedLevel(1e6));
    }

    @Test
    void zeroSuspectThresholdStillLeavesZeroClean() {
        assertEquals(WantedLevel.CLEAN, WantedLevel.of(0, 0, 50, 200));
        assertEquals(WantedLevel.SUSPECT, WantedLevel.of(0.1, 0, 50, 200));
    }

    @Test
    void disabledMeansNobodyIsWanted() {
        assertEquals(WantedLevel.CLEAN, rules.withEnabled(false).wantedLevel(500));
    }

    @Test
    void atLeast() {
        assertTrue(WantedLevel.NOTORIOUS.atLeast(WantedLevel.WANTED));
        assertTrue(WantedLevel.WANTED.atLeast(WantedLevel.WANTED));
        assertFalse(WantedLevel.SUSPECT.atLeast(WantedLevel.WANTED));
    }

    @Test
    void misorderedThresholdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CrimeRules(true, java.util.Map.of(), java.util.Map.of(),
                1, 0, 100, 60, 50, 200, 1, false));
    }
}
