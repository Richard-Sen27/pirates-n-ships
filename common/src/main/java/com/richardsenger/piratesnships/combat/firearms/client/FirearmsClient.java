package com.richardsenger.piratesnships.combat.firearms.client;

import com.richardsenger.piratesnships.combat.firearms.FirearmContent;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

/** Client setup of the firearms (physical client only, from {@code FirearmsModule.initClient()}). */
public final class FirearmsClient {

    /** The lead ball is drawn as the lead shot item sprite at this scale. */
    private static final float BALL_SCALE = 0.35f;

    private FirearmsClient() {
    }

    public static void init() {
        ClientEvents.registerEntityRenderer(FirearmContent.LEAD_BALL, ctx -> new ThrownItemRenderer<>(ctx, BALL_SCALE, false));
    }
}
