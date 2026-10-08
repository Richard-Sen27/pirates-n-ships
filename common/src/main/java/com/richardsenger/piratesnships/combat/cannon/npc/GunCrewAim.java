package com.richardsenger.piratesnships.combat.cannon.npc;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Pure aiming of a gun crew (WS4a, docs/design.md §8.2, plan {@code docs/plans/world-simulation.md} "WS4a"): which of
 * the cannon's elevation steps sends a ball into a solid block of a target ship closest to the aim point, whether the
 * target lies in the gun's arc and in range, and whether that best shot would hit at all. No world access; the caller
 * turns the cannon's barrel into world space ({@link #worldShot}) and reads the target's bounds, velocity and blocks.
 *
 * <p>WS4a-b: a shot counts as a hit only when its path crosses a solid block of the target ({@link Target#solid}), not
 * merely the target's bounds. A gun fires every ball along the same line, so each hit that breaks a block opens a
 * tunnel along it; a crew that aimed at the bounds alone kept firing through its own holes (measured: once a near and a
 * far wall block on the line were broken, every further ball flew through the hull).
 *
 * <p>The ball is flown with vanilla's thrown-projectile step ({@code ThrowableProjectile#tick}, 1.21.1): each tick the
 * ball moves by its velocity, then the velocity is scaled by the air drag (0.99) and gravity is subtracted. The target
 * moves at its own constant velocity, so the flight is computed relative to the target: the lead comes out of the
 * simulation and is not a separate guess.
 *
 * <p>Units: positions in blocks (world space), velocities in blocks per tick ({@code ShipBody#linearVelocity} m/s ÷ 20).
 */
public final class GunCrewAim {

    /** Vanilla's air drag of a thrown projectile per tick ({@code ThrowableProjectile#tick}). */
    public static final double AIR_DRAG = 0.99;

    private GunCrewAim() {
    }

    /** One elevation step of the gun in world space: where the ball leaves, which way, and the muzzle's own velocity. */
    public record Shot(Vec3 muzzle, Vec3 direction, Vec3 carrierPerTick) {
    }

    /** The ball: start speed relative to the gun (blocks/tick), gravity (blocks/tick²), and how long it flies. */
    public record Ballistics(double muzzleSpeed, double gravity, int lifetimeTicks) {
    }

    /**
     * A target ship: its world bounds, velocity (blocks/tick), and whether a world point (at the target's pose now) lies
     * in one of its solid blocks, as a ball's collision clip sees them.
     */
    public record Target(AABB bounds, Vec3 velocityPerTick, Predicate<Vec3> solid) {

        /** A target solid all through its bounds (a box with no holes). */
        public Target(AABB bounds, Vec3 velocityPerTick) {
            this(bounds, velocityPerTick, bounds::contains);
        }
    }

    /**
     * Rules of the crew.
     *
     * @param arcDegrees      half-width of the gun's horizontal arc: a target further off the barrel's heading is not shot at
     * @param rangeBlocks     largest horizontal distance from the muzzle to the aim point
     * @param aimHeight       where on the target's height the crew aims (0 = bottom of the bounds, 1 = top)
     */
    public record Rules(double arcDegrees, double rangeBlocks, double aimHeight) {
    }

    /**
     * The crew's answer for one target.
     *
     * @param bestStep   of the elevation steps whose ball strikes a solid block of the target, the one passing closest to
     *                   the aim point; when no step strikes one, the step passing closest to the aim point
     * @param miss       that closest distance, in blocks
     * @param hits       the best step's ball strikes a solid block of the target
     * @param inArc      the aim point (led by the target's motion) lies within the arc
     * @param inRange    the aim point lies within the range
     * @param offDegrees horizontal angle between the barrel and the led aim point
     * @param distance   horizontal distance from the best step's muzzle to the aim point
     */
    public record Solution(int bestStep, double miss, boolean hits, boolean inArc, boolean inRange, double offDegrees, double distance) {

        /** The target can be engaged at all: in the arc and in range. */
        public boolean engageable() {
            return inArc && inRange;
        }

        /** The crew fires: the target is in the arc and in range and the best elevation hits it. */
        public boolean fires() {
            return engageable() && hits;
        }
    }

    // ---- world barrel -----------------------------------------------------------------------------------------------

    /**
     * A barrel given in the ship's plot frame ({@code CannonService.barrel}) in world space: the muzzle through the
     * ship's pose ({@code toWorld}), the direction through its orientation, plus the muzzle's velocity
     * ({@code ShipBody#velocityAt}, m/s) per tick. Off a ship pass the identity, a unit quaternion and zero.
     */
    public static Shot worldShot(Vec3 plotMuzzle, Vec3 plotDirection, UnaryOperator<Vec3> toWorld, Quaterniondc orientation,
                                 Vec3 carrierMetresPerSecond) {
        Vector3d d = orientation.transform(new Vector3d(plotDirection.x, plotDirection.y, plotDirection.z));
        return new Shot(toWorld.apply(plotMuzzle), new Vec3(d.x, d.y, d.z).normalize(), carrierMetresPerSecond.scale(1.0 / 20.0));
    }

    // ---- aiming -----------------------------------------------------------------------------------------------------

    /** The point the crew aims at: the bounds' horizontal centre at {@code aimHeight} of its height. */
    public static Vec3 aimPoint(AABB bounds, double aimHeight) {
        double h = Math.max(0.0, Math.min(1.0, aimHeight));
        return new Vec3((bounds.minX + bounds.maxX) / 2, bounds.minY + (bounds.maxY - bounds.minY) * h, (bounds.minZ + bounds.maxZ) / 2);
    }

    /**
     * Horizontal angle in degrees (0..180) between {@code direction} and the way from {@code from} to {@code to}; 0 when
     * either has no horizontal part.
     */
    public static double horizontalAngleDegrees(Vec3 direction, Vec3 from, Vec3 to) {
        double ax = direction.x, az = direction.z;
        double bx = to.x - from.x, bz = to.z - from.z;
        double la = Math.sqrt(ax * ax + az * az), lb = Math.sqrt(bx * bx + bz * bz);
        if (la < 1.0e-9 || lb < 1.0e-9) return 0.0;
        double cos = (ax * bx + az * bz) / (la * lb);
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cos))));
    }

    /** One step from {@code current} toward {@code best}: +1 (raise), −1 (lower) or 0 (aimed). */
    public static int stepToward(int current, int best) {
        return Integer.compare(best, current);
    }

    /** Ticks the ball is flown for a target {@code distance} blocks away: three times the straight flight, within the lifetime. */
    static int flightTicks(double distance, Ballistics b) {
        int need = (int) Math.ceil(3.0 * distance / Math.max(0.05, b.muzzleSpeed())) + 10;
        return Math.max(1, Math.min(b.lifetimeTicks(), need));
    }

    /**
     * The ball's path relative to the target, one point per tick from the muzzle: vanilla's step (move, then drag and
     * gravity), minus the target's own motion over the same ticks.
     */
    public static List<Vec3> relativePath(Shot shot, Vec3 targetVelocityPerTick, Ballistics b, int ticks) {
        List<Vec3> out = new ArrayList<>(ticks + 1);
        Vec3 p = shot.muzzle();
        Vec3 v = shot.direction().normalize().scale(b.muzzleSpeed()).add(shot.carrierPerTick());
        out.add(p);
        for (int n = 1; n <= ticks; n++) {
            p = p.add(v);
            v = v.scale(AIR_DRAG).subtract(0, b.gravity(), 0);
            out.add(p.subtract(targetVelocityPerTick.scale(n)));
        }
        return out;
    }

    /** Closest distance of a path (straight segments between its points) to {@code point}. */
    public static double closest(List<Vec3> path, Vec3 point) {
        double best = path.isEmpty() ? Double.MAX_VALUE : path.get(0).distanceTo(point);
        for (int i = 1; i < path.size(); i++) {
            Vec3 a = path.get(i - 1), d = path.get(i).subtract(a);
            double l2 = d.lengthSqr();
            double t = l2 < 1.0e-12 ? 0.0 : Math.max(0.0, Math.min(1.0, point.subtract(a).dot(d) / l2));
            best = Math.min(best, a.add(d.scale(t)).distanceTo(point));
        }
        return best;
    }

    /** Sampling distance along a path inside the target's bounds when looking for a solid block, in blocks. */
    static final double SAMPLE = 0.05;

    /**
     * The first point of a path (relative to the target, {@link #relativePath}) inside a solid block of {@code target}:
     * each segment is clipped to the bounds and walked in steps of {@link #SAMPLE}. Empty when the ball crosses no solid
     * block, e.g. when it passes the target or flies through a hole.
     */
    public static Optional<Vec3> firstSolid(List<Vec3> path, Target target) {
        AABB box = target.bounds();
        for (int i = 1; i < path.size(); i++) {
            Vec3 a = path.get(i - 1), c = path.get(i);
            Vec3 from = box.contains(a) ? a : box.clip(a, c).orElse(null);
            if (from == null) continue;
            Vec3 to = box.contains(c) ? c : box.clip(c, a).orElse(from);
            Vec3 d = to.subtract(from);
            int n = Math.max(1, (int) Math.ceil(d.length() / SAMPLE));
            for (int k = 0; k <= n; k++) {
                Vec3 p = from.add(d.scale((double) k / n));
                if (target.solid().test(p)) return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    /** Whether a path passes through {@code box}. */
    public static boolean passesThrough(List<Vec3> path, AABB box) {
        for (int i = 1; i < path.size(); i++) {
            Vec3 a = path.get(i - 1), c = path.get(i);
            if (box.contains(a) || box.contains(c) || box.clip(a, c).isPresent()) return true;
        }
        return false;
    }

    /**
     * Solves one target for a gun whose elevation steps are {@code steps} (index = elevation step, lowest first): of the
     * steps whose ball strikes a solid block of the target ({@link #firstSolid}), the one passing closest to the aim
     * point (else the closest step, which does not hit), and whether the aim point, led by the target's motion over the
     * straight flight time, lies in the arc of the step's barrel and within range.
     */
    public static Solution solve(List<Shot> steps, Target target, Ballistics b, Rules rules) {
        if (steps.isEmpty()) throw new IllegalArgumentException("a gun has at least one elevation step");
        Vec3 aim = aimPoint(target.bounds(), rules.aimHeight());
        int best = 0;
        double bestMiss = Double.MAX_VALUE;
        boolean bestHits = false;
        for (int i = 0; i < steps.size(); i++) {
            Shot s = steps.get(i);
            List<Vec3> path = relativePath(s, target.velocityPerTick(), b, flightTicks(horizontal(s.muzzle(), aim), b));
            double miss = closest(path, aim);
            boolean hits = firstSolid(path, target).isPresent();
            // a striking step beats any that does not; among equals the one closer to the aim point
            if (hits && !bestHits || hits == bestHits && miss < bestMiss) {
                bestMiss = miss;
                best = i;
                bestHits = hits;
            }
        }
        Shot s = steps.get(best);
        double distance = horizontal(s.muzzle(), aim);
        double ticks = distance / Math.max(0.05, s.direction().normalize().scale(b.muzzleSpeed()).add(s.carrierPerTick()).horizontalDistance());
        Vec3 led = aim.add(target.velocityPerTick().scale(ticks));
        double off = horizontalAngleDegrees(s.direction(), s.muzzle(), led);
        return new Solution(best, bestMiss, bestHits, off <= rules.arcDegrees(), distance <= rules.rangeBlocks(), off, distance);
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = b.x - a.x, dz = b.z - a.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
