package com.richardsenger.piratesnships.mob.kraken.client;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tentacle aim (K1c, sucker side GL1): the first bone points at the target and its sucker face turns down for a
 * raised or level arm and towards the body's axis for a hanging one, with GeckoLib's rotation order and signs (checked
 * against JOML's {@code rotationZYX}, the call GeckoLib renders with). Pivots are the rig's ring in the baked frame (x
 * flipped against the file). The world-space version with the renderer's yaw turn is {@code KrakenWorldPoseTest}.
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

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    /** The same vector turned the way GeckoLib turns the bone: {@code Quaternionf.rotationZYX(z, y, x)}. */
    private static double[] joml(float[] e, double[] v) {
        Vector3f r = new Quaternionf().rotationZYX(e[2], e[1], e[0]).transform(new Vector3f((float) v[0], (float) v[1], (float) v[2]));
        return new double[]{r.x, r.y, r.z};
    }

    @Test
    void armsPointAtTheTargetWithTheirSuckersDownOrTowardsTheAxis() {
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
                    double aim = angleDeg(arm, d), roll = angleDeg(normal, KrakenAim.suckerNormal(p[0], p[2], d));
                    String what = "arm " + i + " side " + side + " elevation " + elevation;
                    assertTrue(aim < 1.0, what + ": points " + aim + " deg off the target");
                    assertTrue(roll < 0.01, what + ": suckers " + roll + " deg off the wanted normal");
                    if (elevation >= 0) assertTrue(normal[1] < -0.3, what + ": raised, the suckers face up " + normal[1]);
                    else assertTrue(dot(normal, towardsAxis(p, d)) > 0.5, what + ": hanging, the suckers face away from the axis");
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
    void theCurlClosesDownWhenRaisedAndTowardsTheAxisWhenHanging() {
        // a point of the curled tip at rest: up the arm and towards the sucker side; after the aim it lies below the arm
        // when the arm is raised or level, and on the axis side when it hangs (the animations' curl and the geometry's
        // tilt close the same way)
        for (int i = 0; i < 8; i++) {
            double[] p = pivot(i);
            double[] n0 = KrakenAim.restNormal(p[0], p[2]);
            double[] tip = {n0[0] * 4, 58, n0[2] * 4};
            for (double elevation : new double[]{60, 25, 0, -50, -89}) {
                double az = Math.atan2(p[0], -p[2]), el = Math.toRadians(elevation);
                double[] d = {Math.sin(az) * Math.cos(el), Math.sin(el), -Math.cos(az) * Math.cos(el)};
                float[] e = KrakenAim.aim(p[0], p[2], d);
                double[] t = joml(e, tip);
                // the tip's offset from the arm's line
                double along = dot(t, d);
                double[] off = {t[0] - along * d[0], t[1] - along * d[1], t[2] - along * d[2]};
                String what = "arm " + i + " elevation " + elevation;
                if (elevation >= 0) assertTrue(off[1] < -1, what + ": the tip curls up " + off[1]);
                else assertTrue(dot(off, towardsAxis(p, d)) > 0, what + ": the tip curls away from the axis");
            }
        }
    }

    @Test
    void theSuckerNormalTurnsSmoothlyFromHangingToRaised() {
        // sweeping an arm from straight down over the outside to straight up never flips its suckers
        for (int i = 0; i < 8; i++) {
            double[] p = pivot(i);
            double az = Math.atan2(p[0], -p[2]);
            for (double side : new double[]{0, 40, 90, -90, 150}) {
                double[] prev = null;
                for (double el = -89.5; el <= 89.5; el += 0.5) {
                    double a = az + Math.toRadians(side), r = Math.toRadians(el);
                    double[] d = {Math.sin(a) * Math.cos(r), Math.sin(r), -Math.cos(a) * Math.cos(r)};
                    double[] n = KrakenAim.suckerNormal(p[0], p[2], d);
                    if (prev != null) {
                        assertTrue(angleDeg(prev, n) < 3, "arm " + i + " side " + side + " elevation " + el + ": roll jumps " + angleDeg(prev, n));
                    }
                    prev = n;
                }
            }
        }
    }

    @Test
    void degenerateAimsStillGiveARotation() {
        for (int i = 0; i < 8; i++) {
            double[] p = pivot(i);
            // the sucker reference is parallel to the arm for arms pointing inwards 24.5 deg off vertical (r = -0.5 y^2)
            double y = Math.sqrt(2 * (Math.sqrt(2) - 1)), r = 0.5 * y * y / RING;
            for (double[] d : new double[][]{{p[0], 0, p[2]}, {-p[0], 0, -p[2]}, {0, 1, 0}, {0, -1, 0},
                    {-p[0] * r, y, -p[2] * r}, {-p[0] * r, -y, -p[2] * r}}) {
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
