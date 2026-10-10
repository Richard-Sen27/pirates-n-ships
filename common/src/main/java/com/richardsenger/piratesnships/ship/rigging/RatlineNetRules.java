package com.richardsenger.piratesnships.ship.rigging;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure rules of the ratline nets (RL2, docs/design.md §4.8 "Ratline nets between fittings"): the cells a net strung
 * between two ring points takes (a 3D DDA walk), whether a net may be strung (length and pitch), and the climbing
 * assist's velocity along the net; RL4's fans (several strands sharing a hub, the triangles between them); RL5's sheets
 * (strands in a row joined into a rectangle or a trapezoid, an anchor's capacity). Coordinates are block coordinates in
 * one frame (the world, or one ship's plot).
 */
public final class RatlineNetRules {

    /** A block cell. */
    public record Cell(int x, int y, int z) {
    }

    /** A point in block coordinates. */
    public record Point(double x, double y, double z) {
        Point minus(Point o) {
            return new Point(x - o.x, y - o.y, z - o.z);
        }

        double length() {
            return Math.sqrt(x * x + y * y + z * z);
        }

        Cell cell() {
            return new Cell((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        }
    }

    /** The outcome of {@link #check}. */
    public enum Verdict {
        OK,
        /** Both ends in the same block. */
        SAME,
        /** Longer than the maximum length. */
        TOO_LONG,
        /** Flatter than the minimum pitch. */
        TOO_FLAT
    }

    /**
     * Upper bound of the cells one walk may return (a guard against a broken input): a line of the longest allowed net
     * ({@code rigging.net_max_length} at most 64) crosses at most 3 x 64 + 1 = 193 cells.
     */
    private static final int MAX_CELLS = 256;
    /** Upper bound of the cells of one fan triangle (legs of 64 blocks, a base of 16: about 1500 cells at most). */
    private static final int MAX_TRIANGLE_CELLS = 16384;

    private RatlineNetRules() {
    }

    /** The pitch of the line from {@code a} to {@code b} above the horizontal [degrees, 0..90]. */
    public static double pitch(Point a, Point b) {
        Point d = b.minus(a);
        return Math.toDegrees(Math.atan2(Math.abs(d.y), Math.hypot(d.x, d.z)));
    }

    /** Whether a net may run from ring {@code a} to ring {@code b} (length first, then pitch). */
    public static Verdict check(Point a, Point b, double maxLength, double minPitch) {
        if (a.cell().equals(b.cell())) {
            return Verdict.SAME;
        }
        if (b.minus(a).length() > maxLength + 1.0e-9) {
            return Verdict.TOO_LONG;
        }
        if (pitch(a, b) < minPitch - 1.0e-9) {
            return Verdict.TOO_FLAT;
        }
        return Verdict.OK;
    }

    /**
     * Every cell the segment from {@code a} to {@code b} passes through, in order from {@code a}'s cell to {@code b}'s
     * (Amanatides and Woo's voxel walk). Consecutive cells share a face: where the line crosses an edge or a corner
     * exactly, the walk steps one axis at a time (x before y before z on a tie).
     */
    public static List<Cell> walk(Point a, Point b) {
        List<Cell> out = new ArrayList<>();
        Cell start = a.cell();
        Cell end = b.cell();
        int x = start.x(), y = start.y(), z = start.z();
        out.add(start);
        double dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
        int sx = (int) Math.signum(dx), sy = (int) Math.signum(dy), sz = (int) Math.signum(dz);
        double tdx = sx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double tdy = sy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double tdz = sz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);
        double tmx = sx == 0 ? Double.POSITIVE_INFINITY : ((sx > 0 ? x + 1 - a.x : a.x - x) * tdx);
        double tmy = sy == 0 ? Double.POSITIVE_INFINITY : ((sy > 0 ? y + 1 - a.y : a.y - y) * tdy);
        double tmz = sz == 0 ? Double.POSITIVE_INFINITY : ((sz > 0 ? z + 1 - a.z : a.z - z) * tdz);
        while (!(x == end.x() && y == end.y() && z == end.z()) && out.size() < MAX_CELLS) {
            if (tmx <= tmy && tmx <= tmz) {
                if (tmx > 1.0) break;
                x += sx;
                tmx += tdx;
            } else if (tmy <= tmz) {
                if (tmy > 1.0) break;
                y += sy;
                tmy += tdy;
            } else {
                if (tmz > 1.0) break;
                z += sz;
                tmz += tdz;
            }
            out.add(new Cell(x, y, z));
        }
        if (!out.get(out.size() - 1).equals(end)) {
            out.add(end); // float rounding left the walk one step short of b's cell
        }
        return out;
    }

    /** The cells of the net between the two fittings: {@link #walk} without the fittings' own cells. */
    public static List<Cell> netCells(Point a, Point b) {
        List<Cell> all = walk(a, b);
        Cell ca = a.cell(), cb = b.cell();
        List<Cell> out = new ArrayList<>(all.size());
        for (Cell c : all) {
            if (!c.equals(ca) && !c.equals(cb)) {
                out.add(c);
            }
        }
        return out;
    }

    /**
     * Points along the line from {@code a} to {@code b} every {@code spacing} blocks, as many as fit, centred so that
     * both ends keep at least half a spacing clear (the rings).
     */
    public static List<Point> rungs(Point a, Point b, double spacing) {
        Point d = b.minus(a);
        double len = d.length();
        int count = (int) Math.floor(len / spacing + 1.0e-9);
        List<Point> out = new ArrayList<>(Math.max(0, count));
        double first = (len - (count - 1) * spacing) / 2;
        for (int i = 0; i < count; i++) {
            double t = (first + i * spacing) / len;
            out.add(new Point(a.x + d.x * t, a.y + d.y * t, a.z + d.z * t));
        }
        return out;
    }

    /**
     * The sideways unit vector of a net from {@code a} to {@code b} (horizontal, at right angles to the line): the side
     * ropes run at {@code ±half} of it. A vertical net takes {@code fallbackX, fallbackZ} (the fitting's facing turned a
     * quarter, so the net spreads along the mast face).
     */
    public static Point side(Point a, Point b, double fallbackX, double fallbackZ) {
        Point d = b.minus(a);
        double h = Math.hypot(d.x, d.z);
        if (h < 1.0e-3) {
            double f = Math.hypot(fallbackX, fallbackZ);
            return f < 1.0e-9 ? new Point(1, 0, 0) : new Point(fallbackX / f, 0, fallbackZ / f);
        }
        return new Point(-d.z / h, 0, d.x / h);
    }

    // ------------------------------------------------------------------ fans (RL4)

    /** A cell of a fan: the strand that owns it (its index in fan order) and whether it fills a triangle. */
    public record FanCell(Cell cell, int owner, boolean fill) {
    }

    /**
     * The bearing of a strand's foot ring {@code foot} around its hub ring {@code hub} [degrees, 0..360): the
     * horizontal angle {@code atan2(dz, dx)}. A foot straight under the hub has bearing 0.
     */
    public static double bearing(Point hub, Point foot) {
        double dx = foot.x - hub.x, dz = foot.z - hub.z;
        if (Math.abs(dx) < 1.0e-9 && Math.abs(dz) < 1.0e-9) {
            return 0;
        }
        double a = Math.toDegrees(Math.atan2(dz, dx));
        return a < 0 ? a + 360 : a;
    }

    /**
     * The fan order of strands with feet {@code feet} round the hub {@code hub}: their indices sorted by
     * {@link #bearing}, starting after the widest gap between two neighbouring bearings (the open side of the fan), so
     * neighbours in the order are neighbours in the fan and the first and the last are its outer strands. Equal bearings
     * keep the input order.
     */
    public static int[] fanOrder(Point hub, List<Point> feet) {
        int n = feet.size();
        Integer[] idx = new Integer[n];
        double[] b = new double[n];
        for (int i = 0; i < n; i++) {
            idx[i] = i;
            b[i] = bearing(hub, feet.get(i));
        }
        java.util.Arrays.sort(idx, (p, q) -> Double.compare(b[p], b[q]));
        int start = 0;
        if (n > 1) {
            double widest = -1;
            for (int i = 0; i < n; i++) {
                double next = i + 1 < n ? b[idx[i + 1]] : b[idx[0]] + 360;
                double gap = next - b[idx[i]];
                if (gap > widest + 1.0e-9) {
                    widest = gap;
                    start = (i + 1) % n;
                }
            }
        }
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = idx[(start + i) % n];
        }
        return out;
    }

    /** Whether two neighbouring strands with feet {@code a} and {@code b} form one net: their feet at most {@code maxSpread} apart. */
    public static boolean joins(Point a, Point b, double maxSpread) {
        return b.minus(a).length() <= maxSpread + 1.0e-9;
    }

    /** The distance between two feet [blocks]. */
    public static double spread(Point a, Point b) {
        return b.minus(a).length();
    }

    /** How far into a cell a triangle must reach to take it [blocks]: a triangle only touching a face takes nothing. */
    private static final double TOUCH = 1.0e-6;

    /**
     * Every cell the triangle ({@code hub}, {@code a}, {@code b}) passes through, sorted by y, then x, then z: each cell
     * of the triangle's bounding box tested exactly against it (the separating-axis test of a triangle and a box, the
     * box shrunk by {@link #TOUCH}). A walk of cross lines every half block misses the slivers where the
     * triangle only clips a cell's corner, and a climber would fall through those.
     */
    public static List<Cell> triangleCells(Point hub, Point a, Point b) {
        int x0 = (int) Math.floor(Math.min(hub.x, Math.min(a.x, b.x))), x1 = (int) Math.floor(Math.max(hub.x, Math.max(a.x, b.x)));
        int y0 = (int) Math.floor(Math.min(hub.y, Math.min(a.y, b.y))), y1 = (int) Math.floor(Math.max(hub.y, Math.max(a.y, b.y)));
        int z0 = (int) Math.floor(Math.min(hub.z, Math.min(a.z, b.z))), z1 = (int) Math.floor(Math.max(hub.z, Math.max(a.z, b.z)));
        List<Cell> out = new ArrayList<>();
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    if (overlaps(hub, a, b, x + 0.5, y + 0.5, z + 0.5, 0.5 - TOUCH)) {
                        out.add(new Cell(x, y, z));
                    }
                }
            }
        }
        if (out.size() > MAX_TRIANGLE_CELLS) {
            throw new IllegalArgumentException("a fan triangle of " + out.size() + " cells");
        }
        return out;
    }

    /** Whether the triangle (p, q, r) and the cube round (cx, cy, cz) with half size h overlap (Akenine-Möller's axes). */
    static boolean overlaps(Point p, Point q, Point r, double cx, double cy, double cz, double h) {
        double[] v0 = {p.x - cx, p.y - cy, p.z - cz};
        double[] v1 = {q.x - cx, q.y - cy, q.z - cz};
        double[] v2 = {r.x - cx, r.y - cy, r.z - cz};
        double[][] v = {v0, v1, v2};
        double[][] f = {sub(v1, v0), sub(v2, v1), sub(v0, v2)};
        // the box's face normals
        for (int i = 0; i < 3; i++) {
            double lo = Math.min(v0[i], Math.min(v1[i], v2[i])), hi = Math.max(v0[i], Math.max(v1[i], v2[i]));
            if (lo > h || hi < -h) {
                return false;
            }
        }
        // the nine cross products of the box's axes and the triangle's edges
        for (int i = 0; i < 3; i++) {
            double[] e = new double[3];
            e[i] = 1;
            for (double[] edge : f) {
                double[] axis = cross(e, edge);
                if (separates(axis, v, h)) {
                    return false;
                }
            }
        }
        // the triangle's plane
        return !separates(cross(f[0], f[1]), v, h);
    }

    private static boolean separates(double[] axis, double[][] v, double h) {
        double p0 = dot(axis, v[0]), p1 = dot(axis, v[1]), p2 = dot(axis, v[2]);
        double r = h * (Math.abs(axis[0]) + Math.abs(axis[1]) + Math.abs(axis[2]));
        return Math.min(p0, Math.min(p1, p2)) > r || Math.max(p0, Math.max(p1, p2)) < -r;
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    /**
     * The cells of a fan round the hub ring {@code hub}: strands with feet {@code feet} and their own cells
     * {@code strandCells}, both in fan order ({@link #fanOrder}); {@code joinNext[k]} when strand k and k + 1 form one
     * net ({@link #joins}). First every strand's own cells (a cell two strands share belongs to the earlier one), then
     * the cells of each joined pair's triangle ({@link #triangleCells}) not yet taken, owned by the earlier strand of
     * the pair (the one on their left by bearing). The hub's and every foot's own cell are never cells of the fan.
     */
    public static List<FanCell> fanCells(Point hub, List<Point> feet, List<List<Cell>> strandCells, boolean[] joinNext) {
        java.util.Set<Cell> fittings = new java.util.HashSet<>();
        fittings.add(hub.cell());
        for (Point f : feet) {
            fittings.add(f.cell());
        }
        java.util.Map<Cell, FanCell> out = new java.util.LinkedHashMap<>();
        for (int k = 0; k < strandCells.size(); k++) {
            for (Cell c : strandCells.get(k)) {
                if (!fittings.contains(c)) {
                    out.putIfAbsent(c, new FanCell(c, k, false));
                }
            }
        }
        for (int k = 0; k + 1 < feet.size(); k++) {
            if (k < joinNext.length && joinNext[k]) {
                for (Cell c : triangleCells(hub, feet.get(k), feet.get(k + 1))) {
                    if (!fittings.contains(c)) {
                        out.putIfAbsent(c, new FanCell(c, k, true));
                    }
                }
            }
        }
        return new ArrayList<>(out.values());
    }

    // ------------------------------------------------------------------ sheets (RL5)

    /** What holds a strand's end (RL5): a chainplate and a railing latch hold one strand, a mast hoop several. */
    public enum Anchor { CHAINPLATE, LATCH, HOOP }

    /** How many strands {@code anchor} holds: a hoop {@code maxStrands} ({@code rigging.net_max_strands}), any other one. */
    public static int capacity(Anchor anchor, int maxStrands) {
        return anchor == Anchor.HOOP ? Math.max(1, maxStrands) : 1;
    }

    /** Whether {@code anchor}, holding {@code held} strands, takes another (so a net is never wider at the top). */
    public static boolean takesAnother(Anchor anchor, int held, int maxStrands) {
        return held < capacity(anchor, maxStrands);
    }

    /** Tolerance of "at one height" and "along one axis" for ring points [blocks]. */
    static final double ROW_EPS = 1.0e-3;

    /**
     * The horizontal axis along which {@code a} and {@code b} lie in one row at one height: 0 for x, 2 for z; -1 when
     * they are at different heights, on a diagonal or the same point.
     */
    public static int rowAxis(Point a, Point b) {
        if (Math.abs(a.y - b.y) > ROW_EPS) {
            return -1;
        }
        boolean sameX = Math.abs(a.x - b.x) <= ROW_EPS, sameZ = Math.abs(a.z - b.z) <= ROW_EPS;
        if (sameX == sameZ) {
            return -1;
        }
        return sameZ ? 0 : 2;
    }

    private static double along(Point p, int axis) {
        return axis == 0 ? p.x : p.z;
    }

    /** Whether two ring points are the same ring. */
    public static boolean sameRing(Point a, Point b) {
        return b.minus(a).length() <= ROW_EPS;
    }

    /**
     * RL5's join rule: two neighbouring strands (feet {@code footA}, {@code footB}, hubs {@code hubA}, {@code hubB})
     * form one sheet when their feet lie in a row at one height at most {@code maxSpread} apart, and their hubs are the
     * same ring or lie in a row at one height at most {@code maxSpread} apart without crossing the feet's order.
     */
    public static boolean joins(Point footA, Point hubA, Point footB, Point hubB, double maxSpread) {
        if (rowAxis(footA, footB) < 0 || spread(footA, footB) > maxSpread + 1.0e-9) {
            return false;
        }
        if (sameRing(hubA, hubB)) {
            return true;
        }
        if (rowAxis(hubA, hubB) < 0 || spread(hubA, hubB) > maxSpread + 1.0e-9) {
            return false;
        }
        Point df = footB.minus(footA), dh = hubB.minus(hubA);
        return df.x * dh.x + df.z * dh.z >= -1.0e-9;
    }

    /**
     * Which of two joined strands owns the cells between them (RL5, the RL4 rule of "the strand on their left by
     * bearing"): {@code a} when the bearing round the middle of their hubs grows from a's foot to b's, else {@code b}; when
     * the hubs stand right over the feet's row (no bearing to tell), the one further along the row's axis.
     */
    static boolean ownsPair(Point footA, Point hubA, Point footB, Point hubB) {
        double mx = (hubA.x + hubB.x) / 2, mz = (hubA.z + hubB.z) / 2;
        double ux = footA.x - mx, uz = footA.z - mz, vx = footB.x - mx, vz = footB.z - mz;
        double cross = ux * vz - uz * vx;
        if (Math.abs(cross) > 1.0e-6) {
            return cross > 0;
        }
        int axis = rowAxis(footA, footB);
        return axis >= 0 && along(footA, axis) > along(footB, axis);
    }

    /**
     * RL5: the joins of a group of strands (feet {@code feet}, hubs {@code hubs}, {@code joinable[k]} when strand k may
     * join at all: its hub is a mast hoop). Each strand's nearest neighbour along a row (x before z on a tie) is its
     * neighbour; neighbours that {@link #joins join} form a pair, owned by {@link #ownsPair}. Returns for each strand the
     * index of the strand it owns a pair with, or -1 (a strand owns at most one pair; each strand is in at most two).
     */
    public static int[] sheetPartners(List<Point> feet, List<Point> hubs, boolean[] joinable, double maxSpread) {
        int n = feet.size();
        int[] out = new int[n];
        java.util.Arrays.fill(out, -1);
        for (int k = 0; k < n; k++) {
            if (!joinable[k]) {
                continue;
            }
            for (int axis : new int[] {0, 2}) {
                int best = -1;
                double bestD = Double.POSITIVE_INFINITY;
                for (int j = 0; j < n; j++) {
                    if (j == k || !joinable[j] || rowAxis(feet.get(k), feet.get(j)) != axis) {
                        continue;
                    }
                    double d = along(feet.get(j), axis) - along(feet.get(k), axis);
                    if (d > ROW_EPS && d < bestD - 1.0e-9) {
                        best = j;
                        bestD = d;
                    }
                }
                if (best < 0 || !joins(feet.get(k), hubs.get(k), feet.get(best), hubs.get(best), maxSpread)) {
                    continue;
                }
                boolean kOwns = ownsPair(feet.get(k), hubs.get(k), feet.get(best), hubs.get(best));
                int owner = kOwns ? k : best, other = kOwns ? best : k;
                if (out[owner] < 0) {
                    out[owner] = other;
                }
            }
        }
        return out;
    }

    /** A cell of a sheet: the strand that owns it, whether it fills a pair, and the pair's other strand when its hub differs (else -1). */
    public record SheetCell(Cell cell, int owner, boolean fill, int otherHub) {
    }

    /**
     * RL5: the cells of a group of strands (feet, hubs, their own cells, {@link #sheetPartners}): first every strand's
     * own cells (a cell two strands share belongs to the earlier one), then each pair's fill not yet taken, owned by
     * the pair's owner: the triangle (hub, both feet) when both strands share a hub (RL4's fan), else the quadrilateral
     * of both feet and both hubs as two triangles (a rectangle or a trapezoid). The fittings' own cells
     * ({@code fittings}) are never cells of the sheet.
     */
    public static List<SheetCell> sheetCells(List<Point> feet, List<Point> hubs, List<List<Cell>> strandCells,
                                             int[] partner, java.util.Set<Cell> fittings) {
        java.util.Map<Cell, SheetCell> out = new java.util.LinkedHashMap<>();
        for (int k = 0; k < strandCells.size(); k++) {
            for (Cell c : strandCells.get(k)) {
                if (!fittings.contains(c)) {
                    out.putIfAbsent(c, new SheetCell(c, k, false, -1));
                }
            }
        }
        for (int k = 0; k < partner.length; k++) {
            int p = partner[k];
            if (p < 0) {
                continue;
            }
            boolean shared = sameRing(hubs.get(k), hubs.get(p));
            List<Cell> fill = new ArrayList<>();
            if (shared) {
                fill.addAll(triangleCells(hubs.get(k), feet.get(k), feet.get(p)));
            } else {
                fill.addAll(triangleCells(feet.get(k), feet.get(p), hubs.get(p)));
                fill.addAll(triangleCells(feet.get(k), hubs.get(p), hubs.get(k)));
            }
            for (Cell c : fill) {
                if (!fittings.contains(c)) {
                    out.putIfAbsent(c, new SheetCell(c, k, true, shared ? -1 : p));
                }
            }
        }
        return new ArrayList<>(out.values());
    }

    /**
     * Where a fill cell between two hubs climbs to (RL5): the point of the hubs' segment nearest the cell's centre, so a
     * rectangle climbs straight up its column and a trapezoid towards the nearest point of its top edge.
     */
    public static Point topPoint(Cell cell, Point hubA, Point hubB) {
        Point c = centre(cell);
        Point d = hubB.minus(hubA);
        double len2 = d.x * d.x + d.y * d.y + d.z * d.z;
        double t = len2 < 1.0e-12 ? 0 : Math.max(0, Math.min(1, ((c.x - hubA.x) * d.x + (c.y - hubA.y) * d.y + (c.z - hubA.z) * d.z) / len2));
        return new Point(hubA.x + d.x * t, hubA.y + d.y * t, hubA.z + d.z * t);
    }

    /** The climb direction of a net cell: from its centre towards the hub ring, unit (straight up when it is there). */
    public static Point climbDirection(Cell cell, Point hub) {
        Point d = hub.minus(centre(cell));
        double len = d.length();
        return len < 1.0e-9 ? new Point(0, 1, 0) : new Point(d.x / len, d.y / len, d.z / len);
    }

    /** Whether {@code cell} is a top cell of its net: the next cell from its centre towards the hub ring is the hub's. */
    public static boolean isTop(Cell cell, Point hub) {
        List<Cell> w = walk(centre(cell), hub);
        return w.size() >= 2 && w.get(1).equals(hub.cell());
    }

    /** The centre of a cell. */
    public static Point centre(Cell c) {
        return new Point(c.x() + 0.5, c.y() + 0.5, c.z() + 0.5);
    }

    // ------------------------------------------------------------------ climbing assist

    /** Vanilla's horizontal speed limit on a climbable block ({@code LivingEntity#handleOnClimbable}) [blocks/tick]. */
    public static final double CLIMB_HORIZONTAL_LIMIT = 0.15;
    /** How strongly the assist pulls a climber back onto the line, per tick, as a fraction of the sideways offset. */
    public static final double CENTRE_GAIN = 0.35;

    /**
     * The climbing assist (RL2, {@code rigging.net_assist}): the velocity for a climber with its feet at {@code feet} in
     * a net from {@code a} to {@code b} that climbs at {@code vy} blocks of height per tick (up, down, or 0 to hold). The
     * climber moves along the line ({@code vy} of rise and the matching run towards the higher ring) plus a pull of
     * {@link #CENTRE_GAIN} times its offset from the nearest point of the line, so it keeps to the line at any pitch
     * and joins it from beside or below. When the move is faster sideways than vanilla lets a climber go
     * ({@link #CLIMB_HORIZONTAL_LIMIT} per axis), all of it slows down instead (a flat net climbs slower). Returns
     * {x, y, z}.
     *
     * <p>Over the top (RL3): a climber whose feet are above the higher ring and that climbs on ({@code vy > 0}) is no
     * longer pulled; it rises straight up at {@code vy} and keeps its own horizontal velocity ({@code ownX},
     * {@code ownZ}, what its input and vanilla's tick gave it), so forward carries it off the net onto whatever stands
     * at the top. Holding or climbing down there it is pulled back onto the line as anywhere else.
     */
    public static double[] assist(Point a, Point b, Point feet, double vy, double ownX, double ownZ) {
        Point hi = a.y <= b.y ? b : a;
        if (vy > 0 && feet.y >= hi.y) {
            return new double[] {clampAcross(ownX), vy, clampAcross(ownZ)};
        }
        return assist(a, b, feet, vy);
    }

    private static double clampAcross(double v) {
        return Math.max(-CLIMB_HORIZONTAL_LIMIT, Math.min(CLIMB_HORIZONTAL_LIMIT, v));
    }

    /** {@link #assist(Point, Point, Point, double, double, double)} without the release over the top. */
    public static double[] assist(Point a, Point b, Point feet, double vy) {
        Point lo = a.y <= b.y ? a : b;
        Point hi = a.y <= b.y ? b : a;
        Point d = hi.minus(lo);
        double len2 = d.x * d.x + d.y * d.y + d.z * d.z;
        if (d.y < 1.0e-6 || len2 < 1.0e-9) {
            return new double[] {0, vy, 0};
        }
        // along the line: vy of rise and the run that goes with it
        double ax = vy * d.x / d.y, az = vy * d.z / d.y;
        // the nearest point of the line (clamped to the net) and the pull towards it
        Point r = feet.minus(lo);
        double t = Math.max(0, Math.min(1, (r.x * d.x + r.y * d.y + r.z * d.z) / len2));
        double cx = (lo.x + d.x * t - feet.x) * CENTRE_GAIN;
        double cy = (lo.y + d.y * t - feet.y) * CENTRE_GAIN;
        double cz = (lo.z + d.z * t - feet.z) * CENTRE_GAIN;
        double vx = ax + cx, vyOut = vy + cy, vz = az + cz;
        double over = Math.max(Math.abs(vx), Math.abs(vz)) / CLIMB_HORIZONTAL_LIMIT;
        if (over > 1) {
            // too fast sideways for a climber: slow the whole move down so it keeps to the line
            double slow = 1 / over;
            return new double[] {vx * slow, vyOut * slow, vz * slow};
        }
        return new double[] {vx, vyOut, vz};
    }
}
