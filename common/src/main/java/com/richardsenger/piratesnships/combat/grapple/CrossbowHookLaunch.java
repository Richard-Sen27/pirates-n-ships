package com.richardsenger.piratesnships.combat.grapple;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ChargedProjectiles;

/**
 * The vanilla crossbow as a hook launcher (GR3, docs/design.md §8.3 "Launching"). The crossbow is vanilla's, so our
 * code owns only the start and the result of its session:
 * <ul>
 *   <li><b>Draw.</b> Use with the hook in the other hand starts the crossbow's own use session
 *       ({@code startUsingItem} on the crossbow's hand, from {@link GrapplingHookItem#use} or the client's
 *       {@code GrappleClient}), so vanilla draws it: the charge pose in both views, the charging sounds, Quick Charge.
 *       Vanilla's {@code CrossbowItem#use} never runs, so it never looks for arrows.</li>
 *   <li><b>Loaded.</b> Once the session has been held for the crossbow's charge time ({@link #onPlayerTick}, both
 *       sides; the server is authoritative, the local client predicts the look), the hook leaves the other hand and
 *       sits in the crossbow: {@link GrappleContent#LOADED_HOOK} plus vanilla's {@code charged_projectiles} holding the
 *       hook item, so the crossbow shows vanilla's charged look and hold pose. Vanilla's release then sees a charged
 *       crossbow and does nothing; letting go earlier is below full power and does nothing either (cancelled, nothing
 *       taken).</li>
 *   <li><b>Fire.</b> A press on the hook-loaded crossbow ({@code GrappleClient} cancels vanilla's use and sends
 *       {@link FireLoadedHookPayload}; or the hook's own use from the other hand) shoots the hook at once at the
 *       crossbow's speed and rope length, with the crossbow's shot sound and one point of wear. A crossbow has no aim
 *       session: vanilla's charged crossbow fires on the press and holds its aim pose while charged.</li>
 * </ul>
 */
public final class CrossbowHookLaunch {

    private CrossbowHookLaunch() {
    }

    /** True when {@code crossbow} holds a hook (ours, and vanilla still sees it charged). */
    public static boolean isHookLoaded(ItemStack crossbow) {
        return crossbow.getItem() instanceof CrossbowItem && GrappleContent.isHookLoaded(crossbow);
    }

    /** True when the crossbow is charged with anything that is not our hook (vanilla's arrows or fireworks). */
    public static boolean chargedWithOther(ItemStack crossbow) {
        return CrossbowItem.isCharged(crossbow) && !GrappleContent.isHookLoaded(crossbow);
    }

    /** What use on {@code crossbow} does in a hook arrangement. */
    public static GrappleLaunch.CrossbowUse use(ItemStack crossbow) {
        return GrappleLaunch.crossbowUse(isHookLoaded(crossbow), chargedWithOther(crossbow));
    }

    /** Starts the crossbow's draw (both sides). False when it is not empty. */
    public static boolean startDraw(Player player, InteractionHand crossbowHand) {
        ItemStack crossbow = player.getItemInHand(crossbowHand);
        if (!(crossbow.getItem() instanceof CrossbowItem) || use(crossbow) != GrappleLaunch.CrossbowUse.DRAW) return false;
        player.startUsingItem(crossbowHand);
        return true;
    }

    /**
     * {@code PLAYER_TICK_END}: completes a running hook draw on the server (authoritative) and predicts its look on the
     * local client.
     */
    public static void onPlayerTick(Player player) {
        if (!player.isUsingItem() || !(player.getUseItem().getItem() instanceof CrossbowItem)) return;
        if (player.level().isClientSide && !player.isLocalPlayer()) return;
        tickDraw(player, player.getTicksUsingItem());
    }

    /**
     * The draw of the crossbow in the player's used hand has been held {@code heldTicks}: loads the hook once that
     * reaches the crossbow's charge time. Returns true when it loaded the hook in this call.
     */
    public static boolean tickDraw(Player player, int heldTicks) {
        InteractionHand crossbowHand = player.getUsedItemHand();
        ItemStack crossbow = player.getItemInHand(crossbowHand);
        if (!(crossbow.getItem() instanceof CrossbowItem) || CrossbowItem.isCharged(crossbow)) return false;
        if (GrapplingHookItem.launcherHand(player) != crossbowHand) return false;
        if (!GrappleLaunch.drawn(heldTicks, CrossbowItem.getChargeDuration(crossbow, player))) return false;
        ItemStack hook = player.getItemInHand(GrappleLaunch.other(crossbowHand));
        if (!(hook.getItem() instanceof GrapplingHookItem)) return false;
        boolean take = !player.hasInfiniteMaterials();
        load(crossbow, new LoadedHook(hook.copyWithCount(1), take));
        if (!player.level().isClientSide) {
            if (take) {
                hook.shrink(1);
            }
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_LOADING_END.value(),
                    SoundSource.PLAYERS, 1.0f, 1.0f);
        }
        return true;
    }

    private static void load(ItemStack crossbow, LoadedHook hook) {
        crossbow.set(GrappleContent.LOADED_HOOK.get(), hook);
        crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(hook.hook()));
    }

    /**
     * Shoots the hook out of the crossbow in {@code crossbowHand} (server). Returns false when it holds none or the
     * crossbow launch is switched off.
     */
    public static boolean fire(ServerLevel level, Player player, InteractionHand crossbowHand) {
        ItemStack crossbow = player.getItemInHand(crossbowHand);
        if (!isHookLoaded(crossbow) || !GrappleConfig.ENABLED.get() || !GrappleConfig.CROSSBOW_ENABLED.get()) return false;
        LoadedHook loaded = GrappleContent.loadedHook(crossbow);
        crossbow.remove(GrappleContent.LOADED_HOOK.get());
        crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
        GrappleService.launchHook(level, player, loaded.hook(), loaded.taken(), GrappleLaunch.Mode.CROSSBOW);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0f, 0.9f);
        crossbow.hurtAndBreak(1, player, LivingEntity.getSlotForHand(crossbowHand));
        return true;
    }
}
