package com.richardsenger.piratesnships.sailing.anchor;

/**
 * The shape of the anchor chain for rendering (docs/design.md §5.2, AN2b): points along the chain from the anchor's
 * ring to the hawse. Pure math, no world access; one instance is reused every frame, so computing a curve allocates
 * nothing.
 *
 * <ul>
 *   <li><b>Slack</b> (the paid-out length {@code L} longer than the distance {@code d}): a catenary of length {@code L}
 *       through both ends, {@code y = a·cosh((u − u₀)/a) + c} in the vertical plane through them. Its parameter is
 *       solved per call: with {@code h} the horizontal and {@code v} the vertical distance,
 *       {@code 2a·sinh(h / 2a) = √(L² − v²)}, by bisection on {@code U = h / 2a} ({@code sinh U / U = √(L² − v²) / h}).
 *       The points are spaced evenly along the arc, in closed form. With a {@code floor}, points below it lie on it
 *       (the chain lying on the seabed).</li>
 *   <li><b>Taut</b> (or {@code L ≤ d}, or a vertical chain): the straight line with a slight downward bow of at most
 *       {@link #MAX_TAUT_BOW} blocks.</li>
 * </ul>
 *
 * <p>Point 0 is the ring, point {@code segments} the hawse, both exact. Cost: about 60 {@code sinh} evaluations for the
 * solve plus a few {@code sqrt}/{@code log} per point.
 */
public final class ChainCurve {

    /** Most segments a chain is drawn with. */
    public static final int MAX_SEGMENTS = 64;
    /** Largest bow of a taut chain [blocks]. */
    public static final double MAX_TAUT_BOW = 0.1;
    /** Bow of a taut chain per block of its length, up to {@link #MAX_TAUT_BOW}. */
    static final double TAUT_BOW_PER_BLOCK = 0.005;
    /** Horizontal distance below which the chain counts as vertical [blocks]. */
    static final double MIN_HORIZONTAL = 1.0e-4;
    private static final int ITERATIONS = 60;

    private final double[] xs = new double[MAX_SEGMENTS + 1];
    private final double[] ys = new double[MAX_SEGMENTS + 1];
    private final double[] zs = new double[MAX_SEGMENTS + 1];
    private int segments;
    private boolean slack;

    /** Segments for a chain of {@code length} blocks: about one per block, at least 1, at most {@link #MAX_SEGMENTS}. */
    public static int segmentsFor(double length) {
        if (!(length > 1.0)) {
            return 1;
        }
        return (int) Math.min(MAX_SEGMENTS, Math.ceil(length));
    }

    /**
     * Computes the chain from the ring {@code (rx, ry, rz)} to the hawse {@code (hx, hy, hz)}.
     *
     * @param length   paid-out length [blocks]; shorter than the distance draws the straight line
     * @param taut     the chain is taut: straight with a slight bow
     * @param floor    y below which no point goes (the seabed), or {@link Double#NEGATIVE_INFINITY}
     * @param segments number of segments, clamped to 1..{@link #MAX_SEGMENTS}
     * @return the number of segments ({@link #x}, {@link #y}, {@link #z} take 0..that)
     */
    public int compute(double rx, double ry, double rz, double hx, double hy, double hz, double length, boolean taut,
                       double floor, int segments) {
        int n = Math.max(1, Math.min(MAX_SEGMENTS, segments));
        this.segments = n;
        this.slack = false;
        double dx = hx - rx, dy = hy - ry, dz = hz - rz;
        double h = Math.sqrt(dx * dx + dz * dz);
        double d = Math.sqrt(h * h + dy * dy);
        if (taut || !(length > d + 1.0e-6) || h < MIN_HORIZONTAL) {
            straight(rx, ry, rz, dx, dy, dz, Math.min(MAX_TAUT_BOW, TAUT_BOW_PER_BLOCK * d), n);
        } else {
            catenary(rx, ry, rz, dx / h, dz / h, h, dy, length, floor, n);
            slack = true;
        }
        xs[0] = rx;
        ys[0] = ry;
        zs[0] = rz;
        xs[n] = hx;
        ys[n] = hy;
        zs[n] = hz;
        return n;
    }

    public int segments() {
        return segments;
    }

    /** Whether the last curve was a slack catenary (not the straight line). */
    public boolean slack() {
        return slack;
    }

    public double x(int i) {
        return xs[i];
    }

    public double y(int i) {
        return ys[i];
    }

    public double z(int i) {
        return zs[i];
    }

    private void straight(double rx, double ry, double rz, double dx, double dy, double dz, double bow, int n) {
        for (int i = 1; i < n; i++) {
            double t = (double) i / n;
            xs[i] = rx + dx * t;
            ys[i] = ry + dy * t - 4.0 * bow * t * (1.0 - t);
            zs[i] = rz + dz * t;
        }
    }

    /** The catenary in the vertical plane along the unit direction {@code (ex, ez)}, from the ring to {@code (h, v)}. */
    private void catenary(double rx, double ry, double rz, double ex, double ez, double h, double v, double length,
                          double floor, int n) {
        double a = parameter(h, v, length);
        // w(s) = sinh((u(s) − u₀)/a), the slope at arc length s from the ring; w0 at the ring
        double w0 = Math.sinh(atanh(v / length) - h / (2.0 * a));
        double r0 = Math.sqrt(1.0 + w0 * w0);
        for (int i = 1; i < n; i++) {
            double s = length * i / n;
            double w = w0 + s / a;
            double u = a * (asinh(w) - asinh(w0));
            // a·(√(1+w²) − √(1+w0²)) without cancellation, since w − w0 = s/a
            double y = s * (w + w0) / (Math.sqrt(1.0 + w * w) + r0);
            xs[i] = rx + ex * u;
            ys[i] = Math.max(floor, ry + y);
            zs[i] = rz + ez * u;
        }
    }

    /** The catenary parameter {@code a} for a chain of {@code length} over {@code h} across and {@code v} up. */
    static double parameter(double h, double v, double length) {
        double ratio = Math.sqrt(length * length - v * v) / h; // > 1 for a slack chain
        double lo = 0.0, hi = 1.0;
        while (Math.sinh(hi) / hi < ratio && hi < 700.0) {
            hi *= 2.0;
        }
        for (int k = 0; k < ITERATIONS; k++) {
            double mid = 0.5 * (lo + hi);
            if (Math.sinh(mid) / mid < ratio) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        double u = Math.max(1.0e-12, 0.5 * (lo + hi));
        return h / (2.0 * u);
    }

    static double asinh(double x) {
        double ax = Math.abs(x);
        double r = Math.log1p(ax + ax * ax / (1.0 + Math.sqrt(1.0 + ax * ax)));
        return x < 0 ? -r : r;
    }

    static double atanh(double x) {
        double c = Math.max(-1.0 + 1.0e-12, Math.min(1.0 - 1.0e-12, x));
        return 0.5 * Math.log1p(2.0 * c / (1.0 - c));
    }
}
