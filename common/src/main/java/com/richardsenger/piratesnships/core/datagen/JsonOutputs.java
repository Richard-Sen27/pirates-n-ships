package com.richardsenger.piratesnships.core.datagen;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Raw JSON files contributed through {@link DataContributions#json} / {@code encoded} / {@code definitions}.
 * Output paths are checked for duplicates when they are added, so a clash fails the data run with both callers'
 * path in the message.
 */
final class JsonOutputs {

    record Entry(PackOutput.Target target, String directory, ResourceLocation id, Function<HolderLookup.Provider, JsonElement> json) {
        /** Path relative to the output root, e.g. {@code data/sable/physics_block_properties/x.json}. */
        String relativePath() {
            String dir = directory.isEmpty() ? "" : directory + "/"; // empty: a file at the namespace root
            return folder(target) + "/" + id.getNamespace() + "/" + dir + id.getPath() + ".json";
        }
    }

    /** Top-level folder of a pack target ({@code PackOutput.Target.directory} is not public). */
    static String folder(PackOutput.Target target) {
        return switch (target) {
            case DATA_PACK -> "data";
            case RESOURCE_PACK -> "assets";
            case REPORTS -> "reports";
        };
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    synchronized void add(PackOutput.Target target, String directory, ResourceLocation id, Function<HolderLookup.Provider, JsonElement> json) {
        if (directory.startsWith("/") || directory.endsWith("/") || directory.equals(".") || directory.contains("./")) {
            throw new IllegalArgumentException("Invalid datagen JSON directory '" + directory + "' for " + id);
        }
        if (target == PackOutput.Target.RESOURCE_PACK && directory.isEmpty() && id.getPath().equals("sounds")) {
            throw new IllegalArgumentException("Don't write " + id.getNamespace() + "/sounds.json through data.json: add entries with data.sounds(...)");
        }
        Entry e = new Entry(target, directory, id, json);
        if (entries.putIfAbsent(e.relativePath(), e) != null) {
            throw new IllegalStateException("Duplicate datagen output " + e.relativePath() + " (two contributions for " + id + " in " + directory + ")");
        }
    }

    synchronized List<Entry> entries(PackOutput.Target target) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries.values()) if (e.target() == target) out.add(e);
        return out;
    }

    static <T> JsonElement encode(Codec<T> codec, T value, HolderLookup.Provider registries) {
        return codec.encodeStart(RegistryOps.create(JsonOps.INSTANCE, registries), value)
                .getOrThrow(msg -> new IllegalStateException("Failed to encode datagen JSON value " + value + ": " + msg));
    }

    /** Writes every entry of one pack target. */
    record Provider(PackOutput output, PackOutput.Target target, JsonOutputs outputs,
                    CompletableFuture<HolderLookup.Provider> lookup) implements DataProvider {
        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            return lookup.thenCompose(registries -> {
                List<CompletableFuture<?>> futures = new ArrayList<>();
                for (Entry e : outputs.entries(target)) {
                    PackOutput.PathProvider paths = output.createPathProvider(target, e.directory());
                    futures.add(DataProvider.saveStable(cache, e.json().apply(registries), paths.json(e.id())));
                }
                return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
            });
        }

        @Override
        public String getName() {
            return "Raw JSON (" + folder(target) + "): " + Constants.MOD_ID;
        }
    }
}
