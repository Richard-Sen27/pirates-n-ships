package com.richardsenger.piratesnships.compat;

import com.richardsenger.piratesnships.crew.provisions.ProvisionType;
import com.richardsenger.piratesnships.trade.good.TradeGood;
import com.richardsenger.piratesnships.trade.good.TradeGoods;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.MarketParams;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The category data of the JEI pages (JEI1): market prices, provision lines, the hidden list and the lang. */
class CompatDataTest {

    @Test
    void marketPricesFollowTheMarketRules() {
        TradeGood sugar = TradeGoods.DEFAULTS.get(TradeGoods.SUGAR);
        MarketEntry e = MarketEntry.of(TradeGoods.SUGAR, sugar, MarketParams.DEFAULTS);
        // base 2.0; spread 0.2 -> buy x1.1, sell x0.9; produced x0.6, demanded x1.6
        assertEquals(2.2, e.buy(), 1e-9);
        assertEquals(1.8, e.sell(), 1e-9);
        assertEquals(1.32, e.buyProduced(), 1e-9);
        assertEquals(2.88, e.sellDemanded(), 1e-9);
        assertEquals(List.of(Climate.TROPICAL), e.producedIn());
        assertEquals(List.of(
                TextLine.of(CompatText.MARKET_BUY, "2.2"),
                TextLine.of(CompatText.MARKET_SELL, "1.8"),
                TextLine.of(CompatText.MARKET_PRODUCED, "1.3"),
                TextLine.of(CompatText.MARKET_DEMANDED, "2.9"),
                TextLine.of(CompatText.MARKET_PRODUCED_IN, List.of(TextLine.of(CompatText.climate(Climate.TROPICAL)))),
                TextLine.of(CompatText.MARKET_WEIGHT, "0.25")), e.lines());
    }

    @Test
    void marketSkipsMissingItemsAndSortsByItem() {
        Set<ResourceLocation> known = new HashSet<>();
        TradeGoods.DEFAULTS.values().forEach(g -> known.add(g.item()));
        known.remove(ResourceLocation.parse("pirates_n_ships:spices"));
        List<MarketEntry> all = MarketEntry.all(TradeGoods.DEFAULTS, MarketParams.DEFAULTS, known::contains);
        assertEquals(TradeGoods.DEFAULTS.size() - 1, all.size());
        for (int i = 1; i < all.size(); i++) {
            assertTrue(all.get(i - 1).item().toString().compareTo(all.get(i).item().toString()) <= 0, "sorted by item");
        }
        MarketEntry gunpowder = MarketEntry.of(TradeGoods.GUNPOWDER, TradeGoods.DEFAULTS.get(TradeGoods.GUNPOWDER), MarketParams.DEFAULTS);
        assertTrue(gunpowder.lines().contains(TextLine.of(CompatText.MARKET_PRODUCED_NOWHERE)), "no climate makes gunpowder");
    }

    @Test
    void provisionLines() {
        assertEquals(List.of(
                TextLine.of(CompatText.PROVISION_FOOD, "3"),
                TextLine.of(CompatText.PROVISION_SPOILS, "5"),
                TextLine.of(CompatText.PROVISION_ANTI_SCURVY),
                TextLine.of(CompatText.PROVISION_WEIGHT, "0.25")),
                ProvisionEntry.lines(ProvisionType.food("x:lime", 3, false, true, 0.25, 120000), 5));
        assertEquals(List.of(
                TextLine.of(CompatText.PROVISION_WATER, "16"),
                TextLine.of(CompatText.PROVISION_PRESERVED),
                TextLine.of(CompatText.PROVISION_WEIGHT, "4")),
                ProvisionEntry.lines(ProvisionType.water("x:barrel", 16, 4), 0));
        assertEquals(TextLine.of(CompatText.PROVISION_RUM, "1"), ProvisionEntry.lines(ProvisionType.rum("x:rum", 1, 0.5), 0).get(0));
    }

    @Test
    void hiddenListAndLang() {
        assertEquals(List.of("ship_receipt", "ship_commission"), HiddenItems.IDS.stream().map(ResourceLocation::getPath).toList());
        assertTrue(HiddenItems.hidden(ResourceLocation.parse("pirates_n_ships:ship_receipt")));
        assertTrue(!HiddenItems.hidden(ResourceLocation.parse("pirates_n_ships:bounty_proof")));
        Map<String, String> lang = CompatText.lang();
        for (Climate c : Climate.values()) assertTrue(lang.containsKey(CompatText.climate(c)), c + " has a name");
        lang.forEach((k, v) -> assertTrue(k.startsWith("pirates_n_ships.jei.") && !v.isBlank(), k));
    }
}
