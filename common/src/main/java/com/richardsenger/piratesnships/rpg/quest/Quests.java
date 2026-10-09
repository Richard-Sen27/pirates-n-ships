package com.richardsenger.piratesnships.rpg.quest;

import com.richardsenger.piratesnships.mob.captain.CaptainEntry;
import com.richardsenger.piratesnships.mob.captain.CaptainRegistry;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.trade.TradeConfig;
import com.richardsenger.piratesnships.trade.TradeData;
import com.richardsenger.piratesnships.trade.TradeRandom;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.contract.ContractGenerator;
import com.richardsenger.piratesnships.trade.contract.ContractParams;
import com.richardsenger.piratesnships.trade.contract.DeliveryContract;
import com.richardsenger.piratesnships.trade.desk.HarborDeskService;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.Market;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.WorldConfig;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.world.port.TreasureSite;
import com.richardsenger.piratesnships.world.treasure.TreasureBinding;
import com.richardsenger.piratesnships.world.treasure.TreasureMapService;
import com.richardsenger.piratesnships.worldsim.materialize.MaterializeConfig;
import com.richardsenger.piratesnships.worldsim.materialize.Materializer;
import com.richardsenger.piratesnships.worldsim.voyage.Voyage;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageKind;
import com.richardsenger.piratesnships.worldsim.voyage.Voyages;
import com.richardsenger.piratesnships.worldsim.lane.Lanes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The quest API (docs/design.md §15, QST1), server side. Offers live per port in {@link QuestData} and are
 * regenerated lazily at the first look on a new day ({@link #offers}): offers older than {@code offer_days} go, new
 * ones fill up to {@code offers_per_port}. Accepting moves an offer into the player's {@link QuestLog} (at most
 * {@code max_active}); a delivery then makes its contract (held by the player, no deposit, the quest's reward), a
 * treasure hunt hands out a bound map. {@link #apply} moves every active quest of a player on by an event
 * ({@link QuestTracker} feeds it) and pays finished ones through {@link QuestRewards}.
 *
 * <pre>{@code
 * List<Quest> offers = Quests.offers(server, portId);
 * Quests.Result r = Quests.accept(player, portId, offers.get(0).id());
 * Quests.apply(player, new QuestEvent.DeedDone(Deed.KILL_PIRATE));
 * }</pre>
 */
public final class Quests {

    // result ids (QuestText.result)
    public static final String ACCEPTED = "accepted";
    public static final String ABANDONED = "abandoned";
    public static final String DISABLED = "disabled";
    public static final String NO_SESSION = "no_session";
    public static final String TREASURE_GONE = "treasure_gone";
    public static final String NO_PORT = "no_port";
    public static final String CAPTAIN_GONE = "captain_gone";
    public static final String NO_CONVOY = "no_convoy";
    public static final String UNKNOWN = "unknown";

    /** The answer to an accept or abandon: done or not, a result id ({@link QuestText#result}) and the quest. */
    public record Result(boolean done, String key, Optional<Quest> quest) {
        static Result fail(String key) {
            return new Result(false, key, Optional.empty());
        }
    }

    private Quests() {
    }

    public static boolean enabled() {
        return QuestConfig.enabled();
    }

    public static long day(MinecraftServer server) {
        return TradeService.day(server);
    }

    // --- Log ----------------------------------------------------------------------------------------------------

    public static QuestLog log(Player player) {
        return Services.ATTACHMENTS.get(player, QuestAttachments.LOG);
    }

    public static void store(Player player, QuestLog log) {
        Services.ATTACHMENTS.set(player, QuestAttachments.LOG, log);
    }

    // --- Offers -------------------------------------------------------------------------------------------------

    /**
     * The port's open offers, made or replaced first if this is the port's first look today; none while disabled.
     * A captain hunt whose captain is no longer alive is withdrawn at once.
     */
    public static List<Quest> offers(MinecraftServer server, ResourceLocation port) {
        if (!enabled()) return List.of();
        refresh(server, port);
        QuestData data = QuestData.get(server);
        for (Quest q : data.offers(port)) {
            if (q.type() == QuestType.HUNT_CAPTAIN && q.target() instanceof QuestTarget.Victim v && !captainAlive(server, v.id())) {
                data.removeOffer(port, q.id());
            }
        }
        return data.offers(port);
    }

    /** Drops expired offers and fills up to {@code offers_per_port}, once per port and day. */
    public static void refresh(MinecraftServer server, ResourceLocation port) {
        QuestData data = QuestData.get(server);
        long day = day(server);
        if (data.lastOfferDay(port) >= day) return;
        List<Quest> kept = new ArrayList<>();
        for (Quest q : data.offers(port)) if (!QuestRules.expired(q, day)) kept.add(q);
        QuestParams params = QuestConfig.params();
        Optional<QuestGenerator.Context> ctx = context(server, port, day);
        // one captain hunt per port: a kept one leaves the captains out of today's new offers
        if (kept.stream().anyMatch(q -> q.type() == QuestType.HUNT_CAPTAIN)) ctx = ctx.map(QuestGenerator.Context::withoutCaptains);
        int missing = params.offersPerPort() - kept.size();
        if (ctx.isPresent() && missing > 0) kept.addAll(QuestGenerator.offers(ctx.get(), missing, params));
        data.setOffers(port, kept);
        data.setLastOfferDay(port, day);
    }

    /**
     * Adds one offer of {@code type} at the port now (the operator command); empty if the port is unknown or can't
     * offer the type (not listed for its kind, nothing to deliver, no treasure in reach).
     */
    public static Optional<Quest> offerOfType(MinecraftServer server, ResourceLocation port, QuestType type) {
        long day = day(server);
        QuestParams params = QuestConfig.params();
        QuestParams all = params.withTypes(java.util.Map.of(PortKind.SEAFARER_VILLAGE, List.of(type), PortKind.NAVY_OUTPOST, List.of(type),
                PortKind.PIRATE_ISLAND, List.of(type)));
        Optional<Quest> q = context(server, port, day).flatMap(ctx -> QuestGenerator.offer(type, ctx, server.overworld().getGameTime(), all));
        q.ifPresent(QuestData.get(server)::addOffer);
        return q;
    }

    /** What the generator needs to know about the port today; empty for an unknown port without a market. */
    public static Optional<QuestGenerator.Context> context(MinecraftServer server, ResourceLocation port, long day) {
        Optional<Port> known = PortRegistry.get(server).index().byId(port);
        PortKind kind;
        Climate climate;
        if (known.isPresent()) {
            kind = known.get().kind();
            climate = known.get().climate();
        } else {
            Optional<Market> market = TradeService.market(server, port);
            if (market.isEmpty()) return Optional.empty();
            kind = market.get().profile().kind();
            climate = Climate.TEMPERATE;
        }
        long seed = server.overworld().getSeed();
        return Optional.of(new QuestGenerator.Context(port, kind, climate, day, seed, deliveries(server, port, day, seed),
                treasures(server, known), captains(server, known), sea(server, known)));
    }

    /**
     * What the sea quests can follow from this port (QST2): other registered villages and navy outposts with an open
     * market in the port's dimension (escort destinations, with their straight distance), and whether merchant convoys,
     * navy patrols and pirate ships are at sea now. Nothing while ships can't appear ({@code MaterializeConfig.active}).
     */
    private static QuestGenerator.SeaOptions sea(MinecraftServer server, Optional<Port> port) {
        if (port.isEmpty() || !MaterializeConfig.active()) return QuestGenerator.SeaOptions.NONE;
        Port p = port.get();
        List<QuestGenerator.EscortOption> escorts = new ArrayList<>();
        for (Port other : PortRegistry.get(server).index().all()) {
            if (other.id().equals(p.id()) || other.kind() == PortKind.PIRATE_ISLAND || !other.dimension().equals(p.dimension())) continue;
            if (TradeService.market(server, other.id()).isEmpty()) continue;
            double dx = other.centre().getX() - p.centre().getX();
            double dz = other.centre().getZ() - p.centre().getZ();
            escorts.add(new QuestGenerator.EscortOption(other.id(), Math.sqrt(dx * dx + dz * dz)));
        }
        boolean convoys = false, patrols = false, pirates = false;
        for (Voyage v : Voyages.active(server)) {
            convoys |= v.kind() == VoyageKind.CONVOY && v.faction() == com.richardsenger.piratesnships.law.flag.Faction.MERCHANTS;
            patrols |= v.kind() == VoyageKind.PATROL;
            pirates |= v.faction() == com.richardsenger.piratesnships.law.flag.Faction.PIRATES;
        }
        return new QuestGenerator.SeaOptions(escorts, convoys, patrols, pirates);
    }

    /**
     * The living pirate captains in the port's dimension (QST1b), with their posts' distance and bearing from the port's
     * centre; the generator keeps the nearest within {@code captain_hunt_radius}. None for a port that isn't registered.
     */
    private static List<QuestGenerator.CaptainOption> captains(MinecraftServer server, Optional<Port> port) {
        if (port.isEmpty()) return List.of();
        Port p = port.get();
        List<QuestGenerator.CaptainOption> out = new ArrayList<>();
        for (CaptainEntry e : CaptainRegistry.get(server).all().values()) {
            if (!e.alive() || !e.dimension().equals(p.dimension())) continue;
            double dx = e.post().getX() - p.centre().getX();
            double dz = e.post().getZ() - p.centre().getZ();
            out.add(new QuestGenerator.CaptainOption(e.id(), e.name(), Math.sqrt(dx * dx + dz * dz), QuestGenerator.bearing(dx, dz)));
        }
        return out;
    }

    /** Whether the pirate captain {@code id} is still an island's living captain ({@code CaptainRegistry}). */
    public static boolean captainAlive(MinecraftServer server, UUID id) {
        return CaptainRegistry.get(server).all().values().stream().anyMatch(e -> e.alive() && e.id().equals(id));
    }

    /** Two cargo runs the trade module's contract generator would make from this port today (any destination). */
    private static List<QuestGenerator.DeliveryOption> deliveries(MinecraftServer server, ResourceLocation port, long day, long seed) {
        Optional<Market> market = TradeService.market(server, port);
        if (market.isEmpty()) return List.of();
        List<ContractGenerator.Destination> dests = HarborDeskService.destinations(server, port);
        if (dests.isEmpty()) return List.of();
        ContractParams cp = TradeConfig.contractParams();
        ContractParams two = new ContractParams(true, 2, cp.minQuantity(), cp.maxQuantity(), cp.rewardBase(), cp.priceDifferenceWeight(),
                cp.distanceBonusPer1000(), cp.riskBonus(), cp.blocksPerDay(), cp.slackDays(), cp.offerLifetimeDays(), cp.depositFraction(),
                cp.maxActivePerPlayer());
        List<QuestGenerator.DeliveryOption> out = new ArrayList<>();
        for (DeliveryContract c : ContractGenerator.generate(port, market.get().profile(), dests, TradeService.goods(false).tradeable(), day,
                TradeRandom.mix(seed, "quest_deliveries"), two, TradeConfig.marketParams())) {
            out.add(new QuestGenerator.DeliveryOption(c.good(), c.quantity(), c.destination(), c.reward()));
        }
        return out;
    }

    /**
     * Unlooted treasure sites a map from this port could lead to: the island's own at a pirate island, else those of
     * the island nearest to the port within {@code world.treasure_maps.search_radius}.
     */
    private static List<QuestGenerator.TreasureOption> treasures(MinecraftServer server, Optional<Port> port) {
        if (port.isEmpty() || !WorldConfig.TREASURE_MAPS_ENABLED.get()) return List.of();
        Port p = port.get();
        Optional<Port> island = p.kind() == PortKind.PIRATE_ISLAND && TreasureBinding.hasUnlooted(p) ? Optional.of(p)
                : TreasureBinding.choose(PortRegistry.get(server).index().all(), p.dimension(), p.centre(),
                WorldConfig.TREASURE_MAP_SEARCH_RADIUS.get()).map(TreasureBinding.Choice::port);
        List<QuestGenerator.TreasureOption> out = new ArrayList<>();
        island.ifPresent(i -> {
            for (TreasureSite s : i.treasures()) if (!s.looted()) out.add(new QuestGenerator.TreasureOption(i.id(), s.pos()));
        });
        return out;
    }

    // --- Accept, abandon, complete --------------------------------------------------------------------------------

    /** The player accepts the offer {@code id} of {@code port} (the caller checked the desk session). */
    public static Result accept(ServerPlayer player, ResourceLocation port, UUID id) {
        if (!enabled()) return Result.fail(DISABLED);
        MinecraftServer server = player.server;
        refresh(server, port);
        QuestData data = QuestData.get(server);
        Optional<Quest> offer = data.offer(port, id);
        if (offer.isEmpty()) return Result.fail(QuestRules.NOT_OFFERED);
        long day = day(server);
        QuestParams params = QuestConfig.params();
        Optional<String> refusal = QuestRules.canAccept(log(player), offer.get(), day, params.maxActive());
        if (refusal.isPresent()) return Result.fail(refusal.get());
        Quest quest = QuestRules.accept(offer.get(), day, params.deadlineDays());
        if (quest.type() == QuestType.HUNT_CAPTAIN && quest.target() instanceof QuestTarget.Victim v && !captainAlive(server, v.id())) {
            data.removeOffer(port, id);
            return Result.fail(CAPTAIN_GONE);
        }
        ItemStack map = ItemStack.EMPTY;
        if (quest.target() instanceof QuestTarget.Treasure t) {
            Optional<Port> island = PortRegistry.get(server).index().byId(t.port());
            ServerLevel level = island.map(p -> server.getLevel(p.dimension())).orElse(null);
            Optional<TreasureSite> site = island.flatMap(p -> p.treasures().stream().filter(s -> s.pos().equals(t.site())).findFirst());
            if (level == null || site.isEmpty()) return Result.fail(NO_PORT);
            if (site.get().looted()) {
                data.removeOffer(port, id);
                return Result.fail(TREASURE_GONE);
            }
            map = TreasureMapService.boundMap(level, island.get(), site.get());
        }
        if (quest.target() instanceof QuestTarget.Cargo c) {
            UUID contract = UUID.randomUUID();
            int reward = (int) Math.min(Integer.MAX_VALUE, quest.rewardCoins());
            TradeData.get(server).putContract(new DeliveryContract(contract, c.good(), c.quantity(), port, c.destination(), day, day,
                    quest.deadlineDay(), reward, 0, DeliveryContract.State.ACCEPTED, Optional.of(player.getUUID())));
            quest = quest.withTarget(c.withContract(contract));
            player.displayClientMessage(Component.translatable(QuestText.DELIVER_HINT), false);
        }
        if (quest.target() instanceof QuestTarget.Escort e) {
            // QST2b: the convoy sets sail now if the lane is charted; else the harbor master charts it off the tick
            // (Lanes queues it for the voyage scheduler) and the next poll after it is ready sails the convoy
            if (TradeService.market(server, port).isEmpty() || TradeService.market(server, e.destination()).isEmpty()) {
                data.removeOffer(port, id);
                return Result.fail(NO_CONVOY);
            }
            if (Lanes.between(server, port, e.destination()).isPresent()) {
                Optional<Quest> sailed = sail(player, quest);
                if (sailed.isEmpty()) {
                    data.removeOffer(port, id);
                    return Result.fail(NO_CONVOY);
                }
                quest = sailed.get();
            } else if (Lanes.status(server, port, e.destination()) == Lanes.Status.QUEUED) {
                quest = QuestRules.chart(quest, server.overworld().getGameTime());
                player.displayClientMessage(Component.translatable(QuestText.ESCORT_CHARTING), false);
            } else {
                data.removeOffer(port, id);
                return Result.fail(NO_CONVOY);
            }
        }
        data.removeOffer(port, id);
        store(player, log(player).with(quest));
        if (!map.isEmpty()) {
            player.getInventory().placeItemBackInInventory(map);
            player.containerMenu.broadcastChanges();
            player.displayClientMessage(Component.translatable(QuestText.MAP_GIVEN), false);
        }
        return new Result(true, ACCEPTED, Optional.of(quest));
    }

    /**
     * Sails the convoy of the escort {@code quest} from its port to its destination on the cached lane (never computes
     * one) and binds the quest to it, active; tells the player. Empty if no convoy can sail (no cached lane, a market
     * closed).
     */
    static Optional<Quest> sail(ServerPlayer player, Quest quest) {
        if (!(quest.target() instanceof QuestTarget.Escort e)) return Optional.empty();
        Optional<Voyage> convoy = Voyages.spawnConvoy(player.server, quest.port(), e.destination(), player.getRandom(), false);
        if (convoy.isEmpty()) return Optional.empty();
        Voyage v = convoy.get();
        Quest bound = QuestRules.bindEscort(quest, v.id(), Materializer.name(v), v.waypoints().size() - 1, QuestConfig.ESCORT_FRACTION.get());
        player.displayClientMessage(Component.translatable(QuestText.ESCORT_HINT, ((QuestTarget.Escort) bound.target()).name(),
                QuestConfig.ESCORT_RADIUS.get()), false);
        return Optional.of(bound);
    }

    /** The player gives up the active quest {@code id}: it counts as failed; a delivery's contract is abandoned. */
    public static Result abandon(ServerPlayer player, UUID id) {
        QuestLog log = log(player);
        Optional<Quest> q = log.find(id);
        if (q.isEmpty()) return Result.fail(UNKNOWN);
        if (q.get().target() instanceof QuestTarget.Cargo c && c.contract().isPresent()) {
            TradeService.abandon(player.server, c.contract().get(), player.getUUID());
        }
        store(player, log.with(q.get().withState(QuestState.FAILED)).settle());
        return new Result(true, ABANDONED, q);
    }

    // --- Progress -----------------------------------------------------------------------------------------------

    /**
     * Moves every active quest of the player on by {@code event}, stores the log and then pays completed quests
     * ({@link QuestRewards}), tells about failed ones and shows progress. Does nothing while quests are off (an
     * operator's {@link QuestEvent.Complete} still works). Returns the quests that changed, as they are now.
     */
    public static List<Quest> apply(ServerPlayer player, QuestEvent event) {
        if (!enabled() && !(event instanceof QuestEvent.Complete)) return List.of();
        QuestLog log = log(player);
        if (log.active().isEmpty()) return List.of();
        List<Quest> changed = new ArrayList<>();
        QuestLog next = log.map(q -> {
            Quest a = QuestRules.advance(q, event);
            if (!a.equals(q)) changed.add(a);
            return a;
        });
        if (changed.isEmpty()) return List.of();
        // store first: the reward deed reaches the listeners (and this method) again
        store(player, next.settle());
        for (Quest q : changed) {
            switch (q.state()) {
                case DONE -> QuestRewards.grant(player, q);
                case FAILED -> player.displayClientMessage(Component.translatable(QuestText.FAILED, QuestText.title(q, false)), false);
                default -> player.displayClientMessage(Component.translatable(QuestText.PROGRESS, QuestText.title(q, false),
                        q.progress(), q.needed()), true);
            }
        }
        return changed;
    }

    /** Applies {@code event} to the one active quest {@code id} only (the operator's complete). */
    public static Optional<Quest> applyTo(ServerPlayer player, UUID id, QuestEvent event) {
        QuestLog log = log(player);
        Optional<Quest> q = log.find(id);
        if (q.isEmpty()) return Optional.empty();
        Quest a = QuestRules.advance(q.get(), event);
        if (a.equals(q.get())) return Optional.of(a);
        store(player, log.with(a).settle());
        if (a.state() == QuestState.DONE) QuestRewards.grant(player, a);
        return Optional.of(a);
    }
}
