package com.richardsenger.piratesnships.ship.sable;

import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionContainer;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionRegion;
import dev.ryanhcode.sable.util.BoundedBitVolume3i;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Sable's water occlusion regions (docs/sable-notes.md §4.4): sets of plot cells in which world water does not exist for
 * entities, the camera, particles and rendering. Regions are per level and not networked, so the server and every
 * client each keep their own. On the client, {@code addRegion} also builds the depth-mask render region.
 *
 * <p>Backed by {@code sublevel/water_occlusion/WaterOcclusionContainer.java} ({@code getContainer} l.41,
 * {@code isOccluded} l.54, {@code addRegion}/{@code removeRegion} l.111-113, {@code getRegions} l.115),
 * {@code ServerWaterOcclusionContainer.java} / {@code ClientWaterOcclusionContainer.java} ({@code addRegion} l.22 / l.31)
 * and {@code util/BoundedBitVolume3i.java} (constructor l.17, {@code setOccupied} l.63). Note that
 * {@code BoundedBitVolume3i} lives in Sable's {@code util} package, not its API.
 */
public final class WaterRegions {

    private WaterRegions() {
    }

    /** One region we added, bound to the container it was added to (a client level change makes it stale). */
    public static final class Handle {
        private final WaterOcclusionContainer<?> container;
        private final WaterOcclusionRegion region;

        private Handle(WaterOcclusionContainer<?> container, WaterOcclusionRegion region) {
            this.container = container;
            this.region = region;
        }

        /** Whether Sable marked the region dirty because a solid block next to it changed ({@code WaterOcclusionRegion#isDirty}). */
        public boolean isDirty() {
            return region.isDirty();
        }
    }

    /**
     * Adds a region of plot cells. {@code cells} uses the layout {@code x + sx·(z + sz·y)} relative to the min corner
     * (the {@code HullGrid} index layout). Returns null when the level has no container or the set is empty.
     */
    public static @Nullable Handle add(Level level, int minX, int minY, int minZ, int sx, int sy, int sz, BitSet cells) {
        WaterOcclusionContainer<?> container = WaterOcclusionContainer.getContainer(level);
        if (container == null || cells.isEmpty()) {
            return null;
        }
        BoundedBitVolume3i volume = new BoundedBitVolume3i(minX, minY, minZ, minX + sx - 1, minY + sy - 1, minZ + sz - 1);
        for (int i = cells.nextSetBit(0); i >= 0; i = cells.nextSetBit(i + 1)) {
            int x = i % sx, z = (i / sx) % sz, y = i / (sx * sz);
            volume.setOccupied(minX + x, minY + y, minZ + z, true);
        }
        return new Handle(container, container.addRegion(volume));
    }

    /** Removes a region from the container it was added to (no-op if that container is gone or already lost it). */
    public static void remove(Handle handle) {
        if (handle.container.getRegions().contains(handle.region)) {
            handle.container.removeRegion(handle.region);
        }
    }

    /** Whether the handle's region is still registered in the level's current container. */
    public static boolean isLive(Level level, Handle handle) {
        WaterOcclusionContainer<?> container = WaterOcclusionContainer.getContainer(level);
        return container == handle.container && container.getRegions().contains(handle.region);
    }

    /** Whether a world position is inside any region of the level (what Sable's entity mixins ask). */
    public static boolean isOccluded(Level level, Vec3 worldPos) {
        WaterOcclusionContainer<?> container = WaterOcclusionContainer.getContainer(level);
        return container != null && container.isOccluded(worldPos);
    }

    /**
     * A read-only look at one region of the level (ours or another mod's) for the client's plant culling (HV1): its plot
     * bounds (inclusive), its cells and the pose of the sub-level that holds it, captured now. Like
     * {@code WaterOcclusionContainer#isOccluded} (l.54-67), the sub-level is the one containing the region's min corner,
     * transformed with its {@code logicalPose} ({@code SubLevel} l.151); a region in no sub-level is in world space.
     * Use it on the thread that owns the level, right away (the pose is not copied).
     */
    public static final class View {
        private final BoundedBitVolume3i volume;
        private final @Nullable SubLevel subLevel;
        public final int minX, minY, minZ, maxX, maxY, maxZ;

        private View(BoundedBitVolume3i volume, @Nullable SubLevel subLevel) {
            this.volume = volume;
            this.subLevel = subLevel;
            var min = volume.getMinBlockPos();
            var max = volume.getMaxBlockPos();
            minX = min.getX(); minY = min.getY(); minZ = min.getZ();
            maxX = max.getX(); maxY = max.getY(); maxZ = max.getZ();
        }

        /** Whether plot cell x/y/z is in the region ({@code BoundedBitVolume3i#getOccupied}, false outside the bounds). */
        public boolean occupied(int x, int y, int z) {
            return volume.getOccupied(x, y, z);
        }

        public Vec3 toWorld(Vec3 plot) {
            return subLevel == null ? plot : subLevel.logicalPose().transformPosition(plot);
        }

        public Vec3 toPlot(Vec3 world) {
            return subLevel == null ? world : subLevel.logicalPose().transformPositionInverse(world);
        }
    }

    /** All regions of the level as {@link View}s (empty without a container). */
    public static List<View> views(Level level) {
        WaterOcclusionContainer<?> container = WaterOcclusionContainer.getContainer(level);
        if (container == null || container.getRegions().isEmpty()) {
            return List.of();
        }
        List<View> out = new ArrayList<>(container.getRegions().size());
        for (WaterOcclusionRegion region : container.getRegions()) {
            BoundedBitVolume3i volume = region.getVolume();
            SubLevel sub = Sable.HELPER.getContaining(level, volume.getMinBlockPos());
            if (sub != null && sub.isRemoved()) {
                continue;
            }
            out.add(new View(volume, sub));
        }
        return out;
    }

    /** Number of regions in the level, ours and everyone else's. */
    public static int count(Level level) {
        WaterOcclusionContainer<?> container = WaterOcclusionContainer.getContainer(level);
        return container == null ? 0 : container.getRegions().size();
    }
}
