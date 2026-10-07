package com.richardsenger.piratesnships.mob.kraken;

import com.richardsenger.piratesnships.mob.kraken.KrakenRules.HitZone;
import com.richardsenger.piratesnships.mob.kraken.KrakenRules.Swimmer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KrakenRulesTest {

    @Test
    void weakSpotMultipliers() {
        assertEquals(4.0, KrakenRules.bodyDamage(HitZone.BODY, 4, 3, 0.5), 1e-9);
        assertEquals(12.0, KrakenRules.bodyDamage(HitZone.EYE, 4, 3, 0.5), 1e-9);
        assertEquals(2.0, KrakenRules.bodyDamage(HitZone.TENTACLE, 4, 3, 0.5), 1e-9);
        assertEquals(0.0, KrakenRules.bodyDamage(HitZone.TENTACLE, 4, 3, 0.0), 1e-9);
    }

    @Test
    void gripForceIsMassCapped() {
        assertEquals(3.0 * 50, KrakenRules.gripNewtons(3, 50, 400), 1e-9);
        assertEquals(3.0 * 400, KrakenRules.gripNewtons(3, 5000, 400), 1e-9);
        assertEquals(0.0, KrakenRules.gripNewtons(3, 0, 400), 1e-9);
        assertEquals(0.0, KrakenRules.gripNewtons(0, 50, 400), 1e-9);
    }

    @Test
    void gripVectorPullsDownAndTowardTheKraken() {
        double[] v = KrakenRules.gripVector(100, 0.5, 3, 4);
        assertEquals(-100, v[1], 1e-9);
        assertEquals(30, v[0], 1e-9);
        assertEquals(40, v[2], 1e-9);
        double[] straight = KrakenRules.gripVector(100, 0.5, 0, 0);
        assertEquals(0, straight[0], 1e-9);
        assertEquals(0, straight[2], 1e-9);
    }

    @Test
    void grabsOnlyFreeSwimmers() {
        assertTrue(KrakenRules.grabs(new Swimmer(false, false, false, true, false, false)));
        assertFalse(KrakenRules.grabs(new Swimmer(false, false, false, false, false, false)), "not in the water");
        assertFalse(KrakenRules.grabs(new Swimmer(false, false, false, true, true, false)), "on a ship");
        assertFalse(KrakenRules.grabs(new Swimmer(false, false, false, true, false, true)), "in a boat");
        assertFalse(KrakenRules.grabs(new Swimmer(false, false, true, true, false, false)), "creative");
        assertFalse(KrakenRules.grabs(new Swimmer(false, true, false, true, false, false)), "a shark or fish");
        assertFalse(KrakenRules.grabs(new Swimmer(true, false, false, true, false, false)), "a kraken");
    }

    @Test
    void dayChanceModifiersStackAndCap() {
        assertEquals(0.05, KrakenRules.dayChance(0.05, false, false, 3, 3), 1e-12);
        assertEquals(0.15, KrakenRules.dayChance(0.05, true, false, 3, 3), 1e-12);
        assertEquals(0.15, KrakenRules.dayChance(0.05, false, true, 3, 3), 1e-12);
        assertEquals(0.45, KrakenRules.dayChance(0.05, true, true, 3, 3), 1e-12);
        assertEquals(1.0, KrakenRules.dayChance(0.5, true, true, 3, 3), 1e-12);
        assertEquals(0.0, KrakenRules.dayChance(-1, true, true, 3, 3), 1e-12);
    }

    @Test
    void perCheckConvertsADayChanceToTheCheckInterval() {
        double p = KrakenRules.perCheck(0.02, 200);
        // 120 checks a day add back up to the day chance
        assertEquals(0.02, 1 - Math.pow(1 - p, 120), 1e-12);
        assertEquals(0.02 / 120, p, 1e-5); // about the day chance divided by the checks
        assertEquals(0.0, KrakenRules.perCheck(0, 200), 1e-12);
        assertEquals(1.0, KrakenRules.perCheck(1, 200), 1e-12);
        assertEquals(0.0, KrakenRules.perCheck(0.5, 0), 1e-12);
        // a check a day is the day chance itself
        assertEquals(0.3, KrakenRules.perCheck(0.3, 24000), 1e-12);
    }

    @Test
    void spawnRollNeedsDeepOceanSeparationAndLuck() {
        assertTrue(KrakenRules.rollSpawn(true, true, Double.POSITIVE_INFINITY, 200, 0.5, 0.49));
        assertFalse(KrakenRules.rollSpawn(true, true, Double.POSITIVE_INFINITY, 200, 0.5, 0.5));
        assertFalse(KrakenRules.rollSpawn(false, true, Double.POSITIVE_INFINITY, 200, 1, 0));
        assertFalse(KrakenRules.rollSpawn(true, false, Double.POSITIVE_INFINITY, 200, 1, 0));
        assertFalse(KrakenRules.rollSpawn(true, true, 199.9, 200, 1, 0), "another kraken within 200 blocks");
        assertTrue(KrakenRules.rollSpawn(true, true, 200, 200, 1, 0));
    }
}
