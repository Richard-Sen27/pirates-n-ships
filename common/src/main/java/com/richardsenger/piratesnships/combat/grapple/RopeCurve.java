package com.richardsenger.piratesnships.combat.grapple;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The shape of a hanging rope (GR7): a catenary between two points for a given rope length, sampled into the points the
 * renderer joins with straight pieces. A rope no longer than the distance between its ends is a straight line (taut),
 * and so is a rope whose ends lie straight above each other (a player hanging below the hook); a longer rope sags under
 * gravity, the more the longer it is. No world access; tested in JUnit ({@code RopeCurveTest}).
 *
 * <p>With {@code h} the horizontal span, {@code v} the rise from the first end to the second and {@code s} the rope's
 * length, the catenary {@code y = c·cosh((x − x0)/c) + C} through both ends has {@code 2c·sinh(h/2c) = √(s² − v²)} and
 * {@code x0 = h/2 − c·atanh(v/s)}; the height is evaluated as {@code 2c·sinh((X+X0)/2)·sinh((X−X0)/2)} so that steep,
 * slightly slack ropes do not lose precision.
 */
public final class RopeCurve {

    /** Spans shorter than this [blocks] count as vertical: the rope hangs straight. */
    public static final double VERTICAL_SPAN = 1.0e-3;
    /** Slack shorter than this [blocks] counts as taut: the rope is straight. */
    public static final double TAUT_SLACK = 1.0e-3;
    /** Largest {@code h/2c} the solver goes to (a rope hundreds of times longer than its span). */
    private static final double MAX_K = 40.0;

    private RopeCurve() {
    }

    /**
     * {@code segments + 1} points from {@code a} to {@code b} (both included) along a rope of {@code length} hanging
     * between them.
     */
    public static List<Vec3> sample(Vec3 a, Vec3 b, double length, int segments) {
        int n = Math.max(1, segments);
        List<Vec3> points = new ArrayList<>(n + 1);
        double hx = b.x - a.x;
        double hz = b.z - a.z;
        double h = Math.hypot(hx, hz);
        double v = b.y - a.y;
        double chord = Math.sqrt(h * h + v * v);
        if (h < VERTICAL_SPAN || !(length > chord + TAUT_SLACK)) {
            for (int i = 0; i <= n; i++) {
                points.add(a.lerp(b, (double) i / n));
            }
            return points;
        }
        double c = parameter(h, Math.sqrt(length * length - v * v));
        double x0 = h / 2.0 - c * atanh(v / length);
        double big0 = -x0 / c;
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n;
            double x = h * t;
            double big = (x - x0) / c;
            double y = 2.0 * c * Math.sinh((big + big0) / 2.0) * Math.sinh((big - big0) / 2.0);
            points.add(i == n ? b : new Vec3(a.x + hx * t, a.y + y, a.z + hz * t));
        }
        return points;
    }

    /** How far [blocks] the rope's lowest sampled point lies below the lower of its two ends (0 for a straight rope). */
    public static double dip(Vec3 a, Vec3 b, double length, int segments) {
        double low = Math.min(a.y, b.y);
        double min = low;
        for (Vec3 p : sample(a, b, length, segments)) {
            min = Math.min(min, p.y);
        }
        return low - min;
    }

    /**
     * The catenary's parameter {@code c} for a horizontal span {@code h} and {@code level} (the rope's length with the
     * rise taken out, {@code √(s² − v²)}, more than {@code h}): {@code 2c·sinh(h/2c) = level}, solved by bisection on
     * {@code k = h/2c} in {@code sinh(k)/k = level/h}.
     */
    static double parameter(double h, double level) {
        double ratio = level / h;
        double lo = 1.0e-9;
        double hi = MAX_K;
        if (Math.sinh(hi) / hi <= ratio) {
            return h / (2.0 * hi);
        }
        for (int i = 0; i < 100; i++) {
            double mid = 0.5 * (lo + hi);
            if (Math.sinh(mid) / mid < ratio) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return h / (2.0 * (0.5 * (lo + hi)));
    }

    private static double atanh(double x) {
        double c = Math.max(-1.0 + 1.0e-12, Math.min(1.0 - 1.0e-12, x));
        return 0.5 * Math.log((1.0 + c) / (1.0 - c));
    }
}
