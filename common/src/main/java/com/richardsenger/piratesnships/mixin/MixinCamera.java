package com.richardsenger.piratesnships.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.richardsenger.piratesnships.ship.hull.client.FloodSurfaceRenderer;
import net.minecraft.client.Camera;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A camera below the water surface of a flooded compartment is under water (FLD1, docs/design.md §4.4): underwater fog,
 * fog colour and sky, all of which vanilla derives from {@link Camera#getFluidInCamera()}. The world's own answer is
 * wrong inside a flooding hull in two cases: the camera's cell is still in Sable's dry region (Sable's
 * {@code water_occlusion.CameraMixin} turns WATER into NONE there; the server counts a cell flooded only once the water
 * passes its centre), or the flood water stands higher than the sea outside, so there is no world water at all.
 * {@link FloodSurfaceRenderer#isBelowSurface} answers with the surface the client draws.
 *
 * <p>Why a mixin: no loader event sets the camera's fluid. NeoForge's {@code ViewportEvent.RenderFog} and
 * {@code ComputeFogColor} only adjust distances and colour after vanilla chose them, so they would have to re-implement
 * vanilla's water fog (biome colour blending, water vision, sky) and would miss the other readers of this method. Only
 * NONE is changed, and only to WATER. Priority 1500 so this runs after Sable's injector (default 1000) on the same
 * return and has the last word.
 */
@Mixin(value = Camera.class, priority = 1500)
public abstract class MixinCamera {

    @Shadow
    private BlockGetter level;

    @Shadow
    private Vec3 position;

    @ModifyReturnValue(method = "getFluidInCamera", at = @At("RETURN"))
    private FogType pirates_n_ships$underFloodSurface(FogType original) {
        if (original != FogType.NONE || !(level instanceof Level l) || position == null) {
            return original;
        }
        return FloodSurfaceRenderer.isBelowSurface(l, position, FloodSurfaceRenderer.partialTick()) ? FogType.WATER : original;
    }
}
