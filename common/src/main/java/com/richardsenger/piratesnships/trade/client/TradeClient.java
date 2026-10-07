package com.richardsenger.piratesnships.trade.client;

import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.trade.cargo.CargoTooltip;

/** Client setup of the trade module (physical client only, from {@code TradeModule.initClient()}). */
public final class TradeClient {

    private TradeClient() {
    }

    public static void init() {
        ClientEvents.CLIENT_DISCONNECT.register(mc -> ClientMarketState.reset());
        ClientMarketState.setOpener(MarketScreen::open);
        ClientEvents.ITEM_TOOLTIP.register((stack, context, flag, player, lines) -> CargoTooltip.insertBelowName(lines, CargoTooltip.lines(stack)));
    }
}
