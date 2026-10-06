package com.richardsenger.piratesnships.law.world;

import com.richardsenger.piratesnships.law.crime.WantedLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavyHostilityTest {

    @Test
    void attacksAtOrAboveThreshold() {
        assertFalse(NavyHostility.shouldAttack(true, WantedLevel.WANTED, WantedLevel.CLEAN, false, false, false));
        assertFalse(NavyHostility.shouldAttack(true, WantedLevel.WANTED, WantedLevel.SUSPECT, false, false, false));
        assertTrue(NavyHostility.shouldAttack(true, WantedLevel.WANTED, WantedLevel.WANTED, false, false, false));
        assertTrue(NavyHostility.shouldAttack(true, WantedLevel.WANTED, WantedLevel.NOTORIOUS, false, false, false));
        assertTrue(NavyHostility.shouldAttack(true, WantedLevel.SUSPECT, WantedLevel.SUSPECT, false, false, false));
    }

    @Test
    void neverWhenDisabledNavySelfOrExempt() {
        assertFalse(NavyHostility.shouldAttack(false, WantedLevel.WANTED, WantedLevel.NOTORIOUS, false, false, false));
        assertFalse(NavyHostility.shouldAttack(true, WantedLevel.WANTED, WantedLevel.NOTORIOUS, true, false, false));
        assertFalse(NavyHostility.shouldAttack(true, WantedLevel.WANTED, WantedLevel.NOTORIOUS, false, true, false));
        assertFalse(NavyHostility.shouldAttack(true, WantedLevel.WANTED, WantedLevel.NOTORIOUS, false, false, true));
    }
}
