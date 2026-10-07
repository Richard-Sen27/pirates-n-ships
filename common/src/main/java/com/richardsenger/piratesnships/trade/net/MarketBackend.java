package com.richardsenger.piratesnships.trade.net;

import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.ship.template.ShipOrders;
import com.richardsenger.piratesnships.trade.TradeConfig;
import com.richardsenger.piratesnships.trade.TradeData;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.cargo.CargoContainerBlockEntity;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import com.richardsenger.piratesnships.trade.contract.DeliveryContract;
import com.richardsenger.piratesnships.trade.exchange.MarketTransactions;
import com.richardsenger.piratesnships.trade.exchange.TransactionResult;
import com.richardsenger.piratesnships.trade.good.TradeGood;
import com.richardsenger.piratesnships.trade.market.Market;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import com.richardsenger.piratesnships.trade.desk.HarborDeskService;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Server side of the market protocol. A market is opened for a player by server code ({@link #open}: the harbor
 * master's desk ({@link #openDesk}) or the debug command), which starts a session at the player's position. Client requests are only
 * honoured for the session's port, in the same dimension and within {@code market_reach} of where it was opened (desk
 * sessions: within {@code desk_reach} of a desk still bound to the port, with desks enabled);
 * quantities must be 1..{@code max_trade_quantity}; a container must be a loaded cargo container within
 * {@code container_reach} of the player. Every request is answered with a {@link MarketPayloads.State}.
 *
 * <p>Every open session is kept: once every {@code market_refresh_ticks} each valid session whose view changed
 * (prices drifting back, stock, the viewer's doubloons, offers) gets a new state, and a trade or contract action pushes
 * the new state to every other session of that port at once, so several players at one desk see each other's trades.
 * A session ends on {@link MarketPayloads.CloseMarket} (the screen closed), logout, death, or when another market is
 * opened; a session that is out of reach is only paused (no refreshes, requests refused) as before.
 *
 * <p>{@link #onNoticedPlunder} is the hook for the law integration (this package never calls the law module).
 */
public final class MarketBackend {

    /**
     * {@code desk} = opened at a harbor master's desk (reach and binding checked against it on every request);
     * {@code quantity} = the quote quantity last sent, kept for answers to requests without one (contracts) and for
     * refreshes; {@code lastSent} = the view last sent, so a refresh is sent only when it changed.
     */
    record Session(ServerPlayer player, ResourceLocation port, ResourceKey<Level> dimension, Vec3 origin, Optional<BlockPos> desk,
                   int quantity, Optional<MarketView> lastSent) {
        Session withSent(int q, Optional<MarketView> view) {
            return new Session(player, port, dimension, origin, desk, q, view.isPresent() ? view : lastSent);
        }
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<CustomPacketPayload>> RECORDINGS = new ConcurrentHashMap<>();
    private static final List<Consumer<NoticedSale>> NOTICED_LISTENERS = new CopyOnWriteArrayList<>();

    /** A plunder sale a port noticed. */
    public record NoticedSale(ServerPlayer player, ResourceLocation port, TransactionResult result) {
    }

    private MarketBackend() {
    }

    /**
     * Adds a listener for noticed plunder sales made through the protocol (the law module reports them as crimes).
     * Listeners run on the server thread in registration order; register once, at mod construction.
     */
    public static void onNoticedPlunder(Consumer<NoticedSale> listener) {
        NOTICED_LISTENERS.add(listener);
    }

    public static void registerPayloads() {
        Services.NETWORK.registerToServer(MarketPayloads.Refresh.TYPE, MarketPayloads.Refresh.CODEC,
                (p, player) -> handleRefresh((ServerPlayer) player, p));
        Services.NETWORK.registerToServer(MarketPayloads.Trade.TYPE, MarketPayloads.Trade.CODEC,
                (p, player) -> handleTrade((ServerPlayer) player, p));
        Services.NETWORK.registerToServer(MarketPayloads.ContractAction.TYPE, MarketPayloads.ContractAction.CODEC,
                (p, player) -> handleContract((ServerPlayer) player, p));
        Services.NETWORK.registerToServer(MarketPayloads.CloseMarket.TYPE, MarketPayloads.CloseMarket.CODEC,
                (p, player) -> handleClose((ServerPlayer) player, p));
        Services.NETWORK.registerToClient(MarketPayloads.OpenMarket.TYPE, MarketPayloads.OpenMarket.CODEC,
                (p, player) -> com.richardsenger.piratesnships.trade.client.ClientMarketState.open(p));
        Services.NETWORK.registerToClient(MarketPayloads.State.TYPE, MarketPayloads.State.CODEC,
                (p, player) -> com.richardsenger.piratesnships.trade.client.ClientMarketState.accept(p));
        // SW1: the shipwright's Orders tab
        Services.NETWORK.registerToServer(OrderPayloads.PlaceOrder.TYPE, OrderPayloads.PlaceOrder.CODEC,
                (p, player) -> handleOrder((ServerPlayer) player, p));
        Services.NETWORK.registerToClient(OrderPayloads.Orders.TYPE, OrderPayloads.Orders.CODEC,
                (p, player) -> com.richardsenger.piratesnships.trade.client.ClientMarketState.acceptOrders(p));
    }

    // --- Sessions -----------------------------------------------------------------------------------------------

    /** Opens the port's market (it must exist) for the player and sends the state; false if there is no market. */
    public static boolean open(ServerPlayer player, ResourceLocation port, int quantity) {
        if (TradeService.market(player.server, port).isEmpty()) return false;
        SESSIONS.put(player.getUUID(), new Session(player, port, player.level().dimension(), player.position(), Optional.empty(),
                quantity, Optional.empty()));
        send(player, port, quantity, Optional.empty());
        return true;
    }

    /**
     * Opens the port's market at a harbor master's desk: tells the client to open the market screen, then sends the
     * state (quotes for one unit). Later requests are checked against the desk ({@link HarborDeskService#sessionValid}).
     */
    public static boolean openDesk(ServerPlayer player, ResourceLocation port, BlockPos desk) {
        if (TradeService.market(player.server, port).isEmpty()) return false;
        SESSIONS.put(player.getUUID(), new Session(player, port, player.level().dimension(), Vec3.atCenterOf(desk),
                Optional.of(desk.immutable()), 1, Optional.empty()));
        deliver(player, new MarketPayloads.OpenMarket(port, desk, TradeConfig.DESK_REACH.get()));
        send(player, port, 1, Optional.empty());
        // A seafarer village's desk also has the shipwright's Orders tab (SW1)
        ShipOrders.view(player, port).ifPresent(v -> deliver(player, new OrderPayloads.Orders(Optional.of(v), Optional.empty())));
        return true;
    }

    public static void close(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }

    /** Whether {@code player} has a market session for {@code port} (open, not necessarily in reach; tests, debug). */
    public static boolean isOpen(UUID player, ResourceLocation port) {
        Session s = SESSIONS.get(player);
        return s != null && s.port().equals(port);
    }

    /**
     * Every server tick (subscribed in {@code TradeModule.registerEvents}): on refresh ticks, drops the sessions of players
     * who logged out or died and re-sends the state to every valid session whose view changed.
     */
    public static void onServerTick(MinecraftServer server) {
        if (SESSIONS.isEmpty() || !MarketRefresh.due(server.getTickCount(), TradeConfig.MARKET_REFRESH_TICKS.get())) return;
        for (UUID id : new ArrayList<>(SESSIONS.keySet())) {
            Session s = SESSIONS.get(id);
            if (s == null) continue;
            if (s.player().isRemoved() || s.player().hasDisconnected()) {
                // logged out, or died (a respawn is a new player object)
                SESSIONS.remove(id, s);
                continue;
            }
            refresh(s);
        }
    }

    /** Pushes the state to every other valid session of {@code port} whose view changed (after a trade there). */
    private static void pushPort(ServerPlayer except, ResourceLocation port) {
        for (Session s : new ArrayList<>(SESSIONS.values())) {
            if (s.player() != except && s.port().equals(port)) refresh(s);
        }
    }

    private static void refresh(Session s) {
        ServerPlayer player = s.player();
        if (!canUse(player, s.port())) return;
        Optional<MarketView> view = view(player, s.port(), s.quantity());
        if (view.isEmpty() || !MarketRefresh.changed(s.lastSent(), view.get())) return;
        SESSIONS.computeIfPresent(player.getUUID(), (k, cur) -> cur.port().equals(s.port()) ? cur.withSent(cur.quantity(), view) : cur);
        deliver(player, new MarketPayloads.State(view, Optional.empty()));
    }

    public static void clear() {
        SESSIONS.clear();
        RECORDINGS.clear();
    }

    /** Whether the player may trade at {@code port} right now. */
    public static boolean canUse(ServerPlayer player, ResourceLocation port) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || !s.port().equals(port) || !s.dimension().equals(player.level().dimension())) return false;
        if (s.desk().isPresent()) {
            return HarborDeskService.sessionValid(player, s.desk().get(), port) && TradeService.market(player.server, port).isPresent();
        }
        double reach = TradeConfig.MARKET_REACH.get();
        return s.origin().distanceToSqr(player.position()) <= reach * reach && TradeService.market(player.server, port).isPresent();
    }

    static int clampQuantity(int quantity) {
        return Math.max(1, Math.min(quantity, TradeConfig.MAX_TRADE_QUANTITY.get()));
    }

    static boolean validQuantity(int quantity) {
        return quantity >= 1 && quantity <= TradeConfig.MAX_TRADE_QUANTITY.get();
    }

    /** The cargo container at {@code pos} if it is loaded and within reach of the player. */
    public static Optional<CargoContainerBlockEntity> container(ServerPlayer player, BlockPos pos) {
        Level level = player.level();
        if (!level.isLoaded(pos)) return Optional.empty();
        double reach = TradeConfig.CONTAINER_REACH.get();
        if (Vec3.atCenterOf(pos).distanceToSqr(player.getEyePosition()) > reach * reach) return Optional.empty();
        return level.getBlockEntity(pos) instanceof CargoContainerBlockEntity be ? Optional.of(be) : Optional.empty();
    }

    // --- Handlers -----------------------------------------------------------------------------------------------

    public static void handleRefresh(ServerPlayer player, MarketPayloads.Refresh p) {
        if (!canUse(player, p.port())) {
            refuse(player, TransactionResult.failed(TransactionResult.Status.NO_MARKET, p.port()));
            return;
        }
        send(player, p.port(), clampQuantity(p.quantity()), Optional.empty());
    }

    public static void handleTrade(ServerPlayer player, MarketPayloads.Trade p) {
        if (!canUse(player, p.port())) {
            refuse(player, TransactionResult.failed(TransactionResult.Status.NO_MARKET, p.good()));
            return;
        }
        TransactionResult r = trade(player, p);
        send(player, p.port(), clampQuantity(p.quantity()), Optional.of(r));
        if (r.done()) pushPort(player, p.port());
    }

    /** The client closed the market screen: the session ends. */
    public static void handleClose(ServerPlayer player, MarketPayloads.CloseMarket p) {
        close(player);
    }

    /** Runs a validated trade request (the session check is the caller's). */
    public static TransactionResult trade(ServerPlayer player, MarketPayloads.Trade p) {
        if (!validQuantity(p.quantity())) return TransactionResult.failed(TransactionResult.Status.INVALID_QUANTITY, p.good());
        Optional<MarketTransactions.Holder> holder = holder(player, p.container());
        if (holder.isEmpty()) return TransactionResult.failed(TransactionResult.Status.NO_CONTAINER, p.good());
        TransactionResult r = p.buy()
                ? MarketTransactions.buy(player, p.port(), p.good(), p.quantity(), holder.get())
                : MarketTransactions.sell(player, p.port(), p.good(), p.quantity(), effectivePlunder(p, player), holder.get());
        reportNoticed(player, p.port(), r);
        return r;
    }

    /**
     * Tells the {@link #onNoticedPlunder} listeners about {@code result} if it is a noticed plunder sale at
     * {@code port} (no-op otherwise). Every server-side sale path calls it: the protocol ({@link #trade}) and the
     * direct sell command.
     */
    public static void reportNoticed(ServerPlayer player, ResourceLocation port, TransactionResult result) {
        if (!result.noticedPlunder()) return;
        NoticedSale sale = new NoticedSale(player, port, result);
        for (Consumer<NoticedSale> l : NOTICED_LISTENERS) l.accept(sale);
    }

    /** Selling from a container uses the container's own plunder state, not the client's claim. */
    private static boolean effectivePlunder(MarketPayloads.Trade p, ServerPlayer player) {
        if (p.container().isEmpty()) return p.plundered();
        return container(player, p.container().get()).map(be -> com.richardsenger.piratesnships.trade.plunder.PlunderMark.isPlundered(be.heldKind()))
                .orElse(p.plundered());
    }

    public static void handleContract(ServerPlayer player, MarketPayloads.ContractAction p) {
        if (!canUse(player, p.port())) {
            refuse(player, TransactionResult.failed(TransactionResult.Status.NO_MARKET, p.port()));
            return;
        }
        TransactionResult r;
        Optional<DeliveryContract> c = TradeService.contract(player.server, p.contract());
        if (c.isEmpty()) {
            r = TransactionResult.failed(TransactionResult.Status.NO_CONTRACT, p.port());
        } else if (!p.deliver()) {
            // Only offers of this port can be accepted here
            r = c.get().origin().equals(p.port()) ? MarketTransactions.acceptContract(player, p.contract())
                    : TransactionResult.contract(TransactionResult.Status.CONTRACT_REFUSED, c.get().good(), 0, 0, DeliveryContract.Outcome.WRONG_PORT);
        } else {
            Optional<MarketTransactions.Holder> holder = holder(player, p.container());
            r = holder.isEmpty() ? TransactionResult.failed(TransactionResult.Status.NO_CONTAINER, c.get().good())
                    : MarketTransactions.deliverContract(player, p.port(), p.contract(), holder.get());
        }
        Session s = SESSIONS.get(player.getUUID());
        send(player, p.port(), clampQuantity(s == null ? 64 : s.quantity()), Optional.of(r));
        if (r.done()) pushPort(player, p.port());
    }

    private static Optional<MarketTransactions.Holder> holder(ServerPlayer player, Optional<BlockPos> pos) {
        if (pos.isEmpty()) return Optional.of(MarketTransactions.Holder.of(player));
        return container(player, pos.get()).map(MarketTransactions.Holder::of);
    }

    /**
     * A shipwright order from the Orders tab (SW1): refused without a valid desk session for the port (reach,
     * binding); everything else is checked by {@link ShipOrders#place}. Answers with the tab's new content and the
     * result.
     */
    public static void handleOrder(ServerPlayer player, OrderPayloads.PlaceOrder p) {
        if (!canUse(player, p.port())) {
            deliver(player, new OrderPayloads.Orders(Optional.empty(),
                    Optional.of(new OrderPayloads.OrderResult(false, ShipOrders.KEY_NO_SESSION, List.of()))));
            return;
        }
        OrderPayloads.OrderResult r = ShipOrders.place(player, p.port(), p.template());
        deliver(player, new OrderPayloads.Orders(ShipOrders.view(player, p.port()), Optional.of(r)));
        // the doubloons changed: the market view follows
        Session s = SESSIONS.get(player.getUUID());
        send(player, p.port(), clampQuantity(s == null ? 1 : s.quantity()), Optional.empty());
    }

    // --- State --------------------------------------------------------------------------------------------------

    private static void refuse(ServerPlayer player, TransactionResult result) {
        deliver(player, new MarketPayloads.State(Optional.empty(), Optional.of(result)));
    }

    private static void send(ServerPlayer player, ResourceLocation port, int quantity, Optional<TransactionResult> result) {
        Optional<MarketView> view = view(player, port, quantity);
        SESSIONS.computeIfPresent(player.getUUID(), (k, s) -> s.port().equals(port) ? s.withSent(quantity, view) : s);
        deliver(player, new MarketPayloads.State(view, result));
    }

    /** Sends a market payload to the player, or records it for a GameTest ({@link #record}). */
    public static void deliver(ServerPlayer player, CustomPacketPayload payload) {
        List<CustomPacketPayload> rec = RECORDINGS.get(player.getUUID());
        if (rec != null) {
            // A recorded (GameTest) player has a mock connection without negotiated channels: record instead of sending
            rec.add(payload);
            return;
        }
        Services.NETWORK.sendToPlayer(player, payload);
    }

    // --- Test support -------------------------------------------------------------------------------------------

    /**
     * GameTests: from now on every market payload for {@code player} goes into the returned list instead of the
     * network (the mock player's connection has no negotiated channels). Call {@link #stopRecording} at the end.
     */
    public static List<CustomPacketPayload> record(UUID player) {
        return RECORDINGS.computeIfAbsent(player, id -> java.util.Collections.synchronizedList(new ArrayList<>()));
    }

    public static void stopRecording(UUID player) {
        RECORDINGS.remove(player);
    }

    /** The state of {@code port} for {@code player} with quotes for {@code quantity} units. */
    public static Optional<MarketView> view(ServerPlayer player, ResourceLocation port, int quantity) {
        MinecraftServer server = player.server;
        Optional<Market> market = TradeService.market(server, port);
        if (market.isEmpty()) return Optional.empty();
        List<MarketView.GoodLine> lines = new ArrayList<>();
        var tradeable = TradeService.goods(false).tradeable();
        List<ResourceLocation> ids = new ArrayList<>(market.get().profile().roles().keySet());
        ids.sort(Comparator.comparing(ResourceLocation::toString));
        for (ResourceLocation id : ids) {
            Optional<TradeGood> def = tradeable.get(id);
            if (def.isEmpty()) continue;
            lines.add(new MarketView.GoodLine(id, def.get().item(), market.get().profile().role(id),
                    MarketView.Price.of(TradeService.quote(server, port, id, Market.Side.BUY, quantity)),
                    MarketView.Price.of(TradeService.quote(server, port, id, Market.Side.SELL, quantity))));
        }
        TradeService.update(server);
        List<DeliveryContract> offers = new ArrayList<>();
        for (DeliveryContract c : TradeData.get(server).contracts()) {
            if (c.origin().equals(port) && c.state() == DeliveryContract.State.OFFERED) offers.add(c);
        }
        offers.sort(Comparator.comparing(c -> c.id().toString()));
        return Optional.of(new MarketView(port, market.get().profile().kind(), quantity, Wallet.count(player), lines,
                offers, TradeService.contractsOf(server, player.getUUID())));
    }
}
