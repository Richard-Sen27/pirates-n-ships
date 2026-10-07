package com.richardsenger.piratesnships.trade.client;

import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.ship.template.ShipOrderMath;
import com.richardsenger.piratesnships.ship.template.ShipReceiptItem;
import com.richardsenger.piratesnships.ship.template.ShipReceiptText;
import com.richardsenger.piratesnships.trade.cargo.CargoTooltip;

/** Client setup of the trade module (physical client only, from {@code TradeModule.initClient()}). */
public final class TradeClient {

    private TradeClient() {
    }

    public static void init() {
        ClientEvents.CLIENT_DISCONNECT.register(mc -> ClientMarketState.reset());
        ClientMarketState.setOpener(MarketScreen::open);
        ClientEvents.ITEM_TOOLTIP.register((stack, context, flag, player, lines) -> CargoTooltip.insertBelowName(lines, CargoTooltip.lines(stack)));
        // SW1: the ship receipt's ship, village and progress (needs the client's world day)
        ClientEvents.ITEM_TOOLTIP.register((stack, context, flag, player, lines) -> ShipReceiptItem.receipt(stack).ifPresent(r ->
                CargoTooltip.insertBelowName(lines, ShipReceiptText.lines(r, player == null ? 0.0
                        : ShipOrderMath.day(player.level().getDayTime())))));
    }
}
