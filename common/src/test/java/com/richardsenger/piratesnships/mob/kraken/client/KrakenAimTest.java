package com.richardsenger.piratesnships.mob.kraken.client;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tentacle aim (K1c): the first bone points at the target and its sucker face turns towards the body's axis, with
 * GeckoLib's rotation order and signs (checked against JOML's {@code rotationZYX}, the call GeckoLib renders with).
 * Pivots are the rig's ring in the baked frame (x flipped against the file).
 */
class KrakenAimTest {

    private static final double RING = 22.4;
    private static final double[] UP = {0, 1, 0};

    private static double[] pivot(int i) {
        double a = Math.toRadians((i + 0.5) * 45.0);
        return new double[]{Math.sin(a) * RING, 20, -Math.cos(a) * RING};
    }

    private static double angleDeg(double[] a, double[] b) {
        double dot = 0, la = 0, lb = 0;
        for (int k = 0; k < 3; k++) {
            dot += a[k] * b[k];
            la += a[k] * a[k];
            lb += b[k] * b[k];
        }
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot / Math.sqrt(la * lb)))));
    }

    /** The direction from the root to the body's axis, made perpendicular to the arm {@code d}. */
    private static double[] towardsAxis(double[] p, double[] d) {
        double[] w = {-p[0], 0, -p[2]};
        double k = w[0] * d[0] + w[1] * d[1] + w[2] * d[2];
        return new double[]{w[0] - k * d[0], w[1] - k * d[1], w[2] - k * d[2]};
    }

    /** The same vector turned the way GeckoLib turns the bone: {@code Quaternionf.rotationZYX(z, y, x)}. */
    private static double[] joml(float[] e, double[] v) {
        Vector3f r = new Quaternionf().rotationZYX(e[2], e[1], e[0]).transform(new Vector3f((float) v[0], (float) v[1], (float) v[2]));
        return new double[]{r.x, r.y, r.z};
    }

    @Test
    void armsPointAtTheTargetWithTheirSuckersTowardsTheAxis() {
        double worstAim = 0, worstRoll = 0;
        int cases = 0;
        for (int i = 0; i < 8; i++) {
            double[] p = pivot(i);
            double[] n0 = KrakenAim.restNormal(p[0], p[2]);
            double radial = Math.atan2(p[0], -p[2]);
            for (double side : new double[]{-30, 0, 30}) {
                for (double elevation : new double[]{60, 25, -50}) {
                    double az = radial + Math.toRadians(side), el = Math.toRadians(elevation);
                    double[] d = {Math.sin(az) * Math.cos(el), Math.sin(el), -Math.cos(az) * Math.cos(el)};
                    float[] e = KrakenAim.aim(p[0], p[2], new double[]{d[0] * 37, d[1] * 37, d[2] * 37});
                    double[] arm = joml(e, UP), normal = joml(e, n0);
                    double aim = angleDeg(arm, d), roll = angleDeg(normal, towardsAxis(p, d));
                    String what = "arm " + i + " side " + side + " elevation " + elevation;
                    assertTrue(aim < 1.0, what + ": points " + aim + " deg off the target");
                    assertTrue(roll < 15.0, what + ": suckers " + roll + " deg off the body's axis");
                    // our own rotate() is GeckoLib's order
                    double[] mine = KrakenAim.rotate(e, n0);
                    for (int k = 0; k < 3; k++) assertEquals(normal[k], mine[k], 1e-5, what + " rotate()");
                    worstAim = Math.max(worstAim, aim);
                    worstRoll = Math.max(worstRoll, roll);
                    cases++;
                }
            }
        }
        assertEquals(72, cases);
        System.out.printf("KrakenAimTest: %d aims, worst aim error %.4f deg, worst sucker roll error %.4f deg%n", cases, worstAim, worstRoll);
    }

    @Test
    void theCurlStillClosesTowardsTheAxisAfterAiming() {
        // a point of the curled tip at rest: up the arm and towards the sucker side; after the aim it lies on the
        // axis side of the arm (the animations' curl and the geometry's tilt close over the target side)
        for (int i = 0; i < 8; i++) {
            double[] p = pivot(i);
            double[] n0 = KrakenAim.restNormal(p[0], p[2]);
            double[] tip = {n0[0] * 4, 58, n0[2] * 4};
            for (double elevation : new double[]{60, 25, -50}) {
                double az = Math.atan2(p[0], -p[2]), el = Math.toRadians(elevation);
                double[] d = {Math.sin(az) * Math.cos(el), Math.sin(el), -Math.cos(az) * Math.cos(el)};
                float[] e = KrakenAim.aim(p[0], p[2], d);
                double[] t = joml(e, tip);
                double[] towards = towardsAxis(p, d);
                double side = t[0] * towards[0] + t[1] * towards[1] + t[2] * towards[2];
                assertTrue(side > 0, "arm " + i + " elevation " + elevation + ": the tip curls away from the axis");
            }
        }
    }

    @Test
    void degenerateAimsStillGiveARotation() {
        for (int i = 0; i < 8; i++) {
            double[] p = pivot(i);
            for (double[] d : new double[][]{{p[0], 0, p[2]}, {-p[0], 0, -p[2]}, {0, 1, 0}, {0, -1, 0}}) {
                float[] e = KrakenAim.aim(p[0], p[2], d);
                for (float v : e) assertTrue(Float.isFinite(v), "arm " + i + ": no angle for " + java.util.Arrays.toString(d));
                assertTrue(angleDeg(joml(e, UP), d) < 1.0, "arm " + i + " misses " + java.util.Arrays.toString(d));
                double[] n = joml(e, KrakenAim.restNormal(p[0], p[2]));
                assertEquals(0, n[0] * d[0] + n[1] * d[1] + n[2] * d[2], 1e-3 * Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]));
            }
        }
    }

    @Test
    void theRestLeanKeepsTheArmsOutOfTheHead() {
        // head layers (art/README.md): y from, y to, radius; a rounded square reaches 1.082 r at 22.5 deg off its axes
        double[][] head = {{12, 20, 10.8}, {20, 28, 14.6}, {28, 36, 18.4}, {36, 44, 21.4}, {44, 52, 18.2}};
        double halfWidth = 3.5;
        for (int i = 0; i < 8; i++) {
            double[] p = pivot(i);
            double[] d = KrakenAim.restDirection(p[0], p[2], Math.toRadians(KrakenModel.REST_LEAN_DEG));
            float[] e = KrakenAim.aim(p[0], p[2], d);
            assertTrue(angleDeg(joml(e, UP), d) < 1.0, "arm " + i + " rest direction");
            assertEquals(KrakenModel.REST_LEAN_DEG, angleDeg(d, UP), 1e-6, "arm " + i + " lean");
            for (double s = 0; s <= KrakenModel.TENTACLE_LENGTH; s += 1) {
                double x = p[0] + d[0] * s, y = p[1] + d[1] * s, z = p[2] + d[2] * s;
                double r = Math.hypot(x, z);
                for (double[] layer : head) {
                    if (y < layer[0] || y > layer[1]) continue;
                    assertTrue(r - halfWidth / Math.cos(Math.toRadians(KrakenModel.REST_LEAN_DEG)) > 1.082 * layer[2],
                            "arm " + i + " runs into the head at y " + y);
                }
            }
            // straight up it would: the K1b problem this lean fixes
            assertTrue(RING - halfWidth < 1.082 * 21.4, "the check would not catch an upright arm");
        }
    }
}
