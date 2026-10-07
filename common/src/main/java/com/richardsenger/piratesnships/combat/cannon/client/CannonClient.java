package com.richardsenger.piratesnships.combat.cannon.client;

import com.richardsenger.piratesnships.combat.cannon.CannonContent;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

/** Client setup of the cannons (physical client only, from {@code CannonModule.initClient()}). */
public final class CannonClient {

    /** The cannonball is drawn as the cannonball item sprite at this scale (a lead ball uses 0.35). */
    private static final float BALL_SCALE = 1.2f;

    private CannonClient() {
    }

    public static void init() {
        ClientEvents.registerEntityRenderer(CannonContent.CANNONBALL, ctx -> new ThrownItemRenderer<>(ctx, BALL_SCALE, false));
        ClientEvents.registerBlockEntityRenderer(CannonContent.SWIVEL_GUN_ENTITY, SwivelGunRenderer::new);
        SwivelAimClient.init();
    }
}
