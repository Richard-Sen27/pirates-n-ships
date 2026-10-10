package com.richardsenger.piratesnships.worldsim.lane;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * Sea lanes by A* over a {@link SeaGrid} (WS2, pure: no world access).
 *
 * <ul>
 *   <li>Endpoints: the cell of the start (end) block if it is sea, else the nearest sea cell within
 *       {@link Params#endpointRadius} cells (by distance, ties by scan order).</li>
 *   <li>Moves: 8 neighbours, cost 1 straight and √2 diagonal; a diagonal may not cut a land corner (both orthogonal
 *       cells must be passable). Entering a shore cell ({@link SeaGrid#isShore}: passable, but the coast itself) costs
 *       {@link Params#shoreCost} times as much; entering an open sea cell that touches land or shore (any of its 8
 *       neighbours) costs {@link Params#landMarginCost} times as much, so lanes keep off the coasts where they can.</li>
 *   <li>Heuristic: octile distance (admissible, the cheapest move costs 1).</li>
 *   <li>Budget ({@link Params#budget}): proportional to the straight distance between the endpoint cells,
 *       {@link Params#cellsPerCell} cells per cell, clamped to {@link Params#minCells}..{@link Params#maxCells}; once
 *       spent the search gives up ({@link Status#BUDGET}). A search that runs out of cells first ({@link Status#NO_PATH})
 *       has searched the whole sea around the start: the two ports lie on separate seas.</li>
 *   <li>Result: the exact start and end blocks when their own cells are sea (else the endpoint cells' centres) with the
 *       centres of the cells where the path turns in between (collinear runs merged).</li>
 * </ul>
 */
public final class LanePathfinder {

    private static final double SQRT2 = Math.sqrt(2.0);
    private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] DZ = {0, 0, 1, -1, 1, -1, 1, -1};

    /**
     * Tuning: {@code landMarginCost} and {@code shoreCost} ≥ 1, {@code endpointRadius} ≥ 0 cells; the budget is
     * {@code cellsPerCell} × the straight distance in cells, clamped to {@code minCells}..{@code maxCells}
     * ({@code cellsPerCell} 0: always {@code maxCells}).
     */
    public record Params(double landMarginCost, int maxCells, int endpointRadius, double shoreCost, int minCells, int cellsPerCell) {
        public static final Params DEFAULT = new Params(4.0, 200_000, 8, 6.0, 20_000, 400);

        /** A fixed budget of {@code maxCells}; shore cells cost as much as the land margin. */
        public Params(double landMarginCost, int maxCells, int endpointRadius) {
            this(landMarginCost, maxCells, endpointRadius, landMarginCost, maxCells, 0);
        }

        /** The budget for endpoint cells {@code straightCells} apart (octile distance). */
        public int budget(double straightCells) {
            int max = Math.max(1, maxCells);
            if (cellsPerCell <= 0) return max;
            int min = Math.max(1, Math.min(minCells, max));
            double b = Math.ceil(cellsPerCell * Math.max(0.0, straightCells));
            return (int) Math.max(min, Math.min(max, b));
        }
    }

    /**
     * FOUND; NO_START / NO_END: no passable cell within {@code endpointRadius} of the start / end; NO_PATH: every cell
     * reachable from the start was searched (separate seas); BUDGET: the budget ran out first.
     */
    public enum Status { FOUND, NO_START, NO_END, NO_PATH, BUDGET }

    /**
     * The outcome: {@code waypoints} in block coordinates (empty unless FOUND), cells expanded, the path cost, the
     * budget the search had, the start and end cells it used ({@code null} for NO_START, NO_END) and the cell bounds of
     * what it searched ({@code null} when nothing was; for NO_PATH: the extent of the start's closed sea).
     */
    public record Result(Status status, List<Lane.Point> waypoints, int expanded, double cost, int budget, Cell start, Cell end,
                         Bounds searched) {
        public Result(Status status, List<Lane.Point> waypoints, int expanded, double cost) {
            this(status, waypoints, expanded, cost, 0, null, null, null);
        }

        public boolean found() {
            return status == Status.FOUND;
        }
    }

    /** A grid cell. */
    public record Cell(int x, int z) {
    }

    /** Inclusive cell bounds. */
    public record Bounds(int minX, int minZ, int maxX, int maxZ) {
    }

    private LanePathfinder() {
    }

    /** Finds a lane from block {@code (fromX, fromZ)} to block {@code (toX, toZ)} in one go. */
    public static Result find(SeaGrid grid, int fromX, int fromZ, int toX, int toZ, Params p) {
        return new Search(grid, fromX, fromZ, toX, toZ, p).run();
    }

    /**
     * A resumable search: {@link #step} expands cells until the search ends or a deadline passes, so a long search can
     * be spread over several server ticks. Not thread-safe.
     */
    public static final class Search {
        private final SeaGrid grid;
        private final Params p;
        private final int fromX, fromZ, toX, toZ;
        private final Long2DoubleOpenHashMap g = new Long2DoubleOpenHashMap();
        private final Long2LongOpenHashMap parent = new Long2LongOpenHashMap();
        private final LongOpenHashSet closed = new LongOpenHashSet();
        private final PriorityQueue<Node> open = new PriorityQueue<>();
        private long startKey, endKey;
        private int sx, sz, ex, ez;
        private int budget;
        private int bMinX = Integer.MAX_VALUE, bMinZ = Integer.MAX_VALUE, bMaxX = Integer.MIN_VALUE, bMaxZ = Integer.MIN_VALUE;
        private int expanded;
        private Result result;

        public Search(SeaGrid grid, int fromX, int fromZ, int toX, int toZ, Params p) {
            this.grid = grid;
            this.p = p;
            this.fromX = fromX;
            this.fromZ = fromZ;
            this.toX = toX;
            this.toZ = toZ;
            Optional<long[]> start = nearestSea(grid, grid.cellOf(fromX), grid.cellOf(fromZ), p.endpointRadius());
            if (start.isEmpty()) {
                result = new Result(Status.NO_START, List.of(), 0, 0);
                return;
            }
            Optional<long[]> end = nearestSea(grid, grid.cellOf(toX), grid.cellOf(toZ), p.endpointRadius());
            if (end.isEmpty()) {
                result = new Result(Status.NO_END, List.of(), 0, 0, 0, new Cell((int) start.get()[0], (int) start.get()[1]), null, null);
                return;
            }
            sx = (int) start.get()[0];
            sz = (int) start.get()[1];
            ex = (int) end.get()[0];
            ez = (int) end.get()[1];
            budget = p.budget(octile(sx, sz, ex, ez));
            g.defaultReturnValue(Double.POSITIVE_INFINITY);
            startKey = key(sx, sz);
            endKey = key(ex, ez);
            g.put(startKey, 0.0);
            open.add(new Node(sx, sz, octile(sx, sz, ex, ez), 0.0));
        }

        /** The result once the search has ended, else null. */
        public Result result() {
            return result;
        }

        public boolean done() {
            return result != null;
        }

        /** The budget of this search (0 when an endpoint has no sea). */
        public int budget() {
            return budget;
        }

        /** Cells expanded so far. */
        public int expanded() {
            return expanded;
        }

        /** Runs to the end. */
        public Result run() {
            while (result == null) expandOne();
            return result;
        }

        /**
         * Expands cells until the search ends or {@code System.nanoTime()} passes {@code deadlineNanos} (checked every
         * 8 cells). Returns the result if it ended.
         */
        public Optional<Result> step(long deadlineNanos) {
            int n = 0;
            while (result == null) {
                expandOne();
                if (++n % 8 == 0 && System.nanoTime() - deadlineNanos > 0) break;
            }
            return Optional.ofNullable(result);
        }

        private void expandOne() {
            if (result != null) return;
            Node n = open.poll();
            if (n == null) {
                result = done(Status.NO_PATH, List.of(), 0);
                return;
            }
            long k = key(n.x, n.z);
            if (!closed.add(k)) return;
            if (k == endKey) {
                List<long[]> cells = new ArrayList<>();
                long c = k;
                cells.add(new long[]{n.x, n.z});
                while (c != startKey) {
                    c = parent.get(c);
                    cells.add(new long[]{unpackX(c), unpackZ(c)});
                }
                Collections.reverse(cells);
                result = done(Status.FOUND, waypoints(grid, cells, fromX, fromZ, toX, toZ), n.g);
                return;
            }
            if (expanded >= budget) {
                result = done(Status.BUDGET, List.of(), 0);
                return;
            }
            expanded++;
            if (n.x < bMinX) bMinX = n.x;
            if (n.x > bMaxX) bMaxX = n.x;
            if (n.z < bMinZ) bMinZ = n.z;
            if (n.z > bMaxZ) bMaxZ = n.z;
            for (int d = 0; d < 8; d++) {
                int nx = n.x + DX[d], nz = n.z + DZ[d];
                long nk = key(nx, nz);
                if (closed.contains(nk) || !grid.isSea(nx, nz)) continue;
                boolean diagonal = d >= 4;
                if (diagonal && (!grid.isSea(n.x + DX[d], n.z) || !grid.isSea(n.x, n.z + DZ[d]))) continue;
                double step = (diagonal ? SQRT2 : 1.0) * enterCost(grid, nx, nz, p);
                double ng = n.g + step;
                if (ng < g.get(nk)) {
                    g.put(nk, ng);
                    parent.put(nk, k);
                    open.add(new Node(nx, nz, ng + octile(nx, nz, ex, ez), ng));
                }
            }
        }

        private Result done(Status status, List<Lane.Point> waypoints, double cost) {
            Bounds searched = expanded == 0 ? null : new Bounds(bMinX, bMinZ, bMaxX, bMaxZ);
            return new Result(status, waypoints, expanded, cost, budget, new Cell(sx, sz), new Cell(ex, ez), searched);
        }
    }

    /** The cost factor of entering a passable cell: shore, open sea beside the coast, or open sea. */
    static double enterCost(SeaGrid grid, int cx, int cz, Params p) {
        if (grid.isShore(cx, cz)) return Math.max(1.0, p.shoreCost());
        return touchesLand(grid, cx, cz) ? Math.max(1.0, p.landMarginCost()) : 1.0;
    }

    /** Whether any of the 8 neighbours of a cell is land or shore. */
    static boolean touchesLand(SeaGrid grid, int cx, int cz) {
        for (int d = 0; d < 8; d++) {
            int x = cx + DX[d], z = cz + DZ[d];
            if (!grid.isSea(x, z) || grid.isShore(x, z)) return true;
        }
        return false;
    }

    /** The cell itself if sea, else the nearest sea cell within {@code radius} cells (Euclidean, scan order on ties). */
    static Optional<long[]> nearestSea(SeaGrid grid, int cx, int cz, int radius) {
        if (grid.isSea(cx, cz)) return Optional.of(new long[]{cx, cz});
        long[] best = null;
        int bestD = Integer.MAX_VALUE;
        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int d2 = dx * dx + dz * dz;
                    if (d2 > radius * radius || d2 >= bestD) continue;
                    if (grid.isSea(cx + dx, cz + dz)) {
                        bestD = d2;
                        best = new long[]{cx + dx, cz + dz};
                    }
                }
            }
            // A ring at Chebyshev distance r holds Euclidean distances ≥ r: stop once nothing farther can be nearer
            if (best != null && bestD <= (r + 1) * (r + 1)) break;
        }
        return Optional.ofNullable(best);
    }

    /** Waypoints: exact endpoints where their own cells are sea, cell centres at every turn in between. */
    static List<Lane.Point> waypoints(SeaGrid grid, List<long[]> cells, int fromX, int fromZ, int toX, int toZ) {
        List<Lane.Point> out = new ArrayList<>();
        long[] first = cells.get(0);
        boolean exactStart = first[0] == grid.cellOf(fromX) && first[1] == grid.cellOf(fromZ);
        out.add(exactStart ? new Lane.Point(fromX, fromZ) : centre(grid, first));
        for (int i = 1; i < cells.size() - 1; i++) {
            long[] a = cells.get(i - 1), b = cells.get(i), c = cells.get(i + 1);
            boolean turn = (b[0] - a[0]) != (c[0] - b[0]) || (b[1] - a[1]) != (c[1] - b[1]);
            if (turn) out.add(centre(grid, b));
        }
        long[] last = cells.get(cells.size() - 1);
        boolean exactEnd = last[0] == grid.cellOf(toX) && last[1] == grid.cellOf(toZ);
        Lane.Point end = exactEnd ? new Lane.Point(toX, toZ) : centre(grid, last);
        if (!end.equals(out.get(out.size() - 1)) || out.size() == 1) out.add(end);
        return out;
    }

    private static Lane.Point centre(SeaGrid grid, long[] cell) {
        return new Lane.Point(grid.centreOf((int) cell[0]), grid.centreOf((int) cell[1]));
    }

    static double octile(int ax, int az, int bx, int bz) {
        int dx = Math.abs(ax - bx), dz = Math.abs(az - bz);
        return Math.max(dx, dz) + (SQRT2 - 1.0) * Math.min(dx, dz);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int unpackX(long k) {
        return (int) (k >> 32);
    }

    private static int unpackZ(long k) {
        return (int) k;
    }

    private record Node(int x, int z, double f, double g) implements Comparable<Node> {
        @Override
        public int compareTo(Node o) {
            int c = Double.compare(f, o.f);
            return c != 0 ? c : Double.compare(o.g, g); // deeper first on ties: fewer expansions
        }
    }
}
