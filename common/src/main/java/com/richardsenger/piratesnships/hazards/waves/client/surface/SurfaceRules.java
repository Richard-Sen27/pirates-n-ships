package com.richardsenger.piratesnships.hazards.waves.client.surface;

/**
 * The rules of the visible sea surface (WAV3, docs/design.md §5.4): which water columns carry it, where its still level
 * lies and how strongly the waves move it near the shore. Pure: no world access, so the client's cell cache
 * ({@link SeaSurfaceCells}), the mixin that hides vanilla's flat top ({@code mixin.MixinLiquidBlockRenderer}) and the
 * tests share exactly one rule.
 *
 * <h2>Sea cells</h2>
 * A column is a <b>sea cell</b> when the block at sea level − 1 holds still water (the water source, also a
 * waterlogged block) and the block above it is air: the foam's rule (WD1). Vanilla draws the flat top face of exactly
 * these blocks; the mixin skips it and the mesh draws the moving surface instead. Everything else (flowing water,
 * water under a lily pad or a block, lakes above or below sea level) keeps vanilla's water.
 *
 * <h2>Depth fade</h2>
 * The wave height is scaled by {@link #depthFade}: 0 where the sea is one block deep, rising linearly to 1 at
 * {@code surface_full_depth} blocks, so the surface lies flat at the beach and never cuts into the sand. A mesh vertex
 * takes the smallest fade of the cells around it ({@link #vertexFade}), so a vertex touching land does not move.
 */
public final class SurfaceRules {

    /** Height of a still water source's surface inside its block (vanilla {@code FluidState#getOwnHeight}, 8/9). */
    public static final double STILL_HEIGHT = 8.0 / 9.0;
    /** Vanilla lowers a water top face by this so it does not z-fight the block above. */
    public static final double TOP_INSET = 0.001;
    /** Deepest water a column's depth is counted to [blocks] (the largest {@code surface_full_depth}). */
    public static final int MAX_DEPTH = 16;

    private SurfaceRules() {
    }

    /** Y of the block whose top the surface replaces: sea level − 1. */
    public static int surfaceBlockY(int seaLevel) {
        return seaLevel - 1;
    }

    /** World height of vanilla's flat still-water top at sea level [blocks]. */
    public static double stillTop(int seaLevel) {
        return surfaceBlockY(seaLevel) + STILL_HEIGHT - TOP_INSET;
    }

    /**
     * The sea-cell rule.
     *
     * @param blockY      y of the block
     * @param seaLevel    the level's sea level
     * @param stillWater  the block's fluid is the still water source (also in a waterlogged block)
     * @param airAbove    the block above is air
     */
    public static boolean isSeaCell(int blockY, int seaLevel, boolean stillWater, boolean airAbove) {
        return blockY == surfaceBlockY(seaLevel) && stillWater && airAbove;
    }

    /** {@code surface_full_depth} clamped to 2..{@link #MAX_DEPTH}. */
    public static int clampFullDepth(int fullDepth) {
        return Math.max(2, Math.min(MAX_DEPTH, fullDepth));
    }

    /**
     * Share of the wave height a column of {@code depth} water blocks (counted down from the surface block, the surface
     * block included) shows: 0 at one block or less, 1 from {@code fullDepth} on, linear between.
     */
    public static double depthFade(int depth, int fullDepth) {
        int full = clampFullDepth(fullDepth);
        if (depth <= 1) {
            return 0.0;
        }
        if (depth >= full) {
            return 1.0;
        }
        return (depth - 1) / (double) (full - 1);
    }

    /**
     * Fade of a mesh cell: the fade of its shallowest column, or 0 when any of its columns is not a sea cell
     * ({@code minDepth} 0), so a coarse cell over a coast lies flat.
     */
    public static double cellFade(int minDepth, int fullDepth) {
        return minDepth <= 0 ? 0.0 : depthFade(minDepth, fullDepth);
    }

    /** Fade of a mesh vertex: the smallest fade of the (up to four) cells around it. */
    public static double vertexFade(double a, double b, double c, double d) {
        return Math.min(Math.min(a, b), Math.min(c, d));
    }
}
