package com.richardsenger.piratesnships.ship.hull.runtime;

import com.richardsenger.piratesnships.ship.hull.HullVec;
import java.util.Arrays;

/**
 * Pure sea-level math for a floating ship. The flooding model measures heights as {@code up · p} with {@code p} a
 * ship-local (plot) position and {@code up} world up expressed in the ship frame. The world sea surface is the plane
 * {@code y = W}. For a rigid pose {@code worldY(p) = up · p + c}, so the sea in ship-frame height is
 * {@code up · pRef + (W − worldY(pRef))} for any reference point.
 */
public final class SeaLevel {

    /** Sea level used when no water is near the ship: nothing is below it, so nothing floods and nothing floats. */
    public static final double NO_WATER = Double.NEGATIVE_INFINITY;

    private SeaLevel() {
    }

    /**
     * The water surface from column samples (world Y of each column's surface, {@code NaN} where a column has no water).
     * Uses the median so a single dock, waterfall or puddle does not move the sea. Returns {@code NaN} when fewer than
     * {@code minSamples} columns have water (the ship is on land or in the air).
     */
    public static double surface(double[] samples, int minSamples) {
        double[] valid = Arrays.stream(samples).filter(Double::isFinite).sorted().toArray();
        if (valid.length == 0 || valid.length < minSamples) {
            return Double.NaN;
        }
        int n = valid.length;
        return n % 2 == 1 ? valid[n / 2] : 0.5 * (valid[n / 2 - 1] + valid[n / 2]);
    }

    /** The sea level in ship-frame height, or {@link #NO_WATER} when {@code waterWorldY} is not finite. */
    public static double inShipFrame(HullVec up, HullVec refPlot, double refWorldY, double waterWorldY) {
        if (!Double.isFinite(waterWorldY)) {
            return NO_WATER;
        }
        return up.dot(refPlot) + (waterWorldY - refWorldY);
    }

    /** Normalized ship-frame up vector; falls back to {@link HullVec#UP} for a degenerate input. */
    public static HullVec up(double x, double y, double z) {
        HullVec v = new HullVec(x, y, z);
        return v.length() < 1e-9 ? HullVec.UP : v.normalized();
    }
}
