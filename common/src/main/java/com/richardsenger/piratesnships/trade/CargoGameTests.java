package com.richardsenger.piratesnships.trade;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.ship.decor.ShipDecor;
import com.richardsenger.piratesnships.trade.cargo.BulkCargo;
import com.richardsenger.piratesnships.trade.cargo.BulkStore;
import com.richardsenger.piratesnships.trade.cargo.CargoContainerBlockEntity;
import com.richardsenger.piratesnships.trade.cargo.CargoContainers;
import com.richardsenger.piratesnships.trade.cargo.CargoWeighing;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import com.richardsenger.piratesnships.trade.content.TradeContent;
import com.richardsenger.piratesnships.trade.contract.DeliveryContract;
import com.richardsenger.piratesnships.trade.exchange.MarketTransactions;
import com.richardsenger.piratesnships.trade.exchange.TransactionResult;
import com.richardsenger.piratesnships.trade.good.TradeGoods;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.GoodRole;
import com.richardsenger.piratesnships.trade.market.Market;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.trade.market.PortProfile;
import com.richardsenger.piratesnships.trade.plunder.PlunderMark;
import com.richardsenger.piratesnships.trade.plunder.PlunderRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Bulk cargo containers, the wallet and market transactions with real coins and items. */
public final class CargoGameTests {

    static final String CONFIG_NOTICE_BATCH = "pirates_n_ships_config_trade_navy_notice";

