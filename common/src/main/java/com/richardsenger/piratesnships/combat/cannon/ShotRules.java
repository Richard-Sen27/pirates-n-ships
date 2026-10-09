package com.richardsenger.piratesnships.combat.cannon;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Pure rules of the three cannon shots (CAN3, docs/design.md §8.2): which shot a load takes, the order a crew reaches for
 * them in a mixed locker, the start speed of each, the cone of a grapeshot volley, the spread of a chain shot and the
 * distance of its path to a rope. No world access, unit tested ({@code ShotRulesTest}).
 */
public final class ShotRules {

    /** Golden angle [radians]: consecutive pellets of a volley turn by it, so the cone fills evenly. */
    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));

    private ShotRules() {
    }

    // ---- loading --------------------------------------------------------------------------------------------------

    /** Whether a shot may go into a cannon: the ball always, chain shot and grapeshot while their server toggles are on. */
    public static boolean allowed(ShotKind kind, boolean chainEnabled, boolean grapeEnabled) {
        return switch (kind) {
            case BALL -> true;
            case CHAIN -> chainEnabled;
            case GRAPE -> grapeEnabled;
        };
    }

    /**
     * The order in which a crew loading on the captain's "Load!" (or after its own shot) reaches for shot: {@code first}
     * (the {@code cannons.crew.load_preference}), then the ball, chain shot and grapeshot; only the {@code allowed} ones,
     * each once.
     */
    public static List<ShotKind> crewPreference(ShotKind first, Predicate<ShotKind> allowed) {
        return distinct(allowed, first, ShotKind.BALL, ShotKind.CHAIN, ShotKind.GRAPE);
    }

    /**
     * The order in which a gun crew firing by itself (WS4a) reaches for shot: the shot its gunnery wants
     * ({@code combat.cannon.npc.ShotChoice}), then the ball, then chain shot. Grapeshot only when it was chosen: it
     * falls short of a target the rule did not pick it for.
     */
    public static List<ShotKind> gunneryPreference(ShotKind wanted, Predicate<ShotKind> allowed) {
        return distinct(allowed, wanted, ShotKind.BALL, ShotKind.CHAIN);
    }

    private static List<ShotKind> distinct(Predicate<ShotKind> allowed, ShotKind... kinds) {
        List<ShotKind> out = new ArrayList<>(kinds.length);
        for (ShotKind k : kinds) {
            if (!out.contains(k) && allowed.test(k)) out.add(k);
        }
        return out;
    }

    /** The first kind of {@code preference} the supply holds ({@code available}), or null when it holds none. */
    public static @Nullable ShotKind pick(List<ShotKind> preference, Predicate<ShotKind> available) {
        for (ShotKind k : preference) {
            if (available.test(k)) return k;
        }
        return null;
    }

    // ---- firing ---------------------------------------------------------------------------------------------------

    /**
     * The start speed factor of a shot against the cannon's {@code cannons.muzzle_velocity}: 1 for the ball, the
     * configured range factor for chain shot (0.6) and grapeshot (0.4). The reach of a level shot scales with it.
     */
    public static double velocityFactor(ShotKind kind, double chainRangeFactor, double grapeRangeFactor) {
        return switch (kind) {
            case BALL -> 1.0;
            case CHAIN -> chainRangeFactor;
            case GRAPE -> grapeRangeFactor;
        };
    }

    /**
     * The directions of a grapeshot volley of {@code n} pellets around {@code axis} (any length): a sunflower pattern
     * inside the cone of {@code halfAngleDegrees}, pellet {@code i} at {@code halfAngle × √((i + 0.5) / n)} off the axis
     * and turned by the golden angle from the one before, the whole pattern turned by {@code phase} [radians]. Unit
     * vectors; none further off the axis than the half angle.
     */
    public static List<Vec3> cone(Vec3 axis, int n, double halfAngleDegrees, double phase) {
        List<Vec3> out = new ArrayList<>(Math.max(0, n));
        if (n <= 0) return out;
        Vec3 d = axis.lengthSqr() < 1.0e-12 ? new Vec3(1, 0, 0) : axis.normalize();
        Vec3[] basis = basis(d);
        double half = Math.toRadians(Math.max(0.0, Math.min(89.0, halfAngleDegrees)));
        for (int i = 0; i < n; i++) {
            double off = half * Math.sqrt((i + 0.5) / n);
            double turn = phase + i * GOLDEN_ANGLE;
            out.add(tilt(d, basis, off, turn));
        }
        return out;
    }

    /**
     * {@code axis} (any length) turned off itself by up to {@code maxDegrees}: by {@code maxDegrees × √u1} toward the
     * direction {@code 2π × u2} around it ({@code u1}, {@code u2} uniform in [0, 1), so the spread fills a disc evenly).
     * A unit vector; the chain shot's spread.
     */
    public static Vec3 deviate(Vec3 axis, double maxDegrees, double u1, double u2) {
        Vec3 d = axis.lengthSqr() < 1.0e-12 ? new Vec3(1, 0, 0) : axis.normalize();
        double off = Math.toRadians(Math.max(0.0, Math.min(89.0, maxDegrees))) * Math.sqrt(Math.max(0.0, Math.min(1.0, u1)));
        return tilt(d, basis(d), off, 2.0 * Math.PI * u2);
    }

    /** Two unit vectors at right angles to the unit vector {@code d} and to each other. */
    private static Vec3[] basis(Vec3 d) {
        Vec3 helper = Math.abs(d.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 a = d.cross(helper).normalize();
        Vec3 b = d.cross(a).normalize();
        return new Vec3[]{a, b};
    }

    private static Vec3 tilt(Vec3 d, Vec3[] basis, double off, double turn) {
        double s = Math.sin(off);
        return d.scale(Math.cos(off)).add(basis[0].scale(s * Math.cos(turn))).add(basis[1].scale(s * Math.sin(turn))).normalize();
    }

    /** The angle between two directions [degrees]; 0 when either is zero. */
    public static double angleDegrees(Vec3 a, Vec3 b) {
        double l = a.length() * b.length();
        if (l < 1.0e-12) return 0.0;
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, a.dot(b) / l))));
    }

    // ---- rigging ---------------------------------------------------------------------------------------------------

    /**
     * The least distance between the segments {@code p0–p1} (the shot's step) and {@code q0–q1} (a rope), in one frame.
     * Degenerate segments count as points.
     */
    public static double segmentDistance(Vec3 p0, Vec3 p1, Vec3 q0, Vec3 q1) {
        Vec3 d1 = p1.subtract(p0);
        Vec3 d2 = q1.subtract(q0);
        Vec3 r = p0.subtract(q0);
        double a = d1.dot(d1), e = d2.dot(d2), f = d2.dot(r);
        if (a < 1.0e-12 && e < 1.0e-12) {
            return p0.distanceTo(q0);
        }
        double s, t;
        if (a < 1.0e-12) {
            s = 0.0;
            t = clamp01(f / e);
        } else {
            double c = d1.dot(r);
            if (e < 1.0e-12) {
                t = 0.0;
                s = clamp01(-c / a);
            } else {
                double b = d1.dot(d2);
                double denom = a * e - b * b;
                s = denom > 1.0e-12 ? clamp01((b * f - c * e) / denom) : 0.0;
                t = (b * s + f) / e;
                if (t < 0.0) {
                    t = 0.0;
                    s = clamp01(-c / a);
                } else if (t > 1.0) {
                    t = 1.0;
                    s = clamp01((b - c) / a);
                }
            }
        }
        return p0.add(d1.scale(s)).distanceTo(q0.add(d2.scale(t)));
    }

    private static double clamp01(double x) {
        return Math.max(0.0, Math.min(1.0, x));
    }
}
