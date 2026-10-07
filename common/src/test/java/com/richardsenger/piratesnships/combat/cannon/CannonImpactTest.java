package com.richardsenger.piratesnships.combat.cannon;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Glancing hits (Q2): how squarely a ball meets a face, how many blocks it breaks and how it bounces. */
class CannonImpactTest {

    private static final double EPS = 1e-9;
    private static final Vec3 UP_FACE = new Vec3(0, 1, 0);

    /** A flight {@code degrees} off the normal of a face that faces up (coming down onto it). */
    private static Vec3 flightAt(double degrees) {
        double a = Math.toRadians(degrees);
        return new Vec3(Math.sin(a), -Math.cos(a), 0).scale(2.5);
    }

    @Test
    void squarenessIsTheCosineOfTheAngleToTheNormal() {
        assertEquals(1.0, CannonImpact.squareness(flightAt(0), UP_FACE), EPS);
        assertEquals(0.5, CannonImpact.squareness(flightAt(60), UP_FACE), EPS);
        assertEquals(Math.cos(Math.toRadians(80)), CannonImpact.squareness(flightAt(80), UP_FACE), EPS);
        assertEquals(0.0, CannonImpact.squareness(new Vec3(1, 0, 0), UP_FACE), EPS);
        // the sign of the normal does not matter
        assertEquals(0.5, CannonImpact.squareness(flightAt(60), new Vec3(0, -1, 0)), EPS);
        // no flight: counts as straight
        assertEquals(1.0, CannonImpact.squareness(Vec3.ZERO, UP_FACE), EPS);
    }

    @Test
    void aStraightHitBreaksAllBlocks() {
        double cos = CannonImpact.squareness(flightAt(0), UP_FACE);
        assertFalse(CannonImpact.bounces(cos, 75));
        assertEquals(4, CannonImpact.glancingBlocks(4, cos));
        assertEquals(1, CannonImpact.glancingBlocks(1, cos));
    }

    @Test
    void aHitAtSixtyDegreesBreaksHalf() {
        double cos = CannonImpact.squareness(flightAt(60), UP_FACE);
        assertFalse(CannonImpact.bounces(cos, 75));
        assertEquals(2, CannonImpact.glancingBlocks(4, cos));
        assertEquals(3, CannonImpact.glancingBlocks(6, cos));
    }

    @Test
    void aGlancingHitBreaksAtLeastOneBlock() {
        double cos = CannonImpact.squareness(flightAt(74), UP_FACE);
        assertFalse(CannonImpact.bounces(cos, 75));
        assertEquals(1, CannonImpact.glancingBlocks(3, cos));
        assertEquals(1, CannonImpact.glancingBlocks(1, 0.0));
    }

    @Test
    void noBlockDamageStaysNone() {
        assertEquals(0, CannonImpact.glancingBlocks(0, 1.0));
        assertEquals(0, CannonImpact.glancingBlocks(0, 0.3));
    }

    @Test
    void aHitAtEightyDegreesBounces() {
        double cos = CannonImpact.squareness(flightAt(80), UP_FACE);
        assertTrue(CannonImpact.bounces(cos, 75));
        assertFalse(CannonImpact.bounces(cos, 85), "below a higher threshold it does not bounce");
        assertFalse(CannonImpact.bounces(0.0, 90), "90 degrees turns bouncing off");
        assertTrue(CannonImpact.bounces(0.5, 0), "0 degrees: everything off the normal bounces");
        assertFalse(CannonImpact.bounces(1.0, 0), "0 degrees: a straight hit still breaks");
    }

    @Test
    void aBounceTurnsTheNormalPartAroundAndKeepsTheRest() {
        Vec3 v = new Vec3(2.0, -0.5, 0.25);
        Vec3 out = CannonImpact.deflect(v, UP_FACE, 0.3);
        assertEquals(2.0, out.x, EPS);
        assertEquals(0.15, out.y, EPS);
        assertEquals(0.25, out.z, EPS);
        // the normal need not be unit length or point towards the ball
        Vec3 same = CannonImpact.deflect(v, new Vec3(0, -3, 0), 0.3);
        assertEquals(0.15, same.y, EPS);
        Vec3 dead = CannonImpact.deflect(v, UP_FACE, 0.0);
        assertEquals(0.0, dead.y, EPS);
        Vec3 mirror = CannonImpact.deflect(v, new Vec3(1, 0, 0), 1.0);
        assertEquals(-2.0, mirror.x, EPS);
        assertEquals(-0.5, mirror.y, EPS);
    }
}
