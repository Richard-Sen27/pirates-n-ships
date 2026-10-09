package com.richardsenger.piratesnships.ship.hull.client;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Pure math of the dry hull plant culling (HV1, HV1c, docs/design.md §4.4): which world blocks a water occlusion region cuts
 * right now (its <em>footprint</em>), and which chunk sections a change of footprint touches. No world access; the
 * transforms come from the caller ({@code WaterRegions.View} in the game, plain lambdas in tests).
 */
public final class RegionFootprint {

    private RegionFootprint() {
    }

    /** Whether plot cell x/y/z belongs to the region. */
    @FunctionalInterface
    public interface Cells {
        boolean occupied(int x, int y, int z);
    }

    @FunctionalInterface
    public interface Transform {
        Vec3 apply(Vec3 p);
    }

    /** A plant model's random offset at world column x/z (vanilla's {@code BlockState#getOffset}), in blocks. */
    @FunctionalInterface
    public interface ModelOffset {
        Vec3 at(int x, int z);
    }

    /** Overlap (in blocks, along every separating axis) below which a cell only touches a region and is not cut by it. */
    static final double EPS = 1.0e-3;

    /**
     * Adds to {@code cellsOut} (as {@link BlockPos#asLong}) every world block whose unit cell overlaps an occupied plot
     * cell of the region with inclusive plot bounds min..max (HV1c: any part of the cell, not only its center), and to
     * {@code offsetOut} every world block whose cell shifted by {@code offset} at its column overlaps one (for plants
     * with a random model offset, docs/design.md §4.4 "Partly covered cells"). The test is exact for a rigid pose (the
     * separating axis theorem between the world cell, a parallelepiped in plot space, and each plot cell); touching
     * faces (overlap below {@link #EPS}) do not count. Visits the world box around the transformed plot box grown by one
     * block.
     *
     * @return the number of world blocks visited (for the cost counters)
     */
    public static int collect(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Cells cells,
                              Transform toWorld, Transform toPlot, ModelOffset offset, LongSet cellsOut, LongSet offsetOut) {
        double x0 = Double.POSITIVE_INFINITY, y0 = Double.POSITIVE_INFINITY, z0 = Double.POSITIVE_INFINITY;
        double x1 = Double.NEGATIVE_INFINITY, y1 = Double.NEGATIVE_INFINITY, z1 = Double.NEGATIVE_INFINITY;
        for (int c = 0; c < 8; c++) {
            Vec3 w = toWorld.apply(new Vec3((c & 1) == 0 ? minX : maxX + 1, (c & 2) == 0 ? minY : maxY + 1,
                    (c & 4) == 0 ? minZ : maxZ + 1));
            x0 = Math.min(x0, w.x); y0 = Math.min(y0, w.y); z0 = Math.min(z0, w.z);
            x1 = Math.max(x1, w.x); y1 = Math.max(y1, w.y); z1 = Math.max(z1, w.z);
        }
        int bx0 = Mth.floor(x0) - 1, by0 = Mth.floor(y0) - 1, bz0 = Mth.floor(z0) - 1;
        int bx1 = Mth.floor(x1) + 1, by1 = Mth.floor(y1) + 1, bz1 = Mth.floor(z1) + 1;
        CellTest test = new CellTest(cells, toPlot, bx0, by0, bz0);
        int visited = 0;
        for (int z = bz0; z <= bz1; z++) {
            for (int x = bx0; x <= bx1; x++) {
                Vec3 o = offset.at(x, z);
                boolean shifted = o.x != 0 || o.y != 0 || o.z != 0;
                for (int y = by0; y <= by1; y++) {
                    visited++;
                    boolean hit = test.overlaps(x + 0.5, y + 0.5, z + 0.5);
                    if (hit) {
                        cellsOut.add(BlockPos.asLong(x, y, z));
                    }
                    if (shifted ? test.overlaps(x + 0.5 + o.x, y + 0.5 + o.y, z + 0.5 + o.z) : hit) {
                        offsetOut.add(BlockPos.asLong(x, y, z));
                    }
                }
            }
        }
        return visited;
    }

    /**
     * Whether a world unit cube (given by its center) overlaps an occupied plot cell. The plot pose is affine, so a world
     * unit cube is a parallelepiped in plot space with constant half axes; the 15 separating axes and their summed radii
     * are computed once per region, and only the center moves.
     */
    static final class CellTest {
        private final Cells cells;
        private final double rx, ry, rz;                 // plot position of the world reference corner
        private final double[] ex, ey, ez;               // plot images of the world unit vectors
        private final double extX, extY, extZ;           // plot-axis half extents of a world cell
        private final double[][] axes;                   // separating axes (unnormalised)
        private final double[] limits;                   // per axis: radius sum minus EPS·|axis|
        private final int refX, refY, refZ;

