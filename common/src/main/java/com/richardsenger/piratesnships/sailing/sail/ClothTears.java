package com.richardsenger.piratesnships.sailing.sail;

import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * The holes torn into a square sail's cloth (CAN3, docs/design.md §5.2, §8.2: chain shot). Pure and immutable; the head's
 * {@code YardBlockEntity} keeps one, saved and synced, the renderer leaves its cells out and the sailing runtime scales
 * the sail's area by {@link #intactFraction}.
 *
 * <p><b>Cells.</b> The cloth is cut into one-block cells in the cloth coordinates of {@link SquareSail}: column {@code c}
 * spans {@code u ∈ [c − ½, c + ½)} (one block column, the head's at 0), row {@code r} spans {@code v ∈ [r, r + 1)} down
 * from the head's centre, for {@code r} in {@code 0..drop − 1}. A cell belongs to the cloth when its centre
 * {@code (c, r + ½)} lies between the trapezoid's edges ({@link #inCloth}).
 */
public final class ClothTears {

    public static final ClothTears NONE = new ClothTears(new int[0]);

    private static final float EPS = 1.0e-3f;
    private static final int OFFSET = 0x8000;

    /** Packed cells, sorted, each once: {@code (r << 16) | (c + 0x8000)}. */
    private final int[] cells;

    private ClothTears(int[] sortedDistinct) {
        this.cells = sortedDistinct;
    }

    /** Tears from packed cells (any order, duplicates allowed). */
    public static ClothTears of(int[] packed) {
        int[] c = Arrays.stream(packed).distinct().sorted().toArray();
        return c.length == 0 ? NONE : new ClothTears(c);
    }

    public static int pack(int c, int r) {
        return (r << 16) | ((c + OFFSET) & 0xFFFF);
    }

    public static int column(int packed) {
        return (packed & 0xFFFF) - OFFSET;
    }

    public static int row(int packed) {
        return packed >>> 16;
    }

    /** The packed cells (a copy), for saving. */
    public int[] packed() {
        return cells.clone();
    }

    public boolean isEmpty() {
        return cells.length == 0;
    }

    public int size() {
        return cells.length;
    }

    public boolean torn(int c, int r) {
        return r >= 0 && Arrays.binarySearch(cells, pack(c, r)) >= 0;
    }

    /** The cell of the cloth point {@code (u, v)}: its column and row. */
    public static int columnAt(double u) {
        return (int) Math.floor(u + 0.5);
    }

    public static int rowAt(double v) {
        return (int) Math.floor(v);
    }

    /** Whether cell {@code (c, r)} belongs to the cloth {@code g}: its centre lies inside the trapezoid. */
    public static boolean inCloth(ClothGeometry g, int c, int r) {
        if (r < 0 || r >= g.drop()) return false;
        float v = r + 0.5f;
        return c >= g.negativeEdge(v) - EPS && c <= g.positiveEdge(v) + EPS;
    }

    /** Number of cells of the cloth {@code g}. */
    public static int cellCount(ClothGeometry g) {
        int n = 0;
        int reach = (int) Math.ceil(g.maxExtent()) + 1;
        for (int r = 0; r < g.drop(); r++) {
            for (int c = -reach; c <= reach; c++) {
                if (inCloth(g, c, r)) n++;
            }
        }
        return n;
    }

    /** Torn cells that belong to the cloth {@code g} (a tear left over from a larger cloth does not count). */
    public int tornIn(ClothGeometry g) {
        int n = 0;
        for (int p : cells) {
            if (inCloth(g, column(p), row(p))) n++;
        }
        return n;
    }

    /** Share of the cloth {@code g} that is whole, 0..1: the sail draws with this share of its area. */
    public double intactFraction(ClothGeometry g) {
        int total = cellCount(g);
        if (total <= 0) return 1.0;
        return Math.max(0.0, 1.0 - (double) tornIn(g) / total);
    }

    /** These tears plus the packed {@code more}. */
    public ClothTears with(int[] more) {
        if (more.length == 0) return this;
        int[] all = Arrays.copyOf(cells, cells.length + more.length);
        System.arraycopy(more, 0, all, cells.length, more.length);
        return of(all);
    }

    /** Only the tears that belong to the cloth {@code g}. */
    public ClothTears inside(ClothGeometry g) {
        return of(Arrays.stream(cells).filter(p -> inCloth(g, column(p), row(p))).toArray());
    }

    /** One cell mended: the highest torn cell (the smallest row), at the yard first. */
    public ClothTears mendOne() {
        return cells.length <= 1 ? NONE : new ClothTears(Arrays.copyOfRange(cells, 1, cells.length));
    }

    /**
     * Whether the whole cell {@code (c, r)} shows the frayed foot: it is whole and the cell below it is torn, so its
     * bottom edge hangs free (ART5's foot rule applied to a hole).
     */
    public boolean frayedAbove(int c, int r) {
        return !torn(c, r) && torn(c, r + 1);
    }

    /** The packed cells of the cloth {@code g} whose centres lie within {@code radius} of the cloth point {@code (u, v)}. */
    public static int[] cellsWithin(ClothGeometry g, double u, double v, double radius) {
        int reach = (int) Math.ceil(radius) + 1;
        int c0 = columnAt(u), r0 = rowAt(v);
        int[] out = new int[(2 * reach + 1) * (2 * reach + 1)];
        int n = 0;
        for (int r = r0 - reach; r <= r0 + reach; r++) {
            for (int c = c0 - reach; c <= c0 + reach; c++) {
                double du = c - u, dv = r + 0.5 - v;
                if (du * du + dv * dv <= radius * radius + 1.0e-9 && inCloth(g, c, r)) out[n++] = pack(c, r);
            }
        }
        return Arrays.copyOf(out, n);
    }

    /**
     * Where a straight step from {@code a} to {@code b} passes through the drawn cloth of {@code g}, as {@code {u, v}},
     * or null. Points are cloth coordinates {@code {u, across, v}} ({@code across} off the yard's axis, horizontal at
     * right angles to it). The cloth counts as a slab {@code slab} thick on each side of the yard's axis (it bellies off
     * it); a step through the slab crosses at {@code across = 0}, or at its end nearer to the axis when it stays inside
     * the slab on one side. The point must lie in the cloth drawn down to {@code bottom} (0 = furled: never).
     */
    public static double @Nullable [] crossing(ClothGeometry g, double bottom, double[] a, double[] b, double slab) {
        if (bottom <= 0.0) return null;
        double t;
        if (a[1] == 0.0 || Math.signum(a[1]) != Math.signum(b[1])) {
            t = a[1] == b[1] ? 0.0 : a[1] / (a[1] - b[1]);
        } else if (Math.min(Math.abs(a[1]), Math.abs(b[1])) <= slab) {
            t = Math.abs(a[1]) <= Math.abs(b[1]) ? 0.0 : 1.0;
        } else {
            return null;
        }
        double u = a[0] + (b[0] - a[0]) * t;
        double v = a[2] + (b[2] - a[2]) * t;
        double drawn = Math.min(bottom, g.drop());
        if (v < 0.0 || v > drawn) return null;
        if (u < g.negativeEdge((float) v) - EPS || u > g.positiveEdge((float) v) + EPS) return null;
        return new double[]{u, v};
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ClothTears t && Arrays.equals(cells, t.cells);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(cells);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ClothTears[");
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append('(').append(column(cells[i])).append(',').append(row(cells[i])).append(')');
        }
        return sb.append(']').toString();
    }
}
