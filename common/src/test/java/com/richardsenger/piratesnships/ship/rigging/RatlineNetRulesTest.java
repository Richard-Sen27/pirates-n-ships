package com.richardsenger.piratesnships.ship.rigging;

import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.Cell;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.FanCell;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.Point;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.Verdict;
import com.richardsenger.piratesnships.ship.rigging.client.RatlineNetGeometry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link RatlineNetRules} (RL2): the cells of a net, the length and pitch rule, the drawn net and the climbing assist. */
class RatlineNetRulesTest {

    private static Point p(double x, double y, double z) {
        return new Point(x, y, z);
    }

    private static Cell c(int x, int y, int z) {
        return new Cell(x, y, z);
    }

    /** Consecutive cells of a walk share a face (one axis steps by one). */
    private static void assertFaceConnected(List<Cell> cells) {
        for (int i = 0; i + 1 < cells.size(); i++) {
            Cell a = cells.get(i), b = cells.get(i + 1);
            int d = Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y()) + Math.abs(a.z() - b.z());
            assertEquals(1, d, "cells " + a + " and " + b + " do not share a face");
        }
    }

    @Test
    void aVerticalLineTakesTheColumnBetweenTheFittings() {
        // chainplate on the floor at (0, 1, 0), hoop five blocks up, its ring 0.2 towards +x
        List<Cell> cells = RatlineNetRules.netCells(p(0.5, 1.3, 0.5), p(0.7, 6.5, 0.5));
        assertEquals(List.of(c(0, 2, 0), c(0, 3, 0), c(0, 4, 0), c(0, 5, 0)), cells);
    }

    @Test
    void aFortyFiveDegreeLineIsAStaircase() {
        List<Cell> walk = RatlineNetRules.walk(p(1.5, 1.3, 4.5), p(5.7, 5.5, 4.5));
        assertEquals(c(1, 1, 4), walk.get(0));
        assertEquals(c(5, 5, 4), walk.get(walk.size() - 1));
        assertFaceConnected(walk);
        // up 4 and across 4: nine cells, the fittings' two excluded
        assertEquals(9, walk.size());
        assertEquals(List.of(c(2, 1, 4), c(2, 2, 4), c(3, 2, 4), c(3, 3, 4), c(4, 3, 4), c(4, 4, 4), c(5, 4, 4)),
                RatlineNetRules.netCells(p(1.5, 1.3, 4.5), p(5.7, 5.5, 4.5)));
    }

    @Test
    void aDiagonalLineInThreeAxesStaysFaceConnectedAndEndsInBsCell() {
        Point a = p(0.5, 0.3, 0.5), b = p(-3.3, 7.5, 4.7);
        List<Cell> walk = RatlineNetRules.walk(a, b);
        assertFaceConnected(walk);
        assertEquals(c(0, 0, 0), walk.get(0));
        assertEquals(c(-4, 7, 4), walk.get(walk.size() - 1));
        // every cell is crossed by the line: its centre is within sqrt(3)/2 of the line
        for (Cell cell : walk) {
            assertTrue(distanceToSegment(p(cell.x() + 0.5, cell.y() + 0.5, cell.z() + 0.5), a, b) <= Math.sqrt(3) / 2 + 1e-9,
                    cell + " is off the line");
        }
        // the walk is the same backwards
        List<Cell> back = RatlineNetRules.walk(b, a);
        assertEquals(walk.size(), back.size());
    }

    private static double distanceToSegment(Point q, Point a, Point b) {
        double dx = b.x() - a.x(), dy = b.y() - a.y(), dz = b.z() - a.z();
        double t = ((q.x() - a.x()) * dx + (q.y() - a.y()) * dy + (q.z() - a.z()) * dz) / (dx * dx + dy * dy + dz * dz);
        t = Math.max(0, Math.min(1, t));
        double ex = a.x() + dx * t - q.x(), ey = a.y() + dy * t - q.y(), ez = a.z() + dz * t - q.z();
        return Math.sqrt(ex * ex + ey * ey + ez * ez);
    }

    @Test
    void lengthAndPitchDecide() {
        Point a = p(0.5, 1.3, 0.5);
        assertEquals(Verdict.OK, RatlineNetRules.check(a, p(4.7, 5.5, 0.5), 12, 30));
        assertEquals(45.0, RatlineNetRules.pitch(a, p(4.7, 5.5, 0.5)), 1e-9);
        // vertical is fine
        assertEquals(Verdict.OK, RatlineNetRules.check(a, p(0.5, 10.5, 0.5), 12, 30));
        // 29 degrees is too flat, 31 is not
        double run = 6;
        assertEquals(Verdict.TOO_FLAT, RatlineNetRules.check(a, p(0.5 + run, 1.3 + run * Math.tan(Math.toRadians(29)), 0.5), 12, 30));
        assertEquals(Verdict.OK, RatlineNetRules.check(a, p(0.5 + run, 1.3 + run * Math.tan(Math.toRadians(31)), 0.5), 12, 30));
        // longer than twelve blocks
        assertEquals(Verdict.TOO_LONG, RatlineNetRules.check(a, p(0.5, 13.4, 0.5), 12, 30));
        assertEquals(Verdict.OK, RatlineNetRules.check(a, p(0.5, 13.3, 0.5), 12, 30));
        // a downward net is the same as an upward one
        assertEquals(Verdict.OK, RatlineNetRules.check(p(4.7, 5.5, 0.5), a, 12, 30));
        assertEquals(Verdict.SAME, RatlineNetRules.check(a, p(0.7, 1.5, 0.5), 12, 30));
    }

    @Test
    void rungsAreHalfABlockApartAndCentred() {
        List<Point> rungs = RatlineNetRules.rungs(p(0, 0, 0), p(0, 3.2, 0), 0.5);
        assertEquals(6, rungs.size());
        assertEquals(0.35, rungs.get(0).y(), 1e-9);
        assertEquals(2.85, rungs.get(5).y(), 1e-9);
    }

    @Test
    void theDrawnNetSpreadsBetweenTheRingsAndHasARungEveryHalfBlock() {
        Point a = p(0, 0, 0), b = p(3, 3, 0);
        Point side = RatlineNetRules.side(a, b, 1, 0);
        assertEquals(0, side.y(), 1e-9);
        assertEquals(0, side.x() * 3 + side.z() * 0, 1e-9, "the side is at right angles to the run");
        RatlineNetGeometry g = RatlineNetGeometry.of(a, b, side, 0.4, 0.5);
        assertEquals(a, g.left().get(0));
        assertEquals(b, g.left().get(g.left().size() - 1));
        assertEquals(g.left().size(), g.right().size());
        // 4.24 blocks long: eight rungs, the outer ones narrower
        assertEquals(8, g.rungs().size());
        Point[] mid = g.rungs().get(4);
        double width = Math.abs(mid[0].z() - mid[1].z());
        assertEquals(0.8, width, 1e-9);
        assertTrue(Math.abs(g.rungs().get(0)[0].z() - g.rungs().get(0)[1].z()) < 0.8);
        // a vertical net spreads along the fallback
        Point up = RatlineNetRules.side(a, p(0, 4, 0), 0, 1);
        assertEquals(1, up.z(), 1e-9);
    }

    @Test
    void theAssistFollowsTheLineAtAnyPitch() {
        Point lo = p(1.5, 1.3, 4.5);
        // 45 degrees, on the line: as much run as rise
        double[] v = RatlineNetRules.assist(lo, p(5.7, 5.5, 4.5), p(3.5, 3.3, 4.5), 0.1176);
        assertEquals(0.1176, v[0], 1e-9);
        assertEquals(0.1176, v[1], 1e-9);
        assertEquals(0, v[2], 1e-9);
        // the fitting order does not matter
        double[] w = RatlineNetRules.assist(p(5.7, 5.5, 4.5), lo, p(3.5, 3.3, 4.5), 0.1176);
        assertEquals(v[0], w[0], 1e-9);
        // vertical: straight up
        double[] up = RatlineNetRules.assist(lo, p(1.5, 9.3, 4.5), p(1.5, 3, 4.5), 0.1176);
        assertEquals(0, up[0], 1e-9);
        assertEquals(0.1176, up[1], 1e-9);
        // 30 degrees: the run would be 0.2 per tick, over the 0.15 a climber may go sideways, so the climb slows down
        Point b30 = p(1.5 + 6, 1.3 + 6 * Math.tan(Math.toRadians(30)), 4.5);
        double[] flat = RatlineNetRules.assist(lo, b30, p(4.5, 1.3 + 3 * Math.tan(Math.toRadians(30)), 4.5), 0.1176);
        assertEquals(0.15, flat[0], 1e-9);
        assertEquals(0.15 * Math.tan(Math.toRadians(30)), flat[1], 1e-9);
        // beside the line: pulled back towards it
        double[] side = RatlineNetRules.assist(lo, p(5.7, 5.5, 4.5), p(3.5, 3.3, 4.9), 0);
        assertTrue(side[2] < 0, "not pulled back onto the line: " + side[2]);
        // below the line at the bottom: lifted onto it
        double[] below = RatlineNetRules.assist(lo, p(5.7, 5.5, 4.5), p(2.1, 1.0, 4.5), 0.1176);
        assertTrue(below[1] > 0.1176, "not lifted onto the line: " + below[1]);
        // going down follows the line down
        double[] down = RatlineNetRules.assist(lo, p(5.7, 5.5, 4.5), p(3.5, 3.3, 4.5), -0.15);
        assertEquals(-0.15, down[0], 1e-9);
        assertEquals(-0.15, down[1], 1e-9);
    }

    // ------------------------------------------------------------------ RL4: fans

    /** The hoop of the fan tests: on the west side of a mast at x 3, six up and two in from the rail at x 0. */
    private static final Point HUB = p(2.7, 7.5, 4.5);

    private static List<Point> rail(double... zs) {
        List<Point> out = new java.util.ArrayList<>();
        for (double z : zs) {
            out.add(p(0.5, 1.3, z));
        }
        return out;
    }

    /** {@link RatlineNetRules#fanCells} for chainplates on the rail at {@code feet}, all joined, in fan order. */
    private static List<FanCell> fan(List<Point> feet) {
        int[] order = RatlineNetRules.fanOrder(HUB, feet);
        List<Point> ordered = new java.util.ArrayList<>();
        List<List<Cell>> strands = new java.util.ArrayList<>();
        for (int i : order) {
            ordered.add(feet.get(i));
            strands.add(RatlineNetRules.netCells(feet.get(i), HUB));
        }
        boolean[] join = new boolean[ordered.size()];
        java.util.Arrays.fill(join, true);
        return RatlineNetRules.fanCells(HUB, ordered, strands, join);
    }

    private static void assertDirection(Cell c, double x, double y, double z) {
        Point d = RatlineNetRules.climbDirection(c, HUB);
        assertEquals(x, d.x(), 1e-3, c + " x");
        assertEquals(y, d.y(), 1e-3, c + " y");
        assertEquals(z, d.z(), 1e-3, c + " z");
    }

    @Test
    void aThreeWideFanOfChainplatesOneBlockApartIsTwoTriangles() {
        // chainplates on the rail at z 3, 4 and 5 (rings z + 0.5); fan order by bearing: z 5, 4, 3
        List<Point> feet = rail(3.5, 4.5, 5.5);
        int[] order = RatlineNetRules.fanOrder(HUB, feet);
        assertArrayEquals(new int[] {2, 1, 0}, order);
        // every cell of each triangle (hub, foot, foot)
        assertEquals(List.of(c(0, 1, 4), c(0, 1, 5), c(0, 2, 4), c(0, 2, 5), c(1, 2, 4), c(1, 2, 5), c(1, 3, 4),
                        c(1, 3, 5), c(1, 4, 4), c(1, 4, 5), c(1, 5, 4), c(2, 5, 4), c(2, 6, 4), c(2, 7, 4)),
                RatlineNetRules.triangleCells(HUB, feet.get(2), feet.get(1)));
        assertEquals(List.of(c(0, 1, 3), c(0, 1, 4), c(0, 2, 3), c(0, 2, 4), c(1, 2, 3), c(1, 2, 4), c(1, 3, 3),
                        c(1, 3, 4), c(1, 4, 3), c(1, 4, 4), c(1, 5, 4), c(2, 5, 4), c(2, 6, 4), c(2, 7, 4)),
                RatlineNetRules.triangleCells(HUB, feet.get(1), feet.get(0)));
        // with the strands a block apart the strands' own cells cover both triangles: the fan is the 15 cells of the
        // triangles without the fittings' (the hoop at 2 7 4, the chainplates at 0 1 3..5), nothing left to fill
        List<FanCell> cells = fan(feet);
        java.util.Set<Cell> got = new java.util.HashSet<>();
        for (FanCell f : cells) {
            got.add(f.cell());
            assertTrue(!f.fill(), f + " fills nothing here");
        }
        assertEquals(java.util.Set.of(c(0, 2, 3), c(0, 2, 4), c(0, 2, 5), c(1, 2, 3), c(1, 2, 4), c(1, 2, 5),
                c(1, 3, 3), c(1, 3, 4), c(1, 3, 5), c(1, 4, 3), c(1, 4, 4), c(1, 4, 5), c(1, 5, 4), c(2, 5, 4),
                c(2, 6, 4)), got);
        // each cell's climb direction points from its centre to the hoop's ring
        assertDirection(c(0, 2, 3), 0.396, 0.900, 0.180);
        assertDirection(c(0, 2, 4), 0.403, 0.915, 0);
        assertDirection(c(0, 2, 5), 0.396, 0.900, -0.180);
        assertDirection(c(1, 2, 3), 0.229, 0.955, 0.191);
        assertDirection(c(1, 2, 4), 0.233, 0.972, 0);
        assertDirection(c(1, 2, 5), 0.229, 0.955, -0.191);
        assertDirection(c(1, 3, 3), 0.279, 0.931, 0.233);
        assertDirection(c(1, 3, 4), 0.287, 0.958, 0);
        assertDirection(c(1, 3, 5), 0.279, 0.931, -0.233);
        assertDirection(c(1, 4, 3), 0.355, 0.887, 0.296);
        assertDirection(c(1, 4, 4), 0.371, 0.928, 0);
        assertDirection(c(1, 4, 5), 0.355, 0.887, -0.296);
        assertDirection(c(1, 5, 4), 0.514, 0.857, 0);
        assertDirection(c(2, 5, 4), 0.100, 0.995, 0);
        assertDirection(c(2, 6, 4), 0.196, 0.981, 0);
        // the one top cell: right under the hoop
        for (Cell c : got) {
            assertEquals(c.equals(c(2, 6, 4)), RatlineNetRules.isTop(c, HUB), c + " top");
        }
    }

    @Test
    void aWiderFanFillsTheTrianglesBetweenItsStrands() {
        // chainplates two blocks apart (z 2, 4, 6): the rail cells between them and the ones above are fill cells,
        // owned by the strand before them in fan order (z 6 owns z 5, z 4 owns z 3)
        List<FanCell> cells = fan(rail(2.5, 4.5, 6.5));
        List<FanCell> fills = cells.stream().filter(FanCell::fill).toList();
        assertEquals(List.of(new FanCell(c(0, 1, 5), 0, true), new FanCell(c(0, 2, 5), 0, true),
                new FanCell(c(0, 1, 3), 1, true), new FanCell(c(0, 2, 3), 1, true)), fills);
        assertEquals(25, cells.size());
        assertDirection(c(0, 1, 3), 0.340, 0.928, 0.155);
        assertDirection(c(0, 1, 5), 0.340, 0.928, -0.155);
        // no cell twice, none in a fitting
        assertEquals(cells.size(), cells.stream().map(FanCell::cell).distinct().count());
        for (FanCell f : cells) {
            assertTrue(!f.cell().equals(c(2, 7, 4)) && !(f.cell().x() == 0 && f.cell().y() == 1 && f.cell().z() % 2 == 0),
                    f + " is a fitting's cell");
        }
    }

    @Test
    void theFanOrderFollowsTheBearingAndOpensAtTheWidestGap() {
        Point hub = p(0, 5, 0);
        // bearings 0 (east), 90 (south), 180 (west), 350
        List<Point> feet = List.of(p(3, 0, 0), p(0, 0, 3), p(-3, 0, 0), p(3, 0, -0.53));
        assertEquals(0, RatlineNetRules.bearing(hub, feet.get(0)), 1e-9);
        assertEquals(90, RatlineNetRules.bearing(hub, feet.get(1)), 1e-9);
        assertEquals(180, RatlineNetRules.bearing(hub, feet.get(2)), 1e-9);
        assertEquals(350, RatlineNetRules.bearing(hub, feet.get(3)), 0.1);
        // the widest gap is 180..350: the fan runs 350, 0, 90, 180
        assertArrayEquals(new int[] {3, 0, 1, 2}, RatlineNetRules.fanOrder(hub, feet));
        // a foot straight under the hub has bearing 0; one strand is its own fan
        assertEquals(0, RatlineNetRules.bearing(hub, p(0, 0, 0)), 1e-9);
        assertArrayEquals(new int[] {0}, RatlineNetRules.fanOrder(hub, List.of(p(1, 0, 1))));
    }

    @Test
    void neighboursFurtherApartThanTheSpreadStaySeparate() {
        assertTrue(RatlineNetRules.joins(p(0.5, 1.3, 0.5), p(0.5, 1.3, 6.5), 6));
        assertTrue(!RatlineNetRules.joins(p(0.5, 1.3, 0.5), p(0.5, 1.3, 6.6), 6));
        assertEquals(6.1, RatlineNetRules.spread(p(0.5, 1.3, 0.5), p(0.5, 1.3, 6.6)), 1e-9);
        // without the join, no fill between the two
        List<Point> feet = rail(2.5, 6.5);
        int[] order = RatlineNetRules.fanOrder(HUB, feet);
        List<Point> ordered = List.of(feet.get(order[0]), feet.get(order[1]));
        List<List<Cell>> strands = List.of(RatlineNetRules.netCells(ordered.get(0), HUB), RatlineNetRules.netCells(ordered.get(1), HUB));
        assertTrue(RatlineNetRules.fanCells(HUB, ordered, strands, new boolean[] {false, false}).stream().noneMatch(FanCell::fill));
        assertTrue(RatlineNetRules.fanCells(HUB, ordered, strands, new boolean[] {true, false}).stream().anyMatch(FanCell::fill));
    }

    @Test
    void theDrawnFanHasEveryStrandAndRungsNarrowingToTheHoop() {
        List<Point> feet = rail(2.5, 4.5, 6.5);
        RatlineNetGeometry.Fan g = RatlineNetGeometry.fan(HUB, feet, 0.5);
        assertEquals(3, g.ropes().size());
        assertEquals(feet.get(0), g.ropes().get(0)[0]);
        assertEquals(HUB, g.ropes().get(0)[1]);
        // the outer strands are 6.9 blocks long: 13 rungs, each from the first strand to the last through the middle one
        assertEquals(13, g.rungs().size());
        double last = Double.POSITIVE_INFINITY;
        for (List<Point> rung : g.rungs()) {
            assertEquals(3, rung.size());
            double width = Math.abs(rung.get(2).z() - rung.get(0).z());
            assertTrue(width < last, "the rungs do not narrow towards the hoop");
            last = width;
            // the middle point is halfway between the outer ones (the strands at the same fraction of their length)
            assertEquals((rung.get(0).z() + rung.get(2).z()) / 2, rung.get(1).z(), 1e-9);
            assertEquals(rung.get(0).y(), rung.get(1).y(), 1e-9);
        }
        assertEquals(4.0, Math.abs(g.rungs().get(0).get(2).z() - g.rungs().get(0).get(0).z()), 0.4);
    }

    @Test
    void theClimbIntentComesFromTheKeysAndVanillasClimb() {
        assertEquals(RatlineNetAssist.CLIMB_SPEED, RatlineNetAssist.climb(-0.1, 1f, false));
        assertEquals(RatlineNetAssist.CLIMB_SPEED, RatlineNetAssist.climb(0.1176, 0f, false), "a jump climb is a climb up");
        assertEquals(-RatlineNetAssist.SLIDE_SPEED, RatlineNetAssist.climb(0, -1f, false));
        assertEquals(0, RatlineNetAssist.climb(-0.05, 0f, true));
        assertEquals(-0.05, RatlineNetAssist.climb(-0.05, 0f, false));
        assertEquals(-RatlineNetAssist.SLIDE_SPEED, RatlineNetAssist.climb(-0.5, 0f, false));
    }
}
