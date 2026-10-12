package com.richardsenger.piratesnships.sailing.sail;

/**
 * The cloth shape of a {@link TriangularSail} relative to its head cleat, as the head's block entity stores and syncs
 * it and the renderer draws it: the tack's offset from the head (the stay runs from the head's center to the tack's)
 * and the clew's depth straight below the head.
 *
 * @param tackX tack x − head x [blocks]
 * @param tackY tack y − head y (negative) [blocks]
 * @param tackZ tack z − head z [blocks]
 * @param drop  head y − clew y [blocks]
 * @param boomed whether the clew is a boom (SAIL5): the cloth's foot runs along the boom, from the clew point to the
 *               boom's end at the clew's height, as far out as the tack lies horizontally, turned by the boom's sheet
 */
public record TriangleCloth(int tackX, int tackY, int tackZ, int drop, boolean boomed) {

    /** A cloth without a boom. */
    public TriangleCloth(int tackX, int tackY, int tackZ, int drop) {
        this(tackX, tackY, tackZ, drop, false);
    }

    /**
     * The point the cloth's lower outer corner is drawn to, relative to the head {@code {x, y, z}}: the tack, or with a
     * boom its end at {@code sheetDegrees} ({@link BoomRules#turn}, the tack's horizontal offset at the clew's depth).
     */
    public float[] corner(double sheetDegrees) {
        if (!boomed) {
            return new float[] {tackX, tackY, tackZ};
        }
        double[] h = BoomRules.turn(tackX, tackZ, sheetDegrees);
        return new float[] {(float) h[0], -drop, (float) h[1]};
    }

    /** Largest extent of the triangle from the head's center along any axis [blocks]. */
    public int maxExtent() {
        return Math.max(Math.max(Math.abs(tackX), Math.abs(tackZ)), Math.max(Math.abs(tackY), drop));
    }
}
