package com.richardsenger.piratesnships.sailing.sail;

/**
 * One yard: a straight row of yard blocks along one horizontal axis (docs/design.md §5.2, rule F5a).
 *
 * <p><b>Middle block:</b> {@code min + (length - 1) / 2}, so an odd row has its true middle and an even row the
 * lower-coordinate block of its two middle ones. The middle block marks the mast column.
 *
 * @param alongX true for a yard along the x axis, false for one along z
 * @param y      the row's height
 * @param fixed  the row's other horizontal coordinate (z for a yard along x, x for one along z)
 * @param min    lowest coordinate along the axis
 * @param max    highest coordinate along the axis
 */
public record YardRow(boolean alongX, int y, int fixed, int min, int max) {

    public YardRow {
        if (max < min) {
            throw new IllegalArgumentException("yard row max < min: " + max + " < " + min);
        }
    }

    public int length() {
        return max - min + 1;
    }

    /** Coordinate of the middle block along the axis. */
    public int middle() {
        return min + (length() - 1) / 2;
    }

    public int middleX() {
        return alongX ? middle() : fixed;
    }

    public int middleZ() {
        return alongX ? fixed : middle();
    }

    /** x of the block at axis coordinate {@code a}. */
    public int xAt(int a) {
        return alongX ? a : fixed;
    }

    /** z of the block at axis coordinate {@code a}. */
    public int zAt(int a) {
        return alongX ? fixed : a;
    }

    /** Distance from the middle block's center to the row's negative end [blocks]. */
    public double negativeExtent() {
        return middle() - min + 0.5;
    }

    /** Distance from the middle block's center to the row's positive end [blocks]. */
    public double positiveExtent() {
        return max - middle() + 0.5;
    }

    public boolean contains(int x, int y, int z) {
        if (y != this.y) return false;
        int a = alongX ? x : z;
        int f = alongX ? z : x;
        return f == fixed && a >= min && a <= max;
    }

    public boolean isMiddle(int x, int y, int z) {
        return y == this.y && x == middleX() && z == middleZ();
    }
}
