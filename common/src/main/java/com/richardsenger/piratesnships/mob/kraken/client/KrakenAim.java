package com.richardsenger.piratesnships.mob.kraken.client;

/**
 * The aim of a kraken tentacle's first bone (K1c, sucker side GL1), pure maths without world access. Everything is in
 * GeckoLib's baked model frame in px (x flipped against the file, the kraken faces −z) relative to the bone's pivot;
 * {@link #toBoneFrame} takes a world offset there. At rest a tentacle points up (+y) with its sucker face towards the
 * body's vertical axis: the inner normal {@code n0 = −(pivot x, 0, pivot z)}, normalised. The aim turns that frame so
 * the tentacle points along the wanted direction {@code d} and the sucker face looks along {@link #suckerNormal}: down
 * for a raised or level arm (the tip curls down over its target), towards the axis for a hanging one. The rotation
 * maps the rest basis {@code (up, n0, up × n0)} onto {@code (d, n, d × n)} and is returned as GeckoLib's Euler angles:
 * a bone renders with {@code Rz(rotZ)·Ry(rotY)·Rx(rotX)} ({@code RenderUtil#rotateMatrixAroundBone}, JOML
 * {@code rotationZYX}), right-handed, radians, set with {@code GeoBone#setRotX/Y/Z} as they are: the baked frame is
 * the bone frame, so nothing is negated (only Minecraft angles and file values need the x/y flip).
 * <p>
 * K1c kept the suckers towards the axis in every pose. That frame maths was right (KrakenWorldPoseTest), but for an
 * arm raised to a deck the axis direction made perpendicular to the arm points up, so the suckers faced the sky and
 * the curled tip bent upwards (playtest 2026-10-07).
 */
public final class KrakenAim {

    /** Below this length the perpendicular inner direction is too short to use. */
    private static final double DEGENERATE = 1e-4;
    /** How far the sucker reference leans outwards per unit of the arm's upward component (see {@link #suckerNormal}). */
    static final double OUTWARD_TILT = 0.5;

    private KrakenAim() {
    }

    /** The rest inner normal of the tentacle rooted at {@code (pivotX, pivotZ)}: horizontal, towards the body's axis. */
    public static double[] restNormal(double pivotX, double pivotZ) {
        double r = Math.hypot(pivotX, pivotZ);
        if (r < 1e-9) return new double[]{0, 0, -1};
        return new double[]{-pivotX / r, 0, -pivotZ / r};
    }

    /**
     * The direction of a tentacle without a target: up, leaning {@code leanRad} outwards (away from the body's axis).
     */
    public static double[] restDirection(double pivotX, double pivotZ, double leanRad) {
        double[] in = restNormal(pivotX, pivotZ);
        double s = Math.sin(leanRad);
        return new double[]{-in[0] * s, Math.cos(leanRad), -in[2] * s};
    }

    /**
     * The wanted sucker-side normal for a tentacle along the unit direction {@code d} (GL1): world down tilted by
     * {@link #OUTWARD_TILT}{@code · d.y} outwards ({@code w = down − tilt · d.y · n0}), made perpendicular to
     * {@code d}. So a raised or level arm shows its suckers below and hooks its tip down over the target, a hanging
     * arm turns its suckers towards the body's axis, and the two meet without a jump; {@code w} is parallel to
     * {@code d} only for arms pointing inwards about 24° off vertical (through the head or under the body), where the
     * fallbacks (the axis, then forward, then up) take over.
     */
    public static double[] suckerNormal(double pivotX, double pivotZ, double[] d) {
        double[] in = restNormal(pivotX, pivotZ);
        double k = -OUTWARD_TILT * d[1];
        double[] n = perpendicular(new double[]{k * in[0], -1, k * in[2]}, d);
        if (n == null) n = perpendicular(in, d);
        if (n == null) n = perpendicular(new double[]{0, 0, -1}, d);
        if (n == null) n = perpendicular(new double[]{0, 1, 0}, d);
        return n;
    }

