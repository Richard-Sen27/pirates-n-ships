package com.richardsenger.piratesnships.trade.client;

import com.richardsenger.piratesnships.platform.event.ClientEvents;

/** Client setup of the trade module (physical client only, from {@code TradeModule.initClient()}). */
public final class TradeClient {

    private TradeClient() {
    }

    public static void init() {
        ClientEvents.CLIENT_DISCONNECT.register(mc -> ClientMarketState.reset());
    }
}
