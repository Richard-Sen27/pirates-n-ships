package com.richardsenger.piratesnships.combat.grapple;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GR7: the pendulum of a hand-held rope, run against vanilla's own air movement of a player (each tick: move by the
 * velocity, then {@code (v.x·0.91, (v.y − 0.08)·0.98, v.z·0.91)}), then {@link RopeSwing#step} as the end-of-tick
 * correction. Positions are the hand relative to the hook.
 */
class RopeSwingTest {

    private static final double G = 0.08;
    private static final RopeSwing.Params FREE = new RopeSwing.Params(0.0, 0.0, 0.4);
    private static final RopeSwing.Params DAMPED = new RopeSwing.Params(0.0, 0.03, 0.4);
    private static final RopeSwing.Params PUSHED = new RopeSwing.Params(0.015, 0.03, 0.4);

    /** One simulated tick: the state is {position, velocity} relative to a resting hook. */
    private static Vec3[] tick(Vec3 pos, Vec3 vel, double length, Vec3 wish, RopeSwing.Params p) {
        Vec3 np = pos.add(vel);
        Vec3 v = new Vec3(vel.x * RopeSwing.AIR_DRAG_H, (vel.y - G) * RopeSwing.AIR_DRAG_V, vel.z * RopeSwing.AIR_DRAG_H);
        v = RopeSwing.step(np, v, Vec3.ZERO, length, wish, true, p);
        return new Vec3[]{np, v};
    }

    private static Vec3 hanging(double length, double degrees) {
        double a = Math.toRadians(degrees);
        return new Vec3(length * Math.sin(a), -length * Math.cos(a), 0);
    }

    private static double energy(Vec3 pos, Vec3 vel) {
        return 0.5 * vel.lengthSqr() + G * pos.y;
    }

    private static double angle(Vec3 pos) {
        return Math.toDegrees(Math.atan2(pos.x, -pos.y));
    }

    @Test
    void theRopeHoldsTheHandWithinItsLength() {
        double length = 5.0;
        Vec3[] s = {hanging(length, 60), Vec3.ZERO};
        for (int t = 0; t < 400; t++) {
            s = tick(s[0], s[1], length, Vec3.ZERO, DAMPED);
            assertTrue(s[0].length() <= length + RopeSwing.TAUT_TOLERANCE + 1.0e-6, "tick " + t + ": " + s[0].length());
        }
    }

    @Test
    void anUndampedSwingNeverGainsEnergy() {
        // the energy sampled within a tick wobbles a little (the step is first order); the swing's height is the measure
        for (double start : new double[]{20, 45, 80}) {
            double length = 4.0;
            Vec3[] s = tick(hanging(length, start), Vec3.ZERO, length, Vec3.ZERO, FREE);
            double e0 = energy(s[0], s[1]);
            for (int t = 0; t < 600; t++) {
                s = tick(s[0], s[1], length, Vec3.ZERO, FREE);
                assertTrue(Math.abs(angle(s[0])) <= start + 0.5, start + " degrees, tick " + t + ": swung out to " + angle(s[0]));
            }
            assertTrue(energy(s[0], s[1]) <= e0, start + " degrees: the swing ended with more energy than it started");
        }
    }

    @Test
    void anUndampedSwingKeepsSwinging() {
        double length = 5.0;
        Vec3[] s = {hanging(length, 40), Vec3.ZERO};
        double late = 0;
        for (int t = 0; t < 200; t++) {
            s = tick(s[0], s[1], length, Vec3.ZERO, FREE);
            if (t >= 150) late = Math.max(late, Math.abs(angle(s[0])));
        }
        assertTrue(late > 25.0, "without damping a 40 degree swing is down to " + late + " degrees after 10 s");
    }

    @Test
    void dampingLetsASwingDieOutInAFewSeconds() {
        double length = 5.0;
        Vec3[] s = {hanging(length, 40), Vec3.ZERO};
        double late = 0;
        for (int t = 0; t < 200; t++) {
            s = tick(s[0], s[1], length, Vec3.ZERO, DAMPED);
            if (t >= 150) late = Math.max(late, Math.abs(angle(s[0])));
        }
        assertTrue(late < 6.0, "a damped 40 degree swing still swings " + late + " degrees after 7.5 s");
    }

    @Test
    void walkingPushesTheSwing() {
        double length = 5.0;
        Vec3[] s = {hanging(length, 0), Vec3.ZERO};
        double most = 0;
        for (int t = 0; t < 60; t++) {
            s = tick(s[0], s[1], length, new Vec3(1, 0, 0), PUSHED);
            most = Math.max(most, angle(s[0]));
        }
        assertTrue(most > 5.0, "pushing for 3 s swung the player only " + most + " degrees");
        assertTrue(s[0].length() <= length + RopeSwing.TAUT_TOLERANCE + 1.0e-6, "the push stretched the rope");
    }

    @Test
    void aShortenedRopeDrawsTheHandInAtTheClimbingSpeed() {
        Vec3[] s = {hanging(5.0, 0), Vec3.ZERO};
        double length = 3.5;
        double y0 = s[0].y;
        double prev = s[0].length();
        for (int t = 0; t < 30; t++) {
            s = tick(s[0], s[1], length, Vec3.ZERO, DAMPED);
            assertTrue(prev - s[0].length() <= 0.4 + 1.0e-6, "drawn in faster than the climbing speed");
            prev = s[0].length();
        }
        assertEquals(3.5, s[0].length(), 0.1);
        assertTrue(s[0].y > y0 + 1.3, "the climber rose only " + (s[0].y - y0));
    }

    @Test
    void aSlackRopeLeavesTheVelocityAlone() {
        Vec3 v = new Vec3(0.1, -0.2, 0.05);
        assertEquals(v, RopeSwing.step(new Vec3(0, -2, 0), v, Vec3.ZERO, 5.0, Vec3.ZERO, true, DAMPED));
        assertEquals(v, RopeSwing.step(new Vec3(0, -2, 0), v, Vec3.ZERO, Double.NaN, Vec3.ZERO, true, DAMPED));
    }

    @Test
    void aMovingHookCarriesTheHangingPlayerAlong() {
        Vec3 pv = new Vec3(0.2, 0, 0.05); // a ship at 4 m/s
        Vec3 hook = Vec3.ZERO;
        double length = 4.0;
        Vec3 pos = hanging(length, 10);
        Vec3 vel = pv;
        for (int t = 0; t < 200; t++) {
            pos = pos.add(vel);
            hook = hook.add(pv);
            vel = new Vec3(vel.x * RopeSwing.AIR_DRAG_H, (vel.y - G) * RopeSwing.AIR_DRAG_V, vel.z * RopeSwing.AIR_DRAG_H);
            vel = RopeSwing.step(pos.subtract(hook), vel, pv, length, Vec3.ZERO, true, DAMPED);
            assertTrue(pos.distanceTo(hook) <= length + RopeSwing.TAUT_TOLERANCE + 1.0e-6, "tick " + t + ": " + pos.distanceTo(hook));
        }
        assertTrue(pos.subtract(hook).horizontalDistance() < 1.0, "the player trails " + pos.subtract(hook) + " behind the hook");
    }

    @Test
    void theRopePaysOutOnTheGroundAndHoldsInTheAir() {
        assertTrue(Double.isNaN(RopeSwing.track(Double.NaN, 5, true, true, true, 32)), "held on the ground");
        assertEquals(5.0, RopeSwing.track(Double.NaN, 5, true, false, true, 32), 1e-9);
        assertTrue(Double.isNaN(RopeSwing.track(Double.NaN, 5, true, false, false, 32)), "held by a hook below the hand");
        assertEquals(4.0, RopeSwing.track(4.0, 5, true, false, false, 32), 1e-9, "a held rope keeps holding when the hand swings above the hook");
        assertTrue(Double.isNaN(RopeSwing.track(4.0, 3.9, true, true, true, 32)), "landed with a slack rope, still held");
        assertEquals(4.0, RopeSwing.track(4.0, 5.0, true, true, true, 32), 1e-9, "a rope just shortened by a pull keeps holding on the ground");
        assertTrue(Double.isNaN(RopeSwing.track(4.0, 4.0, false, false, true, 32)), "let go but still held");
        assertEquals(32.0, RopeSwing.track(Double.NaN, 40, true, false, true, 32), 1e-9);
    }

    @Test
    void aPullShortensTheRopeByAStep() {
        assertEquals(3.5, RopeSwing.pull(Double.NaN, 5.0, 1.5), 1e-9);
        assertEquals(2.5, RopeSwing.pull(4.0, 5.0, 1.5), 1e-9);
        assertEquals(1.5, RopeSwing.pull(4.0, 3.0, 1.5), 1e-9, "a slack rope is taken in first");
        assertEquals(RopeSwing.MIN_LENGTH, RopeSwing.pull(1.0, 1.0, 1.5), 1e-9);
        assertTrue(RopeSwing.arrives(2.4, 3.0, 1.5, 1.0));
        assertFalse(RopeSwing.arrives(Double.NaN, 3.0, 1.5, 1.0));
    }

    @Test
    void walkingInputTurnsWithTheYaw() {
        Vec3 ahead = RopeSwing.wish(0f, 1f, 0f);
        assertEquals(0.0, ahead.x, 1e-9);
        assertEquals(1.0, ahead.z, 1e-9);
        Vec3 west = RopeSwing.wish(0f, 1f, 90f);
        assertEquals(-1.0, west.x, 1e-9);
        assertEquals(0.0, west.z, 1e-9);
        assertEquals(1.0, RopeSwing.wish(1f, 1f, 30f).length(), 1e-9);
        assertEquals(Vec3.ZERO, RopeSwing.wish(0f, 0f, 30f));
    }
}
