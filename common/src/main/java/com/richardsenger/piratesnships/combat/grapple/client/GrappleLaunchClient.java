package com.richardsenger.piratesnships.combat.grapple.client;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.combat.grapple.CrossbowHookLaunch;
import com.richardsenger.piratesnships.combat.grapple.FireLoadedHookPayload;
import com.richardsenger.piratesnships.combat.grapple.GrappleContent;
import com.richardsenger.piratesnships.combat.grapple.GrappleLaunch;
import com.richardsenger.piratesnships.combat.grapple.GrapplingHookItem;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Client side of launching the hook (GR3, docs/design.md §8.3 "Launching").
 * <ul>
 *   <li><b>The crossbow's use.</b> Vanilla offers the use to the main hand first, and a crossbow there would load
 *       arrows from the inventory (always in creative) or shoot a charged crossbow as an arrow. When the crossbow is
 *       hook-loaded, or empty with the hook in the other hand, the use is cancelled ({@code INTERACTION_KEY}): a
 *       hook-loaded crossbow asks the server to shoot the hook ({@link FireLoadedHookPayload}), an empty one hands the
 *       use to the hook in the other hand ({@code MultiPlayerGameMode#useItem}, vanilla's own packet), which starts the
 *       crossbow's draw on both sides ({@link GrapplingHookItem}). A block or entity in front still gets its
 *       interaction first, as vanilla would give it.</li>
 *   <li><b>The musket's look.</b> The item model property {@code pirates_n_ships:grapple_loaded} (1 on a musket
 *       holding a hook) switches {@code musket.json} to the {@code musket_hook} model.</li>
 * </ul>
 */
public final class GrappleLaunchClient {

    /** Item model property: 1 on a musket holding a hook, 0 otherwise. */
    public static final ResourceLocation HOOK_LOADED_PROPERTY = Constants.id("grapple_loaded");

    private GrappleLaunchClient() {
    }

    /** {@code CLIENT_SETUP} listener. */
    public static void registerItemProperties() {
        ItemProperties.register(CombatContent.MUSKET.get(), HOOK_LOADED_PROPERTY,
                (stack, level, entity, seed) -> GrappleContent.isHookLoaded(stack) ? 1.0f : 0.0f);
    }

    /** {@code INTERACTION_KEY} listener. */
    public static ClientEvents.InteractionKeyResult onInteraction(Minecraft mc, ClientEvents.InteractionInput input, InteractionHand hand) {
        LocalPlayer player = mc.player;
        if (input != ClientEvents.InteractionInput.USE || player == null || mc.gameMode == null || player.isSpectator()) {
            return ClientEvents.InteractionKeyResult.PASS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof CrossbowItem)) {
            return ClientEvents.InteractionKeyResult.PASS;
        }
        if (CrossbowHookLaunch.isHookLoaded(stack)) {
            // vanilla would shoot the charged crossbow as an arrow
            if (!interactFirst(mc, player, hand)) {
                Services.NETWORK.sendToServer(new FireLoadedHookPayload(hand));
            }
            return ClientEvents.InteractionKeyResult.CANCEL;
        }
        if (GrapplingHookItem.launcherHand(player) == hand && CrossbowHookLaunch.use(stack) == GrappleLaunch.CrossbowUse.DRAW
                && !player.isUsingItem()) {
            if (!interactFirst(mc, player, hand)) {
                mc.gameMode.useItem(player, GrappleLaunch.other(hand));
            }
            return ClientEvents.InteractionKeyResult.CANCEL;
        }
        return ClientEvents.InteractionKeyResult.PASS;
    }

    /**
     * Vanilla's entity and block interaction for {@code hand} ({@code Minecraft#startUseItem}), which the cancel would
     * otherwise skip. True when it took the use.
     */
    private static boolean interactFirst(Minecraft mc, LocalPlayer player, InteractionHand hand) {
        HitResult hit = mc.hitResult;
        if (hit instanceof EntityHitResult entityHit && hit.getType() == HitResult.Type.ENTITY) {
            InteractionResult r = mc.gameMode.interactAt(player, entityHit.getEntity(), entityHit, hand);
            if (!r.consumesAction()) {
                r = mc.gameMode.interact(player, entityHit.getEntity(), hand);
            }
            if (r.consumesAction()) {
                if (r.shouldSwing()) player.swing(hand);
                return true;
            }
        } else if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            InteractionResult r = mc.gameMode.useItemOn(player, hand, blockHit);
            if (r.consumesAction()) {
                if (r.shouldSwing()) player.swing(hand);
                return true;
            }
            return r == InteractionResult.FAIL;
        }
        return false;
    }
}
