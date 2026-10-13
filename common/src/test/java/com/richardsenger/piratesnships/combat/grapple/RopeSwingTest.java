package com.richardsenger.piratesnships.combat.grapple;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GR7, GR8: the pendulum of a hand-held rope, run against vanilla's own air movement of a player (each tick: the walking
 * input's air control added, move by the velocity, then {@code (v.x·0.91, (v.y − 0.08)·0.98, v.z·0.91)}), then
 * {@link RopeSwing#step} as the end-of-tick correction. Positions are the hand relative to the hook.
 */
class RopeSwingTest {

    private static final double G = 0.08;
    private static final double MAX = 0.8;
    private static final RopeSwing.Params FREE = new RopeSwing.Params(0.0, 0.4, 0.0, MAX, 0.4);
    private static final RopeSwing.Params DAMPED = new RopeSwing.Params(0.0, 0.4, 0.02, MAX, 0.4);
    /** GR8's defaults: {@code swing_push} 0.004, {@code swing_push_sideways} 0.4, {@code swing_damping} 0.02. */
    private static final RopeSwing.Params PUSHED = new RopeSwing.Params(0.004, 0.4, 0.02, MAX, 0.4);
    /** Facing +x (yaw -90): forward input points along +x, left (strafe +1) along +z. */
    private static final float EAST = -90f;

    private static RopeSwing.Input forward(float f) {
        return new RopeSwing.Input(f * 0.98f, 0f, EAST, RopeSwing.airControl(false));
    }

    private static RopeSwing.Input left(float s) {
        return new RopeSwing.Input(0f, s * 0.98f, EAST, RopeSwing.airControl(false));
    }

    /** One simulated tick: the state is {position, velocity} relative to a resting hook. */
    private static Vec3[] tick(Vec3 pos, Vec3 vel, double length, RopeSwing.Input input, RopeSwing.Params p) {
        Vec3 moved = vel.add(input.vanilla()); // vanilla's moveRelative before the move
        Vec3 np = pos.add(moved);
        Vec3 v = new Vec3(moved.x * RopeSwing.AIR_DRAG_H, (moved.y - G) * RopeSwing.AIR_DRAG_V, moved.z * RopeSwing.AIR_DRAG_H);
        v = RopeSwing.step(np, v, Vec3.ZERO, length, input, true, p);
        return new Vec3[]{np, v};
    }

    private static Vec3[] tick(Vec3 pos, Vec3 vel, double length, RopeSwing.Params p) {
        return tick(pos, vel, length, RopeSwing.Input.NONE, p);
    }

    /** The swing's angle off the vertical in any direction [degrees]. */
    private static double outAngle(Vec3 pos) {
        return Math.toDegrees(Math.atan2(Math.hypot(pos.x, pos.z), -pos.y));
    }

    /** Pumping along x: forward while swinging toward +x, back while swinging toward -x. */
    private static RopeSwing.Input pumpForward(Vec3 vel) {
        return forward(vel.x >= 0 ? 1f : -1f);
    }

    /** Pumping along z by strafing: facing +x, left (strafe +1) points along -z. */
    private static RopeSwing.Input pumpSideways(Vec3 vel) {
        return left(vel.z >= 0 ? -1f : 1f);
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
            s = tick(s[0], s[1], length, DAMPED);
            assertTrue(s[0].length() <= length + RopeSwing.TAUT_TOLERANCE + 1.0e-6, "tick " + t + ": " + s[0].length());
        }
    }

    @Test
    void anUndampedSwingNeverGainsEnergy() {
        // the energy sampled within a tick wobbles a little (the step is first order); the swing's height is the measure
        for (double start : new double[]{20, 45, 80}) {
            double length = 4.0;
            Vec3[] s = tick(hanging(length, start), Vec3.ZERO, length, FREE);
            double e0 = energy(s[0], s[1]);
            for (int t = 0; t < 600; t++) {
                s = tick(s[0], s[1], length, FREE);
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
            s = tick(s[0], s[1], length, FREE);
            if (t >= 150) late = Math.max(late, Math.abs(angle(s[0])));
        }
        assertTrue(late > 25.0, "without damping a 40 degree swing is down to " + late + " degrees after 10 s");
    }

    @Test
    void dampingLetsASwingDieOutInAFewSeconds() {
        double length = 5.0;
        Vec3[] s = {hanging(length, 40), Vec3.ZERO};
        double late = 0;
        for (int t = 0; t < 300; t++) {
            s = tick(s[0], s[1], length, DAMPED);
            if (t >= 250) late = Math.max(late, Math.abs(angle(s[0])));
        }
        assertTrue(late < 6.0, "a damped 40 degree swing still swings " + late + " degrees after 12.5 s");
    }

    @Test
    void withoutInputTheSwingsEnergyStaysBoundedAndDecays() {
        // GR8: no input, defaults: never more energy than at the start, and less every few seconds
        double length = 6.0;
        // the energy sampled within a tick wobbles a little (the step is first order): 2 % of the swing's G·L
        Vec3[] s = tick(hanging(length, 50), Vec3.ZERO, length, PUSHED);
        double e0 = energy(s[0], s[1]) + 0.02 * G * length;
        double lastPeak = e0;
        for (int sec = 0; sec < 10; sec++) {
            double peak = Double.NEGATIVE_INFINITY;
            for (int t = 0; t < 20; t++) {
                s = tick(s[0], s[1], length, PUSHED);
                peak = Math.max(peak, energy(s[0], s[1]));
                assertTrue(energy(s[0], s[1]) <= e0, "second " + sec + ": the swing gained energy");
            }
            if (sec % 3 == 2) {
                assertTrue(peak < lastPeak, "second " + sec + ": the swing's energy did not decay (" + peak + " after " + lastPeak + ")");
                lastPeak = peak;
            }
        }
        assertTrue(outAngle(s[0]) < 15.0, "a 50 degree swing still swings " + outAngle(s[0]) + " degrees after 10 s");
    }

    @Test
    void holdingForwardFromRestLeansToASteadyAngle() {
        // a held key is a small steady force against gravity: the pendulum settles a few degrees out and stays there
        double length = 5.0;
        Vec3[] s = {hanging(length, 0), Vec3.ZERO};
        double early = 0;
        double late = 0;
        double lateLow = Double.POSITIVE_INFINITY;
        for (int t = 0; t < 600; t++) {
            s = tick(s[0], s[1], length, forward(1f), PUSHED);
            if (t < 100) early = Math.max(early, angle(s[0]));
            if (t >= 500) {
                late = Math.max(late, angle(s[0]));
                lateLow = Math.min(lateLow, angle(s[0]));
            }
        }
        assertTrue(early > 1.0, "holding forward did not move the player: " + early + " degrees");
        assertTrue(late < 8.0, "holding forward swung the player " + late + " degrees out (like flying)");
        assertTrue(late - lateLow < 0.5, "holding forward did not settle: " + lateLow + " to " + late + " degrees");
        assertTrue(s[0].length() <= length + RopeSwing.TAUT_TOLERANCE + 1.0e-6, "the push stretched the rope");
    }

    @Test
    void pumpingForwardFromRestReachesASteadyAmplitude() {
        double length = 4.0;
        Vec3[] s = {hanging(length, 0), Vec3.ZERO};
        double[] peaks = new double[6];
        for (int t = 0; t < 1200; t++) {
            s = tick(s[0], s[1], length, pumpForward(s[1]), PUSHED);
            if (t >= 600) {
                int w = (t - 600) / 100;
                peaks[w] = Math.max(peaks[w], Math.abs(angle(s[0])));
            }
        }
        assertTrue(peaks[5] > 12.0, "a minute of pumping swings only " + peaks[5] + " degrees");
        assertTrue(peaks[5] < 45.0, "pumping swings " + peaks[5] + " degrees: still far too strong");
        for (int w = 1; w < peaks.length; w++) {
            assertEquals(peaks[0], peaks[w], 1.0, "the pumped amplitude still changes: " + java.util.Arrays.toString(peaks));
        }
    }

    @Test
    void sidewaysInputSwingsLessThanForward() {
        double length = 4.0;
        Vec3[] f = {hanging(length, 0), Vec3.ZERO};
        Vec3[] l = {hanging(length, 0), Vec3.ZERO};
        Vec3[] hf = {hanging(length, 0), Vec3.ZERO};
        Vec3[] hl = {hanging(length, 0), Vec3.ZERO};
        double pumpedF = 0, pumpedL = 0, heldF = 0, heldL = 0;
        for (int t = 0; t < 800; t++) {
            f = tick(f[0], f[1], length, pumpForward(f[1]), PUSHED);
            l = tick(l[0], l[1], length, pumpSideways(l[1]), PUSHED);
            hf = tick(hf[0], hf[1], length, forward(1f), PUSHED);
            hl = tick(hl[0], hl[1], length, left(1f), PUSHED);
            if (t >= 600) {
                pumpedF = Math.max(pumpedF, outAngle(f[0]));
                pumpedL = Math.max(pumpedL, outAngle(l[0]));
                heldF = Math.max(heldF, outAngle(hf[0]));
                heldL = Math.max(heldL, outAngle(hl[0]));
            }
        }
        assertTrue(pumpedL > 2.0, "sideways pumping does nothing: " + pumpedL);
        assertTrue(pumpedL < 0.7 * pumpedF, "sideways pumping swings " + pumpedL + " degrees, forward " + pumpedF);
        assertTrue(heldL < 0.7 * heldF, "holding sideways leans " + heldL + " degrees, forward " + heldF);
        assertTrue(Math.abs(l[0].x) < 0.05 && Math.abs(hl[0].x) < 0.05, "sideways input moved the player forward");
    }

    @Test
    void theSpeedNeverExceedsTheCap() {
        // a long rope dropped from nearly level would reach 1.3 blocks per tick; pumping on top of it changes nothing
        double length = 12.0;
        Vec3[] s = {hanging(length, 88), Vec3.ZERO};
        double fastest = 0;
        for (int t = 0; t < 600; t++) {
            s = tick(s[0], s[1], length, pumpForward(s[1]), PUSHED);
            fastest = Math.max(fastest, s[1].length());
            assertTrue(s[1].length() <= MAX + 1.0e-9, "tick " + t + ": " + s[1].length() + " blocks per tick");
        }
        assertTrue(fastest > MAX - 0.05, "the drop never reached the cap: " + fastest);
        RopeSwing.Params slow = new RopeSwing.Params(0.004, 0.4, 0.0, 0.3, 0.4);
        Vec3[] q = {hanging(5.0, 60), Vec3.ZERO};
        for (int t = 0; t < 400; t++) {
            q = tick(q[0], q[1], 5.0, pumpForward(q[1]), slow);
            assertTrue(q[1].length() <= 0.3 + 1.0e-9, "tick " + t + ": " + q[1].length() + " over a cap of 0.3");
        }
    }

    @Test
    void thrustCannotRunAway() {
        // GR7's push on top of vanilla's air control (both with the drag undone) ran a held key up to well over 1 block
        // per tick; now a held key on an undamped rope stays slow
        RopeSwing.Params undamped = new RopeSwing.Params(0.004, 0.4, 0.0, MAX, 0.4);
        double length = 5.0;
        Vec3[] s = {hanging(length, 0), Vec3.ZERO};
        double fastest = 0;
        for (int t = 0; t < 600; t++) {
            s = tick(s[0], s[1], length, forward(1f), undamped);
            fastest = Math.max(fastest, s[1].length());
        }
        assertTrue(fastest < 0.15, "a held key on an undamped rope reached " + fastest + " blocks per tick");
    }

    @Test
    void aShortenedRopeDrawsTheHandInAtTheClimbingSpeed() {
        Vec3[] s = {hanging(5.0, 0), Vec3.ZERO};
        double length = 3.5;
        double y0 = s[0].y;
        double prev = s[0].length();
        for (int t = 0; t < 30; t++) {
            s = tick(s[0], s[1], length, DAMPED);
            assertTrue(prev - s[0].length() <= 0.4 + 1.0e-6, "drawn in faster than the climbing speed");
            prev = s[0].length();
        }
        assertEquals(3.5, s[0].length(), 0.1);
        assertTrue(s[0].y > y0 + 1.3, "the climber rose only " + (s[0].y - y0));
    }

    @Test
    void aSlackRopeLeavesTheVelocityAlone() {
        Vec3 v = new Vec3(0.1, -0.2, 0.05);
        assertEquals(v, RopeSwing.step(new Vec3(0, -2, 0), v, Vec3.ZERO, 5.0, forward(1f), true, DAMPED));
        assertEquals(v, RopeSwing.step(new Vec3(0, -2, 0), v, Vec3.ZERO, Double.NaN, forward(1f), true, DAMPED));
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
            vel = RopeSwing.step(pos.subtract(hook), vel, pv, length, RopeSwing.Input.NONE, true, DAMPED);
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
