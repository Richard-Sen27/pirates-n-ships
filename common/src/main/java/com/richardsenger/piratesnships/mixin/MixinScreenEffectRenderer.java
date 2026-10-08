package com.richardsenger.piratesnships.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.richardsenger.piratesnships.ship.hull.client.FloodSurfaceRenderer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The underwater screen overlay also shows while the player's eyes are below the water surface of a flooded compartment
 * (FLD1, docs/design.md §4.4), where the world has no water for them (still in Sable's dry region, or flood water above
 * the sea; see {@link MixinCamera}). Only the overlay's test is widened: the player's eye-in-fluid state, which drives
 * breath and swimming on both sides, stays untouched.
 *
 * <p>Why a mixin: the overlay is drawn only when {@code LocalPlayer#isEyeInFluid(WATER)} holds; NeoForge's
 * {@code RenderBlockScreenEffectEvent} is fired only from inside that branch (it can cancel the overlay, never add it),
 * and a HUD layer would draw over the hotbar. One call site in {@code renderScreenEffect} (checked by
 * {@code ClientMixinTargetsTest} in {@code neoforge}).
 */
@Mixin(ScreenEffectRenderer.class)
public abstract class MixinScreenEffectRenderer {

    @WrapOperation(method = "renderScreenEffect", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private static boolean pirates_n_ships$eyeUnderFloodSurface(LocalPlayer player, TagKey<Fluid> fluid, Operation<Boolean> original) {
        if (original.call(player, fluid)) {
            return true;
        }
        if (fluid != FluidTags.WATER) {
            return false;
        }
        float partialTick = FloodSurfaceRenderer.partialTick();
        return FloodSurfaceRenderer.isBelowSurface(player.level(), player.getEyePosition(partialTick), partialTick);
    }
}
