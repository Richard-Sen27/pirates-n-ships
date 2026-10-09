package com.richardsenger.piratesnships.sailing.effects.client;

import com.richardsenger.piratesnships.hazards.waves.client.surface.SeaSurface;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;

/**
 * The visible sea surface for the wake (WAKE1; the same rule as the WD1 foam): the top of the still water block at sea
 * level, where the sea is open (water at sea level − 1 with air above it), plus the visible wave height at the point
 * ({@link #waveOffset}): WAV3's drawn surface ({@link SeaSurface#surfaceY}) while it is on, 0 while the surface is off
 * or falls back to flat water (a replacing chunk renderer). Physical client only.
 */
public final class WakeSurface {

    /** Height of a wake quad above the moving surface: above WAV3's foam ({@link SurfaceFoamParticle#LIFT} 0.06). */
    public static final double RIDING_LIFT = SurfaceFoamParticle.LIFT + 0.01;

    private WakeSurface() {
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
        double still = water.getY() + fluid.getHeight(level, water);
        return still + waveOffset(still, x, z, time);
    }

    /** Whether the wake rides a moving surface now (WAV3's surface is on). */
    public static boolean riding() {
        return SeaSurface.isActive();
    }

    /**
     * Visible height of the waves above the still water top {@code still} at {@code (x, z)} [blocks]: WAV3's drawn
     * surface there minus the still top, or 0 while the surface is off or falls back to flat water.
     */
    static double waveOffset(double still, double x, double z, double time) {
        double drawn = SeaSurface.surfaceY(x, z, time);
        return Double.isNaN(drawn) ? 0.0 : drawn - still;
    }
}
