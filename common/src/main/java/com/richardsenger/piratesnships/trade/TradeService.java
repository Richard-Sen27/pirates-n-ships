package com.richardsenger.piratesnships.trade;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.data.Definitions;
import com.richardsenger.piratesnships.trade.cargo.CargoWeight;
import com.richardsenger.piratesnships.trade.contract.ContractGenerator;
import com.richardsenger.piratesnships.trade.contract.ContractParams;
import com.richardsenger.piratesnships.trade.contract.DeliveryContract;
import com.richardsenger.piratesnships.trade.good.TradeGood;
import com.richardsenger.piratesnships.trade.good.TradeGoodIndex;
import com.richardsenger.piratesnships.trade.good.TradeGoods;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.Market;
import com.richardsenger.piratesnships.trade.market.MarketParams;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.trade.market.PortProfile;
import com.richardsenger.piratesnships.trade.market.ProfileDeriver;
import com.richardsenger.piratesnships.trade.plunder.PlunderRules;
import com.richardsenger.piratesnships.trade.plunder.PortFees;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The trade API for the port integration and the market screen (server side). Moves no items and no coins: every
 * call returns the doubloons and units involved, and the caller moves them only when the result says so.
 *
 * <pre>{@code
 * // port integration, once per port (lazily; the profile is only derived the first time)
 * TradeService.openMarket(server, portId, () -> TradeService.deriveProfile(server, PortKind.SEAFARER_VILLAGE, Climate.TROPICAL, portSeed));
 * // market screen
 * Market.Quote q = TradeService.quote(server, portId, TradeGoods.SUGAR, Market.Side.BUY, 64);
 * Market.Quote bought = TradeService.buy(server, portId, TradeGoods.SUGAR, 64);
 * if (bought.ok()) { takeDoubloons(player, bought.total()); giveItems(player, sugar, 64); }
 * TradeService.Sale sale = TradeService.sell(server, portId, goodId, count, PlunderMark.isPlundered(stack), player.getRandom());
 * if (sale.unitsTaken()) { removeItems(player, count); giveDoubloons(player, sale.payout()); } // payout 0 if confiscated
 * if (sale.verdict().noticed()) LawService.reportCrime(player, ..., null);  // the integration picks the crime type
 * // harbor master
 * List<DeliveryContract> offers = TradeService.offers(server, portId, destinations);
 * TradeService.accept(server, contractId, player.getUUID());           // take depositDue
 * TradeService.deliver(server, contractId, player.getUUID(), portId, countInHold); // remove consumed, pay payout
 * }</pre>
 *
 * Time: market recovery runs on overworld game time and is applied lazily on every read, so no tick is needed.
 * Contract days are overworld day time / 24000 (the day number players see).
 */
public final class TradeService {

    /** Finished contracts are kept this many days after their deadline, then dropped from the save. */
    public static final long KEEP_FINISHED_DAYS = 7;

    private static volatile TradeGoodIndex serverIndex;
    private static volatile TradeGoodIndex clientIndex;

    private TradeService() {
    }

    // --- Goods ----------------------------------------------------------------------------------------------

    /** The item → good index for the level's side, rebuilt when the definitions were reloaded. */
    public static TradeGoodIndex goods(Level level) {
        return goods(level.isClientSide());
    }

    public static TradeGoodIndex goods(boolean clientSide) {
        Definitions<TradeGood> defs = TradeGoods.TYPE.of(clientSide);
        TradeGoodIndex cached = clientSide ? clientIndex : serverIndex;
        if (cached != null && cached.source() == defs) return cached;
        TradeGoodIndex built = TradeGoodIndex.build(defs, msg -> Constants.LOG.warn("{}", msg));
        if (clientSide) clientIndex = built;
        else serverIndex = built;
        return built;
    }

    public static Optional<TradeGoodIndex.Entry> goodOf(Level level, ItemStack stack) {
        return goods(level).of(stack);
    }

