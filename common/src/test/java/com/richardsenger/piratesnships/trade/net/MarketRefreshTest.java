package com.richardsenger.piratesnships.trade.net;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.trade.market.GoodRole;
import com.richardsenger.piratesnships.trade.market.Market;
import com.richardsenger.piratesnships.trade.market.PortKind;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When an open market session is re-sent its state. */
class MarketRefreshTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static MarketView view(long coins, long buyTotal, int stock) {
        var line = new MarketView.GoodLine(Constants.id("sugar"), ResourceLocation.withDefaultNamespace("sugar"), GoodRole.NEUTRAL,
                new MarketView.Price(buyTotal, stock, Market.Outcome.OK), new MarketView.Price(buyTotal / 2, 100, Market.Outcome.OK));
        return new MarketView(Constants.id("debug/a"), PortKind.SEAFARER_VILLAGE, 1, coins, List.of(line), List.of(), List.of());
    }

    @Test
    void firstViewIsAlwaysSent() {
        assertTrue(MarketRefresh.changed(Optional.empty(), view(10, 5, 50)));
    }

    @Test
    void anEqualViewIsNotResent() {
        assertFalse(MarketRefresh.changed(Optional.of(view(10, 5, 50)), view(10, 5, 50)));
    }

    @Test
    void pricesStockAndCoinsCount() {
        MarketView last = view(10, 5, 50);
        assertTrue(MarketRefresh.changed(Optional.of(last), view(11, 5, 50)), "coins");
        assertTrue(MarketRefresh.changed(Optional.of(last), view(10, 6, 50)), "price");
        assertTrue(MarketRefresh.changed(Optional.of(last), view(10, 5, 49)), "stock");
    }

    @Test
    void refreshTicks() {
        assertTrue(MarketRefresh.due(40, 20));
        assertFalse(MarketRefresh.due(41, 20));
        assertTrue(MarketRefresh.due(41, 1));
        assertTrue(MarketRefresh.due(41, 0), "below 1 means every tick");
    }
}