        CellTest(Cells cells, Transform toPlot, int refX, int refY, int refZ) {
            this.cells = cells;
            this.refX = refX;
            this.refY = refY;
            this.refZ = refZ;
            Vec3 r = toPlot.apply(new Vec3(refX, refY, refZ));
            rx = r.x; ry = r.y; rz = r.z;
            ex = sub(toPlot.apply(new Vec3(refX + 1, refY, refZ)), r);
            ey = sub(toPlot.apply(new Vec3(refX, refY + 1, refZ)), r);
            ez = sub(toPlot.apply(new Vec3(refX, refY, refZ + 1)), r);
            extX = 0.5 * (Math.abs(ex[0]) + Math.abs(ey[0]) + Math.abs(ez[0]));
            extY = 0.5 * (Math.abs(ex[1]) + Math.abs(ey[1]) + Math.abs(ez[1]));
            extZ = 0.5 * (Math.abs(ex[2]) + Math.abs(ey[2]) + Math.abs(ez[2]));
            double[][] unit = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
            double[][] w = {ex, ey, ez};
            java.util.List<double[]> list = new java.util.ArrayList<>(15);
            for (double[] u : unit) {
                list.add(u);
            }
            list.add(cross(ey, ez));
            list.add(cross(ez, ex));
            list.add(cross(ex, ey));
            for (double[] u : unit) {
                for (double[] a : w) {
                    list.add(cross(u, a));
                }
            }
            java.util.List<double[]> kept = new java.util.ArrayList<>(15);
            java.util.List<Double> lim = new java.util.ArrayList<>(15);
            for (double[] n : list) {
                double len = Math.sqrt(dot(n, n));
                if (len < 1.0e-9) {
                    continue; // parallel edges: the axis is covered by the face axes
                }
                double rb = 0.5 * (Math.abs(n[0]) + Math.abs(n[1]) + Math.abs(n[2]));
                double rw = 0.5 * (Math.abs(dot(ex, n)) + Math.abs(dot(ey, n)) + Math.abs(dot(ez, n)));
                kept.add(n);
                lim.add(rb + rw - EPS * len);
            }
            axes = kept.toArray(new double[0][]);
            limits = new double[lim.size()];
            for (int i = 0; i < limits.length; i++) {
                limits[i] = lim.get(i);
            }
        }

        boolean overlaps(double wx, double wy, double wz) {
            double dx = wx - refX, dy = wy - refY, dz = wz - refZ;
            double px = rx + ex[0] * dx + ey[0] * dy + ez[0] * dz;
            double py = ry + ex[1] * dx + ey[1] * dy + ez[1] * dz;
            double pz = rz + ex[2] * dx + ey[2] * dy + ez[2] * dz;
            // the common case first: the center itself lies in an occupied cell
            int cx = Mth.floor(px), cy = Mth.floor(py), cz = Mth.floor(pz);
            if (cells.occupied(cx, cy, cz)) {
                return true;
            }
            int ix0 = Mth.floor(px - extX + EPS), ix1 = Mth.floor(px + extX - EPS);
            int iy0 = Mth.floor(py - extY + EPS), iy1 = Mth.floor(py + extY - EPS);
            int iz0 = Mth.floor(pz - extZ + EPS), iz1 = Mth.floor(pz + extZ - EPS);
            for (int iy = iy0; iy <= iy1; iy++) {
                for (int iz = iz0; iz <= iz1; iz++) {
                    for (int ix = ix0; ix <= ix1; ix++) {
                        if ((ix != cx || iy != cy || iz != cz) && cells.occupied(ix, iy, iz)
                                && overlapping(px - (ix + 0.5), py - (iy + 0.5), pz - (iz + 0.5))) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        /** True when no axis separates the two boxes whose centers differ by d (i.e. they overlap by more than EPS). */
        private boolean overlapping(double dx, double dy, double dz) {
            for (int i = 0; i < axes.length; i++) {
                double[] n = axes[i];
                if (Math.abs(n[0] * dx + n[1] * dy + n[2] * dz) >= limits[i]) {
                    return false;
                }
            }
            return true;
        }

        private static double[] sub(Vec3 a, Vec3 b) {
            return new double[] {a.x - b.x, a.y - b.y, a.z - b.z};
        }

        private static double[] cross(double[] a, double[] b) {
            return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
        }

        private static double dot(double[] a, double[] b) {
            return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        }
    }

    /**
     * The world blocks that are in exactly one of the two footprints (newly covered or left), grouped by chunk section
     * ({@link SectionPos#asLong}). These are the sections whose compiled mesh may now be wrong.
     */
    public static Long2ObjectMap<LongList> changedBySection(LongSet before, LongSet after) {
        Long2ObjectMap<LongList> out = new Long2ObjectOpenHashMap<>();
        addMissing(before, after, out);
        addMissing(after, before, out);
        return out;
    }

    private static void addMissing(LongSet from, LongSet notIn, Long2ObjectMap<LongList> out) {
        for (LongIterator it = from.iterator(); it.hasNext(); ) {
            long pos = it.nextLong();
            if (!notIn.contains(pos)) {
                long section = SectionPos.asLong(SectionPos.blockToSectionCoord(BlockPos.getX(pos)),
                        SectionPos.blockToSectionCoord(BlockPos.getY(pos)), SectionPos.blockToSectionCoord(BlockPos.getZ(pos)));
                out.computeIfAbsent(section, k -> new LongArrayList()).add(pos);
            }
        }
    }
}
