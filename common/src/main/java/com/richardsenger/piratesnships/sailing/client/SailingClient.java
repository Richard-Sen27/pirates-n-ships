package com.richardsenger.piratesnships.sailing.client;

import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.sailing.wind.ClientWind;

/** Client setup of the sailing module (physical client only, from {@code SailingModule.initClient()}). */
public final class SailingClient {

    private SailingClient() {
    }

    public static void init() {
        // Wind of the last server must not leak into the next world
        ClientEvents.CLIENT_DISCONNECT.register(mc -> ClientWind.reset());
    }
}
