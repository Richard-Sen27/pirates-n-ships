package com.richardsenger.piratesnships.fabric.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fires {@code ClientEvents.COMPUTE_FOV} where NeoForge fires {@code ComputeFovModifierEvent} (FAB2, musket zoom).
 *
 * <p>Why a mixin: Fabric API has no field-of-view event. NeoForge's event starts from the modifier already scaled by
 * the "FOV effects" option ({@code Mth.lerp(fovEffectScale, 1, f)}, the method's last expression in vanilla), and the
 * spyglass early return ({@code 0.1}) bypasses it on both loaders; wrapping that one {@code Mth.lerp} call gives the
 * same value and the same bypass. The target is checked headlessly by {@code ClientMixinTargetsTest}.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class MixinAbstractClientPlayer {

    @ModifyExpressionValue(method = "getFieldOfViewModifier", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(FFF)F"))
    private float pirates_n_ships$computeFov(float fovModifier) {
        return ClientEvents.COMPUTE_FOV.invoker().modify((Player) (Object) this, fovModifier);
    }
}