    private CargoGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(CargoGameTests.class);
    }

    private static final BlockPos CRATE = new BlockPos(1, 1, 1);

    private static CargoContainerBlockEntity crate(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ShipDecor.CARGO_CRATE.get());
        return (CargoContainerBlockEntity) helper.getBlockEntity(pos);
    }

    private static BlockHitResult hit(GameTestHelper helper, BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pos)), Direction.UP, helper.absolutePos(pos), false);
    }

    private static ResourceLocation port(GameTestHelper helper, PortKind kind) {
        ResourceLocation id = Constants.id("gametest/cargo_" + UUID.randomUUID());
        TradeService.openMarket(helper.getLevel().getServer(), id,
                () -> new PortProfile(kind, Climate.TROPICAL, 1L, Map.of(TradeGoods.SUGAR, GoodRole.NEUTRAL)));
        return id;
    }

    private static ServerPlayer player(GameTestHelper helper, long coins) {
        // Not added to the level: a mock connection would receive (and reject) other mods' login payloads
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "trade_test");
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, p, cookie);
        p.setPos(Vec3.atCenterOf(helper.absolutePos(CRATE.above())));
        p.getInventory().clearContent();
        Wallet.give(p, coins);
        return p;
    }

    private static int countOf(ServerPlayer p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    private static long quote(GameTestHelper helper, ResourceLocation port, Market.Side side) {
        return TradeService.quote(helper.getLevel().getServer(), port, TradeGoods.SUGAR, side, 64).total();
    }

    @ModGameTest
    public static void playerFillsAndEmptiesACrateAndItSurvivesReload(GameTestHelper helper) {
        CargoContainerBlockEntity be = crate(helper, CRATE);
        ServerPlayer p = player(helper, 0);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SUGAR, 64));
        helper.getBlockState(CRATE).useItemOn(p.getMainHandItem(), helper.getLevel(), p, InteractionHand.MAIN_HAND, hit(helper, CRATE));
        helper.assertValueEqual(be.count(), 64, "sugar in the crate");
        helper.assertTrue(p.getMainHandItem().isEmpty(), "hand emptied");
        p.getInventory().add(new ItemStack(Items.SUGAR, 40));
        p.setShiftKeyDown(true);
        helper.getBlockState(CRATE).useWithoutItem(helper.getLevel(), p, hit(helper, CRATE));
        p.setShiftKeyDown(false);
        helper.assertValueEqual(be.count(), 104, "sneak-use pours in the rest");
        helper.getBlockState(CRATE).useWithoutItem(helper.getLevel(), p, hit(helper, CRATE));
        helper.assertValueEqual(be.count(), 40, "one stack taken out");
        helper.assertValueEqual(countOf(p, Items.SUGAR), 64, "player got a stack");
        CompoundTag tag = be.saveWithFullMetadata(helper.getLevel().registryAccess());
        BlockEntity loaded = BlockEntity.loadStatic(be.getBlockPos(), be.getBlockState(), tag, helper.getLevel().registryAccess());
        helper.assertTrue(loaded instanceof CargoContainerBlockEntity c && c.count() == 40 && c.heldKind().is(Items.SUGAR), "content after reload");
        helper.assertValueEqual(be.signal(), 1 + (int) Math.floor(14.0 * 40 / 2048), "comparator signal");
        helper.succeed();
    }

    @ModGameTest
    public static void crateRefusesASecondKindAndPlunderMixing(GameTestHelper helper) {
        CargoContainerBlockEntity be = crate(helper, CRATE);
        be.insert(new ItemStack(Items.SUGAR, 10));
        ItemStack dirt = new ItemStack(Items.DIRT, 5);
        helper.assertValueEqual(be.check(dirt), BulkStore.Refusal.OTHER_KIND, "dirt refused");
        helper.assertValueEqual(be.insert(dirt), 0, "nothing inserted");
        helper.assertValueEqual(dirt.getCount(), 5, "dirt kept");
        helper.assertValueEqual(be.check(PlunderMark.mark(new ItemStack(Items.SUGAR, 5))), BulkStore.Refusal.PLUNDER_MIX, "plundered sugar refused");
        helper.assertValueEqual(be.check(new ItemStack(Items.DIAMOND_SWORD)), BulkStore.Refusal.OTHER_KIND, "sword refused");
        be.extract(10);
        helper.assertValueEqual(be.check(new ItemStack(Items.DIAMOND_SWORD)), BulkStore.Refusal.NOT_STACKABLE, "unstackables never go in");
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void brokenCrateKeepsItsCargoInTheItem(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 1, 4);
        CargoContainerBlockEntity be = crate(helper, pos);
        be.insertAll(new ItemStack(Items.SUGAR), 2000);
        BlockPos abs = helper.absolutePos(pos);
        List<ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(helper.getBlockState(pos), helper.getLevel(), abs,
                be, null, new ItemStack(Items.IRON_AXE));
        helper.assertValueEqual(drops.size(), 1, "one item dropped");
        ItemStack drop = drops.get(0);
        helper.destroyBlock(pos);
        helper.assertTrue(drop.is(ShipDecor.CARGO_CRATE.get().asItem()), "the crate drops");
        BulkCargo cargo = drop.get(CargoContainers.BULK_CARGO.get());
        helper.assertTrue(cargo != null && cargo.count() == 2000 && cargo.kind().is(Items.SUGAR), "cargo carried by the item: " + cargo);
        helper.assertValueEqual(drop.getMaxStackSize(), 1, "filled crates don't stack");
        CargoContainerBlockEntity placed = crate(helper, pos);
        placed.applyComponentsFromItemStack(drop);
        helper.assertValueEqual(placed.count(), 2000, "content after placing again");
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 200)
    public static void hoppersFillAndEmptyACrate(GameTestHelper helper) {
        BlockPos top = new BlockPos(2, 3, 2);
        BlockPos mid = new BlockPos(2, 2, 2);
        BlockPos bottom = new BlockPos(2, 1, 2);
        helper.setBlock(top, Blocks.HOPPER);
        CargoContainerBlockEntity be = crate(helper, mid);
        helper.setBlock(bottom, Blocks.HOPPER);
        ((HopperBlockEntity) helper.getBlockEntity(top)).setItem(0, new ItemStack(Items.SUGAR, 6));
        helper.succeedWhen(() -> {
            HopperBlockEntity out = (HopperBlockEntity) helper.getBlockEntity(bottom);
            int n = 0;
            for (int i = 0; i < out.getContainerSize(); i++) n += out.getItem(i).is(Items.SUGAR) ? out.getItem(i).getCount() : 0;
            helper.assertValueEqual(n, 6, "sugar passed through the crate");
            helper.assertValueEqual(be.count(), 0, "crate empty again");
        });
    }

    @ModGameTest
    public static void buyMovesCoinsAndGoodsAndRaisesThePrice(GameTestHelper helper) {
        ResourceLocation port = port(helper, PortKind.SEAFARER_VILLAGE);
        long before = quote(helper, port, Market.Side.BUY);
        ServerPlayer p = player(helper, before + 50);
        TransactionResult r = MarketTransactions.buy(p, port, TradeGoods.SUGAR, 64, MarketTransactions.Holder.of(p));
        helper.assertValueEqual(r.status(), TransactionResult.Status.OK, "buy");
        helper.assertValueEqual(r.coins(), before, "paid the quote");
        helper.assertValueEqual(Wallet.count(p), 50L, "coins left");
        helper.assertValueEqual(countOf(p, Items.SUGAR), 64, "sugar received");
        long after = quote(helper, port, Market.Side.BUY);
        helper.assertTrue(after > before, "price rose: " + before + " -> " + after);
        // Not enough coins: nothing moves
        r = MarketTransactions.buy(p, port, TradeGoods.SUGAR, 64, MarketTransactions.Holder.of(p));
        helper.assertValueEqual(r.status(), TransactionResult.Status.NOT_ENOUGH_COINS, "poor buyer");
        helper.assertValueEqual(Wallet.count(p), 50L, "coins untouched");
        helper.assertValueEqual(quote(helper, port, Market.Side.BUY), after, "price untouched");
        // No space: a full inventory refuses the goods
        ServerPlayer full = player(helper, 1_000);
        for (int i = 0; i < full.getInventory().items.size(); i++) {
            if (full.getInventory().items.get(i).isEmpty()) full.getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
        }
        long coins = Wallet.count(full);
        r = MarketTransactions.buy(full, port, TradeGoods.SUGAR, 64, MarketTransactions.Holder.of(full));
        helper.assertValueEqual(r.status(), TransactionResult.Status.NOT_ENOUGH_SPACE, "full inventory");
        helper.assertValueEqual(Wallet.count(full), coins, "coins untouched");
        helper.succeed();
    }

    @ModGameTest
    public static void sellPaysOutAndLowersThePrice(GameTestHelper helper) {
        ResourceLocation port = port(helper, PortKind.SEAFARER_VILLAGE);
        long before = quote(helper, port, Market.Side.SELL);
        ServerPlayer p = player(helper, 0);
        p.getInventory().add(new ItemStack(Items.SUGAR, 64));
        TransactionResult r = MarketTransactions.sell(p, port, TradeGoods.SUGAR, 64, false, MarketTransactions.Holder.of(p));
        helper.assertValueEqual(r.status(), TransactionResult.Status.OK, "sell");
        helper.assertValueEqual(Wallet.count(p), before, "paid the quote");
        helper.assertValueEqual(countOf(p, Items.SUGAR), 0, "sugar gone");
        helper.assertTrue(quote(helper, port, Market.Side.SELL) < before, "price fell");
        r = MarketTransactions.sell(p, port, TradeGoods.SUGAR, 1, false, MarketTransactions.Holder.of(p));
        helper.assertValueEqual(r.status(), TransactionResult.Status.NOT_ENOUGH_GOODS, "nothing left to sell");
        helper.succeed();
    }

    @ModGameTest
    public static void fenceBuysPlunderAtADiscount(GameTestHelper helper) {
        ResourceLocation port = port(helper, PortKind.PIRATE_ISLAND);
        long normal = quote(helper, port, Market.Side.SELL);
        ServerPlayer p = player(helper, 0);
        p.getInventory().add(PlunderMark.mark(new ItemStack(Items.SUGAR, 64)));
        helper.assertValueEqual(MarketTransactions.sell(p, port, TradeGoods.SUGAR, 64, false, MarketTransactions.Holder.of(p)).status(),
                TransactionResult.Status.NOT_ENOUGH_GOODS, "plundered goods aren't sold as clean ones");
        TransactionResult r = MarketTransactions.sell(p, port, TradeGoods.SUGAR, 64, true, MarketTransactions.Holder.of(p));
        helper.assertValueEqual(r.plunder(), PlunderRules.Outcome.FENCED, "fenced");
        long expected = (long) Math.floor(normal * (1.0 - TradeConfig.FENCE_DISCOUNT.get()));
        helper.assertValueEqual(Wallet.count(p), expected, "discounted payout");
        helper.assertFalse(r.noticedPlunder(), "fences ask no questions");
        helper.succeed();
    }

    @ModGameTest(batch = CONFIG_NOTICE_BATCH)
    public static void navyPortRefusesPlunder(GameTestHelper helper) {
        // LAW3: only the fence takes plunder; a navy outpost refuses it (and notices it), nothing moves
        ResourceLocation port = port(helper, PortKind.NAVY_OUTPOST);
        ServerPlayer p = player(helper, 0);
        p.getInventory().add(PlunderMark.mark(new ItemStack(Items.SUGAR, 32)));
        TransactionResult r = MarketTransactions.sell(p, port, TradeGoods.SUGAR, 32, true, MarketTransactions.Holder.of(p));
        helper.assertTrue(r.noticedPlunder(), "noticed: " + r);
        helper.assertValueEqual(r.status(), TransactionResult.Status.PLUNDER_REFUSED, "refused");
        helper.assertValueEqual(Wallet.count(p), 0L, "no payout");
        helper.assertValueEqual(countOf(p, Items.SUGAR), 32, "goods kept");
        // With plunder marks off, plunder sells like any other goods
        ConfigOverrides.during(helper, TradeConfig.PLUNDER_ENABLED, false);
        r = MarketTransactions.sell(p, port, TradeGoods.SUGAR, 32, true, MarketTransactions.Holder.of(p));
        helper.assertValueEqual(r.status(), TransactionResult.Status.OK, "sold with plunder marks off");
        helper.assertValueEqual(countOf(p, Items.SUGAR), 0, "goods sold");
        helper.succeed();
    }

    @ModGameTest
    public static void buyIntoAndSellFromACrate(GameTestHelper helper) {
        ResourceLocation port = port(helper, PortKind.SEAFARER_VILLAGE);
        CargoContainerBlockEntity be = crate(helper, CRATE);
        ServerPlayer p = player(helper, 1_000);
        TransactionResult r = MarketTransactions.buy(p, port, TradeGoods.SUGAR, 100, MarketTransactions.Holder.of(be));
        helper.assertValueEqual(r.status(), TransactionResult.Status.OK, "bought into the crate");
        helper.assertValueEqual(be.count(), 100, "crate filled");
        helper.assertValueEqual(countOf(p, Items.SUGAR), 0, "nothing in the inventory");
        be.insertAll(new ItemStack(Items.SUGAR), 1940);
        long before = Wallet.count(p);
        helper.assertValueEqual(MarketTransactions.buy(p, port, TradeGoods.SUGAR, 64, MarketTransactions.Holder.of(be)).status(),
                TransactionResult.Status.NOT_ENOUGH_SPACE, "crate too full");
        helper.assertValueEqual(Wallet.count(p), before, "no coins taken");
        long coins = Wallet.count(p);
        r = MarketTransactions.sell(p, port, TradeGoods.SUGAR, 200, false, MarketTransactions.Holder.of(be));
        helper.assertValueEqual(r.status(), TransactionResult.Status.OK, "sold from the crate");
        helper.assertValueEqual(be.count(), 1840, "crate drained");
        helper.assertValueEqual(Wallet.count(p), coins + r.coins(), "paid");
        helper.succeed();
    }

    @ModGameTest
    public static void contractWithRealCoinsAndGoods(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ResourceLocation from = port(helper, PortKind.SEAFARER_VILLAGE);
        ResourceLocation to = port(helper, PortKind.NAVY_OUTPOST);
        long day = TradeService.day(server);
        DeliveryContract offer = new DeliveryContract(UUID.randomUUID(), TradeGoods.SUGAR, 20, from, to, day, day + 5, day + 10,
                100, 30, DeliveryContract.State.OFFERED, Optional.empty());
        TradeData.get(server).putContract(offer);
        ServerPlayer poor = player(helper, 10);
        helper.assertValueEqual(MarketTransactions.acceptContract(poor, offer.id()).status(), TransactionResult.Status.NOT_ENOUGH_COINS, "deposit missing");
        ServerPlayer p = player(helper, 50);
        helper.assertValueEqual(MarketTransactions.acceptContract(p, offer.id()).status(), TransactionResult.Status.OK, "accepted");
        helper.assertValueEqual(Wallet.count(p), 20L, "deposit taken");
        p.getInventory().add(new ItemStack(Items.SUGAR, 15));
        helper.assertValueEqual(MarketTransactions.deliverContract(p, to, offer.id(), MarketTransactions.Holder.of(p)).status(),
                TransactionResult.Status.NOT_ENOUGH_GOODS, "short delivery");
        p.getInventory().add(new ItemStack(Items.SUGAR, 10));
        helper.assertValueEqual(MarketTransactions.deliverContract(p, from, offer.id(), MarketTransactions.Holder.of(p)).status(),
                TransactionResult.Status.CONTRACT_REFUSED, "wrong port");
        TransactionResult r = MarketTransactions.deliverContract(p, to, offer.id(), MarketTransactions.Holder.of(p));
        helper.assertValueEqual(r.status(), TransactionResult.Status.OK, "delivered");
        helper.assertValueEqual(Wallet.count(p), 20L + 130L, "reward and deposit paid");
        helper.assertValueEqual(countOf(p, Items.SUGAR), 5, "20 sugar consumed");
        helper.succeed();
    }

    @ModGameTest
    public static void walletTakesAllOrNothing(GameTestHelper helper) {
        ServerPlayer p = player(helper, 150);
        helper.assertValueEqual(Wallet.count(p), 150L, "given");
        helper.assertFalse(Wallet.take(p, 151), "too much");
        helper.assertValueEqual(Wallet.count(p), 150L, "unchanged");
        helper.assertTrue(Wallet.take(p, 100), "taken");
        helper.assertValueEqual(Wallet.count(p), 50L, "rest");
        helper.assertTrue(p.getInventory().contains(new ItemStack(TradeContent.DOUBLOON.get())), "coins are items");
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void cargoWeightOfContainers(GameTestHelper helper) {
        CargoContainerBlockEntity be = crate(helper, new BlockPos(1, 1, 1));
        be.insertAll(new ItemStack(Items.SUGAR), 100); // 0.25 each
        BlockPos chestPos = new BlockPos(3, 1, 1);
        helper.setBlock(chestPos, Blocks.CHEST);
        ((ChestBlockEntity) helper.getBlockEntity(chestPos)).setItem(0, new ItemStack(Items.OAK_LOG, 10)); // 1.0 each
        BlockPos a = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos c = helper.absolutePos(chestPos);
        double w = CargoWeighing.weigh(helper.getLevel(), List.of(a, c, a, helper.absolutePos(new BlockPos(5, 1, 5))));
        helper.assertTrue(Math.abs(w - 35.0) < 1e-9, "100 sugar + 10 logs = 35, got " + w);
        double skipped = CargoWeighing.weigh(helper.getLevel(), List.of(a, c), b -> b instanceof ChestBlockEntity);
        helper.assertTrue(Math.abs(skipped - 25.0) < 1e-9, "skipping the chest (a pantry stand-in), got " + skipped);
        ItemStack filled = new ItemStack(ShipDecor.CARGO_CRATE.get());
        filled.set(CargoContainers.BULK_CARGO.get(), new BulkCargo(new ItemStack(Items.SUGAR), 40));
        filled.set(DataComponents.MAX_STACK_SIZE, 1);
        double nested = CargoWeighing.stackWeight(filled, TradeService.goods(false), 0);
        helper.assertTrue(Math.abs(nested - (TradeConfig.DEFAULT_ITEM_WEIGHT.get() + 10.0)) < 1e-9, "a filled crate item carries its cargo, got " + nested);
        helper.succeed();
    }
}
