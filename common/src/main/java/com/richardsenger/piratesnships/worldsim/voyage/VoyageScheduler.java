package com.richardsenger.piratesnships.worldsim.voyage;

import com.richardsenger.piratesnships.sailing.wind.WindSample;
import com.richardsenger.piratesnships.sailing.wind.WindService;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.worldsim.WorldSimConfig;
import com.richardsenger.piratesnships.worldsim.lane.Lane;
import com.richardsenger.piratesnships.worldsim.lane.Lanes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Moves the abstract voyages (WS2): every {@code world_simulation.voyages.tick_interval_ticks} on the server tick end,
 * a check works on one queued lane for at most {@code lanes.millis_per_check} ms, moves every SAILING voyage by speed × wind factor (the wind sampled at its
 * position in its origin port's dimension), lets arrivals trade or continue, and rolls one departure per registered
 * {@link VoyagePlanner} under the voyage cap. Off with {@code world_simulation.enabled} (records freeze).
 */
public final class VoyageScheduler {

    /** What one check did. */
    public record CheckResult(boolean ran, boolean laneComputed, int moved, int arrived, int spawned) {
        static final CheckResult DISABLED = new CheckResult(false, false, 0, 0, 0);
    }

    private static final Map<VoyageKind, VoyagePlanner> PLANNERS = new EnumMap<>(VoyageKind.class);
    private static final VoyagePlanner NONE = new VoyagePlanner() {
    };
    static final ConvoyPlanner CONVOYS = new ConvoyPlanner();

    static {
        PLANNERS.put(VoyageKind.CONVOY, CONVOYS);
    }

    private VoyageScheduler() {
    }

    /** Sets the planner of {@code kind} (WS4b: PATROL, WS5: RAID). Call during mod construction. */
    public static synchronized void register(VoyageKind kind, VoyagePlanner planner) {
        PLANNERS.put(kind, planner);
    }

    /** The planner of {@code kind}, or one that does nothing. */
    public static VoyagePlanner planner(VoyageKind kind) {
        return PLANNERS.getOrDefault(kind, NONE);
    }

    /** {@code SERVER_TICK_END}: a check every {@code tick_interval_ticks}. */
    public static void onServerTick(MinecraftServer server) {
        int interval = VoyageConfig.TICK_INTERVAL.get();
        if (server.getTickCount() % interval != 0) return;
        check(server, server.overworld().getGameTime(), server.overworld().getRandom());
    }

    /** One scheduler check (also the GameTest entry point). */
    public static CheckResult check(MinecraftServer server, long now, RandomSource rng) {
        if (!WorldSimConfig.ENABLED.get()) return CheckResult.DISABLED;
        int interval = VoyageConfig.TICK_INTERVAL.get();
        boolean laneComputed = Lanes.computeNext(server);
        int cap = WorldSimConfig.MAX_SIMULTANEOUS_VOYAGES.get();
        int spawned = 0;

        for (VoyageKind kind : VoyageKind.values()) {
            for (Voyage v : planner(kind).ready(server)) {
                if (Voyages.active(server).size() >= cap) break;
                Voyages.spawn(server, v);
                spawned++;
            }
        }

        int moved = 0, arrived = 0;
        VoyageRules.Speed speed = VoyageConfig.speed();
        for (Voyage v : Voyages.active(server)) {
            if (v.state() != Voyage.State.SAILING) continue;
            VoyagePlanner planner = planner(v.kind());
            Voyage u = planner.update(server, v);
            if (!u.equals(v)) Voyages.update(server, u);
            if (u.state() != Voyage.State.SAILING) continue;
            double blocks = VoyageRules.blocksIn(speed, interval, windFactor(server, u, speed)) * planner.speedFactor(server, u);
            Voyage next = VoyageRules.advance(u, blocks);
            Voyages.update(server, next);
            moved++;
            if (next.arrived()) {
                Voyages.arrive(server, next);
                arrived++;
            }
        }

        for (VoyageKind kind : VoyageKind.values()) {
            VoyagePlanner planner = planner(kind);
            double chance = planner.spawnChance(server, interval);
            if (chance <= 0) continue;
            if (!VoyageRules.rollSpawn(chance, rng.nextDouble(), Voyages.active(server).size(), cap)) continue;
            Optional<Voyage> planned = planner.plan(server, rng);
            if (planned.isPresent()) {
                Voyages.spawn(server, planned.get());
                spawned++;
            }
        }
        return new CheckResult(true, laneComputed, moved, arrived, spawned);
    }

    /** The wind factor at the voyage's position (1 if the wind is off for voyages or the dimension is unknown). */
    static double windFactor(MinecraftServer server, Voyage v, VoyageRules.Speed speed) {
        if (!speed.windAffects() || v.waypoints().size() < 2) return 1.0;
        Optional<Port> origin = PortRegistry.get(server).index().byId(v.from());
        ServerLevel level = origin.map(p -> server.getLevel(p.dimension())).orElse(server.overworld());
        if (level == null) return 1.0;
        Lane.Position pos = v.position();
        WindSample wind = WindService.sample(level, pos.at(level.getSeaLevel()));
        return VoyageRules.windFactor(pos.headingDegrees(), wind.towardDegrees(), wind.strength(), speed);
    }

    /** Drops transient planner state (server stop). */
    public static void clear() {
        CONVOYS.clear();
    }
}
