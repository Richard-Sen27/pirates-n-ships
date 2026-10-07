package com.richardsenger.piratesnships.combat.grapple;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Rules of sliding along a latched grappling rope (GR2, docs/design.md §8.3): where on the rope a point or a look ray
 * is, which way and how fast a rider slides, and when it arrives. Pure maths without world access; the rope is the
 * straight segment from its near end {@code a} (parameter 0) to the hook {@code b} (parameter 1), both in world space
 * and recomputed every tick by the caller, so a rider follows two moving ships.
 *
 * <p><b>The slide</b>: a rider always moves toward the lower end, never uphill. It starts at {@code slide_speed} and
 * gains {@code slide_gravity × slope} every tick (slope = height difference over rope length, the sine of the rope's
 * angle), up to {@code slide_max_speed}. A rope whose slope is below {@link #LEVEL_SLOPE} counts as level: the rider
 * crawls toward the hook at {@code slide_min_speed}. If the lower end swaps (a ship heels or the hook end climbs), the
 * rider turns and starts again from {@code slide_speed}. It arrives when it is within {@code dismount_distance} of the
 * end it moves toward.
 */
public final class RopeSlide {

    /** Below this slope (height difference per block of rope) the rope counts as level. */
    public static final double LEVEL_SLOPE = 0.05;
    /** Ropes shorter than this [blocks] cannot be ridden (the ends lie together). */
    public static final double MIN_LENGTH = 0.5;

    private RopeSlide() {
    }

    /** Speeds and distances of the slide, from config {@code grapple.slide}. Speeds in blocks per tick. */
    public record Params(double slideSpeed, double slideGravity, double minSpeed, double maxSpeed, double dismountDistance) {
    }

    /**
     * The rider's state after one tick: parameter {@code t} on the rope, {@code speed} (blocks per tick, never
     * negative), {@code dir} (+1 toward the hook, −1 toward the near end) and whether it {@code arrived} at the end it
     * moves toward.
     */
    public record Step(double t, double speed, int dir, boolean arrived) {
    }

    /** A look ray hit on the rope: parameter {@code t} of the rope point, its distance from the ray and from the eye. */
    public record Pick(double t, double rayDistance, double eyeDistance) {
    }

    // ------------------------------------------------------------------ grabbing (GR4)

    /** Whether a use may grab a rope. */
    public enum Grab {
        OK,
        /**
         * The main hand holds a musket or any other item with its own use: that use always wins, the rope is never
         * grabbed (GR4: re-aiming the musket right after a shot hung the player on their own rope).
         */
        HAND_BUSY,
        /** The hook left less than {@code grab_cooldown_ticks} ago. */
        TOO_SOON
    }

    /**
     * The grab rule (GR4): only an intentional use with an empty main hand or a grappling hook in it grabs a rope, and
     * not within {@code cooldownTicks} of the hook being thrown or fired ({@code hookAge}, ticks since it left).
     * Checked on the client before it asks and again on the server ({@code RopeSlideService#tryBoard}).
     */
    public static Grab grab(GrappleLaunch.Held mainHand, int hookAge, int cooldownTicks) {
        if (mainHand != GrappleLaunch.Held.EMPTY && mainHand != GrappleLaunch.Held.HOOK) {
            return Grab.HAND_BUSY;
        }
        return hookAge < cooldownTicks ? Grab.TOO_SOON : Grab.OK;
    }

    // ------------------------------------------------------------------ geometry

    /** Parameter in [0, 1] of the point of segment {@code a}-{@code b} nearest to {@code p} (0 for a point-like rope). */
    public static double parameter(Vec3 a, Vec3 b, Vec3 p) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        if (len2 < 1.0e-12) {
            return 0.0;
        }
        return clamp01(p.subtract(a).dot(ab) / len2);
    }

    /** The rope point at parameter {@code t}. */
    public static Vec3 at(Vec3 a, Vec3 b, double t) {
        return a.lerp(b, t);
    }

    /**
     * Where a look ray from {@code eye} along {@code look} (any length, not zero) meets the rope within {@code reach}
     * blocks: the closest points of the ray piece {@code [eye, eye + reach·look]} and the rope; a hit when they are at
     * most {@code pickRadius} apart. Null when the ray passes farther than that or the rope is out of reach.
     */
    public static @Nullable Pick pick(Vec3 eye, Vec3 look, double reach, Vec3 a, Vec3 b, double pickRadius) {
        double lookLen = look.length();
        if (lookLen < 1.0e-9 || reach <= 0) {
            return null;
        }
        Vec3 d1 = look.scale(reach / lookLen); // ray piece eye .. eye + d1, parameter s
        Vec3 d2 = b.subtract(a);                // rope a .. b, parameter t
        Vec3 r = eye.subtract(a);
        double aa = d1.dot(d1);
        double ee = d2.dot(d2);
        double f = d2.dot(r);
        double s;
        double t;
        if (ee < 1.0e-12) {
            t = 0.0;
            s = clamp01(-d1.dot(r) / aa);
        } else {
            double c = d1.dot(r);
            double bb = d1.dot(d2);
            double denom = aa * ee - bb * bb;
            s = denom > 1.0e-12 ? clamp01((bb * f - c * ee) / denom) : 0.0;
            t = (bb * s + f) / ee;
            if (t < 0.0) {
                t = 0.0;
                s = clamp01(-c / aa);
            } else if (t > 1.0) {
                t = 1.0;
                s = clamp01((bb - c) / aa);
            }
        }
        Vec3 onRay = eye.add(d1.scale(s));
        Vec3 onRope = at(a, b, t);
        double rayDistance = onRay.distanceTo(onRope);
        double eyeDistance = eye.distanceTo(onRope);
        if (rayDistance > pickRadius || eyeDistance > reach + pickRadius) {
            return null;
        }
        return new Pick(t, rayDistance, eyeDistance);
    }

    // ------------------------------------------------------------------ the slide

    /**
     * Slope of the rope from {@code a} to {@code b}: height difference over length (the sine of its angle), positive
     * when the hook end is higher. 0 for a rope shorter than {@link #MIN_LENGTH}.
     */
    public static double slope(double aY, double bY, double length) {
        return length < MIN_LENGTH ? 0.0 : (bY - aY) / length;
    }

    /** True when a rope with this slope counts as level. */
    public static boolean level(double slope) {
        return Math.abs(slope) < LEVEL_SLOPE;
    }

    /** Direction of travel: toward the lower end (+1 the hook, −1 the near end); a level rope goes toward the hook. */
    public static int direction(double slope) {
        return level(slope) || slope < 0 ? 1 : -1;
    }

    /**
     * Speed this tick: on a level rope {@code minSpeed}; else at least {@code slideSpeed} (also after a turn, when
     * {@code turned}), plus {@code slideGravity × |slope|}, at most {@code maxSpeed}.
     */
    public static double speed(double previous, double slope, boolean turned, Params p) {
        if (level(slope)) {
            return p.minSpeed();
        }
        double base = turned ? p.slideSpeed() : Math.max(previous, p.slideSpeed());
        return Math.min(Math.max(p.maxSpeed(), p.slideSpeed()), base + p.slideGravity() * Math.abs(slope));
    }

    /** Distance [blocks] along a rope of {@code length} from parameter {@code t} to the end in direction {@code dir}. */
    public static double remaining(double t, int dir, double length) {
        return (dir > 0 ? 1.0 - t : t) * length;
    }

    /**
     * One tick of the slide from parameter {@code t} with the speed and direction of the last tick ({@code prevDir} 0
     * for the first tick) on a rope of {@code length} whose ends are at heights {@code aY} and {@code bY}.
     */
    public static Step step(double t, double prevSpeed, int prevDir, double length, double aY, double bY, Params p) {
        double slope = slope(aY, bY, length);
        int dir = direction(slope);
        boolean turned = prevDir != 0 && prevDir != dir;
        if (length < MIN_LENGTH || remaining(t, dir, length) <= p.dismountDistance()) {
            return new Step(t, 0.0, dir, true);
        }
        double v = speed(turned ? 0.0 : prevSpeed, slope, turned, p);
        double next = clamp01(t + dir * v / length);
        return new Step(next, v, dir, remaining(next, dir, length) <= p.dismountDistance());
    }

    private static double clamp01(double x) {
        return x < 0.0 ? 0.0 : Math.min(1.0, x);
    }
}
