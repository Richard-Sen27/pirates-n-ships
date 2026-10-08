package com.richardsenger.piratesnships.worldsim.materialize;

import com.richardsenger.piratesnships.worldsim.lane.Lane;

import java.util.List;

/**
 * Where a real ship is along its voyage's route (WS3b): pure. A dematerialised ship continues from the point of the
 * route nearest to it, so the record's progress grows by the distance the ship really sailed along it.
 */
public final class RouteMath {

    private RouteMath() {
    }

    /** Blocks along {@code points} up to waypoint {@code index} (clamped to the route). */
    public static double progressAt(List<Lane.Point> points, int index) {
        double sum = 0;
        int last = Math.min(index, points.size() - 1);
        for (int i = 1; i <= last; i++) sum += points.get(i - 1).distanceTo(points.get(i));
        return sum;
    }

    /**
     * The progress (blocks along the route) of the route point nearest to {@code (x, z)}. Among legs at the same
     * distance the one nearest to {@code hint} (the record's progress) wins, so a route that doubles back is not
     * jumped along. 0 for a route of fewer than two points.
     */
    public static double project(List<Lane.Point> points, double x, double z, double hint) {
        if (points.size() < 2) return 0.0;
        double bestDist = Double.MAX_VALUE;
        double bestProgress = 0.0;
        double start = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            Lane.Point a = points.get(i);
            Lane.Point b = points.get(i + 1);
            double dx = b.x() - a.x();
            double dz = b.z() - a.z();
            double len2 = dx * dx + dz * dz;
            double len = Math.sqrt(len2);
            double t = len2 <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, ((x - a.x()) * dx + (z - a.z()) * dz) / len2));
            double px = a.x() + dx * t;
            double pz = a.z() + dz * t;
            double d = Math.hypot(x - px, z - pz);
            double progress = start + t * len;
            if (d < bestDist - 1e-6 || (Math.abs(d - bestDist) <= 1e-6 && Math.abs(progress - hint) < Math.abs(bestProgress - hint))) {
                bestDist = d;
                bestProgress = progress;
            }
            start += len;
        }
        return bestProgress;
    }

    /** The waypoints still ahead of {@code progress}: from the end of the leg it is on to the last one. */
    public static List<Lane.Point> ahead(List<Lane.Point> points, double progress) {
        if (points.size() < 2) return List.copyOf(points);
        int leg = Lane.positionAlong(points, progress).leg();
        return List.copyOf(points.subList(Math.min(points.size() - 1, leg + 1), points.size()));
    }
}
