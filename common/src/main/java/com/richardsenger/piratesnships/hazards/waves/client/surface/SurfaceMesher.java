package com.richardsenger.piratesnships.hazards.waves.client.surface;

import org.jetbrains.annotations.Nullable;

/**
 * Builds the sea-surface mesh of one frame (WAV3). Pure: the world comes in through {@link Cells}, the vertices go out
 * through {@link Sink}, so the mesh, its LOD seams and its cost are tested without a client. One instance keeps its
 * scratch arrays from frame to frame.
 *
 * <h2>Per ring ({@link SurfaceLod})</h2>
 * <ol>
 *   <li>the wave height at every grid vertex of the ring's outer square ({@link SurfaceWaves#fillGrid});</li>
 *   <li>every cell's fade: a 1-block cell is drawn when its column is a sea cell and fades with its depth; a coarse
 *       cell is drawn when any of its columns is a sea cell and fades with its shallowest column, or not at all
 *       (lies flat) when any column is not sea ({@link SurfaceRules#cellFade}): over a coast it lies at the still level,
 *       inside the land's blocks, so its waves never rise over a beach;</li>
 *   <li>every vertex: still top + wave × the smallest fade of the cells around it;</li>
 *   <li>one quad per drawn cell (far ring first, near last, a rough back-to-front order for the translucency), its
 *       underside too with {@code surface_underside}.</li>
 * </ol>
 *
 * <h2>Seams</h2>
 * On a ring boundary a coarse cell edge meets {@code coarse / fine} fine edges whose inner vertices need not lie on the
 * coarse edge's straight line: a slit opens. Every fine boundary edge whose two cells are both drawn gets a vertical
 * strip from the fine edge to the coarse line (both faces), which closes the slit exactly.
 *
 * <h2>Vertices</h2>
 * Top quads run (x0,z0) (x0,z1) (x1,z1) (x1,z0) like vanilla's water top, the sprite's u along x and v along z over
 * each cell (vanilla's scale on 1-block cells; a coarse cell stretches the sprite over itself). The underside runs
 * the other way round.
 */
public final class SurfaceMesher {

    /** The world, as the mesher sees it. Coordinates are block coordinates; cells are aligned to their size. */
    public interface Cells {
        /** Water depth of the column at (x, z) if it is a sea cell ({@link SurfaceRules}), else 0. */
        int depth(int x, int z);

        /** Smallest {@link #depth} of the {@code size × size} cell at (x, z) (0 when any column is not sea). */
        int minDepth(int x, int z, int size);

        /** Whether any column of the {@code size × size} cell at (x, z) is a sea cell. */
        boolean anySea(int x, int z, int size);

        /** Water colour (0xRRGGBB) of the column at (x, z). */
        int color(int x, int z);

        /** Packed light of the column at (x, z). */
        int light(int x, int z);

        /** Whether the 1-block cell at (x, z) lies wholly inside a dry hull (not drawn). */
        default boolean dry(int x, int z) {
            return false;
        }
    }

    /** Receives the vertices (world coordinates), four per quad. */
    @FunctionalInterface
    public interface Sink {
        void vertex(double x, double y, double z, int rgb, float u, float v, int light, boolean up);
    }

    /** Frustum test of a horizontal area {@code [minX, maxX) × [minZ, maxZ)} at the surface. */
    @FunctionalInterface
    public interface Visibility {
        boolean visible(int minX, int minZ, int maxX, int maxZ);

        Visibility ALL = (a, b, c, d) -> true;
    }

    /**
     * @param baseY     still top of the sea ({@link SurfaceRules#stillTop})
     * @param fullDepth {@code surface_full_depth}
     * @param underside also emit every top quad's underside
     */
    public record Params(double baseY, int fullDepth, boolean underside, float u0, float v0, float u1, float v1) {
    }

    /** What one build emitted. */
    public record Stats(int cells, int seams, int vertices) {
    }

