package com.richardsenger.piratesnships.trade;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.trade.contract.ContractGenerator;
import com.richardsenger.piratesnships.trade.contract.DeliveryContract;
import com.richardsenger.piratesnships.trade.good.TradeGood;
import com.richardsenger.piratesnships.trade.good.TradeGoods;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.GoodRole;
import com.richardsenger.piratesnships.trade.market.Market;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.trade.market.PortProfile;
import com.richardsenger.piratesnships.trade.plunder.PlunderMark;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Trade logic in a real server: datapack goods, item lookup, saved markets and contracts through the service. */
public final class TradeGameTests {

    static final String CONFIG_CONTRACTS_BATCH = "pirates_n_ships_config_trade_contracts";

    private TradeGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(TradeGameTests.class);
    }

    /** A port id nobody else uses, so parallel tests and reruns never share a market. */
    private static ResourceLocation testPort(String name) {
        return Constants.id("gametest/" + name + "_" + UUID.randomUUID());
    }

    private static PortProfile sugarProfile(PortKind kind, GoodRole sugarRole) {
        return new PortProfile(kind, Climate.TROPICAL, 1L, Map.of(TradeGoods.SUGAR, sugarRole));
    }

    @ModGameTest
    public static void generatedTradeGoodsAreLoaded(GameTestHelper helper) {
        var defs = TradeGoods.TYPE.server();
        for (Map.Entry<ResourceLocation, TradeGood> e : TradeGoods.DEFAULTS.entrySet()) {
            helper.assertTrue(defs.contains(e.getKey()), "trade good " + e.getKey() + " not loaded, have " + defs.ids());
            helper.assertValueEqual(defs.require(e.getKey()), e.getValue(), "trade good " + e.getKey());
        }
        helper.succeed();
    }

    @ModGameTest
    public static void vanillaStackMapsToItsGood(GameTestHelper helper) {
        var entry = TradeService.goodOf(helper.getLevel(), new ItemStack(Items.SUGAR, 12));
        helper.assertTrue(entry.isPresent(), "sugar should be a trade good");
        helper.assertValueEqual(entry.get().id(), TradeGoods.SUGAR, "good of a sugar stack");
        helper.assertTrue(TradeService.goodOf(helper.getLevel(), new ItemStack(Items.DIRT)).isEmpty(), "dirt is no trade good");
        helper.assertTrue(TradeService.goodOf(helper.getLevel(), ItemStack.EMPTY).isEmpty(), "empty stack");
        double w = TradeService.cargoWeight(helper.getLevel(), List.of(new ItemStack(Items.OAK_LOG, 10), new ItemStack(Items.DIRT, 4)));
        helper.assertTrue(Math.abs(w - (10 * 1.0 + 4 * 0.25)) < 1e-9, "cargo weight of 10 logs + 4 dirt, got " + w);
        helper.succeed();
    }

    @ModGameTest
    public static void plunderMarkIsCarriedByTheStack(GameTestHelper helper) {
        ItemStack stack = PlunderMark.mark(new ItemStack(Items.SUGAR, 5));
        helper.assertTrue(PlunderMark.isPlundered(stack), "marked stack");
        helper.assertFalse(ItemStack.isSameItemSameComponents(stack, new ItemStack(Items.SUGAR)), "marked and clean sugar don't stack");
        CompoundTag saved = (CompoundTag) stack.save(helper.getLevel().registryAccess());
        ItemStack loaded = ItemStack.parseOptional(helper.getLevel().registryAccess(), saved);
        helper.assertTrue(PlunderMark.isPlundered(loaded), "mark survives saving");
        helper.assertFalse(PlunderMark.isPlundered(PlunderMark.clear(loaded)), "mark can be cleared");
        helper.succeed();
    }

    @ModGameTest
    public static void marketPriceMovesAndSurvivesSaveAndLoad(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation port = testPort("market");
        try {
            TradeService.openMarket(server, port, () -> sugarProfile(PortKind.SEAFARER_VILLAGE, GoodRole.PRODUCES));
            Market.Quote before = TradeService.quote(server, port, TradeGoods.SUGAR, Market.Side.BUY, 64);
            helper.assertTrue(before.ok(), "quote before: " + before);
            Market.Quote bought = TradeService.buy(server, port, TradeGoods.SUGAR, 128);
            helper.assertTrue(bought.ok(), "buy 128 sugar: " + bought);
            Market.Quote after = TradeService.quote(server, port, TradeGoods.SUGAR, Market.Side.BUY, 64);
            helper.assertTrue(after.total() > before.total(), "64 sugar cost more after the buy: " + before.total() + " -> " + after.total());
            helper.assertTrue(after.available() == before.available() - 128, "stock went down by 128");

            TradeData data = TradeData.get(server);
            Market stored = data.market(port).orElseThrow();
            helper.assertTrue(stored.imbalance(TradeGoods.SUGAR) > 127, "imbalance stored, got " + stored.imbalance(TradeGoods.SUGAR));
            CompoundTag tag = data.save(new CompoundTag(), server.registryAccess());
            TradeData reloaded = TradeData.load(tag, server.registryAccess());
            helper.assertValueEqual(reloaded.market(port).orElse(null), stored, "market after save and load");
            helper.succeed();
        } finally {
            TradeData.get(server).removeMarket(port);
        }
    }

    @ModGameTest
    public static void contractCanBeAcceptedAndDelivered(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation origin = testPort("origin");
        ResourceLocation destination = testPort("destination");
        UUID captain = UUID.randomUUID();
        try {
            TradeService.openMarket(server, origin, () -> sugarProfile(PortKind.SEAFARER_VILLAGE, GoodRole.PRODUCES));
            Market dest = TradeService.openMarket(server, destination, () -> sugarProfile(PortKind.NAVY_OUTPOST, GoodRole.DEMANDS));
            var destinations = List.of(new ContractGenerator.Destination(destination, dest.profile(), 2000, 0.5));
            List<DeliveryContract> offers = TradeService.offers(server, origin, destinations);
            helper.assertFalse(offers.isEmpty(), "origin should offer contracts");
            helper.assertValueEqual(TradeService.offers(server, origin, destinations), offers, "same offers on the same day");
            DeliveryContract offer = offers.get(0);

            var accepted = TradeService.accept(server, offer.id(), captain).orElseThrow();
            helper.assertValueEqual(accepted.outcome(), DeliveryContract.Outcome.ACCEPTED, "accept outcome");
            helper.assertValueEqual(accepted.depositDue(), offer.deposit(), "deposit due");
            helper.assertValueEqual(TradeService.contractsOf(server, captain).size(), 1, "captain holds one contract");

            var wrongPort = TradeService.deliver(server, offer.id(), captain, origin, offer.quantity()).orElseThrow();
            helper.assertValueEqual(wrongPort.outcome(), DeliveryContract.Outcome.WRONG_PORT, "delivering at the origin");
            var delivered = TradeService.deliver(server, offer.id(), captain, destination, offer.quantity() + 5).orElseThrow();
            helper.assertValueEqual(delivered.outcome(), DeliveryContract.Outcome.DELIVERED, "deliver outcome");
            helper.assertValueEqual(delivered.consumed(), offer.quantity(), "units consumed");
            helper.assertValueEqual(delivered.payout(), offer.reward() + offer.deposit(), "payout = reward + deposit");
            helper.assertValueEqual(TradeService.contract(server, offer.id()).orElseThrow().state(), DeliveryContract.State.DELIVERED, "stored state");
            helper.succeed();
        } finally {
            TradeData data = TradeData.get(server);
            data.removeMarket(origin);
            data.removeMarket(destination);
            for (DeliveryContract c : data.contracts()) {
                if (c.origin().equals(origin)) data.removeContract(c.id());
            }
        }
    }

    /** Own batch: changes config. */
    @ModGameTest(batch = CONFIG_CONTRACTS_BATCH)
    public static void contractsCanBeDisabled(GameTestHelper helper) {
        ConfigOverrides.during(helper, TradeConfig.CONTRACTS_ENABLED, false);
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation origin = testPort("disabled_origin");
        ResourceLocation destination = testPort("disabled_destination");
        try {
            TradeService.openMarket(server, origin, () -> sugarProfile(PortKind.SEAFARER_VILLAGE, GoodRole.PRODUCES));
            Market dest = TradeService.openMarket(server, destination, () -> sugarProfile(PortKind.NAVY_OUTPOST, GoodRole.DEMANDS));
            var offers = TradeService.offers(server, origin, List.of(new ContractGenerator.Destination(destination, dest.profile(), 2000, 0.5)));
            helper.assertTrue(offers.isEmpty(), "no offers while contracts are disabled");
            helper.succeed();
        } finally {
            TradeData.get(server).removeMarket(origin);
            TradeData.get(server).removeMarket(destination);
        }
    }
}
