package com.richardsenger.piratesnships.compat;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.provisions.ProvisionKind;
import com.richardsenger.piratesnships.crew.provisions.ProvisionSettings;
import com.richardsenger.piratesnships.trade.good.TradeGoods;
import com.richardsenger.piratesnships.trade.market.MarketParams;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The recipe-viewer data in a running server (JEI1): what the JEI plugin's pages are built from, with the real
 * registries, tags and datapack definitions. Whether the plugin loads in JEI is a dev-client check (docs/compat.md).
 */
public final class CompatGameTests {

    private CompatGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(CompatGameTests.class);
    }

    /** Every hidden id names a registered item of ours, so the hidden list cannot silently go stale. */
    @ModGameTest
    public static void hiddenItemsAreRegistered(GameTestHelper helper) {
        for (ResourceLocation id : HiddenItems.IDS) {
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id), "hidden item " + id + " is registered");
        }
        helper.succeed();
    }

    /**
     * The provisions page lists our preserved food, rum, the full water barrel and the vanilla water bottle with the
     * crew's own classification, and leaves out what is no provision (an iron ingot).
     */
    @ModGameTest
    public static void provisionPagesUseTheCrewRules(GameTestHelper helper) {
        List<ProvisionEntry> all = ProvisionEntry.all(ProvisionSettings.DEFAULTS);
        ProvisionEntry hardtack = entry(helper, all, Constants.id("hardtack"));
        helper.assertValueEqual(hardtack.type().kind(), ProvisionKind.FOOD, "hardtack is food");
        helper.assertTrue(hardtack.type().preserved() && hardtack.shelfLifeDays() == 0, "hardtack never spoils");
        ProvisionEntry lime = entry(helper, all, Constants.id("lime"));
        helper.assertTrue(lime.type().antiScurvy(), "lime keeps scurvy away");
        helper.assertValueEqual(entry(helper, all, Constants.id("rum")).type().kind(), ProvisionKind.RUM, "rum is rum");
        ProvisionEntry barrel = entry(helper, all, Constants.id("water_barrel"));
        helper.assertValueEqual(barrel.type().kind(), ProvisionKind.WATER, "the water barrel is water");
        helper.assertValueEqual(barrel.type().valuePerUnit(), (double) ProvisionSettings.DEFAULTS.waterBarrelRations(),
                "a full barrel's rations");
        ProvisionEntry beef = entry(helper, all, BuiltInRegistries.ITEM.getKey(Items.BEEF));
        helper.assertTrue(beef.shelfLifeDays() > 0, "raw beef spoils");
        helper.assertTrue(all.stream().anyMatch(e -> e.stack().is(Items.POTION)), "the water bottle is listed");
        helper.assertTrue(all.stream().noneMatch(e -> e.stack().is(Items.IRON_INGOT)), "an iron ingot is no provision");
        helper.succeed();
    }

    /** The market page has one entry per loaded trade good, each for a registered item, with buy above sell. */
    @ModGameTest
    public static void marketPagesCoverTheTradeGoods(GameTestHelper helper) {
        var goods = TradeGoods.TYPE.server().all();
        List<MarketEntry> entries = MarketEntry.all(goods, MarketParams.DEFAULTS, BuiltInRegistries.ITEM::containsKey);
        helper.assertTrue(!goods.isEmpty(), "trade goods are loaded");
        helper.assertValueEqual(entries.size(), goods.size(), "one market entry per trade good");
        for (MarketEntry e : entries) {
            Item item = BuiltInRegistries.ITEM.get(e.item());
            helper.assertTrue(item != Items.AIR, e.good() + " stands for a registered item");
            helper.assertTrue(e.buy() > e.sell(), e.good() + " buys dearer than it sells");
            helper.assertTrue(e.buyProduced() < e.buy() && e.sellDemanded() > e.sell(), e.good() + " role prices");
        }
        helper.succeed();
    }

    private static ProvisionEntry entry(GameTestHelper helper, List<ProvisionEntry> all, ResourceLocation id) {
        Optional<ProvisionEntry> e = all.stream()
                .filter(p -> BuiltInRegistries.ITEM.getKey(p.stack().getItem()).equals(id)).findFirst();
        helper.assertTrue(e.isPresent(), id + " is listed as a provision");
        return e.orElseThrow();
    }
}
