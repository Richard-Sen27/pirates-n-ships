package com.richardsenger.piratesnships.ship.hull.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Footprint of a region in the world and the sections a footprint change touches (HV1 plant culling). */
class RegionFootprintTest {

    /** A full box region (plot) min..max inclusive. */
    private static RegionFootprint.Cells box(int x0, int y0, int z0, int x1, int y1, int z1) {
        return (x, y, z) -> x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1;
    }

    /** A ship pose: plot → world = rotate by yaw (degrees, about +y through the plot origin {@code o}) then translate. */
    private record Pose(Vec3 o, double yawDeg, Vec3 t) {
        Vec3 toWorld(Vec3 p) {
            double a = Math.toRadians(yawDeg), c = Math.cos(a), s = Math.sin(a);
            double x = p.x - o.x, z = p.z - o.z;
            return new Vec3(c * x - s * z + t.x, p.y - o.y + t.y, s * x + c * z + t.z);
        }

        Vec3 toPlot(Vec3 w) {
            double a = Math.toRadians(yawDeg), c = Math.cos(a), s = Math.sin(a);
            double x = w.x - t.x, z = w.z - t.z;
            return new Vec3(c * x + s * z + o.x, w.y - t.y + o.y, -s * x + c * z + o.z);
        }
    }

    private static LongSet footprint(int x0, int y0, int z0, int x1, int y1, int z1, Pose pose) {
        LongSet out = new LongOpenHashSet();
        RegionFootprint.collect(x0, y0, z0, x1, y1, z1, box(x0, y0, z0, x1, y1, z1), pose::toWorld, pose::toPlot,
                (x, z) -> Vec3.ZERO, out, new LongOpenHashSet());
        return out;
    }

    @Test
    void translatedBoxCoversExactlyItsWorldBlocks() {
        // a 3x2x4 hold in a plot far away, moved so that its min corner sits at world (10, 60, -5)
        Pose pose = new Pose(new Vec3(1_000_000, 100, 2_000_000), 0, new Vec3(10, 60, -5));
        LongSet f = footprint(1_000_000, 100, 2_000_000, 1_000_002, 101, 2_000_003, pose);
        assertEquals(3 * 2 * 4, f.size());
        assertTrue(f.contains(BlockPos.asLong(10, 60, -5)));
        assertTrue(f.contains(BlockPos.asLong(12, 61, -2)));
        assertFalse(f.contains(BlockPos.asLong(13, 60, -5)));
        assertFalse(f.contains(BlockPos.asLong(10, 59, -5)));
    }

    @Test
    void partlyCoveredBlocksCount() {
        // HV1c: shifted by 0.4 (or 0.6), the region cuts world blocks 0 and 4 only in part; both count
        Pose a = new Pose(Vec3.ZERO, 0, new Vec3(0.4, 0, 0));
        Pose b = new Pose(Vec3.ZERO, 0, new Vec3(0.6, 0, 0));
        for (Pose p : new Pose[] {a, b}) {
            LongSet f = footprint(0, 0, 0, 3, 0, 0, p);
            assertEquals(5, f.size(), "footprint " + f);
            assertTrue(f.contains(BlockPos.asLong(0, 0, 0)));
            assertTrue(f.contains(BlockPos.asLong(4, 0, 0)));
            assertFalse(f.contains(BlockPos.asLong(-1, 0, 0)));
            assertFalse(f.contains(BlockPos.asLong(5, 0, 0)));
        }
    }

    @Test
    void waterLineCutsTheTopAndBottomLayers() {
        // a two-layer hold raised by 0.3: world layers 0 (0.3..1), 1 and 2 (2..2.3) are cut, -1 and 3 are not
        LongSet f = footprint(0, 0, 0, 1, 1, 1, new Pose(Vec3.ZERO, 0, new Vec3(0, 0.3, 0)));
        assertEquals(2 * 2 * 3, f.size(), "footprint " + f);
        assertTrue(f.contains(BlockPos.asLong(0, 2, 0)));
        assertFalse(f.contains(BlockPos.asLong(0, 3, 0)));
        assertFalse(f.contains(BlockPos.asLong(0, -1, 0)));
    }

    @Test
    void touchingFacesDoNotCount() {
        // grid-aligned: neighbours share a face with the region but are not cut by it
        assertEquals(3 * 2 * 4, footprint(5, 5, 5, 7, 6, 8, new Pose(Vec3.ZERO, 0, Vec3.ZERO)).size());
        // a sliver thinner than EPS is a touch too
        assertEquals(4, footprint(0, 0, 0, 3, 0, 0, new Pose(Vec3.ZERO, 0, new Vec3(RegionFootprint.EPS / 2, 0, 0))).size());
    }

