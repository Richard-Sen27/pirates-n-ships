package com.richardsenger.piratesnships.ship.sable;

import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionContainer;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionRegion;
import dev.ryanhcode.sable.util.BoundedBitVolume3i;
import java.util.BitSet;
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

    /** Number of regions in the level, ours and everyone else's. */
    public static int count(Level level) {
        WaterOcclusionContainer<?> container = WaterOcclusionContainer.getContainer(level);
        return container == null ? 0 : container.getRegions().size();
    }
}
