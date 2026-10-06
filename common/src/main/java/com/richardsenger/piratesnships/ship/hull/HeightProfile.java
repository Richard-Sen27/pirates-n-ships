package com.richardsenger.piratesnships.ship.hull;

/**
 * Converts water volume to surface height and back for one compartment, and integrates cell positions below a
 * height, all in {@code O(log n)}.
 *
 * <p>Model: every cell is a slab of thickness 1 along the up vector, centered on its height {@code h}. Water at surface
 * height {@code L} fills a cell to the fraction {@code clamp(L + 0.5 - h, 0, 1)}. The volume function is the sum of these
 * fractions: continuous, piecewise linear and monotone, so the inverse is well-defined. A tilted ship just has more
 * distinct heights. Cells are kept sorted by height together with prefix sums of {@code 1, h, x, y, z, h·x, h·y, h·z}.
 */
public final class HeightProfile {

    private final double[] heights;
    // prefix sums, length n + 1
    private final double[] ph, px, py, pz, phx, phy, phz;

    /**
     * @param heights cell heights, sorted ascending
     * @param centers cell centers in the same order, as {@code [x0, y0, z0, x1, ...]}
     */
    HeightProfile(double[] heights, double[] centers) {
        int n = heights.length;
        this.heights = heights;
        ph = new double[n + 1];
        px = new double[n + 1];
        py = new double[n + 1];
        pz = new double[n + 1];
        phx = new double[n + 1];
        phy = new double[n + 1];
        phz = new double[n + 1];
        for (int i = 0; i < n; i++) {
            double h = heights[i], x = centers[3 * i], y = centers[3 * i + 1], z = centers[3 * i + 2];
            ph[i + 1] = ph[i] + h;
            px[i + 1] = px[i] + x;
            py[i + 1] = py[i] + y;
            pz[i + 1] = pz[i] + z;
            phx[i + 1] = phx[i] + h * x;
            phy[i + 1] = phy[i] + h * y;
            phz[i + 1] = phz[i] + h * z;
        }
    }

    public int cellCount() {
        return heights.length;
    }

    /** Surface height of an empty compartment (bottom of the lowest cell). */
    public double bottom() {
        return heights[0] - 0.5;
    }

    /** Surface height of a full compartment (top of the highest cell). */
    public double top() {
        return heights[heights.length - 1] + 0.5;
    }

    /** Water volume when the surface is at {@code level}. */
    public double volumeAt(double level) {
        int full = upperBound(level - 0.5);
        int part = lowerBound(level + 0.5);
        return full + (part - full) * (level + 0.5) - (ph[part] - ph[full]);
    }

    /** Surface height for a water volume (clamped to {@code [bottom, top]}). Bisection, 64 steps, deterministic. */
    public double levelAt(double volume) {
        if (volume <= 0) return bottom();
        if (volume >= heights.length) return top();
        double lo = bottom(), hi = top();
        for (int i = 0; i < 64 && hi - lo > 1e-12; i++) {
            double mid = 0.5 * (lo + hi);
            if (volumeAt(mid) < volume) lo = mid;
            else hi = mid;
        }
        return 0.5 * (lo + hi);
    }

    /**
     * Fill-weighted sums for a surface at {@code level}: {@code [volume, Σw·x, Σw·y, Σw·z]} with
     * {@code w = clamp(level + 0.5 - h, 0, 1)} per cell. Divide by the volume for the centroid.
     */
    public double[] moments(double level) {
        int full = upperBound(level - 0.5);
        int part = lowerBound(level + 0.5);
        double k = level + 0.5;
        double vol = full + (part - full) * k - (ph[part] - ph[full]);
        double mx = px[full] + k * (px[part] - px[full]) - (phx[part] - phx[full]);
        double my = py[full] + k * (py[part] - py[full]) - (phy[part] - phy[full]);
        double mz = pz[full] + k * (pz[part] - pz[full]) - (phz[part] - phz[full]);
        return new double[]{vol, mx, my, mz};
    }

    /** Number of cells whose center lies strictly below {@code level} (the flooded prefix in height order). */
    public int countBelow(double level) {
        return lowerBound(level);
    }

    /** Number of heights {@code <= v}. */
    private int upperBound(double v) {
        int lo = 0, hi = heights.length;
        while (lo < hi) {
            int m = (lo + hi) >>> 1;
            if (heights[m] <= v) lo = m + 1;
            else hi = m;
        }
        return lo;
    }

    /** Number of heights {@code < v}. */
    private int lowerBound(double v) {
        int lo = 0, hi = heights.length;
        while (lo < hi) {
            int m = (lo + hi) >>> 1;
            if (heights[m] < v) lo = m + 1;
            else hi = m;
        }
        return lo;
    }
}
