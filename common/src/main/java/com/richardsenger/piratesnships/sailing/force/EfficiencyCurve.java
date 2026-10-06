package com.richardsenger.piratesnships.sailing.force;

import java.util.List;

/**
 * A sail's efficiency over the apparent wind angle (docs/design.md §5.2), as a table of points that is linearly
 * interpolated, so the curve is continuous. Angles are measured off the bow from where the wind comes from:
 * 0° = head to wind, 90° = beam reach, 180° = dead downwind. Port and starboard share the curve (mirror image).
 *
 * <p>{@code drive} is the coefficient along the hull (forward, negative = pushed astern); {@code side} the coefficient
 * across the hull, always toward leeward. A plain record so it can become a datapack definition later.
 *
 * @param points table points, strictly increasing angles from exactly 0 to exactly 180
 */
public record EfficiencyCurve(List<Point> points) {

    /** One table point. */
    public record Point(double angleDeg, double drive, double side) {
    }

    public EfficiencyCurve {
        points = List.copyOf(points);
        if (points.size() < 2 || points.getFirst().angleDeg() != 0.0 || points.getLast().angleDeg() != 180.0) {
            throw new IllegalArgumentException("Efficiency curve must span 0..180 degrees");
        }
        for (int i = 1; i < points.size(); i++) {
            if (points.get(i).angleDeg() <= points.get(i - 1).angleDeg()) {
                throw new IllegalArgumentException("Efficiency curve angles must increase");
            }
        }
        if (points.getFirst().side() != 0.0 || points.getLast().side() != 0.0) {
            // Side force flips sides when the wind crosses the bow or stern line; it must be zero there to stay continuous.
            throw new IllegalArgumentException("Side coefficient must be 0 at 0 and 180 degrees");
        }
    }

    /** Shorthand: {@code of(angle, drive, side, angle, drive, side, ...)}. */
    public static EfficiencyCurve of(double... triples) {
        if (triples.length % 3 != 0) {
            throw new IllegalArgumentException("Expected (angle, drive, side) triples");
        }
        Point[] pts = new Point[triples.length / 3];
        for (int i = 0; i < pts.length; i++) {
            pts[i] = new Point(triples[i * 3], triples[i * 3 + 1], triples[i * 3 + 2]);
        }
        return new EfficiencyCurve(List.of(pts));
    }

    /** Drive coefficient at an apparent wind angle in [0, 180] degrees (clamped). */
    public double drive(double angleDeg) {
        return interpolate(angleDeg, true);
    }

    /** Leeward side coefficient at an apparent wind angle in [0, 180] degrees (clamped). */
    public double side(double angleDeg) {
        return interpolate(angleDeg, false);
    }

    private double interpolate(double angleDeg, boolean drive) {
        double a = Math.min(Math.max(angleDeg, 0.0), 180.0);
        for (int i = 1; i < points.size(); i++) {
            Point hi = points.get(i);
            if (a <= hi.angleDeg()) {
                Point lo = points.get(i - 1);
                double t = (a - lo.angleDeg()) / (hi.angleDeg() - lo.angleDeg());
                double l = drive ? lo.drive() : lo.side();
                double h = drive ? hi.drive() : hi.side();
                return l + (h - l) * t;
            }
        }
        Point last = points.getLast();
        return drive ? last.drive() : last.side();
    }
}
