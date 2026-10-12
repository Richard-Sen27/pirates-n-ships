package com.richardsenger.piratesnships.combat.grapple;

import net.minecraft.world.phys.Vec3;

/**
 * Pure rules of hanging on one's own hand-held grappling rope (GR7, the human, 2026-10-12: "the rope … should behave like
 * an actual rope and bend to gravity … it should also be able to swing a bit, so liana-like handling is possible").
 * No world access; tested in JUnit ({@code RopeSwingTest}).
 *
 * <p><b>The rope's length</b> ({@link #track}): while the thrower stands on the ground the rope pays out and is taken
 * in freely (no length, NaN). Once they are off the ground with the latched hook above their hand, the rope holds at the
 * distance it had then; landing again with the rope slack frees it. A pull ({@link #pull}) shortens it by
 * {@code grapple.rope_climb_step}, from the rope's length or the hand's distance to the hook, whichever is shorter.
 *
 * <p><b>The pendulum</b> ({@link #step}): a per-tick correction of the velocity vanilla has just computed for the next
 * tick, in the hook's frame (the hand relative to the hook, the velocity relative to the hook's motion). Where the next
 * position would lie beyond the rope's length, the hand is turned back onto the sphere around the hook (the rope's
 * tension), keeping its speed along the swing: so gravity swings the player below the hook and the swing's energy never
 * grows. A rope just shortened by a pull draws the hand in at up to {@code rope_climb_speed} per tick. On a taut rope
 * vanilla's air drag (0.91 across, 0.98 up and down) is undone, so the swing dies out by {@code swing_damping} alone;
 * walking input adds {@code swing_push} along the swing.
 */
public final class RopeSwing {

    /** Vanilla's horizontal air drag on a living entity per tick ({@code LivingEntity#travel}). */
    public static final double AIR_DRAG_H = 0.91;
    /** Vanilla's vertical air drag on a living entity per tick. */
    public static final double AIR_DRAG_V = 0.98;
    /** A rope at most this much [blocks] beyond its length counts as at its length (no draw-in, the swing's speed kept). */
    public static final double TAUT_TOLERANCE = 0.05;
    /** Shortest rope a pull leaves [blocks]: the hand right under the hook. */
    public static final double MIN_LENGTH = 0.8;

    /** The swing's values: walking push and damping per tick, and the speed at which a shortened rope draws in. */
    public record Params(double push, double damping, double climbSpeed) {
    }

    private RopeSwing() {
    }

    /**
     * The rope's length for the next tick (NaN: free, the rope pays out).
     *
     * @param length    the length so far (NaN: free)
     * @param distance  the hand's distance to the hook now
     * @param holds     the player holds the rope: the toggle, a latched hook with the near end in their hand, the main
     *                  hand empty or holding a hook, not sneaking, not riding, not flying
     * @param onGround  the player stands on the ground (or a deck)
     * @param hookAbove the hook is above the player's hand
     * @param maxLength the rope's full length (it never holds longer)
     */
    public static double track(double length, double distance, boolean holds, boolean onGround, boolean hookAbove,
                               double maxLength) {
        if (!holds) {
            return Double.NaN;
        }
        if (Double.isNaN(length)) {
            return !onGround && hookAbove ? Math.min(distance, maxLength) : Double.NaN;
        }
        if (onGround && distance <= length + TAUT_TOLERANCE) {
            return Double.NaN; // landed with the rope slack: it pays out again
        }
        return Math.min(length, maxLength);
    }

    /** Whether the rope holds at a length ({@link #track} gave one). */
    public static boolean holding(double length) {
        return !Double.isNaN(length);
    }

    /**
     * The rope's length after one pull: {@code step} shorter than the length, or than the hand's distance to the hook when
     * that is shorter (a slack or free rope is taken in first), never below {@link #MIN_LENGTH}.
     */
    public static double pull(double length, double distance, double step) {
        double from = Double.isNaN(length) ? distance : Math.min(length, distance);
        return Math.max(MIN_LENGTH, from - step);
    }

    /**
     * Whether a pull from {@code length}/{@code distance} by {@code step} reaches the hook: the hand would come within
     * {@code arrive} of it, so the climber is put on the standing spot at the hook instead.
     */
    public static boolean arrives(double length, double distance, double step, double arrive) {
        double from = Double.isNaN(length) ? distance : Math.min(length, distance);
        return from - step <= arrive;
    }

    /**
     * The velocity for the next tick of a player hanging on the rope.
     *
     * @param rel      the hand relative to the hook now (world axes) [blocks]
     * @param vel      the velocity vanilla computed for the next tick (world) [blocks/tick]
     * @param pivotVel the hook's own velocity (world; zero on land or when Sable carries the player with the ship)
     * @param length   the rope's length (NaN: free, {@code vel} is returned)
     * @param wish     the walking input as a horizontal world direction (length up to 1; zero without input)
     * @param undoDrag whether vanilla's air drag is in {@code vel} and is undone on a taut rope (not in water)
     * @return the corrected velocity (world)
     */
    public static Vec3 step(Vec3 rel, Vec3 vel, Vec3 pivotVel, double length, Vec3 wish, boolean undoDrag, Params p) {
        double d = rel.length();
        if (Double.isNaN(length) || d < 1.0e-6) {
            return vel;
        }
        boolean drawIn = d > length + TAUT_TOLERANCE;
        double limit = drawIn ? Math.max(length, d - p.climbSpeed()) : length;
        Vec3 v = vel.subtract(pivotVel);
        Vec3 next = rel.add(v);
        if (next.length() <= limit) {
            return vel; // slack: the rope does not pull
        }
        if (undoDrag) {
            v = new Vec3(vel.x / AIR_DRAG_H, vel.y / AIR_DRAG_V, vel.z / AIR_DRAG_H).subtract(pivotVel);
            next = rel.add(v);
        }
        if (next.length() > limit) {
            Vec3 chord = next.scale(limit / next.length()).subtract(rel);
            if (drawIn || chord.lengthSqr() < 1.0e-12) {
                v = chord; // a shortened rope draws the hand in
            } else {
                // the rope's tension: no motion away from the hook; the speed along the swing is kept on the chord
                Vec3 u0 = rel.scale(1.0 / d);
                double out = v.dot(u0);
                Vec3 along = out > 0 ? v.subtract(u0.scale(out)) : v;
                v = chord.scale(along.length() / chord.length());
            }
        }
        Vec3 at = rel.add(v);
        double r = at.length();
        if (r < 1.0e-6) {
            return v.add(pivotVel);
        }
        Vec3 u = at.scale(1.0 / r);
        double radial = v.dot(u);
        Vec3 swing = v.subtract(u.scale(radial)).scale(1.0 - p.damping());
        Vec3 push = wish.subtract(u.scale(wish.dot(u))).scale(p.push());
        return swing.add(push).add(u.scale(radial)).add(pivotVel);
    }

    /**
     * The walking input as a horizontal world direction: vanilla's {@code xxa} (strafe, left positive) and {@code zza}
     * (forward) turned by the yaw {@code yRotDegrees}, as {@code Entity#moveRelative} does; length at most 1.
     */
    public static Vec3 wish(float xxa, float zza, float yRotDegrees) {
        double len = Math.hypot(xxa, zza);
        if (len < 1.0e-6) {
            return Vec3.ZERO;
        }
        double s = len > 1.0 ? 1.0 / len : 1.0;
        double x = xxa * s;
        double z = zza * s;
        double rad = Math.toRadians(yRotDegrees);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        return new Vec3(x * cos - z * sin, 0.0, z * cos + x * sin);
    }
}
