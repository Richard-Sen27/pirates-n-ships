package com.richardsenger.piratesnships.trade.fees;

import com.richardsenger.piratesnships.crew.upkeep.ShipCoins;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntime;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.trade.TradeConfig;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.client.MarketLines;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.port.Berth;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortIndex;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Harbor dues charged in game (PRT1b, design.md §10.3 "Port fees", §7.5). Every {@code check_interval_ticks} each
 * loaded owned ship whose centre lies in a port's box ({@link PortIndex#containing}) and that is docked
 * ({@link DockingRules#docked}: anchored, or at rest beside a berth) is charged {@link TradeService#dockingFee} once per
 * {@code fee_period_days} per ship and port ({@link PortVisitData}). The fee is the captain's (the ship's owner): with
 * its rank waiver (CAR2) and reputation waiver (REP1) inside {@code dockingFee}, which needs the owner online, so ships
 * of offline owners are not charged until their owner is back. It comes from the owner's wallet, else from the coins
 * aboard ({@link ShipCoins}, {@code charge_ship_chest}), else it is owed at that port, and the port's harbor desk
 * refuses the owner ({@link #atDesk}) until they pay with doubloons in hand. Only ports where
 * {@code dockingFee} can be above 0 (navy outposts with fees on) are looked at; villages and islands never message.
 */
public final class DockingFees {

    /** One charge, as the owner was told. */
    public record Charge(UUID ship, ResourceLocation port, UUID owner, int fee, DockingRules.Payer payer) {
    }

    /**
     * Names who collects the dues at a port in the owner's message: PRT1a's harbor master installs his name here; the
     * default (empty) names the port.
     */
    private static volatile BiFunction<ServerLevel, Port, Optional<Component>> collector = (level, port) -> Optional.empty();

    private DockingFees() {
    }

    /** PRT1a: the harbor master's name for the owner's message ("Harbor dues at <name>"). */
    public static void setCollector(BiFunction<ServerLevel, Port, Optional<Component>> c) {
        collector = c;
    }

    /** Level tick end: every {@code check_interval_ticks}, {@link #check} with the online players. */
    public static void onLevelTick(ServerLevel level) {
        if (!TradeConfig.PORT_FEES.get()) return;
        int interval = Math.max(1, TradeConfig.FEE_CHECK_INTERVAL.get());
        if (level.getGameTime() % interval != 0) return;
        MinecraftServer server = level.getServer();
        check(level, id -> server.getPlayerList().getPlayer(id));
    }

    /**
     * Looks at every loaded ship of {@code level} once and charges the docked ones that are due.
     *
     * @param online the owner when online ({@code null} otherwise); the GameTests pass their offline test players
     * @return the charges made (also the waived ones)
     */
    public static List<Charge> check(ServerLevel level, Function<UUID, ServerPlayer> online) {
        List<Charge> out = new ArrayList<>();
        if (!TradeConfig.PORT_FEES.get()) return out;
        MinecraftServer server = level.getServer();
        PortIndex ports = PortRegistry.get(server).index();
        if (ports.size() == 0) return out;
        ShipRegistry ships = ShipRegistry.get(server);
        PortVisitData visits = PortVisitData.get(server);
        DockingRules.Params p = TradeConfig.dockingParams();
        long day = TradeService.day(server);
        for (ShipBody ship : List.copyOf(SableShips.all(level))) {
            if (ship.isRemoved()) continue;
            Optional<UUID> owner = ships.find(ship.id()).flatMap(ShipData::owner);
            if (owner.isEmpty()) continue;
            AABB bounds = ship.worldBounds();
            Optional<Port> port = ports.containing(level.dimension(), BlockPos.containing(bounds.getCenter()));
            if (port.isEmpty() || !charges(port.get().kind())) continue;
            ServerPlayer captain = online.apply(owner.get());
            if (captain == null) continue;
            Long last = visits.get(ship.id(), port.get().id()).map(PortVisitData.Visit::day).orElse(null);
            if (!DockingRules.due(last, day, p)) continue;
            if (!docked(ship, port.get(), bounds, p)) continue;
            out.add(charge(level, ship, port.get(), captain, day, visits, p));
        }
        return out;
    }

    /** Whether ports of {@code kind} can charge at all (the fee before any waiver is above 0). */
    public static boolean charges(PortKind kind) {
        return TradeService.dockingFee(kind, Integer.MIN_VALUE) > 0;
    }

    /** {@link DockingRules#docked} for a live ship. */
    public static boolean docked(ShipBody ship, Port port, AABB bounds, DockingRules.Params p) {
        SailingRuntime rt = SailingRuntimes.get(ship.level(), ship.id());
        boolean anchored = rt != null && rt.isAnchored();
        double berth = Double.POSITIVE_INFINITY;
        for (Berth b : port.berths()) {
            Vec3 c = Vec3.atCenterOf(b.pos());
            berth = Math.min(berth, DockingRules.horizontalDistanceToBox(c.x, c.z, bounds.minX, bounds.minZ, bounds.maxX, bounds.maxZ));
        }
        return DockingRules.docked(anchored, berth, ship.linearVelocity().length(), p);
    }

    private static Charge charge(ServerLevel level, ShipBody ship, Port port, ServerPlayer captain, long day,
                                 PortVisitData visits, DockingRules.Params p) {
        int fee = TradeService.dockingFee(port.kind(), captain);
        long shipCoins = fee > 0 && p.chargeShipChest() && Wallet.count(captain) < fee ? ShipCoins.total(level, ship) : 0;
        DockingRules.Payer payer = DockingRules.payer(fee, Wallet.count(captain), shipCoins, p);
        int paid = 0;
        switch (payer) {
            case WALLET -> paid = Wallet.take(captain, fee) ? fee : 0;
            case SHIP -> paid = ShipCoins.take(level, ship, fee) ? fee : 0;
            default -> { }
        }
        if ((payer == DockingRules.Payer.WALLET || payer == DockingRules.Payer.SHIP) && paid == 0) {
            payer = DockingRules.Payer.OWED; // the coins went missing between the count and the take
        }
        int owedBefore = visits.get(ship.id(), port.id()).map(PortVisitData.Visit::owed).orElse(0);
        int owed = payer == DockingRules.Payer.OWED ? owedBefore + fee : owedBefore;
        visits.put(new PortVisitData.Visit(ship.id(), port.id(), captain.getUUID(), day, paid, owed));
        Component at = collector.apply(level, port).orElseGet(() -> Component.literal(MarketLines.portName(port.id())));
        String name = ShipRegistry.get(level.getServer()).find(ship.id()).map(ShipData::name).orElse("");
        captain.sendSystemMessage(FeeText.charged(payer, at, FeeText.shipName(name), fee));
        return new Charge(ship.id(), port.id(), captain.getUUID(), fee, payer);
    }

    // ------------------------------------------------------------------ the desk

    /**
     * The harbor desk's dues check when {@code player} uses a desk of {@code port} ({@code HarborDeskService.use}, also
     * reached through PRT1a's harbor master): nothing owed → open. Owed and doubloons in hand with enough in the wallet →
     * paid, cleared, open. Otherwise the player is told what is owed and, with {@code refuse_desk_when_owed}, refused.
     *
     * @return true when the desk must refuse
     */
    public static boolean atDesk(ServerPlayer player, ResourceLocation port) {
        PortVisitData visits = PortVisitData.get(player.server);
        int owed = visits.owed(player.getUUID(), port);
        if (owed <= 0) return false;
        if (coinsInHand(player) && settle(player, port) > 0) return false;
        player.displayClientMessage(FeeText.deskOwed(owed), true);
        return TradeConfig.dockingParams().refuseDeskWhenOwed();
    }

    /**
     * Pays everything {@code player} owes at {@code port} from their wallet when it holds that much (the hook for paying
     * the harbor master in person, PRT1a). Returns the doubloons paid, 0 when nothing was owed or the wallet is short.
     */
    public static int settle(ServerPlayer player, ResourceLocation port) {
        PortVisitData visits = PortVisitData.get(player.server);
        int owed = visits.owed(player.getUUID(), port);
        if (owed <= 0 || !Wallet.take(player, owed)) return 0;
        visits.clearOwed(player.getUUID(), port);
        player.displayClientMessage(FeeText.deskSettled(owed), false);
        return owed;
    }

    private static boolean coinsInHand(ServerPlayer player) {
        return Wallet.isCoin(player.getItemInHand(InteractionHand.MAIN_HAND)) || Wallet.isCoin(player.getItemInHand(InteractionHand.OFF_HAND));
    }
}
