package com.richardsenger.piratesnships.rpg.client;

import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.rpg.reputation.ClientReputation;

/** Client side of the rpg module (physical client only, called from {@code RpgModule.initClient()}). */
public final class RpgClient {

    private RpgClient() {
    }

    public static void init() {
        ClientEvents.CLIENT_DISCONNECT.register(mc -> ClientReputation.reset());
    }
}
