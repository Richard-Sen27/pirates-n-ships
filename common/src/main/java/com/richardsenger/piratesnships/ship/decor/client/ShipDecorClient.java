package com.richardsenger.piratesnships.ship.decor.client;

import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.ship.decor.ShipDecor;
import com.richardsenger.piratesnships.ship.decor.flag.Flags;
import com.richardsenger.piratesnships.ship.decor.flag.client.FlagClothRenderer;

/** Client setup of the {@code ship.decor} module (physical client only, from {@code ShipDecorModule.initClient()}). */
public final class ShipDecorClient {

    private ShipDecorClient() {
    }

    public static void init() {
        // The ship's name on the nameplate's board (G8)
        ClientEvents.registerBlockEntityRenderer(ShipDecor.NAMEPLATE_BLOCK_ENTITY, NameplateRenderer::new);
        // The flag cloth at its exact downwind yaw, banner flags in the banner's base colour (FL1)
        ClientEvents.registerBlockEntityRenderer(Flags.FLAGPOLE_BLOCK_ENTITY, FlagClothRenderer::new);
    }
}
