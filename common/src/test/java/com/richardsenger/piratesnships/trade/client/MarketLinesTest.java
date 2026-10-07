package com.richardsenger.piratesnships.trade.client;

import com.richardsenger.piratesnships.trade.market.GoodRole;
import com.richardsenger.piratesnships.trade.market.Market;
import com.richardsenger.piratesnships.trade.net.MarketView;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The market screen's pure helpers. */
class MarketLinesTest {

    private static final MarketView.Price OK_100 = new MarketView.Price(100, Integer.MAX_VALUE, Market.Outcome.OK);

    private static MarketView.GoodLine line(String good, GoodRole role) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("pirates_n_ships", good);
        return new MarketView.GoodLine(id, ResourceLocation.withDefaultNamespace(good), role, OK_100, OK_100);
    }

    @Test
    void portListsTradedGoodsProducedFirstThenNeutralThenDemanded() {
        List<MarketView.GoodLine> in = List.of(line("sugar", GoodRole.DEMANDS), line("rum", GoodRole.NOT_TRADED), line("iron", GoodRole.NEUTRAL),
                line("cloth", GoodRole.PRODUCES), line("fish", GoodRole.NEUTRAL), line("spices", GoodRole.PRODUCES), line("timber", GoodRole.DEMANDS));
        List<String> order = MarketLines.order(in).stream().map(l -> l.good().getPath()).toList();
        assertEquals(List.of("cloth", "spices", "fish", "iron", "sugar", "timber"), order);
    }

    @Test
    void quantitiesAreClampedToOneUpToTheMaximum() {
        assertEquals(1, MarketLines.clampQuantity(0, 4096));
        assertEquals(1, MarketLines.clampQuantity(-5, 4096));
        assertEquals(64, MarketLines.clampQuantity(64, 4096));
        assertEquals(4096, MarketLines.clampQuantity(100_000, 4096));
        assertEquals(1, MarketLines.clampQuantity(10, 0), "a broken maximum still allows one");
    }

    @Test
    void typedQuantitiesAreParsedAndClamped() {
        assertEquals(OptionalInt.of(16), MarketLines.parseQuantity("16", 4096));
        assertEquals(OptionalInt.of(16), MarketLines.parseQuantity(" 16 ", 4096));
        assertEquals(OptionalInt.of(1), MarketLines.parseQuantity("0", 4096));
        assertEquals(OptionalInt.of(4096), MarketLines.parseQuantity("99999", 4096));
        assertEquals(OptionalInt.of(4096), MarketLines.parseQuantity("99999999999999", 4096), "no overflow");
        assertEquals(OptionalInt.empty(), MarketLines.parseQuantity("", 4096));
        assertEquals(OptionalInt.empty(), MarketLines.parseQuantity("-3", 4096));
        assertEquals(OptionalInt.empty(), MarketLines.parseQuantity("1e3", 4096));
        assertEquals(OptionalInt.empty(), MarketLines.parseQuantity(null, 4096));
    }

    @Test
    void affordableAmountUsesTheAverageUnitPrice() {
        assertEquals(6, MarketLines.affordable(100, 128, 8), "16 each: 6 units for 100");
        assertEquals(0, MarketLines.affordable(15, 128, 8));
        assertEquals(8, MarketLines.affordable(128, 128, 8));
        assertEquals(0, MarketLines.affordable(0, 128, 8));
        assertEquals(0, MarketLines.affordable(100, 128, 0));
        assertEquals(Long.MAX_VALUE, MarketLines.affordable(1, 0, 8), "free goods");
    }

    @Test
    void buyAndSellNeedAnOkQuoteCoinsAndGoods() {
        assertTrue(MarketLines.canBuy(OK_100, 100));
        assertFalse(MarketLines.canBuy(OK_100, 99));
        assertFalse(MarketLines.canBuy(new MarketView.Price(100, 3, Market.Outcome.LIMIT), 1000));
        assertTrue(MarketLines.canSell(OK_100, 8, 8));
        assertFalse(MarketLines.canSell(OK_100, 7, 8));
        assertFalse(MarketLines.canSell(new MarketView.Price(0, 0, Market.Outcome.NOT_TRADED), 64, 8));
    }

    @Test
    void pricesAndCoinsReadWell() {
        assertEquals("0", MarketLines.formatCoins(0));
        assertEquals("999", MarketLines.formatCoins(999));
        assertEquals("1,000", MarketLines.formatCoins(1000));
        assertEquals("12,345", MarketLines.formatCoins(12345));
        assertEquals("1,234,567", MarketLines.formatCoins(1234567));
        assertEquals("-4,200", MarketLines.formatCoins(-4200));
        assertEquals("1,500", MarketLines.price(new MarketView.Price(1500, 64, Market.Outcome.OK)));
        assertEquals("max 12", MarketLines.price(new MarketView.Price(0, 12, Market.Outcome.LIMIT)));
        assertEquals("-", MarketLines.price(new MarketView.Price(0, 0, Market.Outcome.NOT_TRADED)));
    }

    @Test
    void portNamesComeFromTheIdsLastSegment() {
        assertEquals("Port Royal", MarketLines.portName(ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "debug/port_royal")));
        assertEquals("Cane", MarketLines.portName(ResourceLocation.fromNamespaceAndPath("pirates_n_ships", "debug/cane")));
        assertEquals("Tortuga", MarketLines.portName(ResourceLocation.withDefaultNamespace("tortuga")));
    }

    @Test
    void scrollStaysWithinTheList() {
        assertEquals(0, MarketLines.clampScroll(-2, 10, 4));
        assertEquals(6, MarketLines.clampScroll(9, 10, 4));
        assertEquals(0, MarketLines.clampScroll(3, 2, 4), "short lists never scroll");
    }
}
