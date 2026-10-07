package com.richardsenger.piratesnships.sailing.rope;

import com.richardsenger.piratesnships.sailing.sail.BlockPoint;

/**
 * The shape of a decorative rope line between two anchors (RP1, docs/design.md §5.2), pure: a catenary hanging below
 * the straight chord between the two ends.
 *
 * <p>The sag at the middle is {@code fraction} times the rope's <b>horizontal</b> span ({@code sailing.sails.rope_sag},
 * default 0.08): for a level rope that is the fraction of the span, and a steeper rope sags less, down to a vertical
 * rope that hangs straight. The curve is the level catenary {@code y = a cosh(x / a)} with that sag-to-span ratio,
 * scaled onto the chord: each point is the chord point minus {@link #shape} times the sag, straight down. Both ends
 * may be at the same height.
 */
public final class RopeLine {

    /** Fewest and most straight pieces a drawn rope is made of. */
    public static final int MIN_SEGMENTS = 4;
    public static final int MAX_SEGMENTS = 48;

    private RopeLine() {
    }

    /** Whether two anchors at most {@code maxLength} apart (between block centers) can take a rope. */
    public static boolean fits(BlockPoint a, BlockPoint b, int maxLength) {
        return a.distanceSquared(b) <= (long) maxLength * maxLength;
    }

    /** Sag at the middle [blocks]: {@code fraction} (clamped to 0..0.5) times the horizontal span. */
    public static double sagDepth(double dx, double dz, double fraction) {
        return clampFraction(fraction) * Math.hypot(dx, dz);
    }

    /** Straight pieces of a rope of {@code length} blocks: two per block, within {@link #MIN_SEGMENTS}..{@link #MAX_SEGMENTS}. */
    public static int segments(double length) {
        return Math.max(MIN_SEGMENTS, Math.min(MAX_SEGMENTS, (int) Math.ceil(length * 2)));
    }

    /**
     * The catenary parameter {@code k = L / (2a)} of a level rope of span {@code L} whose sag is {@code ratio} times its
     * span: the root of {@code (cosh k - 1) / (2k) = ratio}, by bisection (0 for no sag).
     */
    public static double catenaryK(double ratio) {
        double r = clampFraction(ratio);
        if (r <= 0) {
            return 0;
        }
        double lo = 0, hi = 1;
        while ((Math.cosh(hi) - 1) / (2 * hi) < r) {
            hi *= 2;
        }
        for (int i = 0; i < 60; i++) {
            double mid = (lo + hi) / 2;
            if ((Math.cosh(mid) - 1) / (2 * mid) < r) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return (lo + hi) / 2;
    }

    /**
     * How far below the chord the rope hangs at {@code t} (0 at one end, 1 at the other), as a fraction of the sag at the
     * middle: 0 at both ends, 1 at {@code t = 0.5}. {@code k} from {@link #catenaryK}; for {@code k} near 0 the
     * catenary is the parabola {@code 4t(1-t)}.
     */
    public static double shape(double t, double k) {
        if (k < 1.0e-6) {
            return 4 * t * (1 - t);
        }
        double ch = Math.cosh(k);
        return (ch - Math.cosh(k * (2 * t - 1))) / (ch - 1);
    }

    /**
     * Points along the rope from {@code a} to {@code b} ({@code {x, y, z}} each), {@link #segments} + 1 of them, the
     * first exactly {@code a} and the last exactly {@code b}.
     */
    public static double[][] points(double ax, double ay, double az, double bx, double by, double bz, double fraction) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        int n = segments(Math.sqrt(dx * dx + dy * dy + dz * dz));
        double sag = sagDepth(dx, dz, fraction);
        double k = catenaryK(clampFraction(fraction));
        double[][] out = new double[n + 1][];
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n;
            double down = i == 0 || i == n ? 0 : sag * shape(t, k);
            out[i] = new double[] {ax + dx * t, ay + dy * t - down, az + dz * t};
        }
        out[n] = new double[] {bx, by, bz};
        return out;
    }

    private static double clampFraction(double f) {
        return Double.isNaN(f) ? 0 : Math.max(0, Math.min(0.5, f));
    }
}
