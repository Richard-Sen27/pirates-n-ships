package com.richardsenger.piratesnships.combat.cannon;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CAN3: the loading, preference, start speed, cone, spread and rope distance rules of the three cannon shots. */
class ShotRulesTest {

    private static final double EPS = 1.0e-9;

    @Test
    void theBallIsAlwaysAllowedTheOthersByTheirToggles() {
        assertTrue(ShotRules.allowed(ShotKind.BALL, false, false));
        assertTrue(ShotRules.allowed(ShotKind.CHAIN, true, false));
        assertFalse(ShotRules.allowed(ShotKind.CHAIN, false, true));
        assertTrue(ShotRules.allowed(ShotKind.GRAPE, false, true));
        assertFalse(ShotRules.allowed(ShotKind.GRAPE, true, false));
    }

    @Test
    void theCrewReachesForItsPreferenceThenBallChainGrape() {
        assertEquals(List.of(ShotKind.BALL, ShotKind.CHAIN, ShotKind.GRAPE), ShotRules.crewPreference(ShotKind.BALL, k -> true));
        assertEquals(List.of(ShotKind.CHAIN, ShotKind.BALL, ShotKind.GRAPE), ShotRules.crewPreference(ShotKind.CHAIN, k -> true));
        assertEquals(List.of(ShotKind.GRAPE, ShotKind.BALL, ShotKind.CHAIN), ShotRules.crewPreference(ShotKind.GRAPE, k -> true));
        // a disabled shot is never reached for, even when preferred
        assertEquals(List.of(ShotKind.BALL, ShotKind.GRAPE), ShotRules.crewPreference(ShotKind.CHAIN, k -> k != ShotKind.CHAIN));
    }

    @Test
    void aGunCrewFiringByItselfNeverFallsBackToGrapeshot() {
        assertEquals(List.of(ShotKind.BALL, ShotKind.CHAIN), ShotRules.gunneryPreference(ShotKind.BALL, k -> true));
        assertEquals(List.of(ShotKind.CHAIN, ShotKind.BALL), ShotRules.gunneryPreference(ShotKind.CHAIN, k -> true));
        assertEquals(List.of(ShotKind.GRAPE, ShotKind.BALL, ShotKind.CHAIN), ShotRules.gunneryPreference(ShotKind.GRAPE, k -> true));
    }

    @Test
    void pickTakesTheFirstShotTheLockerHolds() {
        List<ShotKind> pref = List.of(ShotKind.GRAPE, ShotKind.BALL, ShotKind.CHAIN);
        assertEquals(ShotKind.GRAPE, ShotRules.pick(pref, Set.of(ShotKind.BALL, ShotKind.GRAPE)::contains));
        assertEquals(ShotKind.BALL, ShotRules.pick(pref, Set.of(ShotKind.BALL, ShotKind.CHAIN)::contains));
        assertEquals(ShotKind.CHAIN, ShotRules.pick(pref, Set.of(ShotKind.CHAIN)::contains));
        assertNull(ShotRules.pick(pref, Set.<ShotKind>of()::contains));
    }

    @Test
    void chainShotAndGrapeshotStartSlowerByTheirRangeFactors() {
        assertEquals(1.0, ShotRules.velocityFactor(ShotKind.BALL, 0.6, 0.4), EPS);
        assertEquals(0.6, ShotRules.velocityFactor(ShotKind.CHAIN, 0.6, 0.4), EPS);
        assertEquals(0.4, ShotRules.velocityFactor(ShotKind.GRAPE, 0.6, 0.4), EPS);
    }

