package com.richardsenger.piratesnships.trade.desk;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.trade.TradeConfig;
import com.richardsenger.piratesnships.trade.TradeData;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.contract.ContractGenerator;
import com.richardsenger.piratesnships.trade.market.Market;
import com.richardsenger.piratesnships.trade.net.MarketBackend;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Server side of the harbor master's desk: binding a desk to a port and opening the port's market for a player.
 *
 * <p>Binding: there is no port registry with positions yet (ports are market ids in {@link TradeData}), so a placed
 * desk asks the {@link #setPortLocator port locator}, which finds nothing until world generation installs one;
 * operators bind with {@code /pirates trade desk bind <port>}. Opening starts a desk session in
 * {@link MarketBackend#openDesk}: every later request must come from within {@code harbor_desks.desk_reach} of a
 * desk that is still bound to that port.
 */
public final class HarborDeskService {

    public static final String KEY = "message." + Constants.MOD_ID + ".harbor_desk.";

    /**
     * Contract destinations get this distance and risk when the {@link #setRouteLocator route locator} knows no route
     * (no world simulation installed, or a market without a registered port, as the debug command makes).
     */
    public static final double DEFAULT_DISTANCE = 1000.0;
    public static final double DEFAULT_RISK = 0.5;

    /** Distance (blocks) and risk (0..1) of a delivery route between two ports. */
    public record ContractRoute(double distance, double risk) {
    }

    /** Finds the route between two ports; installed by the world simulation's sea lanes (WS2). */
    @FunctionalInterface
    public interface RouteLocator {
        Optional<ContractRoute> route(MinecraftServer server, ResourceLocation origin, ResourceLocation destination);
    }

    private static volatile RouteLocator routeLocator = (server, origin, destination) -> Optional.empty();

    /** What using a desk did, with the action bar message (null = none). */
    public enum Use {
        OPENED(null), DISABLED(null), UNBOUND(KEY + "unbound"), NO_MARKET(KEY + "no_market"),
        /** PRT1a: {@code harbor_desks.direct_use} is off and the desk was used directly, not through its harbor master. */
        TALK_TO_MASTER(KEY + "talk_to_master");

        private final String message;

        Use(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    private static volatile BiFunction<ServerLevel, BlockPos, Optional<ResourceLocation>> portLocator = (level, pos) -> Optional.empty();

    /** PRT1a: set while {@link #useViaHarborMaster} runs {@link #use} (server thread), so {@code direct_use} lets it pass. */
    private static final ThreadLocal<Boolean> VIA_HARBOR_MASTER = ThreadLocal.withInitial(() -> false);

    private HarborDeskService() {
    }

    /** The world generator (later) installs the lookup "which port's area contains this position". */
    public static void setPortLocator(BiFunction<ServerLevel, BlockPos, Optional<ResourceLocation>> locator) {
        portLocator = locator;
    }

    /** The world simulation (WS2) installs the route lookup: lane length and pirate risk between two ports. */
    public static void setRouteLocator(RouteLocator locator) {
        routeLocator = locator;
    }

    /** Binds a freshly placed desk to the port whose area it is in, if the locator knows one. */
    public static Optional<ResourceLocation> autoBind(ServerLevel level, BlockPos pos) {
        Optional<ResourceLocation> port = portLocator.apply(level, pos);
        port.ifPresent(p -> bind(level, pos, Optional.of(p)));
        return port;
    }

    public static Optional<HarborDeskBlockEntity> desk(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return Optional.empty();
        return level.getBlockEntity(pos) instanceof HarborDeskBlockEntity be ? Optional.of(be) : Optional.empty();
    }

    /** The port a loaded desk at {@code pos} is bound to. */
    public static Optional<ResourceLocation> boundPort(Level level, BlockPos pos) {
        return desk(level, pos).flatMap(HarborDeskBlockEntity::port);
    }

    /** Binds (or with empty unbinds) the desk at {@code pos}; false if there is no desk. */
    public static boolean bind(Level level, BlockPos pos, Optional<ResourceLocation> port) {
        Optional<HarborDeskBlockEntity> be = desk(level, pos);
        be.ifPresent(d -> d.setPort(port));
        return be.isPresent();
    }

    /** Whether {@code player} is within {@code desk_reach} of the desk's center. */
    public static boolean inReach(ServerPlayer player, BlockPos desk) {
        double reach = TradeConfig.DESK_REACH.get();
        return Vec3.atCenterOf(desk).distanceToSqr(player.position()) <= reach * reach;
    }

    /**
     * Whether a desk session for {@code port} is still valid: desks enabled, the desk still stands in the player's
     * level, is bound to {@code port}, and the player is within reach.
     */
    public static boolean sessionValid(ServerPlayer player, BlockPos desk, ResourceLocation port) {
        return TradeConfig.DESKS_ENABLED.get() && boundPort(player.level(), desk).filter(port::equals).isPresent()
                && inReach(player, desk);
    }

    /** A player uses the desk at {@code pos}: opens the bound port's market screen when everything checks out. */
    public static Use use(ServerPlayer player, BlockPos pos) {
        if (!TradeConfig.DESKS_ENABLED.get()) return Use.DISABLED;
        if (!TradeConfig.DESK_DIRECT_USE.get() && !VIA_HARBOR_MASTER.get()) return Use.TALK_TO_MASTER;
        Optional<ResourceLocation> port = boundPort(player.level(), pos);
        if (port.isEmpty()) return Use.UNBOUND;
        MinecraftServer server = player.server;
        if (TradeService.market(server, port.get()).isEmpty()) return Use.NO_MARKET;
        TradeService.offers(server, port.get(), destinations(server, port.get()));
        return MarketBackend.openDesk(player, port.get(), pos.immutable()) ? Use.OPENED : Use.NO_MARKET;
    }

    /**
     * PRT1a: {@code player} talked to the harbor master of the desk at {@code pos} ({@code mob.harbor}): the same as
     * {@link #use}, every check included, except that {@code harbor_desks.direct_use} does not refuse it.
     */
    public static Use useViaHarborMaster(ServerPlayer player, BlockPos pos) {
        VIA_HARBOR_MASTER.set(true);
        try {
            return use(player, pos);
        } finally {
            VIA_HARBOR_MASTER.set(false);
        }
    }

    /**
     * Every other known port as a contract destination, with the route locator's distance and risk (the sea lane's
     * length when it is cached, else the straight distance; {@link #DEFAULT_DISTANCE}/{@link #DEFAULT_RISK} without one).
     */
    public static List<ContractGenerator.Destination> destinations(MinecraftServer server, ResourceLocation origin) {
        List<ContractGenerator.Destination> out = new ArrayList<>();
        Map<ResourceLocation, Market> markets = TradeData.get(server).snapshot().markets();
        List<ResourceLocation> ids = new ArrayList<>(markets.keySet());
        ids.sort(Comparator.comparing(ResourceLocation::toString));
        for (ResourceLocation id : ids) {
            if (id.equals(origin)) continue;
            ContractRoute route = routeLocator.route(server, origin, id).orElse(new ContractRoute(DEFAULT_DISTANCE, DEFAULT_RISK));
            out.add(new ContractGenerator.Destination(id, markets.get(id).profile(), route.distance(), route.risk()));
        }
        return out;
    }
}
