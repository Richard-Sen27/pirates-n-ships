package com.richardsenger.piratesnships.worldsim.voyage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.worldsim.lane.Lane;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One abstract NPC voyage (WS2, design.md §10.4 "Abstract voyages"). Immutable; the scheduler replaces it in
 * {@link VoyageData}.
 *
 * <ul>
 *   <li>{@code waypoints}: the route in block coordinates (a lane, or a straight pursuit course set by a planner);
 *       {@code progress}: blocks travelled along them. The current leg is {@link #legIndex()}.</li>
 *   <li>{@code cargo}: units per trade good aboard (a convoy's cargo, bought at {@code from}).</li>
 *   <li>{@code crew}, {@code fighters}: people aboard; {@link #UNMANNED} (−1) = not decided yet, the materialiser
 *       (WS3b) mans the ship from its config the first time.</li>
 *   <li>{@code health}: 0..1, the hull's condition carried over from a materialised ship.</li>
 *   <li>{@code shipId}: the Sable ship while MATERIALISED (WS3b); {@code pursuit}: the ship a patrol hunts (WS4b).</li>
 * </ul>
 */
public record Voyage(UUID id, VoyageKind kind, Faction faction, ResourceLocation template, ResourceLocation from,
                     ResourceLocation to, List<Lane.Point> waypoints, double progress, Map<ResourceLocation, Integer> cargo,
                     int crew, int fighters, double health, State state, long departedTick, Optional<UUID> shipId,
                     Optional<UUID> pursuit) {

    public static final int UNMANNED = -1;

    public enum State implements StringRepresentable {
        /** Abstract: moved by the scheduler. */
        SAILING,
        /** A real ship (WS3b): the scheduler leaves it alone. */
        MATERIALISED,
        ENDED;

        public static final Codec<State> CODEC = StringRepresentable.fromEnum(State::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final Codec<Faction> FACTION_CODEC = Codec.STRING.xmap(
            s -> Faction.valueOf(s.toUpperCase(Locale.ROOT)), f -> f.name().toLowerCase(Locale.ROOT));

    public static final Codec<Voyage> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Voyage::id),
            VoyageKind.CODEC.fieldOf("kind").forGetter(Voyage::kind),
            FACTION_CODEC.fieldOf("faction").forGetter(Voyage::faction),
            ResourceLocation.CODEC.fieldOf("template").forGetter(Voyage::template),
            ResourceLocation.CODEC.fieldOf("from").forGetter(Voyage::from),
            ResourceLocation.CODEC.fieldOf("to").forGetter(Voyage::to),
            Lane.Point.CODEC.listOf().fieldOf("waypoints").forGetter(Voyage::waypoints),
            Codec.DOUBLE.fieldOf("progress").forGetter(Voyage::progress),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("cargo", Map.of()).forGetter(Voyage::cargo),
            Codec.INT.optionalFieldOf("crew", UNMANNED).forGetter(Voyage::crew),
            Codec.INT.optionalFieldOf("fighters", UNMANNED).forGetter(Voyage::fighters),
            Codec.DOUBLE.optionalFieldOf("health", 1.0).forGetter(Voyage::health),
            State.CODEC.fieldOf("state").forGetter(Voyage::state),
            Codec.LONG.fieldOf("departed").forGetter(Voyage::departedTick),
            UUIDUtil.CODEC.optionalFieldOf("ship").forGetter(Voyage::shipId),
            UUIDUtil.CODEC.optionalFieldOf("pursuit").forGetter(Voyage::pursuit)
    ).apply(i, Voyage::new));

    public Voyage {
        waypoints = List.copyOf(waypoints);
        cargo = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(cargo));
        health = Math.max(0.0, Math.min(1.0, health));
    }

    /** A fresh SAILING voyage at the start of {@code waypoints}. */
    public static Voyage depart(UUID id, VoyageKind kind, Faction faction, ResourceLocation template, ResourceLocation from,
                                ResourceLocation to, List<Lane.Point> waypoints, Map<ResourceLocation, Integer> cargo, long now) {
        return new Voyage(id, kind, faction, template, from, to, waypoints, 0.0, cargo, UNMANNED, UNMANNED, 1.0,
                State.SAILING, now, Optional.empty(), Optional.empty());
    }

    /** Length of the route in blocks. */
    public double length() {
        return Lane.length(waypoints);
    }

    /** Blocks still to sail. */
    public double remaining() {
        return Math.max(0.0, length() - progress);
    }

    public boolean arrived() {
        return progress >= length() - 1e-6;
    }

    /** The current position along the route (block x, z, leg, heading). */
    public Lane.Position position() {
        return Lane.positionAlong(waypoints, progress);
    }

    /** The leg it is on (leg {@code i} runs from waypoint {@code i} to {@code i+1}). */
    public int legIndex() {
        return position().leg();
    }

    /** Total units of cargo aboard. */
    public int cargoUnits() {
        int n = 0;
        for (int v : cargo.values()) n += v;
        return n;
    }

    public Voyage withProgress(double newProgress) {
        return new Voyage(id, kind, faction, template, from, to, waypoints, Math.max(0.0, Math.min(length(), newProgress)), cargo,
                crew, fighters, health, state, departedTick, shipId, pursuit);
    }

    /** A new route from the start (progress 0), e.g. a patrol turning for home or a pursuit course. */
    public Voyage withRoute(ResourceLocation newFrom, ResourceLocation newTo, List<Lane.Point> newWaypoints) {
        return new Voyage(id, kind, faction, template, newFrom, newTo, newWaypoints, 0.0, cargo, crew, fighters, health, state,
                departedTick, shipId, pursuit);
    }

    public Voyage withCargo(Map<ResourceLocation, Integer> newCargo) {
        return new Voyage(id, kind, faction, template, from, to, waypoints, progress, newCargo, crew, fighters, health, state,
                departedTick, shipId, pursuit);
    }

    public Voyage withCrew(int newCrew, int newFighters) {
        return new Voyage(id, kind, faction, template, from, to, waypoints, progress, cargo, newCrew, newFighters, health, state,
                departedTick, shipId, pursuit);
    }

    public Voyage withHealth(double newHealth) {
        return new Voyage(id, kind, faction, template, from, to, waypoints, progress, cargo, crew, fighters, newHealth, state,
                departedTick, shipId, pursuit);
    }

    public Voyage withState(State newState, Optional<UUID> newShipId) {
        return new Voyage(id, kind, faction, template, from, to, waypoints, progress, cargo, crew, fighters, health, newState,
                departedTick, newShipId, pursuit);
    }

    public Voyage withPursuit(Optional<UUID> newPursuit) {
        return new Voyage(id, kind, faction, template, from, to, waypoints, progress, cargo, crew, fighters, health, state,
                departedTick, shipId, newPursuit);
    }

    /** The first 8 characters of the id (commands). */
    public String shortId() {
        return id.toString().substring(0, 8);
    }
}
