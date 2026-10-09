package com.richardsenger.piratesnships.fabric.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.platform.event.ClientEvents.InteractionInput;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fires {@code ClientEvents.INTERACTION_KEY} and {@code ClientEvents.RENDER_FRAME_PRE} at the spots where NeoForge's
 * patched {@code Minecraft} fires {@code InputEvent.InteractionKeyMappingTriggered} ({@code ClientHooks.onClickInput})
 * and {@code RenderFrameEvent.Pre} (FAB2).
 *
 * <p>Why a mixin: Fabric API has no event for either. Its {@code ClientPreAttackCallback} is the nearest, but it fires
 * every tick the attack key is down (before the miss-time, hit-result and busy-hands checks, and also when no block is
 * being mined), and there is nothing for the use key per hand or for pick block; the listeners (melee, firearms,
 * grapple, swivel gun, helm) rely on NeoForge's points. A cancel returns where NeoForge's cancelled event returns, with
 * no hand swing (our forwarder never asks for one):
 * <ul>
 *   <li>{@code startAttack}: after the miss-time, hit-result, busy-hands and item-enabled checks, before the switch on
 *       the hit type (the method's only {@code HitResult#getType} call); cancelled, it returns {@code false}.</li>
 *   <li>{@code continueAttack}: while mining a non-air block, before {@code continueDestroyBlock}.</li>
 *   <li>{@code startUseItem}: per hand, main hand first, before the hand's item is read (the loop's only
 *       {@code getItemInHand} call); a cancel ends the method, so the off hand is skipped too.</li>
 *   <li>{@code pickBlock}: once a block or entity is targeted, before the first statement of that branch.</li>
 *   <li>{@code runTick}: {@code RENDER_FRAME_PRE} just before {@code GameRenderer#render}, after the frame's mouse
 *       movement turned the player and only while rendering is on.</li>
 * </ul>
 * The targets are checked headlessly by {@code ClientMixinTargetsTest} (fabric tests, HV1b).
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Unique
    private boolean pirates_n_ships$cancelled(InteractionInput input, InteractionHand hand) {
        return ClientEvents.INTERACTION_KEY.invoker().onInteraction((Minecraft) (Object) this, input, hand)
                == ClientEvents.InteractionKeyResult.CANCEL;
    }

    @Inject(method = "startAttack", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/HitResult;getType()Lnet/minecraft/world/phys/HitResult$Type;"))
    private void pirates_n_ships$attackKey(CallbackInfoReturnable<Boolean> cir) {
        if (pirates_n_ships$cancelled(InteractionInput.ATTACK, InteractionHand.MAIN_HAND)) cir.setReturnValue(false);
    }

    @Inject(method = "continueAttack", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;continueDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z"))
    private void pirates_n_ships$attackKeyHeld(boolean leftClick, CallbackInfo ci) {
        if (pirates_n_ships$cancelled(InteractionInput.ATTACK, InteractionHand.MAIN_HAND)) ci.cancel();
    }

    @Inject(method = "startUseItem", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getItemInHand(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;"))
    private void pirates_n_ships$useKey(CallbackInfo ci, @Local InteractionHand hand) {
        if (pirates_n_ships$cancelled(InteractionInput.USE, hand)) ci.cancel();
    }

    @Inject(method = "pickBlock", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getAbilities()Lnet/minecraft/world/entity/player/Abilities;"))
    private void pirates_n_ships$pickBlockKey(CallbackInfo ci) {
        if (pirates_n_ships$cancelled(InteractionInput.PICK_BLOCK, InteractionHand.MAIN_HAND)) ci.cancel();
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V"))
    private void pirates_n_ships$renderFramePre(boolean renderLevel, CallbackInfo ci) {
        ClientEvents.RENDER_FRAME_PRE.invoker().onTick((Minecraft) (Object) this);
    }
}
