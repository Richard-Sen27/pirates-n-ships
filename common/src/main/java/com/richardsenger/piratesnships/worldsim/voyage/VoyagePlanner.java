package com.richardsenger.piratesnships.worldsim.voyage;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Optional;

/**
 * The behaviour of one {@link VoyageKind} in the scheduler (WS2). Register one per kind with
 * {@link VoyageScheduler#register}; WS2 registers the CONVOY planner ({@link ConvoyPlanner}), WS4b PATROL, WS5 RAID.
 * All hooks run on the server thread during a scheduler check and are optional.
 *
 * <p>Per check, in this order: {@link #ready} (voyages planned earlier that can depart now), {@link #update} and
 * {@link #speedFactor} for every SAILING voyage of the kind, {@link #onArrive} for those at the end of their route,
 * then one spawn roll with {@link #spawnChance} that calls {@link #plan} on success. Every new voyage passes through
 * {@link #onDepart} before it is stored. The cap {@code world_simulation.max_simultaneous_voyages} applies to
 * {@link #ready} and {@link #plan}.
 *
 * <pre>{@code
 * VoyageScheduler.register(VoyageKind.PATROL, new VoyagePlanner() {
 *     public double spawnChance(MinecraftServer s, int interval) { return VoyageRules.chancePerCheck(perDay(s), interval); }
 *     public Optional<Voyage> plan(MinecraftServer s, RandomSource rng) { return patrolRoute(s, rng); }
 *     public Voyage update(MinecraftServer s, Voyage v) { return hunting(s, v); } // e.g. withRoute(...) toward a target
 *     public Optional<Voyage> onArrive(MinecraftServer s, Voyage v) { return Optional.of(v.withRoute(v.to(), v.from(), back(v))); }
 * });
 * }</pre>
 */
public interface VoyagePlanner {

    /** Chance that a voyage of this kind departs in one check of {@code intervalTicks} (0 = only on request). */
    default double spawnChance(MinecraftServer server, int intervalTicks) {
        return 0.0;
    }

    /**
     * A new voyage after a successful spawn roll, not yet stored (use {@link Voyage#depart}). Empty when nothing fits
     * right now; a planner that needs a lane which is not cached yet queues it ({@code Lanes.between}) and may return
     * the voyage later from {@link #ready}.
     */
    default Optional<Voyage> plan(MinecraftServer server, RandomSource rng) {
        return Optional.empty();
    }

    /** Voyages planned in an earlier check that can depart now (their lane is ready). Called every check. */
    default List<Voyage> ready(MinecraftServer server) {
        return List.of();
    }

    /** Before a new voyage is stored (a convoy buys its cargo here). Returns the voyage to store. */
    default Voyage onDepart(MinecraftServer server, Voyage voyage) {
        return voyage;
    }

    /**
     * Once per check for each SAILING voyage of this kind, before it moves: return the voyage to keep (e.g. a pursuit
     * course with {@link Voyage#withRoute}). Returning a voyage in another state stops it moving this check.
     */
    default Voyage update(MinecraftServer server, Voyage voyage) {
        return voyage;
    }

    /** Multiplies the configured speed for this voyage (e.g. a patrol in pursuit). */
    default double speedFactor(MinecraftServer server, Voyage voyage) {
        return 1.0;
    }

    /**
     * At the end of its route. Return a continuing voyage (e.g. a patrol turning for home) or empty to end it with
     * {@link VoyageEnd#ARRIVED}.
     */
    default Optional<Voyage> onArrive(MinecraftServer server, Voyage voyage) {
        return Optional.empty();
    }
}
