package com.richardsenger.piratesnships.worldsim.raid;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.worldsim.faction.Factions;
import com.richardsenger.piratesnships.worldsim.lane.Lane;
import com.richardsenger.piratesnships.worldsim.lane.Lanes;
import com.richardsenger.piratesnships.worldsim.materialize.MaterializeConfig;
import com.richardsenger.piratesnships.worldsim.materialize.Materializer;
import com.richardsenger.piratesnships.worldsim.voyage.ConvoyPlanner;
import com.richardsenger.piratesnships.worldsim.voyage.Voyage;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageConfig;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageKind;
import com.richardsenger.piratesnships.worldsim.voyage.VoyagePlanner;
import com.richardsenger.piratesnships.worldsim.voyage.Voyages;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The RAID planner (WS5, design.md §10.4 "Pirate raids on navy settlements"). Raids do not depart on a random roll of
 * the scheduler: {@link RaidTracker} starts them with {@link #start}.
 *
 * <ul>
 *   <li><b>Start:</b> {@code raids.ships} RAID voyages of the Pirates from the nearest pirate island of the same
 *       dimension, on the island's cached lane to the settlement cut to its last {@code approach_distance} blocks, each
 *       further ship {@value #SPACING} blocks behind the first. Without a cached lane (it is queued for next time) or
 *       without an island (forced raids only) the raiders come straight in from the sea, on the line from the
 *       settlement's centre through its berth. The course ends at the settlement's berth ({@code Lanes.endpoint}).
 *       The settlement's cooldown starts, its presence count starts over, it is announced ({@link RaidAnnouncer}).</li>
 *   <li><b>Materialisation:</b> a raider whose spawn point is within a player's reach (WS3b's materialise radius plus
 *       linger margin) appears at once, waiting for its chunks if needed; one farther out stays a record and appears
 *       through WS3b's usual trigger as it closes in (WS3b would turn a ship beyond that reach back into a record
 *       anyway).</li>
 *   <li><b>As a record:</b> one that reaches the berth without having been real turns for home without a fight; one
 *       that went back to a record while its fighters were ashore counts as having held the shore.</li>
 * </ul>
 */
public final class RaidPlanner implements VoyagePlanner {

    /** Blocks between two raiding ships on the approach. */
    static final double SPACING = 40.0;

    /** Raiders that wait for their chunks to appear at once. */
    private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();

    /** What {@link #start} did. */
    public record Started(List<Voyage> voyages, BlockPos target, List<BlockPos> bells, boolean viaLane) {
    }

    // ------------------------------------------------------------------ start

    /** Whether {@code port} can be raided on its own: navy outposts, and seafarer villages with {@code target_villages}. */
    public static boolean target(Port port) {
        return port.kind() == PortKind.NAVY_OUTPOST || port.kind() == PortKind.SEAFARER_VILLAGE && RaidConfig.TARGET_VILLAGES.get();
    }

    /**
     * Starts a raid on {@code port} now. {@code forced} (command, tests) skips the target rule (any port but a pirate
     * island) and does without a pirate island. Empty when the port cannot be raided now (a raid under way there, no
     * island for an unforced raid, its dimension not loaded).
     */
    public static Optional<Started> start(MinecraftServer server, Port port, boolean forced, RandomSource rng) {
        if (port.kind() == PortKind.PIRATE_ISLAND || !forced && !target(port)) return Optional.empty();
        RaidData data = RaidData.get(server);
        if (data.raid(port.id()).isPresent()) return Optional.empty();
        ServerLevel level = server.getLevel(port.dimension());
        if (level == null) return Optional.empty();
        Optional<Port> island = nearestIsland(server, port);
        if (island.isEmpty() && !forced) return Optional.empty();

        BlockPos targetPos = Lanes.endpoint(port).atY(level.getSeaLevel());
        Lane.Point target = new Lane.Point(targetPos.getX(), targetPos.getZ());
        int ships = RaidConfig.SHIPS.get();
        double approach = RaidConfig.APPROACH_DISTANCE.get();
        double reach = approach + SPACING * (ships - 1);
        Optional<Lane> lane = island.flatMap(i -> Lanes.between(server, i.id(), port.id()));
        List<Lane.Point> route;
        if (lane.isPresent() && lane.get().waypoints().size() >= 2) {
            route = new ArrayList<>(RaidRules.approach(lane.get().waypoints(), reach));
            route.set(route.size() - 1, target);
        } else {
            double[] sea = seaward(port);
            route = RaidRules.straight(target, sea[0], sea[1], reach);
        }
        double length = Lane.length(route);
        long now = server.overworld().getGameTime();
        List<Voyage> voyages = new ArrayList<>();
        for (int i = 0; i < ships; i++) {
            double progress = Math.max(0.0, length - approach - SPACING * i);
            Voyage v = Voyage.depart(UUID.randomUUID(), VoyageKind.RAID, Faction.PIRATES,
                    Voyages.pickTemplate(VoyageConfig.PIRATE_TEMPLATES.get(), rng), island.map(Port::id).orElse(port.id()),
                    port.id(), route, Map.of(), now).withProgress(progress);
            voyages.add(Voyages.spawn(server, v));
        }
        data.put(RaidData.ActiveRaid.start(port.id(), port.dimension(), targetPos, port.box(), now,
                voyages.stream().map(Voyage::id).toList()));
        data.setLastRaidDay(port.id(), Factions.currentDay(server));
        data.setMinutes(port.id(), 0);
        List<BlockPos> bells = RaidAnnouncer.sighted(level, port.id(), port.box());
        Constants.LOG.debug("Raid on {}: {} ship(s) from {} {} blocks out ({}), {} bell(s)", port.id(), ships,
                island.map(p -> p.id().toString()).orElse("the sea"), (int) approach, lane.isPresent() ? "lane" : "straight", bells.size());
        for (Voyage v : voyages) materializeIfNear(server, level, v);
        return Optional.of(new Started(voyages, targetPos, bells, lane.isPresent()));
    }