    @Test
    void theConeHasOneUnitDirectionPerPelletWithinTheHalfAngle() {
        Vec3 axis = new Vec3(3, 0.5, -1);
        for (double phase : new double[]{0.0, 1.3, 5.9}) {
            List<Vec3> dirs = ShotRules.cone(axis, 9, 6.0, phase);
            assertEquals(9, dirs.size());
            for (Vec3 d : dirs) {
                assertEquals(1.0, d.length(), 1.0e-9);
                assertTrue(ShotRules.angleDegrees(d, axis) <= 6.0 + 1.0e-6, "off the axis by " + ShotRules.angleDegrees(d, axis));
            }
            // the volley spreads: its outermost pellet lies near the cone's edge, and no two pellets fly together
            double widest = dirs.stream().mapToDouble(d -> ShotRules.angleDegrees(d, axis)).max().orElseThrow();
            assertTrue(widest > 5.0, "the cone is too narrow: " + widest);
            for (int i = 0; i < dirs.size(); i++) {
                for (int j = i + 1; j < dirs.size(); j++) {
                    assertTrue(ShotRules.angleDegrees(dirs.get(i), dirs.get(j)) > 0.5, "pellets " + i + " and " + j + " fly together");
                }
            }
        }
    }

    @Test
    void theConeCentresOnTheAxis() {
        Vec3 axis = new Vec3(0, 0, 1);
        List<Vec3> dirs = ShotRules.cone(axis, 32, 8.0, 0.4);
        Vec3 mean = dirs.stream().reduce(Vec3.ZERO, Vec3::add).scale(1.0 / dirs.size());
        assertTrue(ShotRules.angleDegrees(mean, axis) < 1.0, "the volley's mean is off the axis by " + ShotRules.angleDegrees(mean, axis));
        assertTrue(ShotRules.cone(axis, 0, 8.0, 0.0).isEmpty());
        assertEquals(1, ShotRules.cone(axis, 1, 0.0, 0.0).size());
        assertEquals(0.0, ShotRules.angleDegrees(ShotRules.cone(axis, 1, 0.0, 0.0).get(0), axis), 1.0e-9);
    }

    @Test
    void theChainShotSpreadStaysWithinItsAngle() {
        Vec3 axis = new Vec3(1, 0.2, 0);
        assertEquals(0.0, ShotRules.angleDegrees(ShotRules.deviate(axis, 2.0, 0.0, 0.3), axis), 1.0e-6);
        assertEquals(2.0, ShotRules.angleDegrees(ShotRules.deviate(axis, 2.0, 1.0, 0.7), axis), 1.0e-6);
        for (double u = 0; u < 1; u += 0.1) {
            Vec3 d = ShotRules.deviate(axis, 2.0, u, 1 - u);
            assertEquals(1.0, d.length(), 1.0e-9);
            assertTrue(ShotRules.angleDegrees(d, axis) <= 2.0 + 1.0e-6);
        }
        // straight up works too (the helper axis changes)
        assertEquals(3.0, ShotRules.angleDegrees(ShotRules.deviate(new Vec3(0, 1, 0), 3.0, 1.0, 0.2), new Vec3(0, 1, 0)), 1.0e-6);
    }

    @Test
    void segmentDistanceMeasuresTheClosestApproach() {
        // crossing at right angles one block apart
        assertEquals(1.0, ShotRules.segmentDistance(new Vec3(0, 0, -5), new Vec3(0, 0, 5), new Vec3(-5, 1, 0), new Vec3(5, 1, 0)), EPS);
        // parallel, two apart
        assertEquals(2.0, ShotRules.segmentDistance(new Vec3(0, 0, 0), new Vec3(4, 0, 0), new Vec3(1, 2, 0), new Vec3(3, 2, 0)), EPS);
        // the closest points are ends: the step stops short of the rope's line
        assertEquals(Math.sqrt(2.0), ShotRules.segmentDistance(new Vec3(0, 0, -5), new Vec3(0, 0, -1), new Vec3(-5, 1, 0),
                new Vec3(5, 1, 0)), EPS);
        // degenerate segments are points
        assertEquals(5.0, ShotRules.segmentDistance(new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(3, 4, 0), new Vec3(3, 4, 0)), EPS);
        assertEquals(1.0, ShotRules.segmentDistance(new Vec3(2, 1, 0), new Vec3(2, 1, 0), new Vec3(0, 0, 0), new Vec3(4, 0, 0)), EPS);
        assertEquals(1.0, ShotRules.segmentDistance(new Vec3(0, 0, 0), new Vec3(4, 0, 0), new Vec3(2, 1, 0), new Vec3(2, 1, 0)), EPS);
    }
}
