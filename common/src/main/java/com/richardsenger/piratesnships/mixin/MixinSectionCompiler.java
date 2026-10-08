package com.richardsenger.piratesnships.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.richardsenger.piratesnships.ship.hull.client.HiddenWaterPlants;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Leaves water plants of the world out of a chunk section's mesh where they stand inside a dry hull (HV1, docs/design.md
 * §4.4; {@link HiddenWaterPlants} decides). The section compiler reads each block once through
 * {@code RenderChunkRegion#getBlockState}; for a hidden plant it gets the plant's bare fluid instead, so the water is still
 * drawn (and hidden by Sable's mask) while the plant model is not.
 *
 * <p>Why a mixin: no loader offers a hook that removes a block from section compilation. NeoForge's
 * {@code AddSectionGeometryEvent} only adds geometry, Fabric has nothing, and Sable's water occlusion only masks the water
 * (a depth pass before the translucent layer, {@code WaterOcclusionRenderer}), not cutout blocks. The wrapped call is
 * in the vanilla loop of {@code SectionCompiler#compile} and in NeoForge's patched overload of it (which adds model data
 * and render types around the same {@code region.getBlockState(pos)} call), so one common, client-only mixin covers both
 * loaders. Renderers that replace the section compiler (Sodium, Embeddium) skip it: there the plants stay visible.
 */
@Mixin(SectionCompiler.class)
public abstract class MixinSectionCompiler {

    @WrapOperation(method = "compile", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState pirates_n_ships$hideWaterPlants(RenderChunkRegion region, BlockPos pos, Operation<BlockState> original) {
        return HiddenWaterPlants.filter(original.call(region, pos), pos);
    }
}
