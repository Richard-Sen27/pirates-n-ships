package com.richardsenger.piratesnships.worldsim.lane;

import java.util.List;

/**
 * The contract risk of a route (WS2, pure): {@code 1 − (1 − base)·(1 − perIsland)^n} with {@code n} the pirate
 * islands within {@code radius} blocks of the polyline, clamped to [0, 1].
 */
public final class LaneRisk {

    private LaneRisk() {
    }

    public static double risk(List<Lane.Point> route, List<Lane.Point> islands, double radius, double base, double perIsland) {
        int n = 0;
        for (Lane.Point island : islands) if (distanceToPolyline(route, island) <= radius) n++;
        double safe = (1.0 - clamp(base)) * Math.pow(1.0 - clamp(perIsland), n);
        return clamp(1.0 - safe);
    }

    /** Shortest horizontal distance from {@code p} to the polyline (to the point itself if it has one point). */
    public static double distanceToPolyline(List<Lane.Point> line, Lane.Point p) {
        if (line.isEmpty()) return Double.POSITIVE_INFINITY;
        if (line.size() == 1) return line.get(0).distanceTo(p);
        double best = Double.POSITIVE_INFINITY;
        for (int i = 1; i < line.size(); i++) best = Math.min(best, segmentDistance(line.get(i - 1), line.get(i), p));
        return best;
    }

    static double segmentDistance(Lane.Point a, Lane.Point b, Lane.Point p) {
        double vx = b.x() - a.x(), vz = b.z() - a.z();
        double len2 = vx * vx + vz * vz;
        double t = len2 <= 0 ? 0 : Math.max(0, Math.min(1, ((p.x() - a.x()) * vx + (p.z() - a.z()) * vz) / len2));
        double dx = a.x() + t * vx - p.x(), dz = a.z() + t * vz - p.z();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
