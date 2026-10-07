package com.richardsenger.piratesnships.survival.swim;

import com.richardsenger.piratesnships.survival.swim.SwimHungerRules.Medium;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Swimming hunger: vanilla's formula (ServerPlayer.checkMovementStatistics) and our extra share. */
class SwimHungerRulesTest {

    @Test
    void mediumFollowsVanillasOrder() {
        assertEquals(Medium.SWIMMING, SwimHungerRules.medium(true, true, true));
        assertEquals(Medium.UNDER_WATER, SwimHungerRules.medium(false, true, true));
        assertEquals(Medium.ON_WATER, SwimHungerRules.medium(false, false, true));
        assertEquals(Medium.NONE, SwimHungerRules.medium(false, false, false));
    }

    @Test
    void vanillaChargesAHundredthPerBlock() {
        // 0.01F * cm * 0.01F: one block (100 cm) costs 0.01 exhaustion
        assertEquals(0.01F, SwimHungerRules.vanillaExhaustion(Medium.SWIMMING, 1.0, 0, 0), 1e-7);
        assertEquals(0.0001F, SwimHungerRules.VANILLA_EXHAUSTION_PER_CM, 1e-9);
        assertEquals(0.0005F, SwimHungerRules.vanillaExhaustion(Medium.ON_WATER, 0.05, 0, 0), 1e-8);
    }

    @Test
    void swimmingAndDivingCountHeightWadingDoesNot() {
        assertEquals(50, SwimHungerRules.centimetres(Medium.SWIMMING, 0.3, 0.4, 0));
        assertEquals(50, SwimHungerRules.centimetres(Medium.UNDER_WATER, 0, 0.3, 0.4));
        assertEquals(30, SwimHungerRules.centimetres(Medium.ON_WATER, 0.3, 0.4, 0));
        assertEquals(0, SwimHungerRules.centimetres(Medium.ON_WATER, 0, 0.4, 0));
        assertEquals(0, SwimHungerRules.centimetres(Medium.NONE, 1, 1, 1));
    }

    @Test
    void roundsToWholeCentimetresLikeVanilla() {
        assertEquals(0, SwimHungerRules.centimetres(Medium.SWIMMING, 0.004, 0, 0));
        assertEquals(1, SwimHungerRules.centimetres(Medium.SWIMMING, 0.006, 0, 0));
        assertEquals(Math.round((float) Math.sqrt(0.1 * 0.1 + 0.2 * 0.2) * 100.0F),
                SwimHungerRules.centimetres(Medium.SWIMMING, 0.1, 0.2, 0));
    }

    @Test
    void teleportsCostNothing() {
        assertEquals(0, SwimHungerRules.centimetres(Medium.SWIMMING, 100, 0, 0));
        assertEquals(800, SwimHungerRules.centimetres(Medium.SWIMMING, SwimHungerRules.MAX_STEP, 0, 0));
    }

    @Test
    void defaultMultiplierAddsHalfOfVanilla() {
        float vanilla = SwimHungerRules.vanillaExhaustion(Medium.SWIMMING, 0.2, 0, 0);
        float extra = SwimHungerRules.extraExhaustion(true, SwimHungerRules.DEFAULT_MULTIPLIER, false, Medium.SWIMMING, 0.2, 0, 0);
        assertEquals(1.5, SwimHungerRules.DEFAULT_MULTIPLIER);
        assertEquals(vanilla * 0.5F, extra, 1e-9);
        // a real player pays vanilla's share plus ours: 1.5 times vanilla
        assertEquals(vanilla * 1.5F, vanilla + extra, 1e-9);
    }

    @Test
    void multiplierScalesLinearly() {
        float vanilla = SwimHungerRules.vanillaExhaustion(Medium.ON_WATER, 0.15, 0, 0);
        assertEquals(vanilla * 2.0F, SwimHungerRules.extraExhaustion(true, 3.0, false, Medium.ON_WATER, 0.15, 0, 0), 1e-9);
    }

    @Test
    void offCases() {
        assertEquals(0F, SwimHungerRules.extraExhaustion(true, 1.0, false, Medium.SWIMMING, 0.2, 0, 0));
        assertEquals(0F, SwimHungerRules.extraExhaustion(false, 1.5, false, Medium.SWIMMING, 0.2, 0, 0));
        assertEquals(0F, SwimHungerRules.extraExhaustion(true, 1.5, true, Medium.SWIMMING, 0.2, 0, 0));
        assertEquals(0F, SwimHungerRules.extraExhaustion(true, 1.5, false, Medium.NONE, 0.2, 0, 0));
        assertEquals(0F, SwimHungerRules.extraExhaustion(true, 1.5, false, Medium.SWIMMING, 0, 0, 0));
    }
}
