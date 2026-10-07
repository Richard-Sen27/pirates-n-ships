package com.richardsenger.piratesnships.combat.grapple.client;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.combat.grapple.GrappleContent;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;

/**
 * Client side of launching the hook (GR3, docs/design.md §8.3 "Launching"): the musket's look. The item model
 * property {@code pirates_n_ships:grapple_loaded} (1 on a musket holding a hook) switches {@code musket.json} to the
 * {@code musket_hook} model. The musket's loading and aiming are the firearm's own client code; GR4 removed the
 * crossbow and its use interception.
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
}
