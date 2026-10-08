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
 * (a depth pass before the translucent layer, {@code WaterOcclusionRenderer}), not cutout blocks. Renderers that replace
 * the section compiler (Sodium, Embeddium) skip it: there the plants stay visible.
 *
 * <p>Target methods (HV1b). The two loaders put the wrapped {@code region.getBlockState(pos)} call in different methods:
 * <ul>
 *   <li>Vanilla (Fabric) has one {@code compile(SectionPos, RenderChunkRegion, VertexSorting, SectionBufferBuilderPack)},
 *       and the call sits in its block loop.</li>
 *   <li>NeoForge 21.1 patches in a second overload with a trailing
 *       {@code List<AddSectionGeometryEvent.AdditionalSectionRenderer>}. The block loop (and the call) moved there; the
 *       original 4-argument method only delegates to it with {@code List.of()}.</li>
 * </ul>
 * HV1 used the bare selector {@code "compile"}. Mixin reads a bare name as "the first method of that name" (quantifier
 * SINGLE), which on NeoForge is the call-free 4-argument delegate, so the injector found 0 call sites and the client
 * crashed while loading (the GameTest server never loads client mixins, so no test saw it). Both descriptors are now
 * spelled out: each loader matches the method that holds the call (NeoForge also matches the delegate, harmlessly, with
 * no call site in it), a descriptor a loader lacks matches nothing (Mixin allows 0 matches per selector), and
 * {@code require = 1} still demands one real call site in total. The NeoForge descriptor names only
 * {@code java.util.List}, no NeoForge type, so this stays one common mixin. {@code ClientMixinTargetsTest} in the
 * {@code neoforge} module checks both selectors and the call site against the NeoForge-patched class.
 */
@Mixin(SectionCompiler.class)
public abstract class MixinSectionCompiler {

    /** Vanilla's (and Fabric's) only {@code compile}: holds the block loop. On NeoForge, a call-free delegate. */
    private static final String COMPILE_VANILLA = "compile(Lnet/minecraft/core/SectionPos;"
            + "Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;"
            + "Lnet/minecraft/client/renderer/SectionBufferBuilderPack;)"
            + "Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;";
    /** NeoForge's patched overload (extra {@code List} of additional section renderers): holds the block loop there. */
    private static final String COMPILE_NEOFORGE = "compile(Lnet/minecraft/core/SectionPos;"
            + "Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;"
            + "Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)"
            + "Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;";

    @WrapOperation(method = {COMPILE_VANILLA, COMPILE_NEOFORGE}, require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState pirates_n_ships$hideWaterPlants(RenderChunkRegion region, BlockPos pos, Operation<BlockState> original) {
        return HiddenWaterPlants.filter(original.call(region, pos), pos);
    }
}