    /** Side of a visibility tile for rings with cells smaller than it [blocks]. */
    static final int TILE = 16;

    private static final class Scratch {
        SurfaceLod.Ring ring;
        int cps, nv;
        double[] wave = new double[0];
        double[] height = new double[0];
        double[] fade = new double[0];
        boolean[] any = new boolean[0];
        byte[] tiles = new byte[0];
        int tileX, tileZ, tilesPerSide;

        void size(SurfaceLod.Ring r) {
            ring = r;
            cps = r.cellsPerSide();
            nv = cps + 1;
            if (wave.length < nv * nv) {
                wave = new double[nv * nv];
                height = new double[nv * nv];
            }
            if (fade.length < cps * cps) {
                fade = new double[cps * cps];
                any = new boolean[cps * cps];
            }
            tileX = Math.floorDiv(r.outer().minX(), TILE) * TILE;
            tileZ = Math.floorDiv(r.outer().minZ(), TILE) * TILE;
            tilesPerSide = (r.outer().maxX() - tileX + TILE - 1) / TILE + 1;
            int t = tilesPerSide * tilesPerSide;
            if (tiles.length < t) {
                tiles = new byte[t];
            }
            java.util.Arrays.fill(tiles, 0, t, (byte) 0);
        }

        int cellIndex(int x, int z) {
            return ((z - ring.outer().minZ()) / ring.cell()) * cps + (x - ring.outer().minX()) / ring.cell();
        }

        double heightAt(int x, int z) {
            return height[((z - ring.outer().minZ()) / ring.cell()) * nv + (x - ring.outer().minX()) / ring.cell()];
        }

        boolean visible(int x, int z, Visibility vis) {
            int cell = ring.cell();
            if (cell >= TILE) {
                return vis.visible(x, z, x + cell, z + cell);
            }
            int tx = Math.floorDiv(x - tileX, TILE), tz = Math.floorDiv(z - tileZ, TILE);
            int i = tz * tilesPerSide + tx;
            if (tiles[i] == 0) {
                int ax = tileX + tx * TILE, az = tileZ + tz * TILE;
                tiles[i] = vis.visible(ax, az, ax + TILE, az + TILE) ? (byte) 1 : (byte) 2;
            }
            return tiles[i] == 1;
        }
    }

    private final Scratch[] scratch = {new Scratch(), new Scratch(), new Scratch()};
    private int cellsOut, seamsOut, verticesOut;

    /** Builds the frame's mesh into {@code sink}. */
    public Stats build(SurfaceLod.Layout layout, Cells cells, SurfaceWaves waves, Params p, Visibility vis, Sink sink) {
        cellsOut = seamsOut = verticesOut = 0;
        Scratch near = prepare(scratch[0], layout.near(), cells, waves, p);
        Scratch mid = prepare(scratch[1], layout.mid(), cells, waves, p);
        Scratch far = layout.far() == null ? null : prepare(scratch[2], layout.far(), cells, waves, p);
        if (far != null) {
            emitCells(far, cells, p, vis, sink);
            emitSeams(mid, far, cells, p, vis, sink);
        }
        emitCells(mid, cells, p, vis, sink);
        emitSeams(near, mid, cells, p, vis, sink);
        emitCells(near, cells, p, vis, sink);
        return new Stats(cellsOut, seamsOut, verticesOut);
    }

