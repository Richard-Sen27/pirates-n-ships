package com.richardsenger.piratesnships.world.port;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The ports known to a server, without world access (the pure half of {@link PortRegistry}). Immutable; {@link #with}
 * returns a copy. Lookups: by id, nearest to a position, containing a position (same dimension only).
 */
public record PortIndex(Map<ResourceLocation, Port> ports) {

    public static final PortIndex EMPTY = new PortIndex(Map.of());

    /** Stored as a list (ids are inside each port), in insertion order. */
    public static final Codec<PortIndex> CODEC = Port.CODEC.listOf().xmap(PortIndex::of, PortIndex::all);

    public PortIndex {
        ports = Collections.unmodifiableMap(new LinkedHashMap<>(ports));
    }

    public static PortIndex of(List<Port> list) {
        Map<ResourceLocation, Port> map = new LinkedHashMap<>();
        for (Port p : list) map.putIfAbsent(p.id(), p);
        return new PortIndex(map);
    }

    public List<Port> all() {
        return List.copyOf(ports.values());
    }

    public int size() {
        return ports.size();
    }

    public Optional<Port> byId(ResourceLocation id) {
        return Optional.ofNullable(ports.get(id));
    }

    /** A copy with {@code port} added; an existing port with the same id is kept (generation reports it per chunk). */
    public PortIndex with(Port port) {
        if (ports.containsKey(port.id())) return this;
        Map<ResourceLocation, Port> map = new LinkedHashMap<>(ports);
        map.put(port.id(), port);
        return new PortIndex(map);
    }

    /** A copy with {@code port} replacing the known port of the same id; unchanged if that id is unknown. */
    public PortIndex replace(Port port) {
        if (!ports.containsKey(port.id())) return this;
        Map<ResourceLocation, Port> map = new LinkedHashMap<>(ports);
        map.put(port.id(), port);
        return new PortIndex(map);
    }

    /** A copy without the port {@code id}. */
    public PortIndex without(ResourceLocation id) {
        if (!ports.containsKey(id)) return this;
        Map<ResourceLocation, Port> map = new LinkedHashMap<>(ports);
        map.remove(id);
        return new PortIndex(map);
    }

    /** The port nearest to {@code pos} (horizontal distance to its centre) in {@code dim}. */
    public Optional<Port> nearest(ResourceKey<Level> dim, BlockPos pos) {
        return ports.values().stream().filter(p -> p.dimension().equals(dim)).min(byDistanceTo(pos));
    }

    /** The port whose bounding box contains {@code pos} (the one with the nearest centre if boxes overlap). */
    public Optional<Port> containing(ResourceKey<Level> dim, BlockPos pos) {
        return ports.values().stream().filter(p -> p.contains(dim, pos)).min(byDistanceTo(pos));
    }

    private static Comparator<Port> byDistanceTo(BlockPos pos) {
        return Comparator.comparingDouble((Port p) -> horizontalDistanceSqr(p.centre(), pos)).thenComparing(p -> p.id().toString());
    }

    public static double horizontalDistanceSqr(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }
}
