package com.richardsenger.piratesnships.world.port;

import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Where a ship of a template may appear at a port (STR7, design.md §4.1, §10.1): pure rules over column flags, no world
 * access ({@code ShipSpots} reads the flags from a level).
 *
 * <p><b>The rule.</b> A spot is a footprint (the template's beam by its length, along one of the four headings) where
 * every column is {@link #DEEP} (water from the surface down to the template's draught plus {@code berth_depth}, no
 * kelp or ice in it, and nothing solid above the waterline as high as the ship stands), whose ring one block round it
 * is {@link #OPEN} (nothing solid at the hull's height, so the assembler does not gather a pier or a rock with the
 * ship), and whose ring {@code clearance} blocks round it holds no {@link #LAND} (sand, soil, stone, ice from one below
 * the keel to two above the surface). Every footprint column must be reached over open water from where the search
 * started (no ship in a lake behind the village), and no other ship may lie in it or touch it.
 *
 * <p><b>The search.</b> First each berth's own moorings ({@link #berthFootprint}): alongside, on the side away from the
 * pier, one water column between hull and pier, centred on the berth and then slid toward the bow in
 * {@link #BERTH_SLIDES}, as SW1 moored ships. Then outward over the water, ring by ring (an eight-way walk over
 * {@link #WATER} columns that cuts no corners), up to {@code search} blocks from the starts: each column reached is a
 * centre, the heading along the start's bow first and across it second. The first spot that fits wins, so the nearest
 * one does, and at equal distance the earlier start.
 */
public final class ShipSpot {

    /** The surface block is water: the walk goes over it. */
    public static final int WATER = 1;
    /** Water from the surface down to draught + berth depth (no kelp, no ice), free above the waterline. */
    public static final int DEEP = 2;
    /** Nothing solid at the hull's height (keel to the top of the ship). */
    public static final int OPEN = 4;
    /** Ground (sand, soil, stone, ice) from one below the keel to two above the surface, or an unloaded column. */
    public static final int LAND = 8;

    /** Water columns between a moored hull and the berth's pier side (SW1: the berth's own column). */
    public static final int BERTH_GAP = 1;
    /** How far a ship is slid from the berth's centre toward its bow (SW1: the pier's landward end is the beach). */
    public static final int[] BERTH_SLIDES = {0, 4, 8, 12, 16};

    /** The flags of one world column. */
    @FunctionalInterface
    public interface Columns {
        int flags(int x, int z);
    }

    /** Whether another ship lies in or touches a footprint. */
    @FunctionalInterface
    public interface Occupied {
        boolean test(Rect footprint);
    }

    /** A box of columns, inclusive. */
    public record Rect(int minX, int minZ, int maxX, int maxZ) {

        public int xSpan() {
            return maxX - minX + 1;
        }

        public int zSpan() {
            return maxZ - minZ + 1;
        }

        public Rect inflate(int n) {
            return new Rect(minX - n, minZ - n, maxX + n, maxZ + n);
        }

        /** The centre of the footprint in world coordinates (block edges). */
        public double centreX() {
            return (minX + maxX + 1) * 0.5;
        }

        public double centreZ() {
            return (minZ + maxZ + 1) * 0.5;
        }

        public boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }

    /** A hull's footprint: its width and its length along the bow. */
    public record Hull(int beam, int length) {
        public Hull {
            if (beam < 1 || length < 1) throw new IllegalArgumentException("empty hull " + beam + " by " + length);
        }
    }

    /** {@code ports.ship_clearance} and {@code ports.ship_spot_search}. */
    public record Rules(int clearance, int search) {
        public Rules {
            if (clearance < 0 || search < 0) throw new IllegalArgumentException("negative rules " + clearance + ", " + search);
        }
    }

    /**
     * Where the search starts: a berth (its moorings are tried first; {@code pierSide} is the side its pier lies on,
     * null when none or both) or a free point (an NPC ship's lane point); {@code bow} is the preferred heading.
     */
    public record Anchor(int x, int z, Direction bow, boolean berth, Direction pierSide) {
        public Anchor {
            if (!bow.getAxis().isHorizontal()) throw new IllegalArgumentException("horizontal bow only: " + bow);
        }

        public static Anchor point(int x, int z, Direction bow) {
            return new Anchor(x, z, bow, false, null);
        }
    }

    /**
     * A spot: the footprint, the heading of the bow, the start it was found from (index into the anchors) and the
     * walk's distance from it (0: at the berth's moorings).
     */
    public record Spot(Rect footprint, Direction heading, int anchor, int distance) {
    }

    /** Why no spot was found: no water that fits, or water that fits but ships lie there. */
    public enum Failure { NONE, NO_WATER, OCCUPIED }

    /** The outcome: a spot, else why not; {@code tried} counts the footprints checked. */
    public record Search(Optional<Spot> spot, Failure failure, int tried) {
    }

    private ShipSpot() {
    }

    /** The footprint of {@code hull} heading {@code heading} centred on column {@code (x, z)} (odd spans exactly). */
    public static Rect footprint(Hull hull, Direction heading, int x, int z) {
        int xSpan = heading.getAxis() == Direction.Axis.Z ? hull.beam() : hull.length();
        int zSpan = heading.getAxis() == Direction.Axis.Z ? hull.length() : hull.beam();
        int minX = x - (xSpan - 1) / 2;
        int minZ = z - (zSpan - 1) / 2;
        return new Rect(minX, minZ, minX + xSpan - 1, minZ + zSpan - 1);
    }

    /**
     * The footprint of a hull moored at {@code berth} on {@code side} and slid {@code slide} blocks toward the bow: the
     * box {@code TemplatePlacement.berthOrigin} gives (centred along the bow, its near side {@link #BERTH_GAP} beyond the
     * berth's column).
     */
    public static Rect berthFootprint(Hull hull, Anchor berth, Direction side, int slide) {
        Direction bow = berth.bow();
        if (!side.getAxis().isHorizontal() || side.getAxis() == bow.getAxis()) {
            throw new IllegalArgumentException("side must be perpendicular to the bow: " + bow + ", " + side);
        }
        Rect r;
        if (bow.getAxis() == Direction.Axis.Z) {
            int minZ = berth.z() - (hull.length() - 1) / 2;
            int minX = side.getStepX() > 0 ? berth.x() + BERTH_GAP : berth.x() - BERTH_GAP - (hull.beam() - 1);
            r = new Rect(minX, minZ, minX + hull.beam() - 1, minZ + hull.length() - 1);
        } else {
            int minX = berth.x() - (hull.length() - 1) / 2;
            int minZ = side.getStepZ() > 0 ? berth.z() + BERTH_GAP : berth.z() - BERTH_GAP - (hull.beam() - 1);
            r = new Rect(minX, minZ, minX + hull.length() - 1, minZ + hull.beam() - 1);
        }
        int dx = bow.getStepX() * slide;
        int dz = bow.getStepZ() * slide;
        return new Rect(r.minX() + dx, r.minZ() + dz, r.maxX() + dx, r.maxZ() + dz);
    }

    /** The sides of a berth to moor on, in order: away from the pier first; with no pier side known, starboard first. */
    public static List<Direction> berthSides(Anchor berth) {
        Direction right = berth.bow().getClockWise();
        Direction left = berth.bow().getCounterClockWise();
        return berth.pierSide() == right ? List.of(left, right) : List.of(right, left);
    }

    /** The headings tried round a centre reached from {@code anchor}: along its bow, then across it. */
    static List<Direction> headings(Anchor anchor) {
        return List.of(anchor.bow(), anchor.bow().getClockWise());
    }

    /** Searches a spot for {@code hull} from {@code anchors} (see the class comment). */
    public static Search find(Columns columns, List<Anchor> anchors, Hull hull, Rules rules, Occupied occupied) {
        if (anchors.isEmpty()) return new Search(Optional.empty(), Failure.NO_WATER, 0);
        Grid grid = Grid.read(columns, anchors, hull, rules);
        int tried = 0;
        boolean sawOccupied = false;
        // 1. the berths' own moorings
        for (int a = 0; a < anchors.size(); a++) {
            Anchor anchor = anchors.get(a);
            if (!anchor.berth()) continue;
            for (Direction side : berthSides(anchor)) {
                for (int slide : BERTH_SLIDES) {
                    Rect r = berthFootprint(hull, anchor, side, slide);
                    tried++;
                    if (!grid.fits(r, rules.clearance(), false)) continue;
                    if (occupied.test(r)) {
                        sawOccupied = true;
                        continue;
                    }
                    return new Search(Optional.of(new Spot(r, anchor.bow(), a, 0)), Failure.NONE, tried);
                }
            }
        }
        // 2. outward over the water
        for (int i = 0; i < grid.order.size(); i++) {
            int cell = grid.order.get(i);
            int d = grid.dist[cell];
            if (d > rules.search()) break;
            int x = grid.x(cell);
            int z = grid.z(cell);
            Anchor anchor = anchors.get(grid.source[cell]);
            for (Direction heading : headings(anchor)) {
                Rect r = footprint(hull, heading, x, z);
                tried++;
                if (!grid.fits(r, rules.clearance(), true)) continue;
                if (occupied.test(r)) {
                    sawOccupied = true;
                    continue;
                }
                return new Search(Optional.of(new Spot(r, heading, grid.source[cell], d)), Failure.NONE, tried);
            }
        }
        return new Search(Optional.empty(), sawOccupied ? Failure.OCCUPIED : Failure.NO_WATER, tried);
    }

    /** The flags of the search window, their prefix sums and the walk over the water. */
    static final class Grid {
        final int minX, minZ, w, h;
        final int[] deep, open, land, reached;   // prefix sums of "not deep", "not open", "land", "not reached"
        final int[] dist, source;
        final List<Integer> order = new ArrayList<>();

        private Grid(int minX, int minZ, int w, int h) {
            this.minX = minX;
            this.minZ = minZ;
            this.w = w;
            this.h = h;
            int n = (w + 1) * (h + 1);
            deep = new int[n];
            open = new int[n];
            land = new int[n];
            reached = new int[n];
            dist = new int[w * h];
            source = new int[w * h];
        }

        int x(int cell) {
            return minX + cell % w;
        }

        int z(int cell) {
            return minZ + cell / w;
        }

        static Grid read(Columns columns, List<Anchor> anchors, Hull hull, Rules rules) {
            int pad = Math.max(hull.beam(), hull.length()) + rules.clearance() + 2;
            int lx = Integer.MAX_VALUE, lz = Integer.MAX_VALUE, hx = Integer.MIN_VALUE, hz = Integer.MIN_VALUE;
            for (Anchor a : anchors) {
                lx = Math.min(lx, a.x());
                lz = Math.min(lz, a.z());
                hx = Math.max(hx, a.x());
                hz = Math.max(hz, a.z());
            }
            int reach = rules.search() + pad;
            Grid g = new Grid(lx - reach, lz - reach, hx - lx + 2 * reach + 1, hz - lz + 2 * reach + 1);
            int[] flags = new int[g.w * g.h];
            for (int j = 0; j < g.h; j++) {
                for (int i = 0; i < g.w; i++) flags[j * g.w + i] = columns.flags(g.minX + i, g.minZ + j);
            }
            g.walk(flags, anchors, reach);
            for (int j = 0; j < g.h; j++) {
                for (int i = 0; i < g.w; i++) {
                    int f = flags[j * g.w + i];
                    int k = (j + 1) * (g.w + 1) + i + 1;
                    int up = j * (g.w + 1) + i + 1, left = k - 1, diag = j * (g.w + 1) + i;
                    g.deep[k] = ((f & DEEP) == 0 ? 1 : 0) + g.deep[up] + g.deep[left] - g.deep[diag];
                    g.open[k] = ((f & OPEN) == 0 ? 1 : 0) + g.open[up] + g.open[left] - g.open[diag];
                    g.land[k] = ((f & LAND) != 0 ? 1 : 0) + g.land[up] + g.land[left] - g.land[diag];
                    g.reached[k] = (g.dist[j * g.w + i] < 0 ? 1 : 0) + g.reached[up] + g.reached[left] - g.reached[diag];
                }
            }
            return g;
        }

        /** Eight-way breadth-first walk over water columns from the anchors, no corner cutting, up to {@code reach}. */
        private void walk(int[] flags, List<Anchor> anchors, int reach) {
            java.util.Arrays.fill(dist, -1);
            java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
            for (int a = 0; a < anchors.size(); a++) {
                int cell = (anchors.get(a).z() - minZ) * w + anchors.get(a).x() - minX;
                if ((flags[cell] & WATER) == 0 || dist[cell] >= 0) continue;
                dist[cell] = 0;
                source[cell] = a;
                queue.add(cell);
            }
            while (!queue.isEmpty()) {
                int cell = queue.poll();
                order.add(cell);
                if (dist[cell] >= reach) continue;
                int i = cell % w, j = cell / w;
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dz == 0) continue;
                        int ni = i + dx, nj = j + dz;
                        if (ni < 0 || nj < 0 || ni >= w || nj >= h) continue;
                        int next = nj * w + ni;
                        if (dist[next] >= 0 || (flags[next] & WATER) == 0) continue;
                        if (dx != 0 && dz != 0 && ((flags[j * w + ni] & WATER) == 0 || (flags[nj * w + i] & WATER) == 0)) continue;
                        dist[next] = dist[cell] + 1;
                        source[next] = source[cell];
                        queue.add(next);
                    }
                }
            }
        }

        private int sum(int[] table, Rect r) {
            int x0 = r.minX() - minX, z0 = r.minZ() - minZ, x1 = r.maxX() - minX + 1, z1 = r.maxZ() - minZ + 1;
            return table[z1 * (w + 1) + x1] - table[z0 * (w + 1) + x1] - table[z1 * (w + 1) + x0] + table[z0 * (w + 1) + x0];
        }

        private boolean inside(Rect r) {
            return r.minX() >= minX && r.minZ() >= minZ && r.maxX() < minX + w && r.maxZ() < minZ + h;
        }

        /**
         * Whether {@code r} is deep, its one-block ring open and its {@code clearance} ring free of land; with
         * {@code reachedOnly} every footprint column must also have been reached by the walk (the berths' own moorings
         * skip that: a berth's column may be a jigsaw-marked water cell the walk starts from, the hull lies beside it).
         */
        boolean fits(Rect r, int clearance, boolean reachedOnly) {
            Rect ring = r.inflate(Math.max(1, clearance));
            if (!inside(ring)) return false;
            if (sum(deep, r) != 0) return false;
            if (sum(open, r.inflate(1)) != 0) return false;
            if (clearance > 0 && sum(land, r.inflate(clearance)) != 0) return false;
            return !reachedOnly || sum(reached, r) == 0;
        }
    }
}
