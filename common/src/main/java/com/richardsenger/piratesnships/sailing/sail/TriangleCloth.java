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
 */
public record TriangleCloth(int tackX, int tackY, int tackZ, int drop) {

    /** Largest extent of the triangle from the head's center along any axis [blocks]. */
    public int maxExtent() {
        return Math.max(Math.max(Math.abs(tackX), Math.abs(tackZ)), Math.max(Math.abs(tackY), drop));
    }
}
