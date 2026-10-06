package com.richardsenger.piratesnships.trade.net;

import com.richardsenger.piratesnships.platform.Services;
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
import java.util.function.Consumer;

/**
 * Server side of the market protocol. A market is opened for a player by server code ({@link #open}: the harbor
 * master later, the debug command now), which starts a session at the player's position. Client requests are only
 * honoured for the session's port, in the same dimension and within {@code market_reach} of where it was opened;
 * quantities must be 1..{@code max_trade_quantity}; a container must be a loaded cargo container within
 * {@code container_reach} of the player. Every request is answered with a {@link MarketPayloads.State}.
 *
 * <p>{@link #onNoticedPlunder} is the hook for the law integration (this package never calls the law module).
 */
public final class MarketBackend {

    record Session(ResourceLocation port, ResourceKey<Level> dimension, Vec3 origin) {
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static volatile Consumer<NoticedSale> noticedListener = s -> { };

    /** A plunder sale a port noticed. */
    public record NoticedSale(ServerPlayer player, ResourceLocation port, TransactionResult result) {
    }

    private MarketBackend() {
    }

    /** The law integration registers here to report noticed plunder sales made through the protocol. */
    public static void onNoticedPlunder(Consumer<NoticedSale> listener) {
        noticedListener = listener;
    }

    public static void registerPayloads() {
        Services.NETWORK.registerToServer(MarketPayloads.Refresh.TYPE, MarketPayloads.Refresh.CODEC,
                (p, player) -> handleRefresh((ServerPlayer) player, p));
        Services.NETWORK.registerToServer(MarketPayloads.Trade.TYPE, MarketPayloads.Trade.CODEC,
                (p, player) -> handleTrade((ServerPlayer) player, p));
        Services.NETWORK.registerToServer(MarketPayloads.ContractAction.TYPE, MarketPayloads.ContractAction.CODEC,
                (p, player) -> handleContract((ServerPlayer) player, p));
        Services.NETWORK.registerToClient(MarketPayloads.State.TYPE, MarketPayloads.State.CODEC,
                (p, player) -> com.richardsenger.piratesnships.trade.client.ClientMarketState.accept(p));
    }

    // --- Sessions -----------------------------------------------------------------------------------------------

    /** Opens the port's market (it must exist) for the player and sends the state; false if there is no market. */
    public static boolean open(ServerPlayer player, ResourceLocation port, int quantity) {
        if (TradeService.market(player.server, port).isEmpty()) return false;
        SESSIONS.put(player.getUUID(), new Session(port, player.level().dimension(), player.position()));
        send(player, port, quantity, Optional.empty());
        return true;
    }

    public static void close(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }

    public static void clear() {
        SESSIONS.clear();
    }

    /** Whether the player may trade at {@code port} right now. */
    public static boolean canUse(ServerPlayer player, ResourceLocation port) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || !s.port().equals(port) || !s.dimension().equals(player.level().dimension())) return false;
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

    static void handleRefresh(ServerPlayer player, MarketPayloads.Refresh p) {
        if (!canUse(player, p.port())) {
            refuse(player, TransactionResult.failed(TransactionResult.Status.NO_MARKET, p.port()));
            return;
        }
        send(player, p.port(), clampQuantity(p.quantity()), Optional.empty());
    }

    static void handleTrade(ServerPlayer player, MarketPayloads.Trade p) {
        if (!canUse(player, p.port())) {
            refuse(player, TransactionResult.failed(TransactionResult.Status.NO_MARKET, p.good()));
            return;
        }
        TransactionResult r = trade(player, p);
        send(player, p.port(), clampQuantity(p.quantity()), Optional.of(r));
    }

    /** Runs a validated trade request (the session check is the caller's). */
    public static TransactionResult trade(ServerPlayer player, MarketPayloads.Trade p) {
        if (!validQuantity(p.quantity())) return TransactionResult.failed(TransactionResult.Status.INVALID_QUANTITY, p.good());
        Optional<MarketTransactions.Holder> holder = holder(player, p.container());
        if (holder.isEmpty()) return TransactionResult.failed(TransactionResult.Status.NO_CONTAINER, p.good());
        TransactionResult r = p.buy()
                ? MarketTransactions.buy(player, p.port(), p.good(), p.quantity(), holder.get())
                : MarketTransactions.sell(player, p.port(), p.good(), p.quantity(), effectivePlunder(p, player), holder.get());
        if (r.noticedPlunder()) noticedListener.accept(new NoticedSale(player, p.port(), r));
        return r;
    }

    /** Selling from a container uses the container's own plunder state, not the client's claim. */
    private static boolean effectivePlunder(MarketPayloads.Trade p, ServerPlayer player) {
        if (p.container().isEmpty()) return p.plundered();
        return container(player, p.container().get()).map(be -> com.richardsenger.piratesnships.trade.plunder.PlunderMark.isPlundered(be.heldKind()))
                .orElse(p.plundered());
    }

    static void handleContract(ServerPlayer player, MarketPayloads.ContractAction p) {
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
        send(player, p.port(), clampQuantity(64), Optional.of(r));
    }

    private static Optional<MarketTransactions.Holder> holder(ServerPlayer player, Optional<BlockPos> pos) {
        if (pos.isEmpty()) return Optional.of(MarketTransactions.Holder.of(player));
        return container(player, pos.get()).map(MarketTransactions.Holder::of);
    }

    // --- State --------------------------------------------------------------------------------------------------

    private static void refuse(ServerPlayer player, TransactionResult result) {
        Services.NETWORK.sendToPlayer(player, new MarketPayloads.State(Optional.empty(), Optional.of(result)));
    }

    private static void send(ServerPlayer player, ResourceLocation port, int quantity, Optional<TransactionResult> result) {
        Services.NETWORK.sendToPlayer(player, new MarketPayloads.State(view(player, port, quantity), result));
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
