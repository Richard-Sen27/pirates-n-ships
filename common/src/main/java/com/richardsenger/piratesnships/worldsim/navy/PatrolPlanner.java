package com.richardsenger.piratesnships.worldsim.navy;

import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.worldsim.WorldSimConfig;
import com.richardsenger.piratesnships.worldsim.faction.Factions;
import com.richardsenger.piratesnships.worldsim.lane.Lane;
import com.richardsenger.piratesnships.worldsim.lane.Lanes;
import com.richardsenger.piratesnships.worldsim.voyage.ConvoyPlanner;
import com.richardsenger.piratesnships.worldsim.voyage.Voyage;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageConfig;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageKind;
import com.richardsenger.piratesnships.worldsim.voyage.VoyagePlanner;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageRules;
import com.richardsenger.piratesnships.worldsim.voyage.Voyages;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The PATROL planner (WS4b, design.md §10.4 "Navy patrols"): {@code world_simulation.patrols_per_day} × (0.5 + the
 * navy's aggression) departures per day ({@link #chance}). A patrol sails from a navy outpost along the lane to another
 * outpost within {@code max_outpost_distance}; an outpost without one sends it {@code patrol_radius} out along the lane
 * toward the nearest pirate island and back ({@link PatrolRoutes#choose}). The ship, its navy crew and fighters and
 * the navy flag come from the materialiser's kind rules (WS3b: faction NAVY, {@code voyages.navy_templates}). While it
 * sails, {@link Hunting} looks for a quarry every check. A plan whose lane is not cached yet waits, one at a time.
 */
public final class PatrolPlanner implements VoyagePlanner {

    /** Ports the random planner may use; GameTest ports ({@code gametest/…}) are left out. */
    static volatile Predicate<Port> portFilter = ConvoyPlanner::isWorldPort;

    private PatrolRoutes.Plan pending;

    /**
     * Chance per check of {@code intervalTicks} for {@code perDay} patrols a day at navy aggression {@code aggression}
     * (0..1): {@code perDay × (0.5 + aggression)} expected departures a day, so a calm navy sends half and an
     * enraged one one and a half times as many.
     */
    public static double chance(double perDay, double aggression, int intervalTicks) {
        double a = Math.max(0.0, Math.min(1.0, aggression));
        return VoyageRules.chancePerCheck(perDay * (0.5 + a), intervalTicks);
    }

    @Override
    public double spawnChance(MinecraftServer server, int intervalTicks) {
        if (!NavyConfig.active()) return 0.0;
        return chance(WorldSimConfig.PATROLS_PER_DAY.get(), Factions.aggression(server, Faction.NAVY), intervalTicks);
    }

    @Override
    public Optional<Voyage> plan(MinecraftServer server, RandomSource rng) {
        if (pending != null) return Optional.empty(); // one plan waits for its lane at a time
        List<PatrolRoutes.Site> outposts = new ArrayList<>();
        List<PatrolRoutes.Site> islands = new ArrayList<>();
        for (Port p : PortRegistry.get(server).index().all()) {
            if (!portFilter.test(p)) continue;
            PatrolRoutes.Site s = new PatrolRoutes.Site(p.id(), p.dimension().location().toString(), p.centre().getX(), p.centre().getZ());
            if (p.kind() == PortKind.NAVY_OUTPOST) outposts.add(s);
            else if (p.kind() == PortKind.PIRATE_ISLAND) islands.add(s);
        }
        Optional<PatrolRoutes.Plan> plan = PatrolRoutes.choose(outposts, islands, NavyConfig.MAX_OUTPOST_DISTANCE.get(), rng);
        if (plan.isEmpty()) return Optional.empty();
        Optional<Lane> lane = Lanes.between(server, plan.get().outpost(), plan.get().other());
        if (lane.isPresent()) return Optional.of(voyage(server, plan.get(), lane.get(), rng));
        pending = plan.get();
        return Optional.empty();
    }

    @Override
    public List<Voyage> ready(MinecraftServer server) {
        if (pending == null) return List.of();
        PatrolRoutes.Plan p = pending;
        return switch (Lanes.status(server, p.outpost(), p.other())) {
            case READY -> {
                pending = null;
                yield Lanes.between(server, p.outpost(), p.other())
                        .map(l -> List.of(voyage(server, p, l, RandomSource.create()))).orElse(List.of());
            }
            case QUEUED -> {
                Lanes.between(server, p.outpost(), p.other()); // re-queue if the queue was cleared (server restart)
                yield List.of();
            }
            case FAILED, UNKNOWN_PORT -> {
                pending = null;
                yield List.of();
            }
        };
    }

    @Override
    public Voyage update(MinecraftServer server, Voyage voyage) {
        return Hunting.isTest(voyage) ? voyage : Hunting.updateAbstract(server, voyage);
    }

    @Override
    public Optional<Voyage> onArrive(MinecraftServer server, Voyage voyage) {
        return Hunting.onArrive(server, voyage);
    }

    /** Drops a waiting plan (server stop). */
    void clear() {
        pending = null;
    }

    /** The patrol record for a plan on a lane: to the other outpost, or out and back to its own. */
    static Voyage voyage(MinecraftServer server, PatrolRoutes.Plan plan, Lane lane, RandomSource rng) {
        List<Lane.Point> route = plan.outAndBack()
                ? PatrolRoutes.outAndBack(lane.waypoints(), NavyConfig.PATROL_RADIUS.get())
                : lane.waypoints();
        return Voyage.depart(UUID.randomUUID(), VoyageKind.PATROL, VoyageKind.PATROL.defaultFaction(),
                Voyages.pickTemplate(VoyageConfig.NAVY_TEMPLATES.get(), rng), plan.outpost(),
                plan.outAndBack() ? plan.outpost() : plan.other(), route, Map.of(), server.overworld().getGameTime());
    }
}
