package com.richardsenger.piratesnships.combat.cannon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CannonRulesTest {

    private static final double EPS = 1e-9;

    private static void assertVec(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, EPS, "x of " + actual);
        assertEquals(expected.y, actual.y, EPS, "y of " + actual);
        assertEquals(expected.z, actual.z, EPS, "z of " + actual);
    }

    // ---- loading ----

    @Test
    void powderGoesInFirstThenTheBall() {
        assertEquals(CannonRules.LoadOutcome.POWDER_IN, CannonRules.load(CannonLoad.EMPTY, CannonRules.Charge.POWDER, false));
        assertEquals(CannonRules.LoadOutcome.BALL_IN, CannonRules.load(CannonLoad.POWDER, CannonRules.Charge.BALL, false));
        assertEquals(CannonLoad.POWDER, CannonRules.after(CannonRules.LoadOutcome.POWDER_IN, CannonLoad.EMPTY));
        assertEquals(CannonLoad.LOADED, CannonRules.after(CannonRules.LoadOutcome.BALL_IN, CannonLoad.POWDER));
    }

    @Test
    void wrongOrderAndDoubleLoadingAreRefused() {
        assertEquals(CannonRules.LoadOutcome.NEEDS_POWDER_FIRST, CannonRules.load(CannonLoad.EMPTY, CannonRules.Charge.BALL, false));
        assertEquals(CannonRules.LoadOutcome.ALREADY_POWDERED, CannonRules.load(CannonLoad.POWDER, CannonRules.Charge.POWDER, false));
        assertEquals(CannonRules.LoadOutcome.ALREADY_LOADED, CannonRules.load(CannonLoad.LOADED, CannonRules.Charge.BALL, false));
        assertEquals(CannonRules.LoadOutcome.ALREADY_LOADED, CannonRules.load(CannonLoad.LOADED, CannonRules.Charge.POWDER, true));
        for (CannonRules.LoadOutcome o : CannonRules.LoadOutcome.values()) {
            if (!o.accepted()) assertEquals(CannonLoad.POWDER, CannonRules.after(o, CannonLoad.POWDER), o.name());
        }
    }

    @Test
    void aHotBarrelTakesNoPowder() {
        assertEquals(CannonRules.LoadOutcome.RELOADING, CannonRules.load(CannonLoad.EMPTY, CannonRules.Charge.POWDER, true));
        assertEquals(CannonRules.LoadOutcome.NEEDS_POWDER_FIRST, CannonRules.load(CannonLoad.EMPTY, CannonRules.Charge.BALL, true));
        assertEquals(40, CannonRules.reloadLeft(140, 100));
        assertEquals(0, CannonRules.reloadLeft(100, 100));
        assertEquals(0, CannonRules.reloadLeft(50, 100));
    }

    @Test
    void onlyALoadedCannonFires() {
        assertTrue(CannonRules.canFire(CannonLoad.LOADED));
        assertFalse(CannonRules.canFire(CannonLoad.POWDER));
        assertFalse(CannonRules.canFire(CannonLoad.EMPTY));
    }

    // ---- aiming ----

    @Test
    void sixStepsFromMinusFiveToTwenty() {
        double[] expected = {-5, 0, 5, 10, 15, 20};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], CannonRules.elevationDegrees(i, 6, -5, 20), EPS);
        }
        assertEquals(-5, CannonRules.elevationDegrees(-3, 6, -5, 20), EPS);
        assertEquals(20, CannonRules.elevationDegrees(99, 6, -5, 20), EPS);
        assertEquals(20, CannonRules.elevationDegrees(0, 1, -5, 20), EPS);
        assertEquals(1, CannonRules.levelStep(6, -5, 20));
        assertEquals(0, CannonRules.levelStep(4, 0, 30));
    }

    @Test
    void stepsAreClampedAtBothEnds() {
        assertEquals(2, CannonRules.stepElevation(1, 6, true));
        assertEquals(5, CannonRules.stepElevation(5, 6, true));
        assertEquals(0, CannonRules.stepElevation(0, 6, false));
        assertEquals(0, CannonRules.stepElevation(0, 1, true));
    }

    @Test
    void muzzleDirectionFollowsFacingAndElevation() {
        assertVec(new Vec3(0, 0, -1), CannonRules.muzzleDirection(0, -1, 0));
        assertVec(new Vec3(1, 0, 0), CannonRules.muzzleDirection(1, 0, 0));
        double a = Math.toRadians(20);
        assertVec(new Vec3(0, Math.sin(a), Math.cos(a)), CannonRules.muzzleDirection(0, 1, 20));
        assertVec(new Vec3(-Math.cos(Math.toRadians(5)), -Math.sin(Math.toRadians(5)), 0), CannonRules.muzzleDirection(-1, 0, -5));
        assertEquals(1.0, CannonRules.muzzleDirection(1, 0, 15).length(), EPS);
    }

    @Test
    void muzzleLiesAlongTheBarrelFromThePivot() {
        Vec3 dir = CannonRules.muzzleDirection(1, 0, 0);
        assertVec(new Vec3(10.5 + CannonRules.MUZZLE_LENGTH, 64 + CannonRules.PIVOT_HEIGHT, -2.5), CannonRules.muzzle(10, 64, -3, dir));
        assertTrue(CannonRules.MUZZLE_LENGTH > 0.5, "the muzzle must be outside the cannon's own block");
    }

    // ---- firing ----

    @Test
    void ballVelocityAddsTheCarriersVelocityInBlocksPerTick() {
        Vec3 v = CannonRules.ballVelocity(new Vec3(0, 0, 2), 3.0, new Vec3(4, 0, -2));
        assertVec(new Vec3(0.2, 0, 3.0 - 0.1), v);
        assertVec(new Vec3(3, 0, 0), CannonRules.ballVelocity(new Vec3(1, 0, 0), 3.0, Vec3.ZERO));
    }

    @Test
    void recoilPushesAgainstTheBarrel() {
        assertVec(new Vec3(-8, 0, 0), CannonRules.recoilImpulse(new Vec3(2, 0, 0), 8));
        assertVec(Vec3.ZERO, CannonRules.recoilImpulse(new Vec3(0, 0, 1), 0));
    }

    // ---- impact ----

    @Test
    void shipBlocksAndWoodBreakButNotTerrainBedrockOrProofBlocks() {
        assertTrue(CannonRules.destroyable(true, false, 2.0f, true, false, false), "a ship block");
        assertTrue(CannonRules.destroyable(true, false, 2.0f, false, true, false), "planks on land");
        assertFalse(CannonRules.destroyable(true, false, 1.5f, false, false, false), "stone on land");
        assertFalse(CannonRules.destroyable(true, false, -1.0f, true, true, false), "bedrock, even on a ship");
        assertFalse(CannonRules.destroyable(true, false, 50f, true, false, true), "obsidian on a ship");
        assertFalse(CannonRules.destroyable(true, true, 0f, true, true, false), "air");
        assertFalse(CannonRules.destroyable(false, false, 2.0f, true, true, false), "block damage off");
    }

    @Test
    void blocksPerHitAndDamageScaleWithTheMultipliers() {
        assertEquals(1, CannonRules.blocksPerHit(1, 1.0));
        assertEquals(3, CannonRules.blocksPerHit(2, 1.5));
        assertEquals(0, CannonRules.blocksPerHit(1, 0.0));
        assertEquals(20f, CannonRules.entityDamage(20, 1.0), 1e-6);
        assertEquals(10f, CannonRules.entityDamage(20, 0.5), 1e-6);
    }

    @Test
    void impactReachesTheHitBlockThenTheNextSolidOnesAlongThePath() {
        Set<BlockPos> solid = Set.of(new BlockPos(4, 1, 4), new BlockPos(5, 1, 4), new BlockPos(7, 1, 4));
        Vec3 hit = new Vec3(4.0, 1.5, 4.5);
        Vec3 east = new Vec3(1, 0, 0);
        assertEquals(List.of(new BlockPos(4, 1, 4)), CannonImpact.blocksAlong(new BlockPos(4, 1, 4), hit, east, 1, solid::contains));
        assertEquals(List.of(new BlockPos(4, 1, 4), new BlockPos(5, 1, 4)),
                CannonImpact.blocksAlong(new BlockPos(4, 1, 4), hit, east, 2, solid::contains));
        // the gap at x = 6 is skipped, x = 7 is within limit + 1 blocks of the hit point
        assertEquals(List.of(new BlockPos(4, 1, 4), new BlockPos(5, 1, 4), new BlockPos(7, 1, 4)),
                CannonImpact.blocksAlong(new BlockPos(4, 1, 4), hit, east, 3, solid::contains));
        assertTrue(CannonImpact.blocksAlong(new BlockPos(4, 1, 4), hit, east, 0, solid::contains).isEmpty());
        assertEquals(List.of(new BlockPos(4, 1, 4)), CannonImpact.blocksAlong(new BlockPos(4, 1, 4), hit, Vec3.ZERO, 3, solid::contains));
    }
}
