package com.richardsenger.piratesnships.core.data;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The pure parsing step of definition loading: id → JSON in, id → value plus errors out. No world, no
 * {@code Services}; safe to call from JUnit (bootstrap vanilla first if the codec touches registries).
 */
public final class DefinitionParser {

    /** One entry that could not be parsed. */
    public record Error(ResourceLocation id, String message) {
        @Override
        public String toString() {
            return id + ": " + message;
        }
    }

    /** Parsed entries (sorted by id, unmodifiable) and the entries that were skipped. */
    public record Result<T>(Map<ResourceLocation, T> values, List<Error> errors) {
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    private DefinitionParser() {
    }

    /**
     * Decodes every entry. An entry whose JSON is null or does not decode is skipped and reported in
     * {@link Result#errors()}; it never affects the other entries. A codec that throws counts as a decode error.
     * Each id appears at most once (the map's keys). Overriding by id between datapacks happens before this step:
     * the resource manager hands over only the top-most pack's file for each id, so an override replaces the whole
     * entry (no field merging). Partial results of a failed decode are discarded, never used.
     *
     * @param ops JSON ops; use {@code RegistryOps.create(JsonOps.INSTANCE, registries)} when the codec refers to
     *            registry entries, or plain {@code JsonOps.INSTANCE}
     */
    public static <T> Result<T> parse(Decoder<T> decoder, DynamicOps<JsonElement> ops, Map<ResourceLocation, JsonElement> input) {
        Map<ResourceLocation, T> values = new TreeMap<>();
        List<Error> errors = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> e : new TreeMap<>(input).entrySet()) {
            ResourceLocation id = e.getKey();
            JsonElement json = e.getValue();
            if (json == null || json.isJsonNull()) {
                errors.add(new Error(id, "empty file"));
                continue;
            }
            DataResult<T> result;
            try {
                result = decoder.parse(ops, json);
            } catch (RuntimeException ex) {
                errors.add(new Error(id, "decoder threw " + ex));
                continue;
            }
            Optional<T> value = result.result();
            if (value.isPresent()) {
                values.put(id, value.get());
            } else {
                errors.add(new Error(id, result.error().map(DataResult.Error::message).orElse("unknown decode error")));
            }
        }
        return new Result<>(Collections.unmodifiableMap(values), List.copyOf(errors));
    }
}
