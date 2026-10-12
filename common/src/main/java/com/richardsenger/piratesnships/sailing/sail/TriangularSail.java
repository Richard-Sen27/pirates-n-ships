package com.richardsenger.piratesnships.sailing.sail;

import com.richardsenger.piratesnships.sailing.force.SailTrim;

/**
 * A triangular (fore-and-aft) sail (docs/design.md §5.2, rule F5b): the cloth fills the triangle between the
 * <b>head</b> cleat A (the upper end of the stay, which carries the trim), the <b>tack</b> cleat B (the stay's lower
 * end) and the <b>clew</b> cleat C straight below the head. The corners are the cleats' block centers.
 *
 * <p><b>Drawn cloth per trim:</b> furled = none (bundled along the stay), half = the triangle A-B-M with M halfway
 * down from A to C, full = A-B-C. The area of the whole triangle is half the length of {@code AB × AC}; as C lies
 * straight below A that is half the drop A-C times B's horizontal distance from the head's column, and the half sail
 * draws half of it.
 *
 * <p><b>Boomed sails (SAIL5).</b> With a boom in the clew's place ({@code boomed}) the sail's foot runs along the boom:
 * the cloth is the triangle head - boom end - clew point, the boom end lying at the clew's height, as far from the
 * clew as the tack lies horizontally, turned by the boom's sheet angle ({@link BoomRules}). The area is the same
 * ({@code drop × that distance / 2}); the centroid follows the boom ({@link #centroid(SailTrim, double)}).
 *
 * @param head   the head cleat A
 * @param tack   the tack cleat B
 * @param clew   the clew C, straight below A: a cleat, or the boom's pivot
 * @param boomed whether the clew is a boom (SAIL5)
 */
public record TriangularSail(BlockPoint head, BlockPoint tack, BlockPoint clew, boolean boomed) {

    /** A sail with a clew cleat (no boom). */
    public TriangularSail(BlockPoint head, BlockPoint tack, BlockPoint clew) {
        this(head, tack, clew, false);
    }

    public TriangularSail {
        if (clew.x() != head.x() || clew.z() != head.z() || clew.y() >= head.y()) {
            throw new IllegalArgumentException("the clew must lie straight below the head: " + head + ", " + clew);
        }
        if (tack.y() >= head.y()) {
            throw new IllegalArgumentException("the tack must be lower than the head: " + head + ", " + tack);
        }
    }

    /** Vertical distance from the head to the clew [blocks]. */
    public int drop() {
        return head.y() - clew.y();
    }

    /** Full cloth area [blocks²]: {@code |AB × AC| / 2}. */
    public double area() {
        double bx = tack.x() - head.x(), by = tack.y() - head.y(), bz = tack.z() - head.z();
        double cx = clew.x() - head.x(), cy = clew.y() - head.y(), cz = clew.z() - head.z();
        double x = by * cz - bz * cy;
        double y = bz * cx - bx * cz;
        double z = bx * cy - by * cx;
        return 0.5 * Math.sqrt(x * x + y * y + z * z);
    }

    /** Fraction of the head-to-clew drop the cloth reaches at {@code trim}: 0, 0.5, 1. */
    public static double drawnFraction(SailTrim trim) {
        return SquareSail.drawnFraction(trim);
    }

    /** Area of the cloth drawn at {@code trim} [blocks²]. */
    public double drawnArea(SailTrim trim) {
        return area() * drawnFraction(trim);
    }

    /**
     * Centroid of the cloth drawn at {@code trim}, relative to the head's center {@code {x, y, z}} [blocks]. Furled:
     * the middle of the stay, where the cloth is bundled.
     */
    public double[] centroid(SailTrim trim) {
        double bx = tack.x() - head.x(), by = tack.y() - head.y(), bz = tack.z() - head.z();
        double f = drawnFraction(trim);
        if (f <= 0.0) {
            return new double[] {bx / 2.0, by / 2.0, bz / 2.0};
        }
        // corners A = 0, B, and M = f * (C - A) = (0, -f * drop, 0)
        return new double[] {bx / 3.0, (by - f * drop()) / 3.0, bz / 3.0};
    }

    /**
     * Centroid of the cloth drawn at {@code trim} with the boom at {@code sheetDegrees} (SAIL5), relative to the head's
     * center: the triangle head - boom end - clew point. Without a boom, {@link #centroid(SailTrim)}.
     */
    public double[] centroid(SailTrim trim, double sheetDegrees) {
        if (!boomed) {
            return centroid(trim);
        }
        double f = drawnFraction(trim);
        if (f <= 0.0) {
            return centroid(trim);
        }
        double[] end = boomEnd(sheetDegrees);
        return new double[] {end[0] / 3.0, (end[1] - f * drop()) / 3.0, end[2] / 3.0};
    }

    /**
     * The boom's end relative to the head's center at {@code sheetDegrees} (SAIL5): at the clew's height, the tack's
     * horizontal offset from the clew turned clockwise seen from above ({@link BoomRules#turn}).
     */
    public double[] boomEnd(double sheetDegrees) {
        double[] h = BoomRules.turn(tack.x() - clew.x(), tack.z() - clew.z(), sheetDegrees);
        return new double[] {h[0], clew.y() - head.y(), h[1]};
    }

    /** The cloth shape relative to the head cleat, for the block entity and the renderer. */
    public TriangleCloth geometry() {
        return new TriangleCloth(tack.x() - head.x(), tack.y() - head.y(), tack.z() - head.z(), drop(), boomed);
    }
}
