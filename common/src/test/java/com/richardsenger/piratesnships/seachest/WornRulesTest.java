package com.richardsenger.piratesnships.seachest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WornRulesTest {

    private static final WornRules.Params DEFAULTS = new WornRules.Params(true, 0.6, 0.035);

    @Test
    void notWornMeansNoEffects() {
        assertSame(WornRules.Effects.NONE, WornRules.effects(false, true, false, true, true, DEFAULTS));
    }

    @Test
    void disabledMeansInert() {
        WornRules.Params off = new WornRules.Params(false, 0.6, 0.02);
        WornRules.Effects e = WornRules.effects(true, true, false, true, true, off);
        assertFalse(e.active());
        assertFalse(e.clearSprint());
        assertFalse(e.clearSwim());
        assertEquals(0.0, e.extraVy());
    }

    @Test
    void wornTakesAllJumpAndSlowsWalking() {
        WornRules.Effects e = WornRules.effects(true, false, false, false, false, DEFAULTS);
        assertTrue(e.active());
        assertEquals(-1.0, e.jumpModifier(), 1e-12, "jump strength × (1 − 1) = 0");
        assertEquals(-0.4, e.speedModifier(), 1e-12, "speed × (1 − 0.4) = 0.6");
        assertEquals(0.0, e.extraVy(), "no pull on land");
    }

    @Test
    void speedMultiplierIsClampedToAtMostNormalSpeed() {
        assertEquals(0.0, WornRules.effects(true, false, false, false, false, new WornRules.Params(true, 1.5, 0.02)).speedModifier(), 1e-12);
        assertEquals(-1.0, WornRules.effects(true, false, false, false, false, new WornRules.Params(true, -0.5, 0.02)).speedModifier(), 1e-12);
    }

    @Test
    void sprintAndSwimFlagsAreCleared() {
        WornRules.Effects e = WornRules.effects(true, true, false, true, true, DEFAULTS);
        assertTrue(e.clearSprint());
        assertTrue(e.clearSwim());
        WornRules.Effects idle = WornRules.effects(true, true, false, false, false, DEFAULTS);
        assertFalse(idle.clearSprint(), "nothing to clear");
        assertFalse(idle.clearSwim());
    }

    @Test
    void dragsTheWearerDownInWaterButNotWhileFlying() {
        assertEquals(-0.035, WornRules.effects(true, true, false, false, false, DEFAULTS).extraVy(), 1e-12);
        assertEquals(0.0, WornRules.effects(true, true, true, false, false, DEFAULTS).extraVy(), "flying in creative");
        assertEquals(0.0, WornRules.effects(true, true, false, false, false, new WornRules.Params(true, 0.6, -1.0)).extraVy(),
                "a negative pull is no pull");
    }

    @Test
    void defaultPullSinksTheWearerAndHoldingJumpNoLongerLiftsIt() {
        // Vanilla water physics per tick (measured in the GameTests): the displacement m becomes 0.8 m - 0.005
        // (drag 0.8, fluid gravity 0.005), holding jump adds 0.04, the chest subtracts the pull. Steady state:
        // m = (jump - 0.005 - pull) / 0.2.
        double pull = -WornRules.effects(true, true, false, false, false, DEFAULTS).extraVy();
        double idle = (-0.005 - pull) / 0.2;
        double swimming = (0.04 - 0.005 - pull) / 0.2;
        double vanillaIdle = -0.005 / 0.2;
        assertEquals(-0.2, idle, 1e-9, "sinks at 0.2 blocks per tick");
        assertTrue(idle < 4 * vanillaIdle, "over four times as fast as without the chest");
        assertTrue(swimming <= 1e-9, "holding jump must not lift a wearer: " + swimming);
    }
}
