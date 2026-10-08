package com.richardsenger.piratesnships.rpg.career;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.apparel.ApparelContent;
import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.law.LawConfig;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.mob.entity.Pirate;
import com.richardsenger.piratesnships.rpg.market.MarketReputation;
import com.richardsenger.piratesnships.rpg.reputation.Faction;
import com.richardsenger.piratesnships.rpg.reputation.Reputation;
import com.richardsenger.piratesnships.rpg.reputation.ReputationConfig;
import com.richardsenger.piratesnships.rpg.reputation.ReputationRules;
import com.richardsenger.piratesnships.ship.ShipConfig;
import com.richardsenger.piratesnships.ship.template.ShipOrderContent;
import com.richardsenger.piratesnships.ship.template.ShipOrderMath;
import com.richardsenger.piratesnships.ship.template.ShipOrders;
import com.richardsenger.piratesnships.ship.template.ShipTemplates;
import com.richardsenger.piratesnships.trade.TradeConfig;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import com.richardsenger.piratesnships.trade.desk.HarborDeskBlockEntity;
import com.richardsenger.piratesnships.trade.desk.HarborDesks;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.GoodRole;
import com.richardsenger.piratesnships.trade.market.Market;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.trade.market.PortProfile;
import com.richardsenger.piratesnships.trade.good.TradeGoods;
import com.richardsenger.piratesnships.trade.net.MarketBackend;
import com.richardsenger.piratesnships.trade.net.OrderPayloads;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.world.port.PortService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The rank rewards in a real server (CAR2, docs/design.md §15): the navy flag right and the false-flag rule, the
 * docking fee waiver, the navy shipyard at an outpost's desk with the rank discount, the fence's infamy bonus, the
 * pirates' friendship from infamy, and the promotion gifts (once per rank, dropped when the pack is full). Config
 * changes run in batches of their own ({@code pirates_n_ships_config_career_rewards_*}).
 */
public final class CareerRewardsGameTests {

    private static final String BATCH = "pirates_n_ships_career_rewards_";
    private static final String CONFIG_BATCH = "pirates_n_ships_config_career_rewards_";
    private static final ResourceLocation BASIC = ShipTemplates.STARTER_SLOOP_BASIC_ID;

