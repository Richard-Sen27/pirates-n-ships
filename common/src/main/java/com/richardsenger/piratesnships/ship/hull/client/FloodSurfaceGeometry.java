package com.richardsenger.piratesnships.ship.hull.client;

import com.richardsenger.piratesnships.ship.hull.runtime.CellSet;

/**
 * Pure geometry of a flooded compartment's water surface (FLD1, docs/design.md §4.4): the plane {@code n · p = d} cut
 * into one convex polygon per cell of the compartment that the plane passes through. Coordinates are local to the cell
 * set's min corner (cell {@code (x, y, z)} is the unit cube {@code [x, x+1] × [y, y+1] × [z, z+1]}), in the ship's plot
 * frame. Since every polygon lies inside one cell of the set, the surface never leaves the compartment; neighbouring
 * cells share their polygon edges, so the surface has no gaps.
 *
 * <p>A cell belongs to the surface when the plane passes through it: its lowest corner is strictly below the plane and
 * its highest corner on or above it. A plane that coincides with a face between two cells (an upright ship at a whole
 * level) therefore belongs to the lower cell only, as that cell's top face.
 */
public final class FloodSurfaceGeometry {

    /** Points closer than this are merged (a plane through a corner crosses several edges there). */
    private static final double MERGE_EPS = 1e-7;

    /** Corner offsets of a unit cube, index bits {@code x | y << 1 | z << 2}. */
    private static final int[][] EDGES = {
            {0, 1}, {2, 3}, {4, 5}, {6, 7}, // along x
            {0, 2}, {1, 3}, {4, 6}, {5, 7}, // along y
            {0, 4}, {1, 5}, {2, 6}, {3, 7}, // along z
    };

    private FloodSurfaceGeometry() {
    }

    /** Receives one polygon: its cell, {@code count} vertices as {@code xyz} triples, counter-clockwise seen from +n. */
    @FunctionalInterface
    public interface PolygonSink {
        void accept(int cellX, int cellY, int cellZ, double[] xyz, int count);
    }

    /**
     * Cuts the plane {@code n · p = d} ({@code n} unit length, local coordinates) by every cell of {@code cells} and passes
     * each polygon (3 to 6 vertices) to {@code sink}. The vertex array is reused between calls. Returns the polygon count.
     */
    public static int polygons(CellSet cells, double nx, double ny, double nz, double d, PolygonSink sink) {
        int sx = cells.sizeX(), sz = cells.sizeZ();
        double lowOff = Math.min(0, nx) + Math.min(0, ny) + Math.min(0, nz);
        double highOff = Math.max(0, nx) + Math.max(0, ny) + Math.max(0, nz);
        double[] f = new double[8];
        double[] pts = new double[3 * 12];
        double[] out = new double[3 * 6];
        double[] angle = new double[12];
        double[] basis = basis(nx, ny, nz);
        int n = 0;
        for (int i = cells.bits().nextSetBit(0); i >= 0; i = cells.bits().nextSetBit(i + 1)) {
            int x = i % sx, z = (i / sx) % sz, y = i / (sx * sz);
            double f0 = nx * x + ny * y + nz * z - d;
            if (!(f0 + lowOff < 0 && f0 + highOff >= 0)) {
                continue;
            }
            for (int c = 0; c < 8; c++) {
                f[c] = f0 + ((c & 1) != 0 ? nx : 0) + ((c & 2) != 0 ? ny : 0) + ((c & 4) != 0 ? nz : 0);
            }
            int k = 0;
            for (int[] e : EDGES) {
                double fa = f[e[0]], fb = f[e[1]];
                if ((fa < 0) == (fb < 0)) {
                    continue;
                }
                double t = fa / (fa - fb);
                double px = x + cx(e[0]) + t * (cx(e[1]) - cx(e[0]));
                double py = y + cy(e[0]) + t * (cy(e[1]) - cy(e[0]));
                double pz = z + cz(e[0]) + t * (cz(e[1]) - cz(e[0]));
                if (!contains(pts, k, px, py, pz)) {
                    pts[3 * k] = px;
                    pts[3 * k + 1] = py;
                    pts[3 * k + 2] = pz;
                    k++;
                }
            }
            if (k < 3) {
                continue; // the plane only touches an edge or a corner
            }
            int count = Math.min(k, 6);
            sortCounterClockwise(pts, k, basis, angle, out);
            sink.accept(x, y, z, out, count);
            n++;
        }
        return n;
    }

    /**
     * The point the surface pivots on when the client levels it to the world (FLD1): the mean of the polygon centroids
     * of the plane {@code n · p = d} in {@code cells}, or the plane point above the set's box center when the plane misses
     * every cell. Local coordinates.
     */
    public static double[] anchor(CellSet cells, double nx, double ny, double nz, double d) {
        double[] sum = new double[4];
        polygons(cells, nx, ny, nz, d, (x, y, z, xyz, count) -> {
            double ax = 0, ay = 0, az = 0;
            for (int i = 0; i < count; i++) {
                ax += xyz[3 * i];
                ay += xyz[3 * i + 1];
                az += xyz[3 * i + 2];
            }
            sum[0] += ax / count;
            sum[1] += ay / count;
            sum[2] += az / count;
            sum[3]++;
        });
        if (sum[3] > 0) {
            return new double[] {sum[0] / sum[3], sum[1] / sum[3], sum[2] / sum[3]};
        }
        double cxm = cells.sizeX() / 2.0, cym = cells.sizeY() / 2.0, czm = cells.sizeZ() / 2.0;
        double off = d - (nx * cxm + ny * cym + nz * czm);
        return new double[] {cxm + off * nx, cym + off * ny, czm + off * nz};
    }

