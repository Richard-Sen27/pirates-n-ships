package com.richardsenger.piratesnships.trade.desk;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.trade.TradeConfig;
import com.richardsenger.piratesnships.trade.TradeData;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import com.richardsenger.piratesnships.trade.contract.DeliveryContract;
import com.richardsenger.piratesnships.trade.exchange.MarketTransactions;
import com.richardsenger.piratesnships.trade.exchange.TransactionResult;
import com.richardsenger.piratesnships.trade.good.TradeGoods;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.GoodRole;
import com.richardsenger.piratesnships.trade.market.Market;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.trade.market.PortProfile;
import com.richardsenger.piratesnships.trade.net.MarketBackend;
import com.richardsenger.piratesnships.trade.net.MarketPayloads;
import com.richardsenger.piratesnships.trade.net.MarketView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** The harbor master's desk: binding, opening the market for a player, and the desk session's checks. */
public final class HarborDeskGameTests {

    static final String CONFIG_DISABLED_BATCH = "pirates_n_ships_config_trade_desks_disabled";
    static final String REFRESH_BATCH = "pirates_n_ships_config_trade_market_refresh";

    private static final BlockPos DESK = new BlockPos(1, 1, 1);

    private HarborDeskGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(HarborDeskGameTests.class);
    }

    private static ResourceLocation port(GameTestHelper helper, PortKind kind) {
        ResourceLocation id = Constants.id("gametest/desk_" + UUID.randomUUID());
        TradeService.openMarket(helper.getLevel().getServer(), id,
                () -> new PortProfile(kind, Climate.TROPICAL, 1L, Map.of(TradeGoods.SUGAR, GoodRole.NEUTRAL)));
        return id;
    }

    private static HarborDeskBlockEntity desk(GameTestHelper helper, Optional<ResourceLocation> port) {
        helper.setBlock(DESK, HarborDesks.HARBOR_DESK.get().defaultBlockState());
        HarborDeskBlockEntity be = (HarborDeskBlockEntity) helper.getBlockEntity(DESK);
        be.setPort(port);
        return be;
    }

    private static ServerPlayer player(GameTestHelper helper, long coins) {
        // Not added to the level: a mock connection would receive (and reject) other mods' login payloads
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "desk_test");
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, p, cookie);
        p.setPos(Vec3.atCenterOf(helper.absolutePos(DESK.north())));
        p.getInventory().clearContent();
        Wallet.give(p, coins);
        return p;
    }

    private static InteractionResult use(GameTestHelper helper, ServerPlayer p) {
        BlockPos abs = helper.absolutePos(DESK);
        return helper.getBlockState(DESK).useWithoutItem(helper.getLevel(), p,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false));
    }

    private static int countOf(ServerPlayer p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    private static <T> List<T> of(List<CustomPacketPayload> sent, Class<T> type) {
        synchronized (sent) {
            return sent.stream().filter(type::isInstance).map(type::cast).toList();
        }
    }

    private static MarketPayloads.State lastState(GameTestHelper helper, List<CustomPacketPayload> sent) {
        List<MarketPayloads.State> states = of(sent, MarketPayloads.State.class);
        helper.assertFalse(states.isEmpty(), "a market state was sent");
        return states.get(states.size() - 1);
    }

    private static void finish(GameTestHelper helper, ServerPlayer... players) {
        for (ServerPlayer p : players) {
            MarketBackend.close(p);
            MarketBackend.stopRecording(p.getUUID());
        }
        helper.succeed();
    }

    @ModGameTest
    public static void boundDeskOpensItsPortsMarket(GameTestHelper helper) {
        ResourceLocation port = port(helper, PortKind.SEAFARER_VILLAGE);
        desk(helper, Optional.of(port));
        ServerPlayer p = player(helper, 123);
        List<CustomPacketPayload> sent = MarketBackend.record(p.getUUID());
        helper.assertValueEqual(use(helper, p), InteractionResult.CONSUME, "use result");
        List<MarketPayloads.OpenMarket> opens = of(sent, MarketPayloads.OpenMarket.class);
        helper.assertValueEqual(opens.size(), 1, "one open payload");
        helper.assertValueEqual(opens.get(0), new MarketPayloads.OpenMarket(port, helper.absolutePos(DESK), TradeConfig.DESK_REACH.get()), "open payload");
        MarketView view = lastState(helper, sent).view().orElseThrow(() -> new AssertionError("no market view"));
        helper.assertValueEqual(view.port(), port, "view port");
        helper.assertValueEqual(view.kind(), PortKind.SEAFARER_VILLAGE, "view kind");
        helper.assertValueEqual(view.coins(), 123L, "view coins");
        helper.assertValueEqual(view.quantity(), 1, "quotes for one unit");
        helper.assertTrue(view.goods().stream().anyMatch(l -> l.good().equals(TradeGoods.SUGAR) && l.buy().outcome() == Market.Outcome.OK),
                "sugar is listed with a buy price: " + view.goods());
        helper.assertTrue(MarketBackend.canUse(p, port), "desk session open");
        // A refresh for another quantity answers with quotes for it
        MarketBackend.handleRefresh(p, new MarketPayloads.Refresh(port, 8));
        helper.assertValueEqual(lastState(helper, sent).view().orElseThrow().quantity(), 8, "refreshed quantity");
        finish(helper, p);
    }

    @ModGameTest
    public static void buyThroughTheDeskMatchesTheCommandPath(GameTestHelper helper) {
        ResourceLocation deskPort = port(helper, PortKind.SEAFARER_VILLAGE);
        ResourceLocation commandPort = port(helper, PortKind.SEAFARER_VILLAGE);
        var server = helper.getLevel().getServer();
        long price = TradeService.quote(server, deskPort, TradeGoods.SUGAR, Market.Side.BUY, 64).total();
        helper.assertValueEqual(TradeService.quote(server, commandPort, TradeGoods.SUGAR, Market.Side.BUY, 64).total(), price, "identical ports");
        desk(helper, Optional.of(deskPort));
        ServerPlayer atDesk = player(helper, price + 40);
        ServerPlayer byCommand = player(helper, price + 40);
        List<CustomPacketPayload> sent = MarketBackend.record(atDesk.getUUID());
        use(helper, atDesk);
        MarketBackend.handleTrade(atDesk, new MarketPayloads.Trade(deskPort, true, TradeGoods.SUGAR, 64, false, Optional.empty()));
        TransactionResult viaCommand = MarketTransactions.buy(byCommand, commandPort, TradeGoods.SUGAR, 64, MarketTransactions.Holder.of(byCommand));
        TransactionResult viaDesk = lastState(helper, sent).result().orElseThrow(() -> new AssertionError("no result"));
        helper.assertValueEqual(viaDesk, viaCommand, "same transaction result");
        helper.assertValueEqual(viaDesk.status(), TransactionResult.Status.OK, "bought");
        helper.assertValueEqual(Wallet.count(atDesk), 40L, "desk buyer paid the quote");
        helper.assertValueEqual(Wallet.count(atDesk), Wallet.count(byCommand), "same coins left");
        helper.assertValueEqual(countOf(atDesk, Items.SUGAR), 64, "desk buyer got the sugar");
        helper.assertValueEqual(countOf(byCommand, Items.SUGAR), 64, "command buyer got the sugar");
        helper.assertValueEqual(TradeService.quote(server, deskPort, TradeGoods.SUGAR, Market.Side.BUY, 64).total(),
                TradeService.quote(server, commandPort, TradeGoods.SUGAR, Market.Side.BUY, 64).total(), "both prices moved the same");
        // The answer's view already shows the new wallet
        helper.assertValueEqual(lastState(helper, sent).view().orElseThrow().coins(), 40L, "view coins after the buy");
        finish(helper, atDesk, byCommand);
    }

    @ModGameTest
    public static void sellingAndContractsThroughTheDesk(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ResourceLocation here = port(helper, PortKind.SEAFARER_VILLAGE);
        ResourceLocation there = port(helper, PortKind.NAVY_OUTPOST);
        desk(helper, Optional.of(here));
        ServerPlayer p = player(helper, 50);
        List<CustomPacketPayload> sent = MarketBackend.record(p.getUUID());
        use(helper, p);
        // sell 10 sugar
        p.getInventory().add(new ItemStack(Items.SUGAR, 10));
        long payout = TradeService.quote(server, here, TradeGoods.SUGAR, Market.Side.SELL, 10).total();
        MarketBackend.handleTrade(p, new MarketPayloads.Trade(here, false, TradeGoods.SUGAR, 10, false, Optional.empty()));
        helper.assertValueEqual(lastState(helper, sent).result().orElseThrow().status(), TransactionResult.Status.OK, "sold");
        helper.assertValueEqual(Wallet.count(p), 50L + payout, "paid the sell quote");
        helper.assertValueEqual(countOf(p, Items.SUGAR), 0, "sugar gone");
        // accept an offer of this port: the deposit is taken and the contract shows as the player's
        long day = TradeService.day(server);
        DeliveryContract offer = new DeliveryContract(UUID.randomUUID(), TradeGoods.SUGAR, 5, here, there, day, day + 5, day + 10,
                40, 10, DeliveryContract.State.OFFERED, Optional.empty());
        TradeData.get(server).putContract(offer);
        MarketBackend.handleContract(p, new MarketPayloads.ContractAction(here, false, offer.id(), Optional.empty()));
        MarketPayloads.State s = lastState(helper, sent);
        helper.assertValueEqual(s.result().orElseThrow().status(), TransactionResult.Status.OK, "accepted");
        helper.assertValueEqual(Wallet.count(p), 50L + payout - 10, "deposit taken");
        helper.assertTrue(s.view().orElseThrow().contracts().stream().anyMatch(c -> c.id().equals(offer.id())), "listed as the player's contract");
        // delivering here (not the destination) is refused
        p.getInventory().add(new ItemStack(Items.SUGAR, 5));
        MarketBackend.handleContract(p, new MarketPayloads.ContractAction(here, true, offer.id(), Optional.empty()));
        helper.assertValueEqual(lastState(helper, sent).result().orElseThrow().status(), TransactionResult.Status.CONTRACT_REFUSED, "wrong port");
        helper.assertValueEqual(countOf(p, Items.SUGAR), 5, "sugar kept");
        finish(helper, p);
    }

    @ModGameTest
    public static void playerBeyondDeskReachIsRefused(GameTestHelper helper) {
        ResourceLocation port = port(helper, PortKind.SEAFARER_VILLAGE);
        desk(helper, Optional.of(port));
        ServerPlayer p = player(helper, 2_000);
        List<CustomPacketPayload> sent = MarketBackend.record(p.getUUID());
        use(helper, p);
        double reach = TradeConfig.DESK_REACH.get();
        p.setPos(Vec3.atCenterOf(helper.absolutePos(DESK)).add(reach + 1.5, 0, 0));
        helper.assertFalse(MarketBackend.canUse(p, port), "session invalid beyond reach");
        MarketBackend.handleTrade(p, new MarketPayloads.Trade(port, true, TradeGoods.SUGAR, 8, false, Optional.empty()));
        MarketPayloads.State s = lastState(helper, sent);
        helper.assertTrue(s.view().isEmpty(), "no view for a refused request");
        helper.assertValueEqual(s.result().orElseThrow().status(), TransactionResult.Status.NO_MARKET, "refused");
        helper.assertValueEqual(Wallet.count(p), 2_000L, "coins untouched");
        helper.assertValueEqual(countOf(p, Items.SUGAR), 0, "no sugar");
        // back within reach: trading works again
        p.setPos(Vec3.atCenterOf(helper.absolutePos(DESK)).add(reach - 1.0, 0, 0));
        MarketBackend.handleTrade(p, new MarketPayloads.Trade(port, true, TradeGoods.SUGAR, 8, false, Optional.empty()));
        helper.assertValueEqual(lastState(helper, sent).result().orElseThrow().status(), TransactionResult.Status.OK, "bought within reach");
        // a desk that is broken ends the session
        helper.setBlock(DESK, net.minecraft.world.level.block.Blocks.AIR);
        helper.assertFalse(MarketBackend.canUse(p, port), "session ends with the desk");
        finish(helper, p);
    }

    @ModGameTest
    public static void unboundDeskRefusesAndBindingPersists(GameTestHelper helper) {
        ResourceLocation port = port(helper, PortKind.PIRATE_ISLAND);
        HarborDeskBlockEntity be = desk(helper, Optional.empty());
        ServerPlayer p = player(helper, 100);
        List<CustomPacketPayload> sent = MarketBackend.record(p.getUUID());
        helper.assertValueEqual(HarborDeskService.use(p, helper.absolutePos(DESK)), HarborDeskService.Use.UNBOUND, "unbound");
        helper.assertValueEqual(use(helper, p), InteractionResult.CONSUME, "the block consumes the click");
        helper.assertTrue(of(sent, MarketPayloads.OpenMarket.class).isEmpty(), "no screen opened");
        helper.assertFalse(MarketBackend.canUse(p, port), "no session");
        // a desk bound to a port without a market says so
        be.setPort(Optional.of(Constants.id("gametest/no_such_port")));
        helper.assertValueEqual(HarborDeskService.use(p, helper.absolutePos(DESK)), HarborDeskService.Use.NO_MARKET, "no market");
        // bind through the service (the command's path) and reload the block entity
        helper.assertTrue(HarborDeskService.bind(helper.getLevel(), helper.absolutePos(DESK), Optional.of(port)), "bound");
        CompoundTag tag = be.saveWithFullMetadata(helper.getLevel().registryAccess());
        BlockEntity loaded = BlockEntity.loadStatic(be.getBlockPos(), be.getBlockState(), tag, helper.getLevel().registryAccess());
        helper.assertTrue(loaded instanceof HarborDeskBlockEntity d && d.port().equals(Optional.of(port)), "binding survives a reload");
        helper.assertValueEqual(HarborDeskCommands.resolvePort(helper.getLevel().getServer(), port), Optional.of(port), "full port id resolves");
        helper.assertValueEqual(HarborDeskService.use(p, helper.absolutePos(DESK)), HarborDeskService.Use.OPENED, "opens once bound");
        finish(helper, p);
    }

    private static List<MarketView> views(List<CustomPacketPayload> sent) {
        return of(sent, MarketPayloads.State.class).stream().flatMap(st -> st.view().stream()).toList();
    }

    private static MarketView.GoodLine sugar(MarketView v) {
        return v.goods().stream().filter(l -> l.good().equals(TradeGoods.SUGAR)).findFirst()
                .orElseThrow(() -> new AssertionError("sugar not listed"));
    }

    /**
     * Two players at one desk: a sale by A is pushed to B at once, a change outside the protocol (B's doubloons)
     * reaches B within {@code market_refresh_ticks}, and after A closes the screen A gets no more states while B does.
     */
    @ModGameTest(batch = REFRESH_BATCH, timeoutTicks = 200)
    public static void openSessionsRefreshAndCloseEndsOne(GameTestHelper helper) {
        int interval = 10;
        ConfigOverrides.during(helper, TradeConfig.MARKET_REFRESH_TICKS, interval);
        ResourceLocation port = port(helper, PortKind.SEAFARER_VILLAGE);
        desk(helper, Optional.of(port));
        ServerPlayer a = player(helper, 0);
        ServerPlayer b = player(helper, 100);
        List<CustomPacketPayload> sentA = MarketBackend.record(a.getUUID());
        List<CustomPacketPayload> sentB = MarketBackend.record(b.getUUID());
        long[] mark = new long[2];
        helper.startSequence()
                .thenExecute(() -> {
                    use(helper, a);
                    use(helper, b);
                    helper.assertTrue(MarketBackend.isOpen(a.getUUID(), port) && MarketBackend.isOpen(b.getUUID(), port), "both sessions open");
                    MarketView before = lastState(helper, sentB).view().orElseThrow();
                    // A sells: B's session gets the new stock and price at once, without a request of its own
                    a.getInventory().add(new ItemStack(Items.SUGAR, 32));
                    int statesB = of(sentB, MarketPayloads.State.class).size();
                    MarketBackend.handleTrade(a, new MarketPayloads.Trade(port, false, TradeGoods.SUGAR, 32, false, Optional.empty()));
                    helper.assertValueEqual(lastState(helper, sentA).result().orElseThrow().status(), TransactionResult.Status.OK, "A sold");
                    List<MarketPayloads.State> states = of(sentB, MarketPayloads.State.class);
                    helper.assertTrue(states.size() > statesB, "the sale is pushed to B");
                    MarketPayloads.State pushed = states.get(states.size() - 1);
                    helper.assertTrue(pushed.result().isEmpty(), "a push carries no result");
                    MarketView after = pushed.view().orElseThrow();
                    helper.assertValueEqual(after, MarketBackend.view(b, port, 1).orElseThrow(), "B sees the current market");
                    helper.assertFalse(sugar(after).buy().equals(sugar(before).buy()) && sugar(after).sell().equals(sugar(before).sell()),
                            "B's sugar quote moved: " + sugar(before) + " -> " + sugar(after));
                    // A change outside the protocol: B's doubloons
                    Wallet.give(b, 7);
                    mark[0] = helper.getTick();
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(views(sentB).stream().anyMatch(v -> v.coins() == 107L), "B's refreshed doubloons");
                    helper.assertTrue(helper.getTick() - mark[0] <= interval + 1, "refreshed within the interval");
                })
                .thenExecute(() -> {
                    MarketBackend.handleClose(a, MarketPayloads.CloseMarket.INSTANCE);
                    helper.assertFalse(MarketBackend.isOpen(a.getUUID(), port), "A's session ended");
                    helper.assertFalse(MarketBackend.canUse(a, port), "A can no longer trade");
                    helper.assertTrue(MarketBackend.isOpen(b.getUUID(), port), "B's session continues");
                    mark[1] = of(sentA, MarketPayloads.State.class).size();
                    Wallet.give(a, 5);
                    Wallet.give(b, 5);
                })
                .thenIdle(interval + 2)
                .thenExecute(() -> {
                    helper.assertValueEqual((long) of(sentA, MarketPayloads.State.class).size(), mark[1], "no refresh after A closed");
                    helper.assertTrue(views(sentB).stream().anyMatch(v -> v.coins() == 112L), "B still refreshed");
                    MarketBackend.close(a);
                    MarketBackend.close(b);
                    MarketBackend.stopRecording(a.getUUID());
                    MarketBackend.stopRecording(b.getUUID());
                })
                .thenSucceed();
    }

    @ModGameTest(batch = CONFIG_DISABLED_BATCH)
    public static void disabledDesksAreInert(GameTestHelper helper) {
        ResourceLocation port = port(helper, PortKind.SEAFARER_VILLAGE);
        desk(helper, Optional.of(port));
        ServerPlayer p = player(helper, 2_000);
        List<CustomPacketPayload> sent = MarketBackend.record(p.getUUID());
        // A session opened while desks were on ends when they are switched off
        MarketBackend.openDesk(p, port, helper.absolutePos(DESK));
        helper.assertTrue(MarketBackend.canUse(p, port), "session while enabled");
        ConfigOverrides.during(helper, TradeConfig.DESKS_ENABLED, false);
        sent.clear();
        helper.assertValueEqual(use(helper, p), InteractionResult.PASS, "use passes");
        helper.assertTrue(of(sent, MarketPayloads.OpenMarket.class).isEmpty(), "no screen opened");
        helper.assertFalse(MarketBackend.canUse(p, port), "session invalid");
        MarketBackend.handleTrade(p, new MarketPayloads.Trade(port, true, TradeGoods.SUGAR, 8, false, Optional.empty()));
        helper.assertValueEqual(lastState(helper, sent).result().orElseThrow().status(), TransactionResult.Status.NO_MARKET, "trade refused");
        helper.assertValueEqual(Wallet.count(p), 2_000L, "coins untouched");
        finish(helper, p);
    }

    /** Placing a desk asks the port locator (installed by world generation later). */
    @ModGameTest(template = GameTestTemplates.EMPTY_3, batch = "pirates_n_ships_trade_desk_locator")
    public static void placedDeskBindsThroughThePortLocator(GameTestHelper helper) {
        ResourceLocation port = port(helper, PortKind.NAVY_OUTPOST);
        BlockPos abs = helper.absolutePos(DESK);
        HarborDeskService.setPortLocator((level, pos) -> pos.equals(abs) ? Optional.of(port) : Optional.empty());
        try {
            desk(helper, Optional.empty());
            helper.assertValueEqual(HarborDeskService.autoBind(helper.getLevel(), abs), Optional.of(port), "locator found the port");
            helper.assertValueEqual(HarborDeskService.boundPort(helper.getLevel(), abs), Optional.of(port), "bound");
        } finally {
            HarborDeskService.setPortLocator((level, pos) -> Optional.empty());
        }
        helper.succeed();
    }
}