    /**
     * GeckoLib's {@code {rotX, rotY, rotZ}} that turn the tentacle rooted at {@code (pivotX, pivotZ)} from its rest pose
     * (up, sucker face towards the axis) to point along {@code dir} (any length but zero) with its sucker face along
     * {@link #suckerNormal}.
     */
    public static float[] aim(double pivotX, double pivotZ, double[] dir) {
        double[] d = normalize(dir);
        double[] n = suckerNormal(pivotX, pivotZ, d);
        double[] b = cross(d, n);
        double[] u0 = {0, 1, 0};
        double[] n0 = restNormal(pivotX, pivotZ);
        double[] b0 = cross(u0, n0);
        // R = [d n b] · [u0 n0 b0]^T
        double[][] m = new double[3][3];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                m[r][c] = d[r] * u0[c] + n[r] * n0[c] + b[r] * b0[c];
            }
        }
        return eulerZYX(m);
    }

    /**
     * A world offset from the entity's position (any unit) in the entity's bone frame: the inverse of
     * {@code GeoEntityRenderer#applyRotations}' {@code Axis.YP.rotationDegrees(180 - bodyYaw)}, nothing else. GeckoLib
     * draws the baked model without a mirror matrix (the file's x flip is baked into the pivots and cubes), so the
     * result is in the frame of the baked pivots and of {@link #aim}.
     */
    public static double[] toBoneFrame(double x, double y, double z, double bodyYawDeg) {
        double t = Math.toRadians(180.0 - bodyYawDeg), c = Math.cos(t), s = Math.sin(t);
        // Ry(t)^-1 = Ry(-t): (x, z) -> (x cos t - z sin t, x sin t + z cos t)
        return new double[]{x * c - z * s, y, x * s + z * c};
    }

    /** A first segment's pose: GeckoLib's {@code {rotX, rotY, rotZ}} and its y stretch. */
    public record Reach(float[] rot, float stretch) {
    }

    /**
     * The pose of the first segment pivoted at {@code pivot} (baked px) so the arm, {@code length} px at rest, ends at
     * the world offset {@code target} (blocks, from the entity's position) of an entity turned to {@code bodyYawDeg};
     * {@code null} when the target sits on the pivot.
     */
    public static Reach reach(double[] pivot, double[] target, double bodyYawDeg, double length, double minStretch, double maxStretch) {
        double[] t = toBoneFrame(target[0], target[1], target[2], bodyYawDeg);
        double[] d = {t[0] * 16 - pivot[0], t[1] * 16 - pivot[1], t[2] * 16 - pivot[2]};
        double len = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        if (len < 1e-3) return null;
        return new Reach(aim(pivot[0], pivot[2], d), (float) Math.max(minStretch, Math.min(maxStretch, len / length)));
    }

    /** {@code {x, y, z}} with {@code m = Rz(z)·Ry(y)·Rx(x)}. */
    static float[] eulerZYX(double[][] m) {
        double sy = Math.max(-1, Math.min(1, -m[2][0]));
        double y = Math.asin(sy);
        double x, z;
        if (Math.abs(sy) < 0.999999) {
            x = Math.atan2(m[2][1], m[2][2]);
            z = Math.atan2(m[1][0], m[0][0]);
        } else {
            // gimbal lock: only x - z (sy = 1) or x + z (sy = -1) is defined; put it all in z
            x = 0;
            z = Math.atan2(-m[0][1], m[1][1]);
        }
        return new float[]{(float) x, (float) y, (float) z};
    }

    /** {@code Rz(e[2])·Ry(e[1])·Rx(e[0])·v}: how GeckoLib turns a vector of the bone. */
    public static double[] rotate(float[] e, double[] v) {
        double cx = Math.cos(e[0]), sx = Math.sin(e[0]);
        double cy = Math.cos(e[1]), sy = Math.sin(e[1]);
        double cz = Math.cos(e[2]), sz = Math.sin(e[2]);
        double x = v[0], y = v[1] * cx - v[2] * sx, z = v[1] * sx + v[2] * cx;
        double x2 = x * cy + z * sy, z2 = -x * sy + z * cy;
        return new double[]{x2 * cz - y * sz, x2 * sz + y * cz, z2};
    }

    private static double[] perpendicular(double[] w, double[] d) {
        double k = w[0] * d[0] + w[1] * d[1] + w[2] * d[2];
        double[] p = {w[0] - k * d[0], w[1] - k * d[1], w[2] - k * d[2]};
        double len = Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
        return len < DEGENERATE ? null : new double[]{p[0] / len, p[1] / len, p[2] / len};
    }

    static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static double[] normalize(double[] v) {
        double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len < 1e-12) throw new IllegalArgumentException("zero direction");
        return new double[]{v[0] / len, v[1] / len, v[2] / len};
    }
}
