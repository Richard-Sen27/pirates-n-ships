package com.richardsenger.piratesnships.chart.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The chart's markers list (work package CHART2), pure: every marker with its horizontal distance and compass point
 * from the player, nearest first (ties by name, then id, so the order is stable). North is -z and east +x, as on the
 * chart.
 */
public final class MarkerList {

    /** The eight compass points, clockwise from north. */
    public enum Point {
        N, NE, E, SE, S, SW, W, NW;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** One row: the marker, its distance in whole blocks and the point it lies toward ({@code null} when within a block). */
    public record Entry(ChartMarker marker, int distance, Point point) {
    }

    private MarkerList() {
    }

    /** The compass point from {@code (fromX, fromZ)} toward {@code (toX, toZ)}; {@code null} for the same spot. */
    public static Point point(double fromX, double fromZ, double toX, double toZ) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        if (dx * dx + dz * dz < 1e-9) return null;
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        return Point.values()[Math.floorMod((int) Math.round(degrees / 45.0), 8)];
    }

    /** The rows for a player at {@code (x, z)}; a marker's spot is the centre of its block. */
    public static List<Entry> entries(List<ChartMarker> markers, double x, double z) {
        List<Entry> out = new ArrayList<>(markers.size());
        for (ChartMarker m : markers) {
            double mx = m.x() + 0.5;
            double mz = m.z() + 0.5;
            double d = Math.sqrt((mx - x) * (mx - x) + (mz - z) * (mz - z));
            int blocks = (int) Math.round(d);
            out.add(new Entry(m, blocks, blocks < 1 ? null : point(x, z, mx, mz)));
        }
        out.sort(Comparator.comparingInt(Entry::distance)
                .thenComparing(e -> e.marker().name(), String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(e -> e.marker().id()));
        return out;
    }
}
