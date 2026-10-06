package com.richardsenger.piratesnships.law;

import com.richardsenger.piratesnships.law.bounty.BountyRules;
import com.richardsenger.piratesnships.law.bounty.PirateTier;
import com.richardsenger.piratesnships.law.crime.CrimeRules;
import com.richardsenger.piratesnships.law.crime.CrimeType;
import com.richardsenger.piratesnships.law.flag.FalseColorsDetection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The adapter from config handles to the plain rule records. */
class LawConfigTest {

    @AfterEach
    void reset() {
        LawConfig.CRIMINAL_SCORE_ENABLED.reset();
        LawConfig.SEVERITIES.get(CrimeType.THEFT).reset();
        LawConfig.COOLDOWNS.get(CrimeType.THEFT).reset();
        LawConfig.SUSPECT_THRESHOLD.reset();
        LawConfig.BOUNTY_THRESHOLD.reset();
        LawConfig.PLAYER_BOUNTY_DURATION_DAYS.reset();
        LawConfig.FALSE_FLAG_DETECTION_STRENGTH.reset();
    }

    @Test
    void defaultsMatchTheBuiltInRules() {
        assertEquals(CrimeRules.defaults(), LawConfig.crimeRules());
        assertEquals(BountyRules.defaults(), LawConfig.bountyRules());
        assertEquals(FalseColorsDetection.Params.defaults(), LawConfig.detectionParams());
    }

    @Test
    void everyCrimeAndTierHasConfigValues() {
        for (CrimeType t : CrimeType.values()) {
            assertEquals(t.defaultSeverity(), LawConfig.SEVERITIES.get(t).get());
            assertEquals(t.defaultCooldownSeconds(), LawConfig.COOLDOWNS.get(t).get());
        }
        for (PirateTier t : PirateTier.values()) assertEquals(t.defaultReward(), LawConfig.TURN_IN_REWARDS.get(t).get());
    }

    @Test
    void overridesReachTheRules() {
        LawConfig.CRIMINAL_SCORE_ENABLED.set(false);
        LawConfig.SEVERITIES.get(CrimeType.THEFT).set(7);
        LawConfig.COOLDOWNS.get(CrimeType.THEFT).set(2);
        LawConfig.PLAYER_BOUNTY_DURATION_DAYS.set(3);
        LawConfig.FALSE_FLAG_DETECTION_STRENGTH.set(2.5);
        CrimeRules c = LawConfig.crimeRules();
        assertFalse(c.enabled());
        assertEquals(7, c.severityOf(CrimeType.THEFT));
        assertEquals(40, c.cooldownOf(CrimeType.THEFT));
        BountyRules b = LawConfig.bountyRules();
        assertFalse(b.navyBounties(), "no navy bounties without criminal score");
        assertEquals(3 * CrimeRules.TICKS_PER_DAY, b.playerDurationTicks());
        assertEquals(2.5, LawConfig.detectionParams().strength());
    }

    @Test
    void misorderedThresholdsAreRepaired() {
        LawConfig.SUSPECT_THRESHOLD.set(500);
        LawConfig.BOUNTY_THRESHOLD.set(300);
        CrimeRules c = LawConfig.crimeRules();
        assertEquals(300, c.suspectThreshold());
        assertEquals(300, c.notoriousThreshold());
        assertEquals(300, LawConfig.bountyRules().navyThreshold());
    }
}
