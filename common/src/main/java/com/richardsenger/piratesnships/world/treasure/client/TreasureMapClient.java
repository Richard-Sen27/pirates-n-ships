package com.richardsenger.piratesnships.world.treasure.client;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.platform.event.ClientEvents;

/** Client side of the treasure maps (physical client only, TM1): the overlay while a bound map is held. */
public final class TreasureMapClient {

    private TreasureMapClient() {
    }

    public static void init() {
        ClientEvents.registerHudLayer(Constants.id("treasure_map"), TreasureMapHud::render);
        ClientEvents.CLIENT_DISCONNECT.register(mc -> TreasureMapTextures.releaseAll());
    }
}
