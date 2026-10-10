package com.richardsenger.piratesnships.ship.rigging.client;

import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.Point;

import java.util.ArrayList;
import java.util.List;

/**
 * The drawn shape of a ratline net (RL2, pure): two side ropes from ring A to ring B that spread to {@code halfWidth}
 * either side of the line within {@link #TAPER} blocks of each ring (where they are tied together), and a rung between
 * them every {@code spacing} blocks ({@link RatlineNetRules#rungs}); RL4's fans ({@link #fan}); RL5's pairs of joined
 * strands ({@link #pairRungs}). No client classes, so
 * it is tested with JUnit.
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

    /** RL4: the drawn shape of a fan: every strand's rope from its foot to the hub, and rungs across all of them. */
    public record Fan(List<Point[]> ropes, List<List<Point>> rungs) {
    }

    /**
     * RL4: a fan of strands from {@code feet} (in fan order, outermost first and last) to one {@code hub}: every strand
     * is a rope from its foot ring to the hub ring, and a rung every {@code spacing} blocks of the longest strand runs
     * from the first strand to the last through the ones between, the rung at fraction f joining the points at fraction
     * f of each strand's length (so the rungs narrow towards the hub). Rungs narrower than {@link #MIN_RUNG} are left out.
     */
    public static Fan fan(Point hub, List<Point> feet, double spacing) {
        List<Point[]> ropes = new ArrayList<>();
        double longest = 0;
        for (Point f : feet) {
            ropes.add(new Point[] {f, hub});
            longest = Math.max(longest, Math.sqrt(sq(hub.x() - f.x()) + sq(hub.y() - f.y()) + sq(hub.z() - f.z())));
        }
        List<List<Point>> rungs = new ArrayList<>();
        if (longest > 1.0e-9) {
            for (Point at : RatlineNetRules.rungs(new Point(0, 0, 0), new Point(0, longest, 0), spacing)) {
                double t = at.y() / longest;
                List<Point> rung = new ArrayList<>(feet.size());
                double width = 0;
                for (Point f : feet) {
                    Point p = new Point(f.x() + (hub.x() - f.x()) * t, f.y() + (hub.y() - f.y()) * t, f.z() + (hub.z() - f.z()) * t);
                    if (!rung.isEmpty()) {
                        Point q = rung.get(rung.size() - 1);
                        width += Math.sqrt(sq(p.x() - q.x()) + sq(p.y() - q.y()) + sq(p.z() - q.z()));
                    }
                    rung.add(p);
                }
                if (width >= MIN_RUNG) {
                    rungs.add(rung);
                }
            }
        }
        return new Fan(ropes, rungs);
    }

    /**
     * RL5: the rungs between two joined strands, each from its foot to its own hub (the same hub in a fan, two hubs in a
     * rectangle or a trapezoid): a rung every {@code spacing} blocks of the longer strand, the rung at fraction f joining
     * the points at fraction f of each strand. Rungs narrower than {@link #MIN_RUNG} are left out.
     */
    public static List<Point[]> pairRungs(Point footA, Point hubA, Point footB, Point hubB, double spacing) {
        double longest = Math.max(dist(footA, hubA), dist(footB, hubB));
        List<Point[]> out = new ArrayList<>();
        if (longest < 1.0e-9) {
            return out;
        }
        for (Point at : RatlineNetRules.rungs(new Point(0, 0, 0), new Point(0, longest, 0), spacing)) {
            double t = at.y() / longest;
            Point pa = lerp(footA, hubA, t), pb = lerp(footB, hubB, t);
            if (dist(pa, pb) >= MIN_RUNG) {
                out.add(new Point[] {pa, pb});
            }
        }
        return out;
    }

    private static Point lerp(Point a, Point b, double t) {
        return new Point(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t, a.z() + (b.z() - a.z()) * t);
    }

    private static double dist(Point a, Point b) {
        return Math.sqrt(sq(b.x() - a.x()) + sq(b.y() - a.y()) + sq(b.z() - a.z()));
    }

    private static double sq(double v) {
        return v * v;
    }
}
