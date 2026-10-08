package com.richardsenger.piratesnships.sailing.sail;

/**
 * The cloth shape of a {@link SquareSail} relative to its head block, as the head's block entity stores and syncs it
 * and the renderer draws it. Cloth coordinates as in {@link SquareSail}: {@code u} along the yard axis from the head
 * block's center, {@code v} down from it.
 *
 * @param alongX   the yards run along x (else along z)
 * @param upperNeg distance from the head's center to the upper yard's negative end [blocks]
 * @param upperPos distance to the upper yard's positive end [blocks]
 * @param lowerNeg distance from the mast column to the lower yard's negative end [blocks]
 * @param lowerPos distance to the lower yard's positive end [blocks]
 * @param drop     vertical distance from the upper yard to the lower one [blocks]
 */
public record ClothGeometry(boolean alongX, float upperNeg, float upperPos, float lowerNeg, float lowerPos, int drop) {

    /** Distance from the yard's axis at which the cloth hangs where it is clear of the yards (outside a full mast block). */
    public static final float STANDOFF = 0.6f;
    /** Distance from the yard's axis where the cloth meets a yard (the face of the 6 px beam). */
    public static final float AT_YARD = 0.2f;
    /** Extra bulge in the middle of a full sail, per block of sail width (capped). */
    public static final float BELLY_PER_WIDTH = 0.06f;
    public static final float MAX_BELLY = 0.5f;

    /** Negative edge of the cloth at depth {@code v} (linear between the yards). */
    public float negativeEdge(float v) {
        float t = drop <= 0 ? 0f : v / drop;
        return -(upperNeg + (lowerNeg - upperNeg) * t);
    }

    /** Positive edge of the cloth at depth {@code v}. */
    public float positiveEdge(float v) {
        float t = drop <= 0 ? 0f : v / drop;
        return upperPos + (lowerPos - upperPos) * t;
    }

    /** Widest extent of the cloth from the head's center along the axis [blocks]. */
    public float maxExtent() {
        return Math.max(Math.max(upperNeg, upperPos), Math.max(lowerNeg, lowerPos));
    }

    /**
     * How far the cloth stands off the yard axis at depth {@code v} and across-fraction {@code t} (0 at the negative
     * edge, 1 at the positive one), when it reaches down to {@code bottom}. It meets the yards at {@link #AT_YARD}, is
     * clear of the mast ({@link #STANDOFF}) half a block away from them, and bulges in the middle. When the cloth
     * hangs free above the lower yard (half sail), its foot keeps the standoff.
     */
    public float standoff(float v, float t, float bottom) {
        float base = clearance(v, bottom);
        float width = upperNeg + upperPos;
        float belly = Math.min(MAX_BELLY, BELLY_PER_WIDTH * width);
        float depth = bottom <= 0f ? 0f : (float) Math.sin(Math.PI * Math.min(1f, Math.max(0f, v / bottom)));
        float across = 1f - (2f * t - 1f) * (2f * t - 1f);
        return base + belly * depth * across;
    }

    /**
     * The part of {@link #standoff} without the belly: {@link #AT_YARD} at a yard, {@link #STANDOFF} half a block away
     * from it, the same for every point across. VIS1b adds its own moving belly ({@link SailShape}) to this.
     */
    public float clearance(float v, float bottom) {
        float fromYard = hangsToLowerYard(bottom) ? Math.min(v, drop - v) : v;
        float s = smoothstep(Math.min(1f, Math.max(0f, fromYard / 0.5f)));
        return AT_YARD + (STANDOFF - AT_YARD) * s;
    }

    /** Whether a cloth reaching down to {@code bottom} is held by the lower yard (else its foot hangs free). */
    public boolean hangsToLowerYard(float bottom) {
        return bottom >= drop - 1.0e-3f;
    }

    private static float smoothstep(float x) {
        return x * x * (3f - 2f * x);
    }
}