    // --- Time -----------------------------------------------------------------------------------------------

    public static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    public static long day(MinecraftServer server) {
        return Math.floorDiv(server.overworld().getDayTime(), MarketParams.TICKS_PER_DAY);
    }

    // --- Markets --------------------------------------------------------------------------------------------

    /** A profile from the tradeable goods (items present) of the current datapack. */
    public static PortProfile deriveProfile(MinecraftServer server, PortKind kind, Climate climate, long seed) {
        return ProfileDeriver.derive(kind, climate, seed, goods(false).tradeable());
    }

    /** The port's market, created from {@code profile} the first time, recovered to now and extended by new goods. */
    public static Market openMarket(MinecraftServer server, ResourceLocation port, Supplier<PortProfile> profile) {
        TradeData data = TradeData.get(server);
        Market market = data.market(port).orElseGet(() -> Market.fresh(profile.get(), now(server)));
        Market current = market.withProfile(market.profile().extendedWith(goods(false).tradeable()))
                .recoverTo(now(server), TradeConfig.marketParams());
        data.setMarket(port, current);
        return current;
    }

    /** The port's market recovered to now, if it was opened before. */
    public static Optional<Market> market(MinecraftServer server, ResourceLocation port) {
        return TradeData.get(server).market(port).map(m -> m.recoverTo(now(server), TradeConfig.marketParams()));
    }

    public static Market.Quote quote(MinecraftServer server, ResourceLocation port, ResourceLocation good, Market.Side side, int quantity) {
        Optional<Market> market = market(server, port);
        Optional<TradeGood> def = tradeable(good);
        if (market.isEmpty() || def.isEmpty()) return new Market.Quote(good, side, quantity, 0, 0, Market.Outcome.NOT_TRADED);
        return market.get().quote(good, def.get(), side, quantity, TradeConfig.marketParams());
    }

    /** The customer buys; on {@code ok()} the caller takes {@code total} doubloons and hands over the units. */
    public static Market.Quote buy(MinecraftServer server, ResourceLocation port, ResourceLocation good, int quantity) {
        return trade(server, port, good, Market.Side.BUY, quantity);
    }

    /** A sale with its plunder verdict. The market moved only if {@code verdict.sold()} and {@code quote.ok()}. */
    public record Sale(Market.Quote quote, PlunderRules.Verdict verdict) {
        /** Doubloons to pay the seller. */
        public long payout() {
            return quote.ok() ? verdict.payout() : 0;
        }

        /** Whether the units leave the seller (sold or confiscated). */
        public boolean unitsTaken() {
            return quote.ok();
        }
    }

    /**
     * LAW3 (§13.4): whether {@code port} refuses plunder-marked goods outright. Only the pirate fence buys them; every
     * other port refuses while plunder marks matter ({@code cargo_trade.plunder.enabled}). False without a market.
     */
    public static boolean refusesPlunder(MinecraftServer server, ResourceLocation port) {
        return market(server, port).map(m -> refusesPlunder(m.profile().kind(), TradeConfig.PLUNDER_ENABLED.get())).orElse(false);
    }

    /** Pure: the refusal rule of {@link #refusesPlunder(MinecraftServer, ResourceLocation)}. */
    public static boolean refusesPlunder(PortKind kind, boolean marksMatter) {
        return marksMatter && kind != PortKind.PIRATE_ISLAND;
    }

    /** The customer sells; plundered goods go through {@link PlunderRules} with the port kind and {@code random}. */
    public static Sale sell(MinecraftServer server, ResourceLocation port, ResourceLocation good, int quantity,
                            boolean plundered, RandomSource random) {
        Market.Quote q = quote(server, port, good, Market.Side.SELL, quantity);
        if (!q.ok()) return new Sale(q, new PlunderRules.Verdict(PlunderRules.Outcome.NORMAL, 0, false, false));
        PortKind kind = TradeData.get(server).market(port).orElseThrow().profile().kind();
        PlunderRules.Verdict verdict = PlunderRules.judge(kind, plundered, quantity, q.total(), random, TradeConfig.plunderParams());
        if (verdict.sold()) trade(server, port, good, Market.Side.SELL, quantity);
        return new Sale(q, verdict);
    }

