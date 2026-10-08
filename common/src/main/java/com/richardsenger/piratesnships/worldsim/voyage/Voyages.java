package com.richardsenger.piratesnships.worldsim.voyage;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.ship.template.ShipTemplates;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.worldsim.lane.Lane;
import com.richardsenger.piratesnships.worldsim.lane.Lanes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The voyage API (WS2) for the scheduler, commands and later packages (WS3b materialisation, WS4b patrols, WS5
 * raids). Server thread only.
 *
 * <pre>{@code
 * List<Voyage> sailing = Voyages.active(server);
 * Optional<Voyage> v = Voyages.spawnConvoy(server, fromPort, toPort, rng); // computes the lane now if needed
 * Voyages.onEnd((server, voyage, reason) -> { if (reason == VoyageEnd.ARRIVED) ... });
 * Voyages.end(server, v.get().id(), VoyageEnd.SUNK);
 * }</pre>
 */
public final class Voyages {

    @FunctionalInterface
    public interface SpawnListener {
        void onSpawn(MinecraftServer server, Voyage voyage);
    }

    @FunctionalInterface
    public interface EndListener {
        void onEnd(MinecraftServer server, Voyage voyage, VoyageEnd reason);
    }

    private static final List<SpawnListener> SPAWN = new CopyOnWriteArrayList<>();
    private static final List<EndListener> END = new CopyOnWriteArrayList<>();

    private Voyages() {
    }

    public static void onSpawn(SpawnListener listener) {
        SPAWN.add(listener);
    }

    public static void onEnd(EndListener listener) {
        END.add(listener);
    }

    /** Every voyage that has not ended, in departure order. */
    public static List<Voyage> active(MinecraftServer server) {
        return VoyageData.get(server).all();
    }

    public static Optional<Voyage> get(MinecraftServer server, UUID id) {
        return VoyageData.get(server).get(id);
    }

    /** The voyage whose id starts with {@code prefix} (commands), if exactly one does. */
    public static Optional<Voyage> find(MinecraftServer server, String prefix) {
        List<Voyage> hits = active(server).stream().filter(v -> v.id().toString().startsWith(prefix)).toList();
        return hits.size() == 1 ? Optional.of(hits.get(0)) : Optional.empty();
    }

    /**
     * Starts {@code voyage}: its kind's {@link VoyagePlanner#onDepart} runs (a convoy buys its cargo), it is stored and
     * the spawn listeners hear of it. Ignores the voyage cap (the scheduler checks it). Returns the stored voyage.
     */
    public static Voyage spawn(MinecraftServer server, Voyage voyage) {
        Voyage departed = VoyageScheduler.planner(voyage.kind()).onDepart(server, voyage);
        VoyageData.get(server).put(departed);
        Constants.LOG.debug("Voyage {} ({}) departs {} for {}", departed.shortId(), departed.kind().getSerializedName(),
                departed.from(), departed.to());
        for (SpawnListener l : SPAWN) l.onSpawn(server, departed);
        return departed;
    }

    /**
     * A convoy from {@code from} to {@code to} now (commands, tests): the lane from the cache or computed at once,
     * cargo as {@link VoyageRules#cargo} picks it from the two markets. Empty if a port or its market is unknown or
     * there is no lane. Ignores the cap and the spawn chance.
     */
    public static Optional<Voyage> spawnConvoy(MinecraftServer server, ResourceLocation from, ResourceLocation to, RandomSource rng) {
        PortRegistry ports = PortRegistry.get(server);
        Optional<Port> pf = ports.index().byId(from);
        Optional<Port> pt = ports.index().byId(to);
        if (pf.isEmpty() || pt.isEmpty() || from.equals(to)) return Optional.empty();
        Optional<VoyageRules.PortView> vf = ConvoyPlanner.view(server, pf.get());
        Optional<VoyageRules.PortView> vt = ConvoyPlanner.view(server, pt.get());
        if (vf.isEmpty() || vt.isEmpty()) return Optional.empty();
        Optional<Lane> lane = Lanes.between(server, from, to);
        if (lane.isEmpty()) lane = Lanes.compute(server, from, to).lane();
        if (lane.isEmpty()) return Optional.empty();
        VoyageRules.ConvoyPlan plan = new VoyageRules.ConvoyPlan(from, to,
                VoyageRules.cargo(vf.get(), vt.get(), VoyageConfig.CONVOY_CARGO_UNITS.get(), rng));
        return Optional.of(spawn(server, ConvoyPlanner.voyage(server, plan, lane.get(), rng)));
    }

    /** Replaces the stored voyage with the same id (e.g. WS3b marking it MATERIALISED). False if it is not active. */
    public static boolean update(MinecraftServer server, Voyage voyage) {
        VoyageData data = VoyageData.get(server);
        if (data.get(voyage.id()).isEmpty()) return false;
        data.put(voyage);
        return true;
    }

    /** Moves a voyage {@code blocks} along its route; at the end it arrives. Returns the voyage after the move. */
    public static Optional<Voyage> advance(MinecraftServer server, UUID id, double blocks) {
        Optional<Voyage> v = get(server, id);
        if (v.isEmpty()) return v;
        Voyage moved = VoyageRules.advance(v.get(), blocks);
        VoyageData.get(server).put(moved);
        if (moved.arrived()) arrive(server, moved);
        return Optional.of(moved);
    }

    /** At the end of its route: the planner's {@link VoyagePlanner#onArrive} continues it or it ends ARRIVED. */
    public static void arrive(MinecraftServer server, Voyage voyage) {
        Optional<Voyage> next = VoyageScheduler.planner(voyage.kind()).onArrive(server, voyage);
        if (next.isPresent()) update(server, next.get());
        else end(server, voyage.id(), VoyageEnd.ARRIVED);
    }

    /** Ends and removes a voyage; the end listeners hear of it. False if it was not active. */
    public static boolean end(MinecraftServer server, UUID id, VoyageEnd reason) {
        Optional<Voyage> removed = VoyageData.get(server).remove(id);
        if (removed.isEmpty()) return false;
        Voyage ended = removed.get().withState(Voyage.State.ENDED, removed.get().shipId());
        Constants.LOG.debug("Voyage {} ended: {}", ended.shortId(), reason.getSerializedName());
        for (EndListener l : END) l.onEnd(server, ended, reason);
        return true;
    }

    /** A random template id from a config list; the starter sloop if none parses. */
    public static ResourceLocation pickTemplate(List<String> ids, RandomSource rng) {
        List<ResourceLocation> parsed = ids.stream().map(ResourceLocation::tryParse).filter(java.util.Objects::nonNull).toList();
        if (parsed.isEmpty()) return ShipTemplates.STARTER_SLOOP_ID;
        return parsed.get(rng.nextInt(parsed.size()));
    }
}
