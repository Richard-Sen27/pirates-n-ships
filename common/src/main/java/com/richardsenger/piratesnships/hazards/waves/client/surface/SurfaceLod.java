package com.richardsenger.piratesnships.hazards.waves.client.surface;

import org.jetbrains.annotations.Nullable;

/**
 * Level of detail of the sea-surface mesh (WAV3): three square rings around the camera. Pure.
 *
 * <ul>
 *   <li><b>near</b>: 1-block cells in a square of half-size {@code surface_near_radius} (rounded up to 4), aligned to
 *       4 blocks around the camera;</li>
 *   <li><b>mid</b>: 4-block cells out to {@code surface_mid_radius}, a square aligned to the far cell size, minus the
 *       near square;</li>
 *   <li><b>far</b>: cells of {@code surface_far_cell} blocks (a power of two from 4 to 64) out to the render
 *       distance, minus the mid square.</li>
 * </ul>
 * Every square is aligned to the cell size of the ring around it, so the rings tile the far square without gaps or
 * overlap and every coarse cell edge on a ring boundary is made of whole fine cell edges. The fine side of such a
 * boundary has more vertices than the coarse side (T-junctions), so the mesher closes the slits with <b>seam
 * skirts</b>: one vertical strip per fine boundary edge between the fine edge and the coarse edge's straight line
 * ({@link SurfaceMesher}).
 */
public final class SurfaceLod {

    /** Cell size of the near and mid rings [blocks]. */
    public static final int NEAR_CELL = 1;
    public static final int MID_CELL = 4;
    public static final int MIN_FAR_CELL = 4;
    public static final int MAX_FAR_CELL = 64;

    private SurfaceLod() {
    }

    /** A square {@code [minX, maxX) × [minZ, maxZ)} of block coordinates. */
    public record Square(int minX, int minZ, int maxX, int maxZ) {

        public int size() {
            return maxX - minX;
        }

        /** Whether the cell {@code [x, x + cell) × [z, z + cell)} lies inside. */
        public boolean containsCell(int x, int z, int cell) {
            return x >= minX && z >= minZ && x + cell <= maxX && z + cell <= maxZ;
        }

        public boolean contains(int x, int z) {
            return x >= minX && z >= minZ && x < maxX && z < maxZ;
        }
    }

    /** One ring: cells of {@code cell} blocks covering {@code outer} minus {@code hole} (null: no hole). */
    public record Ring(int cell, Square outer, @Nullable Square hole) {

        /** Cells per side of the outer square. */
        public int cellsPerSide() {
            return outer.size() / cell;
        }

        /** Whether the cell at {@code (x, z)} (aligned) is one of this ring's cells. */
        public boolean hasCell(int x, int z) {
            return outer.containsCell(x, z, cell) && (hole == null || !hole.containsCell(x, z, cell));
        }

        /** Number of cells of the ring. */
        public int cellCount() {
            int n = cellsPerSide() * cellsPerSide();
            if (hole != null) {
                int h = hole.size() / cell;
                n -= h * h;
            }
            return n;
        }
    }

    /** The rings of one frame; {@code far} is null when the mid ring already reaches the render distance. */
    public record Layout(Ring near, Ring mid, @Nullable Ring far) {

        /** Fine boundary edges, i.e. seam skirts at most: the near square's and the mid square's perimeter. */
        public int seamCount() {
            int n = 4 * near.outer().size() / NEAR_CELL;
            if (far != null) {
                n += 4 * mid.outer().size() / MID_CELL;
            }
            return n;
        }

        /** Cells of all rings. */
        public int cellCount() {
            return near.cellCount() + mid.cellCount() + (far == null ? 0 : far.cellCount());
        }
    }

    /** {@code surface_far_cell} as the power of two in 4..64 nearest below or at it. */
    public static int farCell(int configured) {
        int c = Math.max(MIN_FAR_CELL, Math.min(MAX_FAR_CELL, configured));
        return Integer.highestOneBit(c);
    }

    /** {@code v} rounded up to a multiple of {@code step}. */
    static int roundUp(int v, int step) {
        return Math.floorDiv(v + step - 1, step) * step;
    }

    /**
     * The rings around the camera.
     *
     * @param nearRadius   {@code surface_near_radius} [blocks]
     * @param midRadius    {@code surface_mid_radius} [blocks]
     * @param farCell      {@code surface_far_cell} [blocks] (see {@link #farCell})
     * @param renderBlocks the render distance [blocks]
     */
    public static Layout layout(double camX, double camZ, int nearRadius, int midRadius, int farCell, int renderBlocks) {
        int f = farCell(farCell);
        int rn = roundUp(Math.max(MID_CELL, nearRadius), MID_CELL);
        int minMid = roundUp(rn + f, f);
        int rf = roundUp(Math.max(minMid, renderBlocks), f);
        int rm = Math.max(minMid, Math.min(roundUp(Math.max(midRadius, 1), f), rf));
        int bx = (int) Math.floor(camX), bz = (int) Math.floor(camZ);
        int nx = Math.floorDiv(bx, MID_CELL) * MID_CELL, nz = Math.floorDiv(bz, MID_CELL) * MID_CELL;
        int mx = Math.floorDiv(bx, f) * f, mz = Math.floorDiv(bz, f) * f;
        Square near = new Square(nx - rn, nz - rn, nx + rn, nz + rn);
        Square mid = new Square(mx - rm, mz - rm, mx + rm, mz + rm);
        Ring nearRing = new Ring(NEAR_CELL, near, null);
        Ring midRing = new Ring(MID_CELL, mid, near);
        Ring farRing = rf > rm ? new Ring(f, new Square(mx - rf, mz - rf, mx + rf, mz + rf), mid) : null;
        return new Layout(nearRing, midRing, farRing);
    }

    /**
     * Upper bound of the vertices one frame emits when every cell is sea: four per quad, one quad per cell and per seam
     * skirt, each twice with the underside (the skirts are always two-sided).
     */
    public static long maxVertices(Layout layout, boolean underside) {
        long cells = layout.cellCount();
        long seams = layout.seamCount();
        return 4L * (cells * (underside ? 2 : 1) + seams * 2);
    }
}