    /**
     * An NPC trade (world simulation, WS2: a convoy buying its cargo at the origin or selling it at the destination).
     * Trades up to {@code quantity} units, as many as the market allows right now ({@link Market#available}); no
     * plunder verdict and no coins. The returned quote is for the units actually traded ({@code ok()} with
     * {@code quantity() ≤} the request), or not ok when nothing could be traded.
     */
    public static Market.Quote npcTrade(MinecraftServer server, ResourceLocation port, ResourceLocation good, Market.Side side, int quantity) {
        Market.Quote q = quote(server, port, good, side, Math.max(1, quantity));
        if (q.outcome() == Market.Outcome.NOT_TRADED || quantity < 1) return q;
        int units = Math.min(quantity, q.available());
        if (units < 1) return q;
        return trade(server, port, good, side, units);
    }

    private static Market.Quote trade(MinecraftServer server, ResourceLocation port, ResourceLocation good, Market.Side side, int quantity) {
        TradeData data = TradeData.get(server);
        Optional<Market> market = data.market(port);
        Optional<TradeGood> def = tradeable(good);
        if (market.isEmpty() || def.isEmpty()) return new Market.Quote(good, side, quantity, 0, 0, Market.Outcome.NOT_TRADED);
        Market.Trade t = market.get().trade(good, def.get(), side, quantity, now(server), TradeConfig.marketParams());
        data.setMarket(port, t.market());
        return t.quote();
    }

    private static Optional<TradeGood> tradeable(ResourceLocation good) {
        return goods(false).tradeable().get(good);
    }

    // --- Contracts ------------------------------------------------------------------------------------------

    /**
     * Today's open offers of a port. Generates the day's offers on the first call of a day (from the world seed, the
     * port's market profile and {@code destinations}), expires old offers and fails overdue contracts first.
     * The port's market must be open.
     */
    public static List<DeliveryContract> offers(MinecraftServer server, ResourceLocation port, List<ContractGenerator.Destination> destinations) {
        TradeData data = TradeData.get(server);
        long day = day(server);
        update(server);
        Optional<Market> market = data.market(port);
        ContractParams cp = TradeConfig.contractParams();
        if (market.isPresent() && data.lastOfferDay(port) < day) {
            data.setLastOfferDay(port, day);
            for (DeliveryContract c : ContractGenerator.generate(port, market.get().profile(), destinations, goods(false).tradeable(),
                    day, server.overworld().getSeed(), cp, TradeConfig.marketParams())) {
                if (data.contract(c.id()).isEmpty()) data.putContract(c);
            }
        }
        List<DeliveryContract> out = new ArrayList<>();
        for (DeliveryContract c : data.contracts()) {
            if (c.origin().equals(port) && c.state() == DeliveryContract.State.OFFERED) out.add(c);
        }
        out.sort(Comparator.comparing(c -> c.id().toString()));
        return out;
    }

    /** Applies deadlines and offer expiry to every contract and drops long-finished ones. */
    public static void update(MinecraftServer server) {
        TradeData data = TradeData.get(server);
        long day = day(server);
        for (DeliveryContract c : data.contracts()) {
            DeliveryContract.ContractResult r = c.update(day);
            if (r.changed()) data.putContract(r.contract());
            DeliveryContract now = r.contract();
            if (now.state().finished() && day > Math.max(now.deadlineDay(), now.offerExpiresDay()) + KEEP_FINISHED_DAYS) {
                data.removeContract(now.id());
            }
        }
    }

    public static Optional<DeliveryContract> contract(MinecraftServer server, UUID id) {
        update(server);
        return TradeData.get(server).contract(id);
    }

