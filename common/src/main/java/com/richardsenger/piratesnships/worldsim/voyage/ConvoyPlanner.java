package com.richardsenger.piratesnships.worldsim.voyage;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.market.GoodRole;
import com.richardsenger.piratesnships.trade.market.Market;
import com.richardsenger.piratesnships.trade.market.PortProfile;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.worldsim.WorldSimConfig;
import com.richardsenger.piratesnships.worldsim.faction.FactionEvent;
import com.richardsenger.piratesnships.worldsim.faction.Factions;
import com.richardsenger.piratesnships.worldsim.lane.Lane;
import com.richardsenger.piratesnships.worldsim.lane.Lanes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The CONVOY planner (WS2): {@code world_simulation.convoys_per_day} departures per day; a departure picks origin,
 * destination and cargo with {@link VoyageRules#planConvoy} from the registered ports' markets, buys the cargo at the
 * origin ({@link VoyageEconomy#load}) and sails the cached lane; on arrival it sells the cargo at the destination.
 * When the lane is not cached yet the plan waits (one at a time) until the scheduler has computed it.
 */
public final class ConvoyPlanner implements VoyagePlanner {

    /** Ports the random planner may use; GameTest ports ({@code gametest/…}) are left out unless a test says so. */
    static volatile Predicate<Port> portFilter = ConvoyPlanner::isWorldPort;

    private VoyageRules.ConvoyPlan pending;

    public static boolean isWorldPort(Port port) {
        return !port.id().getPath().startsWith("gametest/");
    }

    @Override
    public double spawnChance(MinecraftServer server, int intervalTicks) {
        return VoyageRules.chancePerCheck(WorldSimConfig.CONVOYS_PER_DAY.get(), intervalTicks);
    }

    @Override
    public Optional<Voyage> plan(MinecraftServer server, RandomSource rng) {
        if (pending != null) return Optional.empty(); // one plan waits for its lane at a time
        List<VoyageRules.PortView> views = new ArrayList<>();
        for (Port p : PortRegistry.get(server).index().all()) {
            if (portFilter.test(p)) view(server, p).ifPresent(views::add);
        }
        Optional<VoyageRules.ConvoyPlan> plan = VoyageRules.planConvoy(views, VoyageConfig.CONVOY_MIN_DISTANCE.get(),
                VoyageConfig.CONVOY_MAX_DISTANCE.get(), VoyageConfig.CONVOY_CARGO_UNITS.get(), rng);
        if (plan.isEmpty()) return Optional.empty();
        Optional<Lane> lane = Lanes.between(server, plan.get().from(), plan.get().to());
        if (lane.isPresent()) return Optional.of(voyage(server, plan.get(), lane.get(), rng));
        pending = plan.get();
        return Optional.empty();
    }

    @Override
    public List<Voyage> ready(MinecraftServer server) {
        if (pending == null) return List.of();
        VoyageRules.ConvoyPlan p = pending;
        return switch (Lanes.status(server, p.from(), p.to())) {
            case READY -> {
                pending = null;
                yield Lanes.between(server, p.from(), p.to()).map(l -> List.of(voyage(server, p, l, RandomSource.create()))).orElse(List.of());
            }
            case QUEUED -> {
                Lanes.between(server, p.from(), p.to()); // re-queue if the queue was cleared (server restart)
                yield List.of();
            }
            case FAILED, UNKNOWN_PORT -> {
                pending = null;
                yield List.of();
            }
        };
    }

    @Override
    public Voyage onDepart(MinecraftServer server, Voyage voyage) {
        return voyage.withCargo(VoyageEconomy.load(server, voyage.from(), voyage.cargo()));
    }

    /**
     * WS2 sells the cargo at the destination. WS3c: a convoy that still carries cargo reports the world event
     * {@link FactionEvent#CONVOY_DELIVERED} (WS1: Merchants' wealth +100 and the Navy's +20, times {@code event_scale})
     * once per voyage, whether it arrived as a record or as a real ship; one plundered empty reports nothing.
     */
    @Override
    public Optional<Voyage> onArrive(MinecraftServer server, Voyage voyage) {
        // once per voyage: a second arrival of the same record (it ends right after this) is not a second delivery
        boolean active = Voyages.get(server, voyage.id()).isPresent();
        Map<ResourceLocation, Integer> sold = active ? VoyageEconomy.unload(server, voyage.to(), voyage.cargo()) : Map.of();
        Constants.LOG.debug("Convoy {} arrived at {} and sold {}", voyage.shortId(), voyage.to(), sold);
        if (active && VoyageRules.delivers(voyage)) {
            Factions.report(server, FactionEvent.CONVOY_DELIVERED);
            for (DeliveryListener l : DELIVERED) l.onDelivered(server, voyage);
        }
        return Optional.empty();
    }

    /** Hears every convoy that delivered its cargo (WS3c; tests, later quests). */
    @FunctionalInterface
    public interface DeliveryListener {
        void onDelivered(MinecraftServer server, Voyage voyage);
    }

    private static final List<DeliveryListener> DELIVERED = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void onDelivered(DeliveryListener listener) {
        DELIVERED.add(listener);
    }

    /** Drops a waiting plan (server stop). */
    void clear() {
        pending = null;
    }

    /** The convoy record for a plan on a lane (cargo as planned; bought on departure). */
    static Voyage voyage(MinecraftServer server, VoyageRules.ConvoyPlan plan, Lane lane, RandomSource rng) {
        return Voyage.depart(UUID.randomUUID(), VoyageKind.CONVOY, VoyageKind.CONVOY.defaultFaction(),
                Voyages.pickTemplate(VoyageConfig.MERCHANT_TEMPLATES.get(), rng), plan.from(), plan.to(), lane.waypoints(),
                plan.cargo(), server.overworld().getGameTime());
    }

    /** What a registered port with an open market offers convoys. */
    static Optional<VoyageRules.PortView> view(MinecraftServer server, Port port) {
        Optional<Market> market = TradeService.market(server, port.id());
        if (market.isEmpty()) return Optional.empty();
        PortProfile profile = market.get().profile();
        Set<ResourceLocation> traded = new HashSet<>();
        profile.roles().forEach((g, r) -> {
            if (r.traded()) traded.add(g);
        });
        return Optional.of(new VoyageRules.PortView(port.id(), port.dimension().location().toString(), port.centre().getX(),
                port.centre().getZ(), profile.goodsWith(GoodRole.PRODUCES), profile.goodsWith(GoodRole.DEMANDS), traded));
    }
}
