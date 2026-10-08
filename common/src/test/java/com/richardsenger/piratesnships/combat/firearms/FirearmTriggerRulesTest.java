package com.richardsenger.piratesnships.combat.firearms;

import com.richardsenger.piratesnships.combat.firearms.FirearmTriggerRules.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirearmTriggerRulesTest {

    // ---- the attack key ----

    @Test
    void aLoadedGunFiresOnAttack() {
        assertEquals(Outcome.FIRE, FirearmTriggerRules.onAttack(true, true, false, false, true));
    }

    @Test
    void anEmptyGunClicks() {
        assertEquals(Outcome.EMPTY, FirearmTriggerRules.onAttack(true, true, false, false, false));
    }

    @Test
    void reloadingNeverFires() {
        assertEquals(Outcome.RELOADING, FirearmTriggerRules.onAttack(true, true, true, false, true));
        assertEquals(Outcome.RELOADING, FirearmTriggerRules.onAttack(true, true, true, false, false));
        assertEquals(Outcome.RELOADING, FirearmTriggerRules.onAttack(true, true, true, true, true));
    }

    @Test
    void theCooldownBlocksTheTrigger() {
        assertEquals(Outcome.COOLDOWN, FirearmTriggerRules.onAttack(true, true, false, true, true));
        assertEquals(Outcome.COOLDOWN, FirearmTriggerRules.onAttack(true, true, false, true, false));
    }

    @Test
    void theTogglesTurnTheTriggerOff() {
        assertEquals(Outcome.OFF, FirearmTriggerRules.onAttack(true, false, false, false, true));
        assertEquals(Outcome.OFF, FirearmTriggerRules.onAttack(false, true, false, false, true));
        assertEquals(Outcome.OFF, FirearmTriggerRules.onAttack(false, false, true, true, false));
    }

    @Test
    void theClientInterceptsOnlyWithAGunAndBothTogglesOn() {
        assertTrue(FirearmTriggerRules.interceptsAttack(true, true, true));
        assertFalse(FirearmTriggerRules.interceptsAttack(true, true, false));
        assertFalse(FirearmTriggerRules.interceptsAttack(true, false, true));
        assertFalse(FirearmTriggerRules.interceptsAttack(false, true, true));
    }

    // ---- spread: aimed or from the hip ----

    @Test
    void fromTheHipTheSpreadIsFullHoweverLong() {
        assertEquals(4.0, FirearmTriggerRules.shotSpread(4.0, false, 0, 20, 0.5));
        assertEquals(4.0, FirearmTriggerRules.shotSpread(4.0, false, 500, 20, 0.5));
        assertEquals(4.0, FirearmTriggerRules.shotSpread(4.0, false, 0, 0, 0.5), "a steady time of 0 doesn't steady a hip shot");
    }

    @Test
    void anAimIsSteadiedAfterTheSteadyTime() {
        assertEquals(4.0, FirearmTriggerRules.shotSpread(4.0, true, 19, 20, 0.5));
        assertEquals(2.0, FirearmTriggerRules.shotSpread(4.0, true, 20, 20, 0.5));
        assertEquals(0.0, FirearmTriggerRules.shotSpread(4.0, true, 0, 0, 0.0));
    }

    @Test
    void theHipIsNeverTighterThanAnAim() {
        for (int held = 0; held < 60; held += 5) {
            for (double factor = 0.0; factor <= 1.0; factor += 0.25) {
                assertTrue(FirearmTriggerRules.shotSpread(3.0, false, held, 20, factor)
                        >= FirearmTriggerRules.shotSpread(3.0, true, held, 20, factor));
            }
        }
    }

    // ---- letting go of use ----

    @Test
    void lettingGoNeverFiresWithFireOnAttack() {
        assertFalse(FirearmTriggerRules.releaseFires(true, 0, 0));
        assertFalse(FirearmTriggerRules.releaseFires(true, 100, 0));
    }

    @Test
    void withTheToggleOffLettingGoFiresAfterTheMinimumHold() {
        assertTrue(FirearmTriggerRules.releaseFires(false, 0, 0));
        assertFalse(FirearmTriggerRules.releaseFires(false, 4, 5));
        assertTrue(FirearmTriggerRules.releaseFires(false, 5, 5));
    }
}
