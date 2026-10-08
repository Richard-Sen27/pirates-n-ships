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
 * Pure math of the dry hull plant culling (HV1, docs/design.md §4.4): which world blocks a water occlusion region covers
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

    /**
     * Adds to {@code out} (as {@link BlockPos#asLong}) every world block whose center lies in an occupied plot cell of the
     * region with inclusive plot bounds min..max, the same test Sable's {@code isOccluded} makes for a point. Visits the
     * world box around the transformed plot box, so the cost is that box's volume.
     *
     * @return the number of world blocks visited (for the cost counters)
     */
    public static int collect(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Cells cells,
                              Transform toWorld, Transform toPlot, LongSet out) {
        double x0 = Double.POSITIVE_INFINITY, y0 = Double.POSITIVE_INFINITY, z0 = Double.POSITIVE_INFINITY;
        double x1 = Double.NEGATIVE_INFINITY, y1 = Double.NEGATIVE_INFINITY, z1 = Double.NEGATIVE_INFINITY;
        for (int c = 0; c < 8; c++) {
            Vec3 w = toWorld.apply(new Vec3((c & 1) == 0 ? minX : maxX + 1, (c & 2) == 0 ? minY : maxY + 1,
                    (c & 4) == 0 ? minZ : maxZ + 1));
            x0 = Math.min(x0, w.x); y0 = Math.min(y0, w.y); z0 = Math.min(z0, w.z);
            x1 = Math.max(x1, w.x); y1 = Math.max(y1, w.y); z1 = Math.max(z1, w.z);
        }
        int bx0 = Mth.floor(x0), by0 = Mth.floor(y0), bz0 = Mth.floor(z0);
        int bx1 = Mth.floor(x1), by1 = Mth.floor(y1), bz1 = Mth.floor(z1);
        int visited = 0;
        for (int y = by0; y <= by1; y++) {
            for (int z = bz0; z <= bz1; z++) {
                for (int x = bx0; x <= bx1; x++) {
                    visited++;
                    Vec3 p = toPlot.apply(new Vec3(x + 0.5, y + 0.5, z + 0.5));
                    if (cells.occupied(Mth.floor(p.x), Mth.floor(p.y), Mth.floor(p.z))) {
                        out.add(BlockPos.asLong(x, y, z));
                    }
                }
            }
        }
        return visited;
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
