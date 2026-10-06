package com.richardsenger.piratesnships.ship.hull.runtime;

import com.richardsenger.piratesnships.ship.hull.CellKind;
import com.richardsenger.piratesnships.ship.hull.HullGrid;
import com.richardsenger.piratesnships.ship.hull.HullVec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    /**
     * Where the sea is measured: the lowest solid cell (grid coordinates) of up to nine columns of the hull's own
     * footprint, at the corners, edge midpoints and center of its solid cells' x/z extent. A sample column without a
     * solid cell is skipped. Computed once per analysis, so the per-tick cost is a handful of block lookups.
     */
    public static List<int[]> probes(HullGrid g) {
        int minX = Integer.MAX_VALUE, maxX = -1, minZ = Integer.MAX_VALUE, maxZ = -1;
        for (int x = 0; x < g.sizeX(); x++) {
            for (int y = 0; y < g.sizeY(); y++) {
                for (int z = 0; z < g.sizeZ(); z++) {
                    if (g.kind(x, y, z) == CellKind.SOLID) {
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minZ = Math.min(minZ, z);
                        maxZ = Math.max(maxZ, z);
                    }
                }
            }
        }
        List<int[]> out = new ArrayList<>(9);
        if (maxX < 0) {
            return out;
        }
        int[] xs = {minX, (minX + maxX) / 2, maxX};
        int[] zs = {minZ, (minZ + maxZ) / 2, maxZ};
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if ((i > 0 && xs[i] == xs[i - 1]) || (j > 0 && zs[j] == zs[j - 1])) {
                    continue; // a narrow hull: do not sample the same column twice
                }
                for (int y = 0; y < g.sizeY(); y++) {
                    if (g.kind(xs[i], y, zs[j]) == CellKind.SOLID) {
                        out.add(new int[] {xs[i], y, zs[j]});
                        break;
                    }
                }
            }
        }
        return out;
    }

    /**
     * The sea surface at the hull from its footprint probes (world Y of the water surface where the world water touches
     * the hull's bottom in that column, {@code NaN} where it does not). The hull is afloat when at least a third of the
     * probes (and at least one) touch water; the surface is then the median of the wet probes. Otherwise the hull is
     * aground, in a dry dock or in the air, and nothing floats or floods, however close the water is.
     */
    public static double hullSurface(double[] probeSurfaces) {
        int n = probeSurfaces.length;
        return n == 0 ? Double.NaN : surface(probeSurfaces, Math.max(1, (n + 2) / 3));
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
