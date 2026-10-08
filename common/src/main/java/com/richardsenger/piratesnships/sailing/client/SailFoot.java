package com.richardsenger.piratesnships.sailing.client;

/**
 * Where a hanging sail cloth shows its frayed foot (ART5, art/README.md "Mooring ring and sail foot"). Pure; used by
 * {@link YardClothRenderer} and {@link StayClothRenderer}.
 *
 * <p>The cloth texture repeats once per block of cloth. The bottom block of a cloth at least {@link #MIN_HEIGHT} blocks
 * tall is drawn with the foot tile ({@code textures/block/sail_cloth_foot.png}) instead: the same tile with a stitched
 * tabling and a frayed edge in its bottom rows. A shorter cloth and the furled bundle keep the plain tile. The tiling
 * is counted up from the foot (a whole tile always ends at the foot, so the frayed rows sit on the edge); the two
 * tiles share their pixels except in the bottom 9 of 32 rows, so the switch between them does not show.
 *
 * <p>The bottom of a cloth is its lowest drawn edge: the lower yard for a full square sail, the cloth's lower edge for
 * a reefed one (half trim), the tack-clew edge for a triangular sail.
 */
public final class SailFoot {

    /** The cloth must hang at least this far [blocks] to show a foot. */
    public static final float MIN_HEIGHT = 2f;
    private static final float EPS = 1.0e-4f;

    private SailFoot() {
    }

    /** Whether a cloth hanging {@code height} blocks below its head shows the foot tile on its bottom block. */
    public static boolean shown(float height) {
        return height >= MIN_HEIGHT - EPS;
    }

    /**
     * Whether row {@code row} (0 at the top) of a square sail's {@code rows} cell rows ({@code cellsPerBlock} rows per
     * block of texture) uses the foot tile: the last {@code cellsPerBlock} rows of a cloth {@link #shown} a foot.
     */
    public static boolean yardFootRow(int row, int rows, int cellsPerBlock, float height) {
        return shown(height) && rows >= cellsPerBlock && row >= rows - cellsPerBlock;
    }

    /**
     * Texture v at the top of row {@code row} of {@code rows}: the tile phase counted up from the bottom edge, so the
     * last row ends at v = 1 (the tile's bottom).
     */
    public static float yardV0(int row, int rows, int cellsPerBlock) {
        return Math.floorMod(row - rows, cellsPerBlock) / (float) cellsPerBlock;
    }

    /**
     * Height [blocks] of a point of a triangular sail's cloth above its foot, straight down: for the point
     * {@code tack * u + clew * v} (head at the origin, the clew {@code bottom} straight below it) the foot point under
     * it is {@code tack * u + clew * (1 - u)}, so the height is {@code bottom * (1 - u - v)}. Lines of equal height run
     * parallel to the foot.
     */
    public static float heightAboveFoot(float u, float v, float bottom) {
        return bottom * (1f - u - v);
    }

    /**
     * Texture v of a point of a triangular sail at {@code h} blocks above its foot: whole tiles counted up from the foot
     * (the foot at an integer v, the tile's bottom edge), offset by a whole number of tiles to stay positive.
     */
    public static float stayV(float h, float bottom) {
        return (float) Math.ceil(bottom) - h;
    }

    /**
     * Whether a triangle of a triangular sail whose highest corner is {@code maxHeight} blocks above the foot uses the
     * foot tile: it must lie wholly within the bottom block, and the cloth must be {@link #shown} a foot.
     */
    public static boolean stayFootTriangle(float maxHeight, float bottom) {
        return shown(bottom) && maxHeight <= 1f + EPS;
    }
}
