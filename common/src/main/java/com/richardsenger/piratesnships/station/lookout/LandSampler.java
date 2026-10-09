package com.richardsenger.piratesnships.station.lookout;

import org.jetbrains.annotations.Nullable;

/**
 * The lookout's cheap land check (CN1): rays round the compass from the nest, sampled every {@code step} blocks from
 * {@code minDistance} out to {@code range}; the first land sample of a ray counts, and the nearest of all is the land
 * called. A column that is not known (chunk not loaded) ends its ray: the lookout never loads chunks. Pure, the world
 * comes in through {@link Surface}.
 */
public final class LandSampler {

    /** What stands at sea level in a column. */
    public enum Column { WATER, LAND, UNKNOWN }

    @FunctionalInterface
    public interface Surface {
        Column at(int x, int z);
    }

    /** Land seen at block column (x, z), {@code distance} blocks from the nest along its ray. */
    public record Land(int x, int z, double distance) {
    }

    private LandSampler() {
    }

    /**
     * The nearest land within {@code range} of (ox, oz) on {@code directions} rays (the first due north, then
     * clockwise), or null.
     */
    public static @Nullable Land nearest(double ox, double oz, double range, int directions, double step, double minDistance,
                                         Surface surface) {
        if (directions <= 0 || !(step > 0) || !(range > 0)) {
            return null;
        }
        Land best = null;
        double start = Math.max(step, minDistance);
        for (int i = 0; i < directions; i++) {
            double a = Math.toRadians(360.0 * i / directions);
            double sx = Math.sin(a);
            double sz = -Math.cos(a);
            for (double d = start; d <= range + 1e-9; d += step) {
                if (best != null && d >= best.distance()) {
                    break; // no nearer than the land found already
                }
                int x = (int) Math.floor(ox + sx * d);
                int z = (int) Math.floor(oz + sz * d);
                Column c = surface.at(x, z);
                if (c == Column.UNKNOWN) {
                    break;
                }
                if (c == Column.LAND) {
                    best = new Land(x, z, d);
                    break;
                }
            }
        }
        return best;
    }

    /** The memory key of land at (x, z): the {@code region}-block square it lies in. */
    public static String regionKey(int x, int z, int region) {
        int r = Math.max(1, region);
        return "land:" + Math.floorDiv(x, r) + "," + Math.floorDiv(z, r);
    }
}
