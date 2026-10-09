package com.richardsenger.piratesnships.combat.cannon.client;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.cannon.CannonContent;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.resources.ResourceLocation;

/** Client setup of the cannons (physical client only, from {@code CannonModule.initClient()}). */
public final class CannonClient {

    /** The cannonball is drawn as the cannonball item sprite at this scale (a lead ball uses 0.35). */
    private static final float BALL_SCALE = 1.2f;

    /** CAN2: the hand-made stand-alone parts {@link CannonBarrelRenderer} draws (models/block/*.json). */
    static final ResourceLocation BARREL_MODEL = Constants.id("block/cannon_barrel");
    static final ResourceLocation BARREL_POWDER_MODEL = Constants.id("block/cannon_barrel_powder");
    static final ResourceLocation BARREL_LOADED_MODEL = Constants.id("block/cannon_barrel_loaded");
    static final ResourceLocation QUOIN_MODEL = Constants.id("block/cannon_quoin");

    private CannonClient() {
    }

    public static void init() {
        ClientEvents.registerEntityRenderer(CannonContent.CANNONBALL, ctx -> new ThrownItemRenderer<>(ctx, BALL_SCALE, false));
        ClientEvents.registerBlockEntityRenderer(CannonContent.SWIVEL_GUN_ENTITY, SwivelGunRenderer::new);
        for (ResourceLocation id : new ResourceLocation[]{BARREL_MODEL, BARREL_POWDER_MODEL, BARREL_LOADED_MODEL, QUOIN_MODEL}) {
            ClientEvents.registerAdditionalModel(id);
        }
        ClientEvents.registerBlockEntityRenderer(CannonContent.CANNON_ENTITY, CannonBarrelRenderer::new);
        SwivelAimClient.init();
    }
}
