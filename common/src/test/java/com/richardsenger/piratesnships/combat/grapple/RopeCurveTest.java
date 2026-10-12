package com.richardsenger.piratesnships.combat.grapple;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** GR7: the hanging rope's catenary, sampled into the renderer's 16 pieces. */
class RopeCurveTest {

    private static final int N = 16;

    private static double polyline(List<Vec3> points) {
        double len = 0;
        for (int i = 1; i < points.size(); i++) len += points.get(i).distanceTo(points.get(i - 1));
        return len;
    }

    private static double offLine(List<Vec3> points, Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a).normalize();
        double worst = 0;
        for (Vec3 p : points) {
            Vec3 r = p.subtract(a);
            worst = Math.max(worst, r.subtract(d.scale(r.dot(d))).length());
        }
        return worst;
    }

    @Test
    void theEndsAreExact() {
        Vec3 a = new Vec3(1, 10, 2), b = new Vec3(5, 6, -1);
        List<Vec3> p = RopeCurve.sample(a, b, 9.0, N);
        assertEquals(N + 1, p.size());
        assertEquals(0.0, p.get(0).distanceTo(a), 1e-9);
        assertEquals(0.0, p.get(N).distanceTo(b), 1e-9);
    }

    @Test
    void aRopeAtFullLengthIsStraight() {
        Vec3 a = new Vec3(0, 10, 0), b = new Vec3(4, 7, 0);
        assertEquals(0.0, offLine(RopeCurve.sample(a, b, 5.0, N), a, b), 1e-9);
        assertEquals(0.0, offLine(RopeCurve.sample(a, b, 4.0, N), a, b), 1e-9, "a rope shorter than the span is drawn taut");
        assertEquals(0.0, RopeCurve.dip(a, b, 5.0, N), 1e-9);
    }

    @Test
    void aPlayerStraightBelowTheHookHangsOnAVerticalRope() {
        Vec3 hook = new Vec3(3, 12, 3), hand = new Vec3(3, 7, 3);
        for (Vec3 p : RopeCurve.sample(hook, hand, 5.0, N)) {
            assertEquals(3.0, p.x, 1e-9);
            assertEquals(3.0, p.z, 1e-9);
        }
        for (Vec3 p : RopeCurve.sample(hook, hand, 7.0, N)) {
            assertEquals(3.0, p.x, 1e-9, "slack straight below is still a vertical line");
        }
    }

    @Test
    void aSlackRopeSagsMoreTheLongerItIs() {
        Vec3 a = new Vec3(0, 5, 0), b = new Vec3(6, 5, 0);
        double prev = 0;
        for (double len : new double[]{6.2, 6.6, 7.5, 9.0}) {
            double dip = RopeCurve.dip(a, b, len, N);
            assertTrue(dip > prev, "length " + len + " dips " + dip + ", not more than " + prev);
            prev = dip;
        }
        // a level catenary of span 6 and length 9 hangs c·(cosh(h/2c) − 1) = 3.02 below its ends (c = 1.849)
        assertEquals(3.016, RopeCurve.dip(a, b, 9.0, 64), 0.01);
    }

    @Test
    void theSampledRopeIsAsLongAsTheRope() {
        Vec3 a = new Vec3(0, 10, 0), b = new Vec3(3, 4, 2);
        for (double len : new double[]{7.2, 8.0, 10.0}) {
            assertEquals(len, polyline(RopeCurve.sample(a, b, len, 200)), len * 0.01, "length " + len);
        }
    }

    @Test
    void aLevelRopeHangsSymmetrically() {
        Vec3 a = new Vec3(0, 5, 0), b = new Vec3(8, 5, 0);
        List<Vec3> p = RopeCurve.sample(a, b, 10.0, N);
        for (int i = 0; i <= N; i++) {
            assertEquals(p.get(i).y, p.get(N - i).y, 1e-6);
        }
        assertTrue(p.get(N / 2).y < 5.0 - 1.0, "the middle does not hang down");
    }

    @Test
    void aSteepSlightlySlackRopeStaysFinite() {
        Vec3 hook = new Vec3(0, 20, 0), hand = new Vec3(0.01, 4, 0);
        for (Vec3 p : RopeCurve.sample(hook, hand, 16.5, N)) {
            assertTrue(Double.isFinite(p.y) && p.y > 3.0 && p.y <= 20.0, "point " + p);
        }
    }
}
