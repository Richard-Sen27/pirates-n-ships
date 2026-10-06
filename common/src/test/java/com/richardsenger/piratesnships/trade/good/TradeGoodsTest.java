package com.richardsenger.piratesnships.trade.good;

import com.mojang.serialization.JsonOps;
import com.richardsenger.piratesnships.core.data.Definitions;
import com.richardsenger.piratesnships.trade.TradeFixtures;
import com.richardsenger.piratesnships.trade.market.Climate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TradeGoodsTest {

    @BeforeAll
    static void bootstrap() {
        TradeFixtures.bootstrap();
    }

    @Test
    void defaultsHaveSaneValues() {
        assertTrue(TradeGoods.DEFAULTS.size() >= 12);
        for (var e : TradeGoods.DEFAULTS.entrySet()) {
            TradeGood g = e.getValue();
            assertTrue(g.basePrice() >= 1.0 && g.basePrice() <= 50.0, e.getKey() + " price");
            assertTrue(g.weight() > 0 && g.weight() <= 2.0, e.getKey() + " weight");
            assertTrue(g.sensitivity() > 0 && g.sensitivity() <= 3.0, e.getKey() + " sensitivity");
            assertTrue(g.stockFactor() > 0, e.getKey() + " stock factor");
        }
        // Spec: vanilla sugar, fish, timber and iron, plus the mod's colonial goods
        for (String item : List.of("minecraft:sugar", "minecraft:cod", "minecraft:oak_log", "minecraft:iron_ingot",
                "pirates_n_ships:tobacco", "pirates_n_ships:spices", "pirates_n_ships:cloth", "pirates_n_ships:rum")) {
            assertTrue(TradeGoods.DEFAULTS.values().stream().anyMatch(g -> g.item().toString().equals(item)), item);
        }
        // Luxuries are worth more per weight than bulk goods
        TradeGood spices = TradeGoods.DEFAULTS.get(TradeGoods.SPICES);
        TradeGood timber = TradeGoods.DEFAULTS.get(TradeGoods.TIMBER);
        assertTrue(spices.basePrice() / spices.weight() > 50 * timber.basePrice() / timber.weight());
        // Every climate produces something
        for (Climate c : Climate.values()) {
            assertTrue(TradeGoods.DEFAULTS.values().stream().anyMatch(g -> g.producedIn(c)), c.name());
        }
    }

    @Test
    void codecRoundTripAndOptionalDefaults() {
        for (TradeGood g : TradeGoods.DEFAULTS.values()) {
            var json = TradeGood.CODEC.encodeStart(JsonOps.INSTANCE, g).getOrThrow();
            assertEquals(g, TradeGood.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        }
        var minimal = com.google.gson.JsonParser.parseString(
                "{\"item\":\"minecraft:sugar\",\"base_price\":2,\"weight\":0.25,\"category\":\"crop\"}");
        TradeGood parsed = TradeGood.CODEC.parse(JsonOps.INSTANCE, minimal).getOrThrow();
        assertEquals(1.0, parsed.sensitivity());
        assertEquals(1.0, parsed.stockFactor());
        assertTrue(parsed.producedIn().isEmpty());
        var bad = com.google.gson.JsonParser.parseString(
                "{\"item\":\"minecraft:sugar\",\"base_price\":-1,\"weight\":0.25,\"category\":\"crop\"}");
        assertTrue(TradeGood.CODEC.parse(JsonOps.INSTANCE, bad).error().isPresent(), "negative price rejected");
    }

    @Test
    void indexSkipsMissingItemsWithALogLine() {
        List<String> log = new ArrayList<>();
        TradeGoodIndex index = TradeGoodIndex.build(TradeFixtures.GOODS, id -> id.getNamespace().equals("minecraft"), log::add);
        assertEquals(List.of(TradeGoods.CLOTH, TradeGoods.RUM, TradeGoods.SPICES, TradeGoods.TOBACCO), index.skipped());
        assertEquals(4, log.size());
        assertTrue(log.get(0).contains("pirates_n_ships:cloth"));
        assertFalse(index.tradeable().contains(TradeGoods.RUM));
        assertTrue(index.tradeable().contains(TradeGoods.SUGAR));
    }

    @Test
    void vanillaRegistryIndexMapsStacks() {
        // Mod items are not registered in JUnit, so they are skipped against the real registry
        TradeGoodIndex index = TradeGoodIndex.build(TradeFixtures.GOODS, msg -> { });
        assertEquals(TradeGoods.SUGAR, index.of(new ItemStack(Items.SUGAR, 3)).orElseThrow().id());
        assertEquals(TradeGoods.IRON, index.of(new ItemStack(Items.IRON_INGOT)).orElseThrow().id());
        assertTrue(index.of(new ItemStack(Items.DIRT)).isEmpty());
        assertTrue(index.of(ItemStack.EMPTY).isEmpty());
        assertTrue(index.skipped().contains(TradeGoods.TOBACCO));
    }

    @Test
    void duplicateItemKeepsSmallerId() {
        TradeGood a = TradeGoods.DEFAULTS.get(TradeGoods.SUGAR);
        var defs = Definitions.of("trade_good", Map.of(
                ResourceLocation.parse("b:sugar"), a, ResourceLocation.parse("a:sugar"), a));
        List<String> log = new ArrayList<>();
        TradeGoodIndex index = TradeGoodIndex.build(defs, id -> true, log::add);
        assertEquals(ResourceLocation.parse("a:sugar"), index.byItem(a.item()).orElseThrow().id());
        assertEquals(List.of(ResourceLocation.parse("b:sugar")), index.skipped());
        assertEquals(1, log.size());
    }
}