    /** The nearest pirate island in the port's dimension (GameTest ports only see GameTest islands and back). */
    public static Optional<Port> nearestIsland(MinecraftServer server, Port port) {
        boolean world = ConvoyPlanner.isWorldPort(port);
        Port best = null;
        double bestD = Double.MAX_VALUE;
        for (Port p : PortRegistry.get(server).index().all()) {
            if (p.kind() != PortKind.PIRATE_ISLAND || !p.dimension().equals(port.dimension()) || ConvoyPlanner.isWorldPort(p) != world) continue;
            double d = p.centre().distSqr(port.centre());
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return Optional.ofNullable(best);
    }

    /** The direction toward the sea: from the settlement's centre through its first berth (north without berths). */
    static double[] seaward(Port port) {
        if (port.berths().isEmpty()) return new double[]{0.0, -1.0};
        BlockPos b = port.berths().get(0).pos();
        double dx = b.getX() - port.centre().getX();
        double dz = b.getZ() - port.centre().getZ();
        if (Math.hypot(dx, dz) < 1.0) return new double[]{0.0, -1.0};
        return new double[]{dx, dz};
    }

    private static void materializeIfNear(MinecraftServer server, ServerLevel level, Voyage v) {
        if (!MaterializeConfig.active()) return;
        Lane.Position p = v.position();
        double reach = MaterializeConfig.radius(server) + MaterializeConfig.LINGER_MARGIN.get();
        boolean near = false;
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && Math.hypot(player.getX() - p.x(), player.getZ() - p.z()) <= reach) near = true;
        }
        if (!near) return;
        Materializer.Outcome o = Materializer.materialize(server, v.id());
        if (o == Materializer.Outcome.WAITING_FOR_CHUNKS) PENDING.add(v.id());
    }

    // ------------------------------------------------------------------ planner hooks

    @Override
    public Voyage update(MinecraftServer server, Voyage voyage) {
        Optional<RaidData.ActiveRaid> raid = RaidData.get(server).raidOf(voyage.id());
        if (raid.isEmpty()) {
            PENDING.remove(voyage.id());
            return voyage;
        }
        RaidData.ShipRaid ship = raid.get().ships().get(voyage.id());
        if (ship.phase() == RaidData.Phase.LANDED) {
            // back to a record while its fighters were ashore (nobody near any more): they held the shore
            PENDING.remove(voyage.id());
            RaidLanding.resolve(server, voyage.id(), true, true);
            return voyage;
        }
        if (ship.phase() == RaidData.Phase.APPROACH && PENDING.contains(voyage.id())) {
            Materializer.Outcome o = Materializer.materialize(server, voyage.id());
            if (o != Materializer.Outcome.WAITING_FOR_CHUNKS) PENDING.remove(voyage.id());
            return Voyages.get(server, voyage.id()).orElse(voyage);
        }
        return voyage;
    }

    @Override
    public Optional<Voyage> onArrive(MinecraftServer server, Voyage voyage) {
        PENDING.remove(voyage.id());
        Optional<RaidData.ActiveRaid> raid = RaidData.get(server).raidOf(voyage.id());
        if (raid.isEmpty() || raid.get().ships().get(voyage.id()).phase() == RaidData.Phase.DONE) return Optional.empty();
        // reached the berth without a fight (never real, or WS3b's course arrived first): turn for home
        RaidLanding.resolve(server, voyage.id(), false, false);
        Lane.Point end = voyage.waypoints().get(voyage.waypoints().size() - 1);
        return Optional.of(voyage.withRoute(voyage.to(), voyage.from(), RaidRules.home(voyage.waypoints(), end.x(), end.z())));
    }

    static void clear() {
        PENDING.clear();
    }
}