    private static Scratch prepare(Scratch s, SurfaceLod.Ring ring, Cells cells, SurfaceWaves waves, Params p) {
        s.size(ring);
        int cell = ring.cell();
        int minX = ring.outer().minX(), minZ = ring.outer().minZ();
        waves.fillGrid(minX, minZ, cell, s.nv, s.wave);
        for (int cz = 0; cz < s.cps; cz++) {
            for (int cx = 0; cx < s.cps; cx++) {
                int x = minX + cx * cell, z = minZ + cz * cell;
                int i = cz * s.cps + cx;
                if (cell == 1) {
                    int d = cells.depth(x, z);
                    s.any[i] = d > 0;
                    s.fade[i] = SurfaceRules.cellFade(d, p.fullDepth());
                } else {
                    boolean any = cells.anySea(x, z, cell);
                    s.any[i] = any;
                    s.fade[i] = any ? SurfaceRules.cellFade(cells.minDepth(x, z, cell), p.fullDepth()) : 0.0;
                }
            }
        }
        for (int vz = 0; vz < s.nv; vz++) {
            for (int vx = 0; vx < s.nv; vx++) {
                double f = 1.0;
                for (int dz = -1; dz <= 0; dz++) {
                    for (int dx = -1; dx <= 0; dx++) {
                        int cx = vx + dx, cz = vz + dz;
                        if (cx >= 0 && cz >= 0 && cx < s.cps && cz < s.cps) {
                            f = Math.min(f, s.fade[cz * s.cps + cx]);
                        }
                    }
                }
                int v = vz * s.nv + vx;
                s.height[v] = p.baseY() + s.wave[v] * f;
            }
        }
        return s;
    }

    private void emitCells(Scratch s, Cells cells, Params p, Visibility vis, Sink sink) {
        SurfaceLod.Ring ring = s.ring;
        int cell = ring.cell();
        int minX = ring.outer().minX(), minZ = ring.outer().minZ();
        SurfaceLod.Square hole = ring.hole();
        for (int cz = 0; cz < s.cps; cz++) {
            for (int cx = 0; cx < s.cps; cx++) {
                int i = cz * s.cps + cx;
                if (!s.any[i]) {
                    continue;
                }
                int x0 = minX + cx * cell, z0 = minZ + cz * cell;
                if (hole != null && hole.containsCell(x0, z0, cell)) {
                    continue;
                }
                if (cell == 1 && cells.dry(x0, z0)) {
                    continue;
                }
                if (!s.visible(x0, z0, vis)) {
                    continue;
                }
                int x1 = x0 + cell, z1 = z0 + cell;
                double h00 = s.height[cz * s.nv + cx], h10 = s.height[cz * s.nv + cx + 1];
                double h01 = s.height[(cz + 1) * s.nv + cx], h11 = s.height[(cz + 1) * s.nv + cx + 1];
                int half = cell / 2;
                int rgb = cells.color(x0 + half, z0 + half), light = cells.light(x0 + half, z0 + half);
                float u0 = p.u0(), u1 = p.u1(), v0 = p.v0(), v1 = p.v1();
                sink.vertex(x0, h00, z0, rgb, u0, v0, light, true);
                sink.vertex(x0, h01, z1, rgb, u0, v1, light, true);
                sink.vertex(x1, h11, z1, rgb, u1, v1, light, true);
                sink.vertex(x1, h10, z0, rgb, u1, v0, light, true);
                verticesOut += 4;
                if (p.underside()) {
                    sink.vertex(x0, h00, z0, rgb, u0, v0, light, false);
                    sink.vertex(x1, h10, z0, rgb, u1, v0, light, false);
                    sink.vertex(x1, h11, z1, rgb, u1, v1, light, false);
                    sink.vertex(x0, h01, z1, rgb, u0, v1, light, false);
                    verticesOut += 4;
                }
                cellsOut++;
            }
        }
    }

    /** The skirts between the fine ring {@code f} and the coarse ring {@code c} around it (along {@code f}'s outer square). */
    private void emitSeams(Scratch f, Scratch c, Cells cells, Params p, Visibility vis, Sink sink) {
        SurfaceLod.Square b = f.ring.outer();
        int s = f.ring.cell(), big = c.ring.cell();
        for (int u = b.minZ(); u < b.maxZ(); u += s) {
            seam(f, c, cells, p, vis, sink, b.minX(), u, true, b.minX(), u, b.minX() - big);  // west
            seam(f, c, cells, p, vis, sink, b.maxX(), u, true, b.maxX() - s, u, b.maxX());    // east
        }
        for (int u = b.minX(); u < b.maxX(); u += s) {
            seam(f, c, cells, p, vis, sink, u, b.minZ(), false, u, b.minZ(), b.minZ() - big); // north
            seam(f, c, cells, p, vis, sink, u, b.maxZ(), false, u, b.maxZ() - s, b.maxZ());   // south
        }
    }

