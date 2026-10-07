package com.richardsenger.piratesnships.combat.grapple.client;

import com.richardsenger.piratesnships.combat.grapple.GrappleContent;
import com.richardsenger.piratesnships.combat.grapple.GrapplingHookEntity;
import com.richardsenger.piratesnships.combat.grapple.ReleaseHookPayload;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;

/** Client setup of the grappling hook (physical client only, from {@code GrappleModule.initClient()}). */
public final class GrappleClient {

    private GrappleClient() {
    }

    public static void init() {
        ClientEvents.registerEntityRenderer(GrappleContent.HOOK, GrapplingHookRenderer::new);
        ClientEvents.INTERACTION_KEY.register(GrappleClient::onInteraction);
    }

    /**
     * Sneak + use with an empty main hand while one of our hooks is out asks the server to release it. Vanilla's own
     * action still runs (PASS), so sneak-using a block with an empty hand works as before.
     */
    private static ClientEvents.InteractionKeyResult onInteraction(Minecraft mc, ClientEvents.InteractionInput input, InteractionHand hand) {
        if (input == ClientEvents.InteractionInput.USE && hand == InteractionHand.MAIN_HAND && mc.player != null && mc.level != null
                && mc.player.isShiftKeyDown() && mc.player.getMainHandItem().isEmpty() && hasHookOut(mc)) {
            Services.NETWORK.sendToServer(ReleaseHookPayload.INSTANCE);
        }
        return ClientEvents.InteractionKeyResult.PASS;
    }

    private static boolean hasHookOut(Minecraft mc) {
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof GrapplingHookEntity hook && hook.getOwner() == mc.player) {
                return true;
            }
        }
        return false;
    }
}
