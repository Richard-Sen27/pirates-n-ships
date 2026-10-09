package com.richardsenger.piratesnships.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.richardsenger.piratesnships.hazards.waves.client.surface.SeaSurface;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hides vanilla's flat water top where the visible sea surface draws instead (WAV3, docs/design.md §5.4;
 * {@link SeaSurface#hidesTop} decides: a still water source at sea level − 1 with air above, while
 * {@code wave_effects.surface} is on). Only the up face: the sides, the under face and every other fluid stay vanilla.
 *
 * <p>How: {@code tesselate} draws the up face only when {@code isFaceOccludedByNeighbor(level, pos, UP, height, above)}
 * is false. The wrap makes that call report "occluded" for a hidden top. The method is called three times in
 * {@code tesselate} (down face, up face, each side in the loop); the wrap acts only on the call whose direction argument
 * is {@code UP}, so it needs no ordinal and does not depend on the order of the calls. Vanilla (Fabric) and NeoForge
 * 21.1 both have the private static method with this descriptor and call it in {@code tesselate}; Fabric API's fluid
 * renderer handlers end in vanilla's {@code tesselate} for water, NeoForge's {@code IClientFluidTypeExtensions} too.
 * Both loaders' {@code ClientMixinTargetsTest} check the target.
 *
 * <p>Why a mixin: no loader offers a hook into the fluid's faces (NeoForge's {@code renderFluid} replaces the whole fluid,
 * Fabric's {@code FluidRenderHandler} likewise; neither can drop one face of vanilla's water). Renderers that replace
 * the chunk mesher (Sodium, Embeddium, Iris, Oculus, VulkanMod) never call {@code tesselate}; the surface turns itself
 * off with them ({@code SurfaceSupport}).
 */
@Mixin(LiquidBlockRenderer.class)
public abstract class MixinLiquidBlockRenderer {

    @WrapOperation(method = "tesselate(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;"
            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lnet/minecraft/world/level/material/FluidState;)V",
            require = 1, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/LiquidBlockRenderer;"
            + "isFaceOccludedByNeighbor(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/core/Direction;FLnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean pirates_n_ships$hideSeaTop(BlockGetter level, BlockPos pos, Direction side, float height, BlockState neighbor,
                                               Operation<Boolean> original) {
        if (side == Direction.UP && SeaSurface.hidesTop(level, pos, neighbor)) {
            return true;
        }
        return original.call(level, pos, side, height, neighbor);
    }
}