    /** Whether local point {@code (x, y, z)} lies in a cell of the set and strictly below the plane {@code n · p = d}. */
    public static boolean below(CellSet cells, double x, double y, double z, double nx, double ny, double nz, double d) {
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
        return cells.contains(cells.minX() + bx, cells.minY() + by, cells.minZ() + bz) && nx * x + ny * y + nz * z < d;
    }

    /**
     * Texture coordinates of a local point inside cell {@code (cellX, cellY, cellZ)}, both in {@code [0, 1]}: the point
     * projected onto the cell face the surface faces most (the top face for an upright ship), so the water texture tiles
     * once per block like vanilla's.
     */
    public static void uv(double x, double y, double z, int cellX, int cellY, int cellZ, double nx, double ny, double nz,
                          double[] out) {
        double fx = clamp01(x - cellX), fy = clamp01(y - cellY), fz = clamp01(z - cellZ);
        double ax = Math.abs(nx), ay = Math.abs(ny), az = Math.abs(nz);
        if (ay >= ax && ay >= az) {
            out[0] = fx;
            out[1] = fz;
        } else if (ax >= az) {
            out[0] = fz;
            out[1] = fy;
        } else {
            out[0] = fx;
            out[1] = fy;
        }
    }

    /**
     * Splits a convex polygon of {@code count} vertices (3 to 6) into quads for a quad-only vertex format: a fan of
     * quads from vertex 0, the last one closed with a repeated vertex when the count is odd. Returns the vertex indices,
     * four per quad.
     */
    public static int[] quadIndices(int count) {
        if (count < 3) {
            return new int[0];
        }
        int quads = (count - 1) / 2;
        int[] out = new int[4 * quads];
        for (int q = 0, k = 1; q < quads; q++, k += 2) {
            out[4 * q] = 0;
            out[4 * q + 1] = k;
            out[4 * q + 2] = k + 1;
            out[4 * q + 3] = Math.min(k + 2, count - 1);
        }
        return out;
    }

    /** Linear interpolation from {@code from} to {@code to} over {@code duration} ticks from {@code start}, clamped. */
    public static double ease(double from, double to, double start, double duration, double now) {
        if (!(duration > 0) || now >= start + duration) {
            return to;
        }
        if (now <= start) {
            return from;
        }
        return from + (to - from) * (now - start) / duration;
    }

    // ------------------------------------------------------------------ helpers

    private static int cx(int c) {
        return c & 1;
    }

    private static int cy(int c) {
        return (c >> 1) & 1;
    }

    private static int cz(int c) {
        return (c >> 2) & 1;
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    private static boolean contains(double[] pts, int k, double x, double y, double z) {
        for (int i = 0; i < k; i++) {
            if (Math.abs(pts[3 * i] - x) < MERGE_EPS && Math.abs(pts[3 * i + 1] - y) < MERGE_EPS
                    && Math.abs(pts[3 * i + 2] - z) < MERGE_EPS) {
                return true;
            }
        }
        return false;
    }

    /** Two unit vectors {@code u, v} perpendicular to {@code n} with {@code u × v = n}, as {@code [ux, uy, uz, vx, vy, vz]}. */
    static double[] basis(double nx, double ny, double nz) {
        // the world axis least aligned with n
        double ax = 0, ay = 0, az = 0;
        if (Math.abs(nx) <= Math.abs(ny) && Math.abs(nx) <= Math.abs(nz)) {
            ax = 1;
        } else if (Math.abs(ny) <= Math.abs(nz)) {
            ay = 1;
        } else {
            az = 1;
        }
        // u = normalize(a × n), v = n × u  =>  u × v = u × (n × u) = n (u ⊥ n, |u| = 1)
        double ux = ay * nz - az * ny, uy = az * nx - ax * nz, uz = ax * ny - ay * nx;
        double l = Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= l;
        uy /= l;
        uz /= l;
        double vx = ny * uz - nz * uy, vy = nz * ux - nx * uz, vz = nx * uy - ny * ux;
        return new double[] {ux, uy, uz, vx, vy, vz};
    }

    /** Writes the first {@code min(k, 6)} points of {@code pts}, sorted by angle about their centroid, into {@code out}. */
    private static void sortCounterClockwise(double[] pts, int k, double[] b, double[] angle, double[] out) {
        double mx = 0, my = 0, mz = 0;
        for (int i = 0; i < k; i++) {
            mx += pts[3 * i];
            my += pts[3 * i + 1];
            mz += pts[3 * i + 2];
        }
        mx /= k;
        my /= k;
        mz /= k;
        for (int i = 0; i < k; i++) {
            double dx = pts[3 * i] - mx, dy = pts[3 * i + 1] - my, dz = pts[3 * i + 2] - mz;
            angle[i] = Math.atan2(dx * b[3] + dy * b[4] + dz * b[5], dx * b[0] + dy * b[1] + dz * b[2]);
        }
        // insertion sort by angle (k <= 12)
        int[] order = new int[k];
        for (int i = 0; i < k; i++) {
            int j = i;
            while (j > 0 && angle[order[j - 1]] > angle[i]) {
                order[j] = order[j - 1];
                j--;
            }
            order[j] = i;
        }
        for (int i = 0; i < Math.min(k, 6); i++) {
            System.arraycopy(pts, 3 * order[i], out, 3 * i, 3);
        }
    }
}
