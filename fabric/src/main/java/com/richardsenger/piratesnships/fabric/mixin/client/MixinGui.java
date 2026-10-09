package com.richardsenger.piratesnships.fabric.mixin.client;

import com.richardsenger.piratesnships.platform.FabricClientSetup;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the HUD layers registered below the chat ({@code ClientEvents.registerHudLayerBelowChat}, HUD4: the ship HUD)
 * at the head of {@code Gui#renderChat}, the chat's own HUD layer, so the chat lines draw over them, as NeoForge's
 * {@code RegisterGuiLayersEvent#registerBelow(VanillaGuiLayers.CHAT, ...)} does.
 *
 * <p>Why a mixin: Fabric API 0.116 for 1.21.1 has only {@code HudRenderCallback}, which fires after the whole vanilla
 * HUD (chat included); its layer ordering API ({@code HudLayerRegistrationCallback}, {@code IdentifiedLayer}) is not
 * in this version's {@code fabric-rendering-v1}. The head of {@code renderChat} runs before its chat-focused check, so
 * the layers draw while the chat screen is open too (the screen draws the chat then, over the HUD). Only run where the
 * HUD shows: vanilla gates {@code renderChat}'s layer group on {@code hideGui}. Checked headlessly by
 * {@code ClientMixinTargetsTest} (fabric tests).
 */
@Mixin(Gui.class)
public abstract class MixinGui {

    @Inject(method = "renderChat(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"))
    private void pirates_n_ships$renderBelowChat(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        FabricClientSetup.renderHudLayersBelowChat(graphics, delta);
    }
}
