package com.richardsenger.piratesnships.sailing.effects.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;

/**
 * The visible sea surface for things that lie on the water (WAKE1; the same rule as the WD1 foam): the top of the
 * still water block at sea level, where the sea is open (water at sea level − 1 with air above it), plus the visible
 * wave height at the point. Today the water renders flat, so the wave part is 0; when WAV3's surface mesh lands, it
 * returns the mesh height there and every wake quad rides it ({@link #waveOffset}: one line). Physical client only.
 */
public final class SeaSurface {

    private SeaSurface() {
    }

    /**
     * World Y of the sea surface at {@code (x, z)} at game time {@code time}, or {@link Double#NaN} when there is no
     * open sea-level water there (land, an unloaded chunk, a block on the water).
     */
    public static double y(ClientLevel level, double x, double z, double time) {
        BlockPos water = BlockPos.containing(x, level.getSeaLevel() - 1.0, z);
        if (!level.isLoaded(water)) {
            return Double.NaN;
        }
        FluidState fluid = level.getFluidState(water);
        if (!fluid.is(FluidTags.WATER) || !level.getBlockState(water.above()).isAir()) {
            return Double.NaN;
        }
        return water.getY() + fluid.getHeight(level, water) + waveOffset(x, z, time);
    }

    /**
     * Visible height of the waves above still water at {@code (x, z)} [blocks]. 0 while the water renders flat; WAV3
     * replaces this with its surface mesh's height (WaveField through ClientWaves, the same sample the mesh draws).
     */
    static double waveOffset(double x, double z, double time) {
        return 0.0;
    }
}
