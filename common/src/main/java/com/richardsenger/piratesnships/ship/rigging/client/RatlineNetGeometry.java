package com.richardsenger.piratesnships.ship.rigging.client;

import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.Point;

import java.util.ArrayList;
import java.util.List;

/**
 * The drawn shape of a ratline net (RL2, pure): two side ropes from ring A to ring B that spread to {@code halfWidth}
 * either side of the line within {@link #TAPER} blocks of each ring (where they are tied together), and a rung between
 * them every {@code spacing} blocks ({@link RatlineNetRules#rungs}). No client classes, so it is tested with JUnit.
 */
public record RatlineNetGeometry(List<Point> left, List<Point> right, List<Point[]> rungs) {

    /** Length over which the side ropes spread from a ring to the full width [blocks]. */
    public static final double TAPER = 0.6;
    /** Rungs narrower than this are left out (they would be knots at the rings) [blocks]. */
    private static final double MIN_RUNG = 0.1;

    public static RatlineNetGeometry of(Point a, Point b, Point side, double halfWidth, double spacing) {
        double len = Math.sqrt(sq(b.x() - a.x()) + sq(b.y() - a.y()) + sq(b.z() - a.z()));
        List<Point> left = new ArrayList<>();
        List<Point> right = new ArrayList<>();
        List<Point[]> rungs = new ArrayList<>();
        left.add(a);
        right.add(a);
        for (Point p : RatlineNetRules.rungs(a, b, spacing)) {
            double s = Math.sqrt(sq(p.x() - a.x()) + sq(p.y() - a.y()) + sq(p.z() - a.z()));
            double w = halfWidth * Math.min(1, Math.min(s, len - s) / TAPER);
            Point l = new Point(p.x() + side.x() * w, p.y() + side.y() * w, p.z() + side.z() * w);
            Point r = new Point(p.x() - side.x() * w, p.y() - side.y() * w, p.z() - side.z() * w);
            left.add(l);
            right.add(r);
            if (2 * w >= MIN_RUNG) {
                rungs.add(new Point[] {l, r});
            }
        }
        left.add(b);
        right.add(b);
        return new RatlineNetGeometry(left, right, rungs);
    }

    private static double sq(double v) {
        return v * v;
    }
}
