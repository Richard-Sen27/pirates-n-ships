package com.richardsenger.piratesnships.ship.screen.client;

import com.richardsenger.piratesnships.platform.event.ClientEvents;

/** Client setup of the ship screen (physical client only, from {@code ShipScreenModule.initClient()}). */
public final class ShipScreenClient {

    private ShipScreenClient() {
    }

    public static void init() {
        ClientEvents.CLIENT_DISCONNECT.register(mc -> ClientShipScreenState.reset());
        ClientShipScreenState.setOpener(ShipScreen::open);
    }
}
