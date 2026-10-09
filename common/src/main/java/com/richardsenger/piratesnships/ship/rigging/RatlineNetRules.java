package com.richardsenger.piratesnships.ship.rigging;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure rules of the ratline nets (RL2, docs/design.md §4.8 "Ratline nets between fittings"): the cells a net strung
 * between two ring points takes (a 3D DDA walk), whether a net may be strung (length and pitch), and the climbing
 * assist's velocity along the net. Coordinates are block coordinates in one frame (the world, or one ship's plot).
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

    /** Upper bound of the cells one walk may return (a guard against a broken input; the length rule is far below). */
    private static final int MAX_CELLS = 256;

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