    @Test
    void aTurnedCellCutsItsEdgeNeighboursButNotItsCornerNeighbours() {
        // one plot cell turned by 45 degrees about its center, which sits on the world cell center (0.5, 0.5): a diamond
        // reaching 0.707 out, so its tips cut the four edge neighbours, while the corner neighbours (from 1.0) stay clear
        Pose pose = new Pose(new Vec3(0.5, 0, 0.5), 45, new Vec3(0.5, 0, 0.5));
        LongSet f = footprint(0, 0, 0, 0, 0, 0, pose);
        assertEquals(5, f.size(), "footprint " + f);
        for (int[] d : new int[][] {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            assertTrue(f.contains(BlockPos.asLong(d[0], 0, d[1])), "no block at " + d[0] + ", " + d[1] + ": " + f);
        }
        assertFalse(f.contains(BlockPos.asLong(1, 0, 1)));
    }

    @Test
    void offsetCellsFollowTheModelOffset() {
        // a row x 0..3; a plant one block west (x = -1) pokes in only when its model is shifted east
        Pose aligned = new Pose(Vec3.ZERO, 0, Vec3.ZERO);
        for (double ox : new double[] {0.2, -0.2}) {
            LongSet cells = new LongOpenHashSet(), offset = new LongOpenHashSet();
            RegionFootprint.collect(0, 0, 0, 3, 0, 0, box(0, 0, 0, 3, 0, 0), aligned::toWorld, aligned::toPlot,
                    (x, z) -> new Vec3(ox, 0, 0), cells, offset);
            assertFalse(cells.contains(BlockPos.asLong(-1, 0, 0)));
            assertEquals(ox > 0, offset.contains(BlockPos.asLong(-1, 0, 0)), "offset " + ox + ": " + offset);
            assertEquals(ox < 0, offset.contains(BlockPos.asLong(4, 0, 0)), "offset " + ox + ": " + offset);
            assertTrue(offset.contains(BlockPos.asLong(0, 0, 0)) && offset.contains(BlockPos.asLong(3, 0, 0)));
        }
    }

    /** A general rigid pose: plot → world = rotate (yaw about y, then pitch about x, then roll about z) and translate. */
    private record Rigid(double[] m, Vec3 t) {
        static Rigid of(double yaw, double pitch, double roll, Vec3 t) {
            double[] ry = rot(1, yaw), rx = rot(0, pitch), rz = rot(2, roll);
            return new Rigid(mul(rz, mul(rx, ry)), t);
        }

        private static double[] rot(int axis, double deg) {
            double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
            return switch (axis) {
                case 0 -> new double[] {1, 0, 0, 0, c, -s, 0, s, c};
                case 1 -> new double[] {c, 0, s, 0, 1, 0, -s, 0, c};
                default -> new double[] {c, -s, 0, s, c, 0, 0, 0, 1};
            };
        }

        private static double[] mul(double[] a, double[] b) {
            double[] r = new double[9];
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    for (int k = 0; k < 3; k++) {
                        r[i * 3 + j] += a[i * 3 + k] * b[k * 3 + j];
                    }
                }
            }
            return r;
        }

        Vec3 toWorld(Vec3 p) {
            return new Vec3(m[0] * p.x + m[1] * p.y + m[2] * p.z + t.x, m[3] * p.x + m[4] * p.y + m[5] * p.z + t.y,
                    m[6] * p.x + m[7] * p.y + m[8] * p.z + t.z);
        }

