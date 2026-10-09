package com.richardsenger.piratesnships.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.richardsenger.piratesnships.hazards.waves.client.BoatTilt;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.BoatRenderer;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tilts a vanilla boat's hull along the slope of the waves it rides (WAV6, docs/design.md §5.4; {@link BoatTilt}).
 *
 * <p>How: {@code BoatRenderer#render(Boat, …)} translates the pose by the hull's height once, then turns it by the yaw.
 * The injection sits right after that translate, so {@link BoatTilt} adds a world-space turn under the yaw; the
 * passengers are rendered by their own renderers at the seats the boat entity gives them and are not tilted. The
 * method is named with its full descriptor because the class also has the bridge {@code render(Entity, …)}, which only
 * delegates. Vanilla and NeoForge 21.1 have the same body here; both loaders' {@code ClientMixinTargetsTest} check the
 * translate call.
 *
 * <p>Why a mixin: neither loader has a render event for non-living entities (NeoForge's {@code RenderLivingEvent} is for
 * living entities only; Fabric's entity render callbacks likewise), and the pose of the boat's model is local to this
 * method.
 */
@Mixin(BoatRenderer.class)
public abstract class MixinBoatRenderer {

    @Inject(method = "render(Lnet/minecraft/world/entity/vehicle/Boat;FFLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", shift = At.Shift.AFTER))
    private void pirates_n_ships$tiltOnTheWaves(Boat boat, float entityYaw, float partialTicks, PoseStack poseStack,
                                                MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        BoatTilt.apply(boat, partialTicks, poseStack);
    }
}
