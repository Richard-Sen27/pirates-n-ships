package com.richardsenger.piratesnships.mob;

import org.junit.jupiter.api.Test;

import static com.richardsenger.piratesnships.mob.SharkRules.Prey;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharkRulesTest {

    private static final SharkRules.Params HUNTING = new SharkRules.Params(false, 16);
    private static final SharkRules.Params PEACEFUL = new SharkRules.Params(true, 16);

    /** A swimmer of a prey kind, {@code d} blocks away. */
    private static Prey swimmer(double d) {
        return new Prey(false, false, true, true, false, d);
    }

    @Test
    void huntsPreyInTheWaterWithinRange() {
        assertTrue(SharkRules.huntsOnSight(swimmer(5), HUNTING));
        assertTrue(SharkRules.huntsOnSight(swimmer(16), HUNTING));
        assertFalse(SharkRules.huntsOnSight(swimmer(16.5), HUNTING), "out of detection range");
    }

    @Test
    void neverHuntsOutOfTheWaterOnADeckOrInABoat() {
        assertFalse(SharkRules.huntsOnSight(new Prey(false, false, true, false, false, 4), HUNTING), "on land / on planks");
        assertFalse(SharkRules.huntsOnSight(new Prey(false, false, true, true, true, 4), HUNTING), "in a boat or on a deck");
    }

    @Test
    void neverHuntsSharksExemptPlayersOrOtherKinds() {
        assertFalse(SharkRules.huntsOnSight(new Prey(true, false, false, true, false, 4), HUNTING), "another shark");
        assertFalse(SharkRules.huntsOnSight(new Prey(false, true, true, true, false, 4), HUNTING), "creative player");
        assertFalse(SharkRules.huntsOnSight(new Prey(false, false, false, true, false, 4), HUNTING), "a squid or a drowned");
    }

    @Test
    void peacefulNeverHuntsAndNeverFightsBack() {
        assertFalse(SharkRules.huntsOnSight(swimmer(3), PEACEFUL));
        assertFalse(SharkRules.retaliates(swimmer(3), PEACEFUL));
        assertFalse(SharkRules.keepsTarget(swimmer(3), PEACEFUL, true, 24));
    }

    @Test
    void retaliatesAgainstAnyoneButSharksAndExemptPlayers() {
        Prey drowned = new Prey(false, false, false, true, false, 3);
        assertTrue(SharkRules.retaliates(drowned, HUNTING));
        assertTrue(SharkRules.keepsTarget(drowned, HUNTING, true, 24), "grudge keeps a non-prey attacker");
        assertFalse(SharkRules.keepsTarget(drowned, HUNTING, false, 24), "no grudge, not prey");
        assertFalse(SharkRules.retaliates(new Prey(true, false, false, true, false, 3), HUNTING), "another shark");
        assertFalse(SharkRules.retaliates(new Prey(false, true, true, true, false, 3), HUNTING), "creative player");
    }

    @Test
    void dropsATargetThatLeavesTheWaterOrTheFollowRange() {
        assertTrue(SharkRules.keepsTarget(swimmer(20), HUNTING, false, 24), "beyond detection, within follow range");
        assertFalse(SharkRules.keepsTarget(swimmer(25), HUNTING, false, 24), "beyond follow range");
        assertFalse(SharkRules.keepsTarget(new Prey(false, false, true, false, false, 2), HUNTING, true, 24), "climbed out, even with a grudge");
        assertFalse(SharkRules.keepsTarget(new Prey(false, false, true, true, true, 2), HUNTING, true, 24), "climbed into a boat");
    }

    @Test
    void frenzyBelowTheHealthFraction() {
        assertTrue(SharkRules.frenzy(7, 20, 0.4));
        assertFalse(SharkRules.frenzy(8, 20, 0.4));
        assertFalse(SharkRules.frenzy(1, 20, 0.0), "fraction 0 = never");
        assertFalse(SharkRules.frenzy(0, 0, 0.4), "no max health");
    }

    @Test
    void spawnsOnlyInWaterBelowSeaLevelWhenEnabled() {
        assertTrue(SharkRules.canSpawnAt(true, true, true, 50, 63));
        assertTrue(SharkRules.canSpawnAt(true, true, true, 63 - SharkRules.SPAWN_DEPTH, 63));
        assertFalse(SharkRules.canSpawnAt(true, true, true, 62, 63), "at the surface");
        assertFalse(SharkRules.canSpawnAt(true, true, false, 50, 63), "no water above (a puddle under ice or a ceiling)");
        assertFalse(SharkRules.canSpawnAt(true, false, false, 50, 63), "land");
        assertFalse(SharkRules.canSpawnAt(false, true, true, 50, 63), "disabled");
    }
}
