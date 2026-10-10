package com.richardsenger.piratesnships.combat.cannon.client;

import com.richardsenger.piratesnships.combat.cannon.CannonData;
import com.richardsenger.piratesnships.ship.decor.wood.client.WoodModels;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.cannon.CannonContent;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import net.minecraft.resources.ResourceLocation;

/** Client setup of the cannons (physical client only, from {@code CannonModule.initClient()}). */
public final class CannonClient {

    /** The cannonball is drawn as the cannonball item at this scale (a lead ball uses 0.35); ART15: chain shot and grapeshot
     * pellets have their own looks ({@link CannonballRenderer}). */
    private static final float BALL_SCALE = 1.2f;

    /** CAN2: the hand-made stand-alone parts {@link CannonBarrelRenderer} draws (models/block/*.json). */
    static final ResourceLocation BARREL_MODEL = Constants.id("block/cannon_barrel");
    static final ResourceLocation BARREL_POWDER_MODEL = Constants.id("block/cannon_barrel_powder");
    static final ResourceLocation BARREL_LOADED_MODEL = Constants.id("block/cannon_barrel_loaded");
    /** The quoin is wooden: the renderer draws the child of the cannon's wood ({@code block/<wood>_cannon_quoin}, WOOD4). */
    static final ResourceLocation QUOIN_MODEL = CannonData.QUOIN_MODEL;

    private CannonClient() {
    }

    public static void init() {
        ClientEvents.registerEntityRenderer(CannonContent.CANNONBALL, ctx -> new CannonballRenderer(ctx, BALL_SCALE));
        ClientEvents.registerAdditionalModel(CannonballRenderer.PELLET_MODEL);
        ClientEvents.registerBlockEntityRenderer(CannonContent.SWIVEL_GUN_ENTITY, SwivelGunRenderer::new);
        for (ResourceLocation id : new ResourceLocation[]{BARREL_MODEL, BARREL_POWDER_MODEL, BARREL_LOADED_MODEL}) {
            ClientEvents.registerAdditionalModel(id);
        }
        WoodModels.register(CannonContent.CANNONS, QUOIN_MODEL);
        ClientEvents.registerBlockEntityRenderer(CannonContent.CANNON_ENTITY, CannonBarrelRenderer::new);
        SwivelAimClient.init();
    }
}
