package com.richardsenger.piratesnships.core.data;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * An immutable snapshot of the loaded entries of one {@link DefinitionType}, sorted by id. This is what gameplay
 * code passes around: pure logic takes a {@code Definitions<T>} (or a single {@code T}) as a parameter instead of
 * reaching for the static store, so JUnit tests can build one by hand with {@link #of(String, Map)}.
 */
public final class Definitions<T> {

    private final String typeName;
    private final Map<ResourceLocation, T> entries;

    private Definitions(String typeName, Map<ResourceLocation, T> entries) {
        this.typeName = typeName;
        this.entries = entries;
    }

    /** A snapshot of {@code entries} (copied). {@code typeName} only appears in error messages. */
    public static <T> Definitions<T> of(String typeName, Map<ResourceLocation, ? extends T> entries) {
        return new Definitions<>(typeName, Collections.unmodifiableMap(new TreeMap<>(entries)));
    }

    public static <T> Definitions<T> empty(String typeName) {
        return new Definitions<>(typeName, Map.of());
    }

    public Optional<T> get(ResourceLocation id) {
        return Optional.ofNullable(entries.get(id));
    }

    /** The entry, or an {@link IllegalArgumentException} naming the type and id. */
    public T require(ResourceLocation id) {
        T value = entries.get(id);
        if (value == null) {
            throw new IllegalArgumentException("Unknown " + typeName + " definition " + id + " (loaded: " + entries.size() + ")");
        }
        return value;
    }

    public boolean contains(ResourceLocation id) {
        return entries.containsKey(id);
    }

    /** All entries, sorted by id, unmodifiable. */
    public Map<ResourceLocation, T> all() {
        return entries;
    }

    /** All ids, sorted, unmodifiable. */
    public Set<ResourceLocation> ids() {
        return entries.keySet();
    }

    public Collection<T> values() {
        return entries.values();
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public String typeName() {
        return typeName;
    }

    @Override
    public String toString() {
        return "Definitions[" + typeName + ", " + entries.size() + " entries]";
    }
}
