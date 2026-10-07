package com.richardsenger.piratesnships.combat.firearms;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirearmRulesTest {

    // ---- misfire ----

    @Test
    void neverMisfiresOutOfTheRain() {
        assertFalse(FirearmRules.misfires(false, 1.0, 0.0));
        assertFalse(FirearmRules.misfires(false, 1.0, 0.99));
    }

    @Test
    void misfiresInTheRainWhenTheRollIsBelowTheChance() {
        assertTrue(FirearmRules.misfires(true, 0.25, 0.1));
        assertTrue(FirearmRules.misfires(true, 0.25, 0.2499));
        assertFalse(FirearmRules.misfires(true, 0.25, 0.25));
        assertFalse(FirearmRules.misfires(true, 0.25, 0.9));
    }

    @Test
    void chanceOneAlwaysAndChanceZeroNeverMisfires() {
        for (double roll = 0; roll < 1.0; roll += 0.01) {
            assertTrue(FirearmRules.misfires(true, 1.0, roll));
            assertFalse(FirearmRules.misfires(true, 0.0, roll));
        }
    }

    @Test
    void misfireRateMatchesTheChance() {
        Random random = new Random(42);
        int misfires = 0;
        int n = 100_000;
        for (int i = 0; i < n; i++) {
            if (FirearmRules.misfires(true, 0.25, random.nextDouble())) misfires++;
        }
        assertEquals(0.25, misfires / (double) n, 0.01);
    }

    // ---- spread ----

    @Test
    void zeroSpreadKeepsTheAim() {
        assertArrayEquals(new float[]{10f, 45f}, FirearmRules.spreadRotation(10f, 45f, 0.0, 0.7, 0.3));
    }

    @Test
    void centreRollKeepsTheAim() {
        float[] r = FirearmRules.spreadRotation(-20f, 130f, 4.0, 0.0, 0.5);
        assertEquals(-20f, r[0], 1e-5);
        assertEquals(130f, r[1], 1e-5);
    }

    @Test
    void fullRollDeviatesByTheWholeSpread() {
        // v = 0.25: a pure pitch offset (upward), no yaw
        float[] r = FirearmRules.spreadRotation(0f, 0f, 4.0, 1.0, 0.25);
        assertEquals(-4.0, r[0], 1e-4);
        assertEquals(0.0, r[1], 1e-4);
        assertEquals(4.0, FirearmRules.angleBetween(0f, 0f, r[0], r[1]), 1e-3);
    }

    @ParameterizedTest
    @ValueSource(floats = {-90f, -80f, -45f, 0f, 30f, 60f, 85f, 89.9f})
    void deviationStaysInsideTheConeAtAnyPitch(float pitch) {
        Random random = new Random(7);
        double spread = 4.0;
        double maxSeen = 0;
        for (int i = 0; i < 5_000; i++) {
            float[] r = FirearmRules.spreadRotation(pitch, 70f, spread, random.nextDouble(), random.nextDouble());
            double angle = FirearmRules.angleBetween(pitch, 70f, r[0], r[1]);
            assertTrue(angle <= spread + 0.05, "angle " + angle + " at pitch " + pitch);
            maxSeen = Math.max(maxSeen, angle);
        }
        // shots use most of the cone, not just its centre
        assertTrue(maxSeen > spread * 0.8, "max deviation " + maxSeen);
    }

    @Test
    void spreadIsUniformOverTheDisc() {
        // with sqrt(u) radius, half of all shots land within 1/sqrt(2) of the spread
        Random random = new Random(3);
        double spread = 4.0;
        int inner = 0;
        int n = 50_000;
        for (int i = 0; i < n; i++) {
            float[] r = FirearmRules.spreadRotation(0f, 0f, spread, random.nextDouble(), random.nextDouble());
            if (FirearmRules.angleBetween(0f, 0f, r[0], r[1]) < spread / Math.sqrt(2)) inner++;
        }
        assertEquals(0.5, inner / (double) n, 0.01);
    }

    @Test
    void yawStaysCloseToTheAim() {
        float[] r = FirearmRules.spreadRotation(0f, 719f, 4.0, 1.0, 0.0);
        assertTrue(Math.abs(r[1] - 719f) <= 4.01f, "yaw " + r[1]);
    }

    @Test
    void directionMatchesVanillaConvention() {
        double[] south = FirearmRules.direction(0f, 0f);
        assertEquals(0.0, south[0], 1e-9);
        assertEquals(1.0, south[2], 1e-9);
        double[] west = FirearmRules.direction(0f, 90f);
        assertEquals(-1.0, west[0], 1e-9);
        double[] down = FirearmRules.direction(90f, 0f);
        assertEquals(-1.0, down[1], 1e-9);
    }

    // ---- reload ----

    @Test
    void reloadProgressRunsFromZeroToOne() {
        assertEquals(0f, FirearmRules.reloadProgress(0, 60));
        assertEquals(0.5f, FirearmRules.reloadProgress(30, 60), 1e-6);
        assertEquals(1f, FirearmRules.reloadProgress(60, 60));
        assertEquals(1f, FirearmRules.reloadProgress(90, 60));
        assertEquals(0f, FirearmRules.reloadProgress(-5, 60));
        assertEquals(1f, FirearmRules.reloadProgress(0, 0));
    }

    @Test
    void reloadCompletesAtTheReloadTime() {
        assertFalse(FirearmRules.reloadComplete(59, 60));
        assertTrue(FirearmRules.reloadComplete(60, 60));
        assertTrue(FirearmRules.reloadComplete(61, 60));
    }

    // ---- ammunition ----

    @Test
    void loadingNeedsLeadShotAndGunpowder() {
        assertTrue(FirearmRules.canLoad(false, 1, 1, true));
        assertFalse(FirearmRules.canLoad(false, 0, 1, true));
        assertFalse(FirearmRules.canLoad(false, 1, 0, true));
        assertTrue(FirearmRules.canLoad(false, 1, 0, false));
        assertFalse(FirearmRules.canLoad(false, 0, 5, false));
    }

    @Test
    void infiniteMaterialsLoadWithoutAmmunition() {
        assertTrue(FirearmRules.canLoad(true, 0, 0, true));
    }

    // ---- type ----

    @Test
    void typeRejectsImpossibleNumbers() {
        assertThrows(IllegalArgumentException.class, () -> new FirearmType(-1, 2.5f, 4, 60, 0.25, 1));
        assertThrows(IllegalArgumentException.class, () -> new FirearmType(10, 0, 4, 60, 0.25, 1));
        assertThrows(IllegalArgumentException.class, () -> new FirearmType(10, 2.5f, -1, 60, 0.25, 1));
        assertThrows(IllegalArgumentException.class, () -> new FirearmType(10, 2.5f, 4, 0, 0.25, 1));
        assertThrows(IllegalArgumentException.class, () -> new FirearmType(10, 2.5f, 4, 60, 1.5, 1));
        assertThrows(IllegalArgumentException.class, () -> new FirearmType(10, 2.5f, 4, 60, 0.25, 0));
    }

    @Test
    void typeModifiersKeepTheOtherNumbers() {
        FirearmType pistol = new FirearmType(10, 2.5f, 4, 60, 0.25, 1);
        FirearmType doubled = pistol.withDamageMultiplier(2.0);
        assertEquals(20f, doubled.damage(), 1e-6);
        assertEquals(pistol.reloadTicks(), doubled.reloadTicks());
        FirearmType dry = pistol.withMisfireChance(0.0);
        assertEquals(0.0, dry.misfireChanceInRain());
        assertEquals(pistol.damage(), dry.damage());
    }

    // ---- loading and aiming sessions ----

    @Test
    void aSessionKnowsWhetherItStartedLoaded() {
        int load = FirearmRules.sessionTicks(false);
        int aim = FirearmRules.sessionTicks(true);
        assertTrue(aim > load);
        assertFalse(FirearmRules.isAimSession(load), "a fresh loading session");
        assertFalse(FirearmRules.isAimSession(load - 100), "a loading session after 100 ticks, gun loaded by now");
        assertTrue(FirearmRules.isAimSession(aim), "a fresh aim");
        assertTrue(FirearmRules.isAimSession(aim - 1200), "an aim held for a minute");
        assertEquals(0, FirearmRules.heldTicks(load));
        assertEquals(60, FirearmRules.heldTicks(load - 60));
        assertEquals(0, FirearmRules.heldTicks(aim));
        assertEquals(25, FirearmRules.heldTicks(aim - 25));
    }

    @Test
    void releaseFiresAfterTheMinimumHold() {
        assertTrue(FirearmRules.firesOnRelease(0, 0), "a click fires at once with no minimum");
        assertTrue(FirearmRules.firesOnRelease(1, 0));
        assertFalse(FirearmRules.firesOnRelease(1, 2));
        assertTrue(FirearmRules.firesOnRelease(2, 2));
    }

    @Test
    void aimingSteadilyNarrowsTheSpread() {
        assertEquals(4.0, FirearmRules.aimedSpread(4.0, 0, 20, 0.5));
        assertEquals(4.0, FirearmRules.aimedSpread(4.0, 19, 20, 0.5));
        assertEquals(2.0, FirearmRules.aimedSpread(4.0, 20, 20, 0.5));
        assertEquals(2.0, FirearmRules.aimedSpread(4.0, 600, 20, 0.5));
        assertEquals(0.0, FirearmRules.aimedSpread(4.0, 20, 20, 0.0));
    }

    @Test
    void musketZoomDividesTheFieldOfView() {
        assertEquals(0.8f, FirearmRules.zoomedFov(1.0f, 1.25), 1e-6f);
        assertEquals(1.1f, FirearmRules.zoomedFov(1.1f, 1.0), 1e-6f, "zoom 1 changes nothing");
        assertEquals(0.5f, FirearmRules.zoomedFov(1.0f, 2.0), 1e-6f);
    }

    // ---- lowering (P5) ----

    @Test
    void sneakingDuringAnAimLowersTheGun() {
        assertTrue(FirearmRules.lowers(true, true, true), "sneaking + aim session: no shot");
        assertFalse(FirearmRules.lowers(false, true, true), "a release without sneaking fires");
    }

    @Test
    void sneakingDuringALoadingSessionIsIrrelevant() {
        assertFalse(FirearmRules.lowers(true, false, true));
        assertFalse(FirearmRules.lowers(false, false, true));
    }

    @Test
    void lowerOnSneakOffAlwaysFires() {
        assertFalse(FirearmRules.lowers(true, true, false));
        assertFalse(FirearmRules.lowers(false, true, false));
    }

    @Test
    void aLoadedGunIsNotRaisedWhileSneaking() {
        assertFalse(FirearmRules.aimsOnUse(true, true));
        assertTrue(FirearmRules.aimsOnUse(false, true));
        assertTrue(FirearmRules.aimsOnUse(true, false), "with lower_on_sneak off sneaking changes nothing");
        assertTrue(FirearmRules.aimsOnUse(false, false));
    }
}
