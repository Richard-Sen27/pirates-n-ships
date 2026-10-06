package com.richardsenger.piratesnships.station.order;

/**
 * Pure geometry of a radial menu with {@code n} equal sectors (no client classes, unit-tested). Angles are in radians,
 * measured clockwise on screen from straight up (screen y grows downwards); sector 0 is centered at the top.
 *
 * @param count  number of sectors (at least 1)
 * @param inner  inner radius of the ring, in GUI pixels; also the dead zone: closer to the center selects nothing
 * @param outer  outer radius of the ring, in GUI pixels
 */
public record RadialLayout(int count, double inner, double outer) {

    public static final double TAU = Math.PI * 2;
    /** Outer radius as a share of the smaller window side, and its limits in GUI pixels. */
    static final double OUTER_SHARE = 0.30;
    static final double MIN_OUTER = 48;
    static final double MAX_OUTER = 120;
    /** Inner radius as a share of the outer radius (room for the order name in the center). */
    static final double INNER_SHARE = 0.46;

    public RadialLayout {
        if (count < 1) throw new IllegalArgumentException("a radial menu needs at least one entry");
        if (!(inner >= 0 && outer > inner)) throw new IllegalArgumentException("bad radii " + inner + " / " + outer);
    }

    /**
     * The layout for a GUI-scaled window of {@code width} × {@code height}: the outer radius is a share of the smaller
     * side, clamped (so it fits at GUI scale 4 on a small window and does not sprawl at scale 1), times {@code scale}.
     */
    public static RadialLayout forWindow(int count, int width, int height, double scale) {
        double side = Math.min(width, height);
        double outer = Math.min(Math.max(side * OUTER_SHARE, MIN_OUTER), MAX_OUTER) * scale;
        outer = Math.min(outer, side / 2.0 - 4); // never beyond the window
        outer = Math.max(outer, 8);
        return new RadialLayout(count, outer * INNER_SHARE, outer);
    }

    /** Angular width of one sector. */
    public double span() {
        return TAU / count;
    }

    /** Center angle of sector {@code i}. */
    public double centerAngle(int i) {
        return i * span();
    }

    /** Start (counter-clockwise edge) angle of sector {@code i}; the sector runs to {@code start + span}. */
    public double startAngle(int i) {
        return centerAngle(i) - span() / 2;
    }

    /** Screen angle of the offset (dx, dy) from the center, in [0, 2π): 0 up, π/2 right. */
    public static double angleOf(double dx, double dy) {
        double a = Math.atan2(dx, -dy);
        return a < 0 ? a + TAU : a;
    }

    /** The sector an angle falls in. */
    public int sectorOfAngle(double angle) {
        double a = (angle + span() / 2) % TAU;
        if (a < 0) a += TAU;
        return Math.min(count - 1, (int) Math.floor(a / span()));
    }

    /**
     * Hit test: the sector under the offset (dx, dy) from the center, or -1 inside the dead zone. Beyond the outer
     * radius the direction still counts, so a quick flick past the ring selects too.
     */
    public int sectorAt(double dx, double dy) {
        if (dx * dx + dy * dy < inner * inner) return -1;
        return sectorOfAngle(angleOf(dx, dy));
    }

    /** Screen x offset of the point at {@code angle} and {@code radius}. */
    public static double x(double angle, double radius) {
        return Math.sin(angle) * radius;
    }

    /** Screen y offset of the point at {@code angle} and {@code radius} (negative is up). */
    public static double y(double angle, double radius) {
        return -Math.cos(angle) * radius;
    }

    /** Radius at which the entry icons sit: the middle of the ring. */
    public double iconRadius() {
        return (inner + outer) / 2;
    }
}