    /** Accepted contracts of a holder, by deadline. */
    public static List<DeliveryContract> contractsOf(MinecraftServer server, UUID holder) {
        update(server);
        List<DeliveryContract> out = new ArrayList<>();
        for (DeliveryContract c : TradeData.get(server).contracts()) {
            if (c.state() == DeliveryContract.State.ACCEPTED && c.holder().filter(holder::equals).isPresent()) out.add(c);
        }
        out.sort(Comparator.comparingLong(DeliveryContract::deadlineDay));
        return out;
    }

    /** Accepts an offer; on {@code ACCEPTED} the caller takes {@code depositDue} doubloons. Empty = no such contract. */
    public static Optional<DeliveryContract.ContractResult> accept(MinecraftServer server, UUID contract, UUID holder) {
        Optional<DeliveryContract> c = contract(server, contract);
        if (c.isEmpty()) return Optional.empty();
        if (contractsOf(server, holder).size() >= TradeConfig.contractParams().maxActivePerPlayer()) {
            return Optional.of(new DeliveryContract.ContractResult(c.get(), DeliveryContract.Outcome.TOO_MANY, 0, 0, 0));
        }
        return Optional.of(store(server, c.get().accept(holder, day(server))));
    }

    /** Delivers; on {@code DELIVERED} the caller removes {@code consumed} units and pays {@code payout}. */
    public static Optional<DeliveryContract.ContractResult> deliver(MinecraftServer server, UUID contract, UUID holder,
                                                                    ResourceLocation port, int available) {
        return contract(server, contract).map(c -> store(server, c.deliver(holder, port, available, day(server))));
    }

    public static Optional<DeliveryContract.ContractResult> abandon(MinecraftServer server, UUID contract, UUID holder) {
        return contract(server, contract).map(c -> store(server, c.abandon(holder)));
    }

    private static DeliveryContract.ContractResult store(MinecraftServer server, DeliveryContract.ContractResult r) {
        if (r.changed()) TradeData.get(server).putContract(r.contract());
        return r;
    }

    // --- Fees and cargo -------------------------------------------------------------------------------------

    public static int dockingFee(PortKind kind, int navyStanding) {
        return PortFees.dockingFee(kind, navyStanding, TradeConfig.feeParams());
    }

    /** The fee {@code captain} pays to dock: the navy standing is the captain's navy reputation (REP1, {@link PortFees}). */
    public static int dockingFee(PortKind kind, net.minecraft.world.entity.player.Player captain) {
        return dockingFee(kind, com.richardsenger.piratesnships.rpg.reputation.Reputation.navyStanding(captain));
    }

    /** Weight of cargo stacks (not provisions, see {@link CargoWeight}). */
    public static double cargoWeight(Level level, Iterable<ItemStack> stacks) {
        return CargoWeight.total(stacks, goods(level), TradeConfig.cargoParams());
    }

    public static CargoWeight.LoadLevel loadLevel(double totalWeight, double capacity) {
        return CargoWeight.LoadLevel.of(totalWeight, capacity, TradeConfig.cargoParams());
    }

    /** The load level of a ship of {@code blocks} blocks carrying {@code totalWeight} (capacity from config). */
    public static CargoWeight.LoadLevel shipLoadLevel(double totalWeight, int blocks) {
        return loadLevel(totalWeight, shipCapacity(blocks));
    }

    /** A ship's cargo capacity in weight units ({@code load_levels.capacity_per_block} per block). */
    public static double shipCapacity(int blocks) {
        return CargoWeight.shipCapacity(blocks, TradeConfig.CAPACITY_PER_BLOCK.get());
    }

    /** The weight the ship physics should apply (0 when "cargo weight affects ships" is off). */
    public static double shipEffect(double totalWeight) {
        return CargoWeight.shipEffect(totalWeight, TradeConfig.cargoParams());
    }
}
