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
        RegionFootprint.collect(x0, y0, z0, x1, y1, z1, box(x0, y0, z0, x1, y1, z1), pose::toWorld, pose::toPlot, out);
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
    void halfBlockOffsetStillCoversByBlockCenter() {
        // shifted by 0.4: every world block center stays inside the same cell; by 0.6 it moves one block on
        Pose a = new Pose(Vec3.ZERO, 0, new Vec3(0.4, 0, 0));
        Pose b = new Pose(Vec3.ZERO, 0, new Vec3(0.6, 0, 0));
        assertTrue(footprint(0, 0, 0, 3, 0, 0, a).contains(BlockPos.asLong(0, 0, 0)));
        assertFalse(footprint(0, 0, 0, 3, 0, 0, b).contains(BlockPos.asLong(0, 0, 0)));
        assertTrue(footprint(0, 0, 0, 3, 0, 0, b).contains(BlockPos.asLong(4, 0, 0)));
        assertEquals(4, footprint(0, 0, 0, 3, 0, 0, b).size());
    }

    @Test
    void quarterTurnSwapsTheAxes() {
        // a 4 (x) by 1 (z) row turned by 90 degrees about its origin lies along z
        Pose pose = new Pose(Vec3.ZERO, 90, new Vec3(0.5, 0, 0.5));
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
