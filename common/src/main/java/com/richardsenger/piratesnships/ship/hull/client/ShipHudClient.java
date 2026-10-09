package com.richardsenger.piratesnships.ship.hull.client;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.platform.event.ClientEvents;

/** Client wiring of the ship HUD (HUD1; HUD4: its layer is drawn below the chat), called from {@code HullModule.initClient()}. */
public final class ShipHudClient {

    private ShipHudClient() {
    }

    public static void init() {
        ClientEvents.CLIENT_TICK_END.register(ShipHud::tick);
        ClientEvents.CLIENT_DISCONNECT.register(mc -> ClientShipStatus.reset());
        ClientEvents.registerHudLayerBelowChat(Constants.id("ship_status"), ShipHud::render);
    }
}