    private CareerRewardsGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(CareerRewardsGameTests.class);
    }

    // ------------------------------------------------------------------ helpers

    /** A real server player with a mock connection, not added to the level or the player list. */
    private static ServerPlayer serverPlayer(GameTestHelper helper) {
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "reward_test");
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, p, cookie);
        p.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))));
        p.getInventory().clearContent();
        return p;
    }

    /** A player in the navy at {@code rank} (operator path: no promotion rules), with navy reputation {@code rep}. */
    private static ServerPlayer officer(GameTestHelper h, NavyRank rank, int rep) {
        ServerPlayer p = serverPlayer(h);
        Careers.setNavy(p, rank);
        Reputation.set(p, Faction.NAVY, rep, "test");
        h.assertValueEqual(Careers.navyRank(p), rank, "rank");
        return p;
    }

    private static int countOf(Player p, Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    private static void give(ServerPlayer p, Item item, int count) {
        while (count > 0) {
            int n = Math.min(count, 64);
            p.getInventory().placeItemBackInInventory(new ItemStack(item, n));
            count -= n;
        }
    }

    // ------------------------------------------------------------------ flag right and docking fee

    /**
     * A Lieutenant flies the navy flag legitimately below {@code navy_flag_min_standing}; a Midshipman with the same
     * reputation, and a civilian, fly false colours.
     */
    @ModGameTest(batch = BATCH + "flag")
    public static void lieutenantUnderANavyFlagIsNoFalseFlag(GameTestHelper h) {
        int min = LawConfig.NAVY_FLAG_MIN_STANDING.get();
        int low = Math.max(-100, min - 10);
        ServerPlayer lt = officer(h, NavyRank.LIEUTENANT, 0);
        h.assertFalse(LawService.isFalseFlag(lt, FlagKind.NAVY), "a lieutenant with reputation 0 flies a false flag");
        Reputation.set(lt, Faction.NAVY, low, "test");
        h.assertValueEqual(Careers.effectiveNavyStanding(lt), min, "effective standing of a lieutenant");
        h.assertFalse(LawService.isFalseFlag(lt, FlagKind.NAVY), "a lieutenant below the minimum standing flies a false flag");
        h.assertFalse(LawService.fliesFalseColours(lt, FlagKind.NAVY), "a clean lieutenant flies false colours");

        ServerPlayer mid = officer(h, NavyRank.MIDSHIPMAN, low);
        h.assertValueEqual(Careers.effectiveNavyStanding(mid), low, "a midshipman's standing is his reputation");
        h.assertTrue(LawService.isFalseFlag(mid, FlagKind.NAVY), "a midshipman below the minimum standing has the flag right");

        ServerPlayer civilian = serverPlayer(h);
        Reputation.set(civilian, Faction.NAVY, low, "test");
        h.assertTrue(LawService.isFalseFlag(civilian, FlagKind.NAVY), "a civilian below the minimum standing has the flag right");

        h.succeed();
    }

    /** Navy outposts charge no docking fee from {@code fee_waiver_rank} (Lieutenant) up, whatever the reputation. */
    @ModGameTest(batch = BATCH + "fee")
    public static void dockingFeeIsWaivedFromTheWaiverRank(GameTestHelper h) {
        int fee = TradeConfig.feeParams().navyDockingFee();
        ServerPlayer mid = officer(h, NavyRank.MIDSHIPMAN, 0);
        h.assertValueEqual(TradeService.dockingFee(PortKind.NAVY_OUTPOST, mid), fee, "a midshipman pays");
        NavyRank waiver = CareerConfig.FEE_WAIVER_RANK.get();
        ServerPlayer officer = officer(h, waiver, 0);
        h.assertValueEqual(TradeService.dockingFee(PortKind.NAVY_OUTPOST, officer), 0, "the waiver rank pays");
        ServerPlayer admiral = officer(h, NavyRank.ADMIRAL, -50);
        h.assertValueEqual(TradeService.dockingFee(PortKind.NAVY_OUTPOST, admiral), 0, "an unpopular admiral pays");
        h.succeed();
    }

    /** {@code careers.rewards.flag_right} and {@code fee_waiver} off: a Lieutenant is judged by reputation alone. */
    @ModGameTest(batch = CONFIG_BATCH + "off")
    public static void rewardsOffJudgeByReputation(GameTestHelper h) {
        ConfigOverrides.during(h, CareerConfig.FLAG_RIGHT, false);
        ConfigOverrides.during(h, CareerConfig.FEE_WAIVER, false);
        int min = LawConfig.NAVY_FLAG_MIN_STANDING.get();
        ServerPlayer lt = officer(h, NavyRank.LIEUTENANT, Math.max(-100, min - 10));
        h.assertTrue(LawService.isFalseFlag(lt, FlagKind.NAVY), "the flag right is off but the lieutenant kept it");
        h.assertValueEqual(TradeService.dockingFee(PortKind.NAVY_OUTPOST, lt), TradeConfig.feeParams().navyDockingFee(),
                "the waiver is off but the lieutenant paid nothing");
        h.succeed();
    }

    // ------------------------------------------------------------------ the navy shipyard

    /**
     * An outpost's desk: a Midshipman gets no Orders tab and his order is refused; a Captain gets the tab with the
     * Captain's price ({@code navy_ship_price.captain} 0.7) and his order goes through at that price.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "shipyard")
    public static void outpostOrderNeedsACaptainAndIsDiscounted(GameTestHelper h) {
        Port navy = new Port(Constants.id("gametest/navy_" + UUID.randomUUID().toString().substring(0, 8)), PortKind.NAVY_OUTPOST,
                h.getLevel().dimension(), h.absolutePos(BlockPos.ZERO), BoundingBox.fromCorners(h.absolutePos(BlockPos.ZERO),
                h.absolutePos(new BlockPos(8, 8, 8))), Climate.TEMPERATE, List.of());
        PortService.register(h.getLevel().getServer(), navy);
        BlockPos desk = new BlockPos(4, 1, 4);
        h.setBlock(desk, HarborDesks.HARBOR_DESK.get().defaultBlockState());
        ((HarborDeskBlockEntity) h.getBlockEntity(desk)).setPort(Optional.of(navy.id()));
        BlockPos deskAbs = h.absolutePos(desk);

        ServerPlayer mid = officer(h, NavyRank.MIDSHIPMAN, 20);
        Wallet.give(mid, 1000);
        give(mid, Items.OAK_LOG, 64);
        give(mid, Items.WHITE_WOOL, 32);
        List<CustomPacketPayload> midSent = MarketBackend.record(mid.getUUID());
        h.assertTrue(MarketBackend.openDesk(mid, navy.id(), deskAbs), "the outpost desk opens");
        synchronized (midSent) {
            h.assertFalse(midSent.stream().anyMatch(OrderPayloads.Orders.class::isInstance), "a midshipman got an Orders tab");
        }
        h.assertTrue(ShipOrders.view(mid, navy.id()).isEmpty(), "orders view for a midshipman");
        OrderPayloads.OrderResult refused = ShipOrders.place(mid, navy.id(), BASIC);
        h.assertFalse(refused.done(), "a midshipman's order was taken");
        h.assertValueEqual(refused.key(), ShipOrders.KEY_NOT_VILLAGE, "refusal");
        h.assertValueEqual(Wallet.count(mid), 1000L, "coins of the refused midshipman");

        ServerPlayer capt = officer(h, NavyRank.CAPTAIN, 50);
        Wallet.give(capt, 1000);
        give(capt, Items.OAK_LOG, 64);
        give(capt, Items.WHITE_WOOL, 32);
        List<CustomPacketPayload> captSent = MarketBackend.record(capt.getUUID());
        h.assertTrue(MarketBackend.openDesk(capt, navy.id(), deskAbs), "the outpost desk opens for the captain");
        int templatePrice = ShipTemplates.TYPE.server().get(BASIC).orElseThrow().price();
        double factor = CareerConfig.NAVY_SHIP_PRICE.get(NavyRank.CAPTAIN).get();
        long price = ShipOrderMath.price(templatePrice, ShipConfig.ORDER_PRICE_FACTOR.get() * factor);
        long fullPrice = ShipOrderMath.price(templatePrice, ShipConfig.ORDER_PRICE_FACTOR.get());
        h.assertTrue(price < fullPrice, "no discount: " + price + " vs " + fullPrice);
        OrderPayloads.OrdersView view;
        synchronized (captSent) {
            view = captSent.stream().filter(OrderPayloads.Orders.class::isInstance).map(OrderPayloads.Orders.class::cast)
                    .reduce((a, b) -> b).flatMap(OrderPayloads.Orders::view)
                    .orElseThrow(() -> new AssertionError("no Orders tab for a captain at the outpost"));
        }
        OrderPayloads.OrderLine line = view.lines().stream().filter(l -> l.template().equals(BASIC)).findFirst()
                .orElseThrow(() -> new AssertionError("basic sloop not offered: " + view.lines()));
        h.assertValueEqual(line.price(), price, "the captain's price in the tab");

        OrderPayloads.OrderResult done = ShipOrders.place(capt, navy.id(), BASIC);
        h.assertTrue(done.done(), "the captain's order was refused: " + done);
        h.assertValueEqual(Wallet.count(capt), 1000L - price, "coins after the captain's order");
        h.assertValueEqual(countOf(capt, ShipOrderContent.SHIP_RECEIPT.get()), 1, "receipt");
        h.assertValueEqual(PortRegistry.get(h.getLevel().getServer()).index().byId(navy.id()).orElseThrow().orders().size(), 1,
                "orders at the outpost");

        MarketBackend.close(mid);
        MarketBackend.close(capt);
        MarketBackend.stopRecording(mid.getUUID());
        MarketBackend.stopRecording(capt.getUUID());
        PortRegistry.get(h.getLevel().getServer()).remove(navy.id());
        h.succeed();
    }

    // ------------------------------------------------------------------ fences and pirates

    /** A Dread Captain's fence quote is better than a Deckhand's with the same pirate reputation (0). */
    @ModGameTest(batch = BATCH + "fence")
    public static void dreadCaptainGetsACheaperFenceQuote(GameTestHelper h) {
        ResourceLocation port = Constants.id("gametest/reward_fence_" + UUID.randomUUID());
        TradeService.openMarket(h.getLevel().getServer(), port,
                () -> new PortProfile(PortKind.PIRATE_ISLAND, Climate.TROPICAL, 1L, Map.of(TradeGoods.SUGAR, GoodRole.NEUTRAL)));
        ServerPlayer deckhand = serverPlayer(h);
        ServerPlayer dread = serverPlayer(h);
        Careers.setInfamy(dread, InfamyRank.DREAD_CAPTAIN);
        h.assertValueEqual(MarketReputation.priceScore(deckhand, port), 0, "a deckhand's price score");
        int bonus = CareerRewardRules.infamyPriceBonus(InfamyRank.DREAD_CAPTAIN, CareerConfig.INFAMY_PRICE_BONUS.get());
        h.assertTrue(bonus > 0, "no bonus for a dread captain");
        h.assertValueEqual(MarketReputation.priceScore(dread, port), bonus, "a dread captain's price score");

        int q = 64;
        long base = TradeService.quote(h.getLevel().getServer(), port, TradeGoods.SUGAR, Market.Side.BUY, q).total();
        Market.Quote raw = TradeService.quote(h.getLevel().getServer(), port, TradeGoods.SUGAR, Market.Side.BUY, q);
        long deckhandPrice = MarketReputation.quoteFor(deckhand, port, raw).total();
        long dreadPrice = MarketReputation.quoteFor(dread, port, raw).total();
        h.assertValueEqual(deckhandPrice, base, "a deckhand pays the market price");
        h.assertValueEqual(dreadPrice, ReputationRules.buyPrice(base, bonus, ReputationConfig.PRICE_SWING.get()), "the dread captain's price");
        h.assertTrue(dreadPrice < deckhandPrice, "the dread captain pays no less: " + dreadPrice + " vs " + deckhandPrice);

        // the swing is only at pirate islands
        ResourceLocation village = Constants.id("gametest/reward_village_" + UUID.randomUUID());
        TradeService.openMarket(h.getLevel().getServer(), village,
                () -> new PortProfile(PortKind.SEAFARER_VILLAGE, Climate.TROPICAL, 1L, Map.of(TradeGoods.SUGAR, GoodRole.NEUTRAL)));
        h.assertValueEqual(MarketReputation.priceScore(dread, village), 0, "infamy at a village");
        h.succeed();
    }

    /** Pirates leave a Dread Captain alone (pirate reputation 0) and still go for a Buccaneer. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "pirates")
    public static void piratesSpareADreadCaptain(GameTestHelper h) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
        Pirate pirate = h.spawn(MobContent.PIRATE.get(), new BlockPos(2, 1, 4));
        Player buccaneer = h.makeMockPlayer(GameType.SURVIVAL);
        Player dread = h.makeMockPlayer(GameType.SURVIVAL);
        setInfamyOf(buccaneer, InfamyRank.BUCCANEER);
        setInfamyOf(dread, InfamyRank.DREAD_CAPTAIN);
        h.assertTrue(pirate.attacksOnSight(buccaneer), "the pirate spares a buccaneer");
        h.assertFalse(pirate.attacksOnSight(dread), "the pirate attacks a dread captain");
        h.succeed();
    }

    /** Sets the infamy of a mock (non-server) player directly in its record. */
    private static void setInfamyOf(Player p, InfamyRank rank) {
        Reputation.set(p, Faction.PIRATES, 0, "test");
        Careers.store(p, Careers.record(p).withInfamy(rank));
    }

    // ------------------------------------------------------------------ promotion gifts

    /**
     * A real promotion to Lieutenant puts the bicorne and the saber into the inventory, once: resigning and enlisting
     * again gives nothing twice.
     */
    @ModGameTest(batch = BATCH + "gifts")
    public static void bicorneArrivesOnPromotion(GameTestHelper h) {
        ServerPlayer p = serverPlayer(h);
        CareerThresholds.NavyStep lt = CareerConfig.thresholds().step(NavyRank.LIEUTENANT);
        Reputation.set(p, Faction.NAVY, lt.minNavyRep(), "test");
        // the deeds before enlisting: pirates fought and quests done
        Careers.store(p, Careers.record(p).plus(CareerCounter.PIRATES_KILLED, lt.piratesDefeated()).plus(CareerCounter.NAVY_QUESTS, lt.quests()));
        h.assertValueEqual(countOf(p, ApparelContent.OFFICER_HAT.get()), 0, "a bicorne before enlisting");
        h.assertValueEqual(Careers.enlist(p), CareerRules.EnlistVerdict.OK, "enlist");
        h.assertValueEqual(Careers.navyRank(p), NavyRank.LIEUTENANT, "promoted to lieutenant on enlisting");
        h.assertValueEqual(countOf(p, ApparelContent.OFFICER_HAT.get()), 1, "bicorne after the promotion");
        h.assertValueEqual(countOf(p, CombatContent.SABER.get()), 1, "saber after the promotion");
        h.assertTrue(CareerRewards.gifted(p).contains(CareerRewardRules.giftKey(NavyRank.LIEUTENANT)), "gift noted");

        h.assertTrue(Careers.resign(p), "resign");
        h.assertValueEqual(Careers.enlist(p), CareerRules.EnlistVerdict.OK, "enlist again");
        h.assertValueEqual(Careers.navyRank(p), NavyRank.LIEUTENANT, "lieutenant again");
        h.assertValueEqual(countOf(p, ApparelContent.OFFICER_HAT.get()), 1, "a second bicorne");
        h.succeed();
    }

    /** With a full pack the gifts are dropped at the player's feet. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "gifts_full")
    public static void giftsDropWhenThePackIsFull(GameTestHelper h) {
        ServerPlayer p = serverPlayer(h);
        p.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(4, 1, 4))));
        for (int i = 0; i < p.getInventory().items.size(); i++) p.getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
        Careers.setNavy(p, NavyRank.LIEUTENANT);
        List<ItemEntity> dropped = h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(4));
        h.assertTrue(dropped.stream().anyMatch(e -> e.getItem().is(ApparelContent.OFFICER_HAT.get())), "no bicorne dropped: " + dropped);
        h.assertTrue(dropped.stream().anyMatch(e -> e.getItem().is(CombatContent.SABER.get())), "no saber dropped: " + dropped);
        dropped.forEach(ItemEntity::discard);
        h.succeed();
    }
}
