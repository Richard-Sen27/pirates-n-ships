package com.richardsenger.piratesnships.core.client;

import com.richardsenger.piratesnships.core.data.DefinitionLoading;
import com.richardsenger.piratesnships.platform.event.ClientEvents;

/** Client side of the {@code core} module (physical client only, called from {@code CoreModule.initClient()}). */
public final class CoreClient {

    private CoreClient() {
    }

    public static void init() {
        ClientEvents.CLIENT_DISCONNECT.register(mc -> DefinitionLoading.clearClientStores());
    }
}
