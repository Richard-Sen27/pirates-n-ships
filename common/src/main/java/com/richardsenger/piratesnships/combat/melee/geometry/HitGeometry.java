package com.richardsenger.piratesnships.combat.melee.geometry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Pure hit geometry for melee attacks. Works in whatever frame the caller uses (world or ship-relative), as long as
 * eye, look and boxes are in the same frame.
 *
 * <p>Decisions (all tested):
 * <ul>
 *   <li>A zero look vector hits nothing. A slash needs a horizontal look component, so looking straight up or down
 *       slashes nothing (a thrust still works).</li>
 *   <li>A slash hits a box if any part of its horizontal footprint lies inside the pie slice (reach, arc) and its
 *       vertical extent overlaps the slash band; boxes partly inside the arc count.</li>
 *   <li>An attacker standing inside a target's box always hits it (slash: footprint contains the eye; thrust: t = 0).</li>
 *   <li>A thrust is a ray of the given radius (approximated by inflating each box by the thickness) and hits the
 *       box with the smallest entry distance.</li>
 * </ul>
 */
public final class HitGeometry {

    private static final double EPS = 1.0e-9;

    private HitGeometry() {
    }

    /** A candidate: any key (entity id, entity, ...) with its box. */
    public record Target<K>(K key, Box box) {
    }

    /** Every target hit by a slash, nearest first. */
    public static <K> List<K> slash(Vec eye, Vec look, double reach, double arcDegrees, double verticalTolerance,
                                    List<Target<K>> targets) {
        Vec f = look.horizontal();
        if (f.isZero() || reach <= 0) return List.of();
        double ly = look.normalize().y();
        double bandLo = eye.y() + Math.min(0, ly * reach) - verticalTolerance;
        double bandHi = eye.y() + Math.max(0, ly * reach) + verticalTolerance;
        double half = Math.toRadians(Math.min(180.0, Math.max(0.0, arcDegrees))) / 2;
        List<Target<K>> hits = new ArrayList<>();
        for (Target<K> t : targets) {
            Box b = t.box();
            if (b.maxY() < bandLo || b.minY() > bandHi) continue;
            if (pieIntersectsRect(eye.x(), eye.z(), f.x(), f.z(), reach, half, b)) hits.add(t);
        }
        hits.sort(Comparator.comparingDouble(t -> t.box().closestPoint(eye).sub(eye).length()));
        return hits.stream().map(Target::key).toList();
    }

    /** The first target along a thrust ray, if any. */
    public static <K> Optional<K> thrust(Vec eye, Vec look, double reach, double thickness, List<Target<K>> targets) {
        Vec dir = look.normalize();
        if (dir.isZero() || reach <= 0) return Optional.empty();
        K best = null;
        double bestT = Double.MAX_VALUE;
        for (Target<K> t : targets) {
            double entry = rayEntry(eye, dir, reach, t.box().inflate(Math.max(0, thickness)));
            if (entry >= 0 && entry < bestT) {
                bestT = entry;
                best = t.key();
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Whether {@code source} is in front of a defender: inside the horizontal arc (full angle) around the defender's
     * look. With no usable direction (source at the eye, or a vertical look) the hit counts as frontal.
     */
    public static boolean inFront(Vec defenderEye, Vec defenderLook, Vec source, double arcDegrees) {
        if (arcDegrees >= 360.0) return true;
        Vec f = defenderLook.horizontal();
        Vec d = source.sub(defenderEye).horizontal();
        if (f.isZero() || d.isZero()) return true;
        return f.dot(d) >= Math.cos(Math.toRadians(arcDegrees) / 2) - EPS;
    }

    /** Entry distance of a ray into a box (0 if the origin is inside), or -1 if it misses within {@code max}. */
    static double rayEntry(Vec o, Vec d, double max, Box b) {
        double tMin = 0, tMax = max;
        double[] os = {o.x(), o.y(), o.z()};
        double[] ds = {d.x(), d.y(), d.z()};
        double[] lo = {b.minX(), b.minY(), b.minZ()};
        double[] hi = {b.maxX(), b.maxY(), b.maxZ()};
        for (int a = 0; a < 3; a++) {
            if (Math.abs(ds[a]) < EPS) {
                if (os[a] < lo[a] || os[a] > hi[a]) return -1;
            } else {
                double t1 = (lo[a] - os[a]) / ds[a], t2 = (hi[a] - os[a]) / ds[a];
                tMin = Math.max(tMin, Math.min(t1, t2));
                tMax = Math.min(tMax, Math.max(t1, t2));
                if (tMin > tMax) return -1;
            }
        }
        return tMin;
    }

    /** 2D: does the pie slice (apex, unit forward, radius, half angle ≤ 90°) intersect the box footprint? */
    static boolean pieIntersectsRect(double ex, double ez, double fx, double fz, double r, double half, Box b) {
        // apex inside the footprint
        if (ex >= b.minX() && ex <= b.maxX() && ez >= b.minZ() && ez <= b.maxZ()) return true;
        double cos = Math.cos(half);
        // a corner inside the pie
        double[][] corners = {{b.minX(), b.minZ()}, {b.minX(), b.maxZ()}, {b.maxX(), b.minZ()}, {b.maxX(), b.maxZ()}};
        for (double[] c : corners) if (inPie(c[0] - ex, c[1] - ez, fx, fz, r, cos)) return true;
        // the footprint point nearest to the apex inside the pie (the arc crosses an edge)
        double cx = Math.max(b.minX(), Math.min(ex, b.maxX())), cz = Math.max(b.minZ(), Math.min(ez, b.maxZ()));
        if (inPie(cx - ex, cz - ez, fx, fz, r, cos)) return true;
        // a radial edge of the pie crosses the footprint
        double sin = Math.sin(half);
        for (int sign = -1; sign <= 1; sign += 2) {
            double dx = fx * cos - sign * fz * sin, dz = sign * fx * sin + fz * cos;
            Box flat = new Box(b.minX(), -1, b.minZ(), b.maxX(), 1, b.maxZ());
            if (rayEntry(new Vec(ex, 0, ez), new Vec(dx, 0, dz), r, flat) >= 0) return true;
        }
        return false;
    }

    private static boolean inPie(double dx, double dz, double fx, double fz, double r, double cos) {
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len > r + EPS) return false;
        if (len < EPS) return true;
        return (dx * fx + dz * fz) / len >= cos - EPS;
    }
}
