package com.richardsenger.piratesnships.combat.cannon;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwivelRulesTest {

    private static final double EPS = 1e-9;

    private static void assertVec(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, EPS, "x of " + actual);
        assertEquals(expected.y, actual.y, EPS, "y of " + actual);
        assertEquals(expected.z, actual.z, EPS, "z of " + actual);
    }

    /** Minecraft's look vector for a view yaw and pitch (Entity#calculateViewVector, written out). */
    private static Vec3 look(double yaw, double pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
    }

    @Test
    void lookVectorMatchesMinecraftsViewVector() {
        for (double yaw : new double[]{0, 90, -45, 170}) {
            for (double pitch : new double[]{-60, 0, 30}) {
                assertVec(look(yaw, pitch), SwivelRules.lookVector(yaw, pitch));
            }
        }
    }

    @Test
    void yawFollowsMinecraftsConvention() {
        assertVec(new Vec3(0, 0, 1), SwivelRules.direction(0, 0));   // south
        assertVec(new Vec3(-1, 0, 0), SwivelRules.direction(90, 0)); // west
        assertVec(new Vec3(0, 0, -1), SwivelRules.direction(180, 0)); // north
        assertVec(new Vec3(1, 0, 0), SwivelRules.direction(-90, 0)); // east
        Vec3 up = SwivelRules.direction(-90, 30);
        assertVec(new Vec3(Math.cos(Math.toRadians(30)), Math.sin(Math.toRadians(30)), 0), up);
        assertEquals(1.0, SwivelRules.direction(37, -12).length(), EPS);
    }

    @Test
    void muzzleLiesAlongTheBarrelFromThePivot() {
        assertVec(new Vec3(10.5, 64 + SwivelRules.PIVOT_HEIGHT, -3 + 0.5 + SwivelRules.MUZZLE_LENGTH), SwivelRules.muzzle(10, 64, -3, 0, 0));
        Vec3 raised = SwivelRules.muzzle(0, 0, 0, 90, 45);
        double d = SwivelRules.MUZZLE_LENGTH * Math.sqrt(0.5);
        assertVec(new Vec3(0.5 - d, SwivelRules.PIVOT_HEIGHT + d, 0.5), raised);
        assertTrue(SwivelRules.MUZZLE_LENGTH > 0.5, "the muzzle must be outside the gun's own block when level");
    }

    @Test
    void aimFollowsTheView() {
        for (double yaw : new double[]{0, 45, 90, 135, 179, -30, -90, -170}) {
            for (double pitch : new double[]{-20, 0, 10}) {
                SwivelRules.Aim aim = SwivelRules.aimFromLook(look(yaw, pitch), 0, -30, 45);
                assertEquals(yaw, aim.yawDegrees(), 1e-6, "yaw for view " + yaw + "/" + pitch);
                assertEquals(-pitch, aim.elevationDegrees(), 1e-6, "elevation for view " + yaw + "/" + pitch);
            }
        }
    }

    @Test
    void elevationIsClampedAndAStraightLookKeepsTheYaw() {
        assertEquals(45, SwivelRules.aimFromLook(look(10, -80), 0, -30, 45).elevationDegrees(), 1e-6);
        assertEquals(-30, SwivelRules.aimFromLook(look(10, 70), 0, -30, 45).elevationDegrees(), 1e-6);
        SwivelRules.Aim straightUp = SwivelRules.aimFromLook(new Vec3(0, 1, 0), 123, -30, 45);
        assertEquals(123, straightUp.yawDegrees(), EPS);
        assertEquals(45, straightUp.elevationDegrees(), EPS);
    }

    @Test
    void aShipFrameLookIsAimedInThatFrame() {
        // a ship turned 90° (its +z points to world west): a world look to the west is the ship's "south", yaw 0
        Vec3 worldWest = look(90, 0);
        Vec3 local = new Vec3(worldWest.z, worldWest.y, -worldWest.x); // inverse of the ship turn (rotation y +90°)
        assertEquals(0, SwivelRules.aimFromLook(local, 0, -30, 45).yawDegrees(), 1e-6);
        // and a world look to the north is the ship's "west" (its +x points to world south), yaw 90
        Vec3 north = look(180, 0);
        assertEquals(90, SwivelRules.aimFromLook(new Vec3(north.z, north.y, -north.x), 0, -30, 45).yawDegrees(), 1e-6);
    }

    @Test
    void yawWrapsAndSmallChangesAreNotSent() {
        assertEquals(-170, SwivelRules.wrapYaw(190), EPS);
        assertEquals(180, SwivelRules.wrapYaw(-180), EPS);
        assertEquals(10, SwivelRules.wrapYaw(370), EPS);
        assertFalse(new SwivelRules.Aim(179.8, 0).differs(new SwivelRules.Aim(-179.9, 0), 0.5), "across ±180 is close");
        assertTrue(new SwivelRules.Aim(10, 0).differs(new SwivelRules.Aim(10, 1), 0.5));
    }
}