    /**
     * One skirt along the fine edge from {@code (ex, ez)} running {@code s} blocks along z ({@code alongZ}) or x.
     * {@code (fx, fz)} is the fine cell inside; {@code coarseCross} the coarse cell's coordinate across the edge (x for
     * an edge along z, z for one along x).
     */
    private void seam(Scratch f, Scratch c, Cells cells, Params p, Visibility vis, Sink sink, int ex, int ez, boolean alongZ,
                      int fx, int fz, int coarseCross) {
        int s = f.ring.cell(), big = c.ring.cell();
        if (!f.any[f.cellIndex(fx, fz)]) {
            return;
        }
        int along = alongZ ? ez : ex;
        int coarseAlong = Math.floorDiv(along, big) * big;
        int ccx = alongZ ? coarseCross : coarseAlong, ccz = alongZ ? coarseAlong : coarseCross;
        if (!c.ring.hasCell(ccx, ccz) || !c.any[c.cellIndex(ccx, ccz)]) {
            return;
        }
        if (!f.visible(fx, fz, vis)) {
            return;
        }
        int ax0 = ex, az0 = ez, ax1 = alongZ ? ex : ex + s, az1 = alongZ ? ez + s : ez;
        double hf0 = f.heightAt(ax0, az0), hf1 = f.heightAt(ax1, az1);
        int cx0 = alongZ ? ex : coarseAlong, cz0 = alongZ ? coarseAlong : ez;
        int cx1 = alongZ ? ex : coarseAlong + big, cz1 = alongZ ? coarseAlong + big : ez;
        double hc0 = c.heightAt(cx0, cz0), hc1 = c.heightAt(cx1, cz1);
        double t0 = (along - coarseAlong) / (double) big, t1 = (along + s - coarseAlong) / (double) big;
        double hl0 = hc0 + (hc1 - hc0) * t0, hl1 = hc0 + (hc1 - hc0) * t1;
        if (Math.abs(hf0 - hl0) < 1.0e-4 && Math.abs(hf1 - hl1) < 1.0e-4) {
            return;
        }
        int rgb = cells.color(fx + s / 2, fz + s / 2), light = cells.light(fx + s / 2, fz + s / 2);
        float u0 = p.u0(), u1 = p.u1(), v0 = p.v0(), v1 = p.v1();
        sink.vertex(ax0, hf0, az0, rgb, u0, v0, light, true);
        sink.vertex(ax1, hf1, az1, rgb, u1, v0, light, true);
        sink.vertex(ax1, hl1, az1, rgb, u1, v1, light, true);
        sink.vertex(ax0, hl0, az0, rgb, u0, v1, light, true);
        sink.vertex(ax0, hl0, az0, rgb, u0, v1, light, false);
        sink.vertex(ax1, hl1, az1, rgb, u1, v1, light, false);
        sink.vertex(ax1, hf1, az1, rgb, u1, v0, light, false);
        sink.vertex(ax0, hf0, az0, rgb, u0, v0, light, false);
        verticesOut += 8;
        seamsOut++;
    }

    /** Height of the mesh at block corner (x, z) of the last build's near ring, or NaN outside it (tests, foam). */
    public double nearCornerHeight(int x, int z) {
        Scratch s = scratch[0];
        if (s.ring == null || !inside(s.ring.outer(), x, z)) {
            return Double.NaN;
        }
        return s.heightAt(x, z);
    }

    private static boolean inside(@Nullable SurfaceLod.Square q, int x, int z) {
        return q != null && x >= q.minX() && z >= q.minZ() && x <= q.maxX() && z <= q.maxZ();
    }
}
