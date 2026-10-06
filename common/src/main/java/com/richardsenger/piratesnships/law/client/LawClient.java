package com.richardsenger.piratesnships.law.client;

import com.richardsenger.piratesnships.platform.event.ClientEvents;

/** Client side of the law module (physical client only, called from {@code LawModule.initClient()}). */
public final class LawClient {

    private LawClient() {
    }

    public static void init() {
        ClientEvents.CLIENT_DISCONNECT.register(mc -> ClientWanted.reset());
    }
}