        Vec3 toPlot(Vec3 w) {
            double x = w.x - t.x, y = w.y - t.y, z = w.z - t.z;
            return new Vec3(m[0] * x + m[3] * y + m[6] * z, m[1] * x + m[4] * y + m[7] * z, m[2] * x + m[5] * y + m[8] * z);
        }
    }

    /**
     * The exact cell test against brute force: for random tilted poses and a ragged region, every world block that a
     * dense grid of sample points finds inside the region is in the footprint (nothing that pokes in is missed), and
     * every block in the footprint has a sample point in the region within 0.25 blocks of its cell (nothing far is hidden).
     */
    @Test
    void matchesSamplingOnTiltedPoses() {
        java.util.Random random = new java.util.Random(42);
        RegionFootprint.Cells ragged = (x, y, z) -> x >= 0 && x <= 4 && y >= 0 && y <= 2 && z >= 0 && z <= 3
                && ((x * 7 + y * 3 + z * 5) % 4 != 0);
        for (int round = 0; round < 6; round++) {
            Rigid pose = Rigid.of(random.nextDouble() * 360, random.nextDouble() * 30 - 15, random.nextDouble() * 30 - 15,
                    new Vec3(1000.3 + random.nextDouble(), 60 + random.nextDouble(), -200 + random.nextDouble()));
            LongSet f = new LongOpenHashSet();
            RegionFootprint.collect(0, 0, 0, 4, 2, 3, ragged, pose::toWorld, pose::toPlot, (x, z) -> Vec3.ZERO, f,
                    new LongOpenHashSet());
            int n = 12;
            for (int x = 990; x <= 1012; x++) {
                for (int y = 54; y <= 68; y++) {
                    for (int z = -210; z <= -188; z++) {
                        boolean inside = sampled(pose, ragged, x, y, z, 0.0, n);
                        long key = BlockPos.asLong(x, y, z);
                        if (inside) {
                            assertTrue(f.contains(key), "round " + round + ": block " + x + ", " + y + ", " + z
                                    + " pokes into the region but is not in the footprint");
                        }
                        if (f.contains(key) && !inside) {
                            assertTrue(sampled(pose, ragged, x, y, z, 0.25, n), "round " + round + ": block " + x + ", " + y
                                    + ", " + z + " is in the footprint but no point within 0.25 of it is in the region");
                        }
                    }
                }
            }
        }
    }

    /** Whether one of n³ sample points of the block grown by {@code grow} lies in an occupied plot cell. */
    private static boolean sampled(Rigid pose, RegionFootprint.Cells cells, int x, int y, int z, double grow, int n) {
        double size = 1 + 2 * grow;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < n; k++) {
                    Vec3 p = pose.toPlot(new Vec3(x - grow + size * (i + 0.5) / n, y - grow + size * (j + 0.5) / n,
                            z - grow + size * (k + 0.5) / n));
                    if (cells.occupied((int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Test
    void quarterTurnSwapsTheAxes() {
        // a 4 (x) by 1 (z) row turned by 90 degrees about its origin lies along z (plot x 0..4 -> world z 0..4)
        Pose pose = new Pose(Vec3.ZERO, 90, new Vec3(1, 0, 0));
        LongSet f = footprint(0, 0, 0, 3, 0, 0, pose);
        assertEquals(4, f.size(), "footprint " + f);
        for (int z = 0; z < 4; z++) {
            assertTrue(f.contains(BlockPos.asLong(0, 0, z)), "no block at z " + z + ": " + f);
        }
    }

    @Test
    void changedSectionsHoldOnlyTheDifference() {
        LongSet before = new LongOpenHashSet(new long[] {BlockPos.asLong(15, 0, 0), BlockPos.asLong(14, 0, 0), BlockPos.asLong(3, 20, 3)});
        LongSet after = new LongOpenHashSet(new long[] {BlockPos.asLong(16, 0, 0), BlockPos.asLong(14, 0, 0), BlockPos.asLong(3, 20, 3)});
        Long2ObjectMap<LongList> changed = RegionFootprint.changedBySection(before, after);
        assertEquals(2, changed.size(), "left (15,0,0) in section 0 and entered (16,0,0) in section 1: " + changed);
        assertEquals(1, changed.get(SectionPos.asLong(0, 0, 0)).size());
        assertEquals(1, changed.get(SectionPos.asLong(1, 0, 0)).size());
        assertTrue(RegionFootprint.changedBySection(after, after).isEmpty());
        assertEquals(3, RegionFootprint.changedBySection(new LongOpenHashSet(), after).values().stream().mapToInt(LongList::size).sum());
    }

    @Test
    void negativeCoordinatesMapToTheirSections() {
        LongSet after = new LongOpenHashSet(new long[] {BlockPos.asLong(-1, -1, -17)});
        Long2ObjectMap<LongList> changed = RegionFootprint.changedBySection(new LongOpenHashSet(), after);
        assertTrue(changed.containsKey(SectionPos.asLong(-1, -1, -2)), "sections: " + changed.keySet());
    }

    /**
     * Cost of the refresh (HV1): a 12 x 3 x 5 hold sails at 8 blocks/s (0.4 blocks per tick) for 10 s over a kelp forest
     * that fills every section (the worst case: every section with a changed block is re-marked), refreshing every 5
     * ticks. Prints the sections re-marked per second, straight and at 45 degrees.
     */
    @Test
    void sectionsReMarkedPerSecondAtEightBlocksPerSecond() {
        for (double heading : new double[] {0, 45}) {
            double a = Math.toRadians(heading);
            LongSet prev = new LongOpenHashSet();
            int marked = 0, refreshes = 0;
            long nanos = 0;
            for (int tick = 0; tick <= 200; tick += 5) {
                double d = 0.4 * tick;
                Pose pose = new Pose(new Vec3(1_000_000, 64, 1_000_000), heading,
                        new Vec3(100.3 + d * Math.cos(a), 60, 200.7 + d * Math.sin(a)));
                long t0 = System.nanoTime();
                LongSet f = footprint(1_000_000, 64, 1_000_000, 1_000_011, 66, 1_000_004, pose);
                Long2ObjectMap<LongList> changed = RegionFootprint.changedBySection(prev, f);
                nanos += System.nanoTime() - t0;
                if (tick > 0) {
                    marked += changed.size();
                    refreshes++;
                }
                prev = f;
            }
            double perSecond = marked / 10.0;
            System.out.printf("HV1 refresh cost, heading %.0f deg: %.1f sections re-marked per second (%d refreshes, %.0f us each)%n",
                    heading, perSecond, refreshes, nanos / 1000.0 / (refreshes + 1));
            // a 12-block hold spans 1-2 sections per axis, so each refresh touches at most a handful of sections
            assertTrue(perSecond <= 4 * 8, "too many sections re-marked per second: " + perSecond);
            assertTrue(perSecond > 0, "a moving ship must re-mark something");
        }
    }
}
