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
 * @param head the head cleat A
 * @param tack the tack cleat B
 * @param clew the clew cleat C, straight below A
 */
public record TriangularSail(BlockPoint head, BlockPoint tack, BlockPoint clew) {

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

    /** The cloth shape relative to the head cleat, for the block entity and the renderer. */
    public TriangleCloth geometry() {
        return new TriangleCloth(tack.x() - head.x(), tack.y() - head.y(), tack.z() - head.z(), drop());
    }
}
