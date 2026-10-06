package com.richardsenger.piratesnships.core.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Loads every {@link DefinitionType} from {@code data/<ns>/pirates_n_ships/<type>/}. Reading happens off-thread,
 * decoding and store replacement on the server thread. Broken files are logged with id and reason and skipped.
 */
final class DefinitionReloadListener extends SimplePreparableReloadListener<DefinitionReloadListener.Prepared> {

    record Prepared(Map<DefinitionType<?>, Map<ResourceLocation, JsonElement>> json, Map<DefinitionType<?>, List<DefinitionParser.Error>> readErrors) { }

    private final HolderLookup.Provider registries;

    DefinitionReloadListener(HolderLookup.Provider registries) {
        this.registries = registries;
    }

    @Override
    protected Prepared prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<DefinitionType<?>, Map<ResourceLocation, JsonElement>> json = new LinkedHashMap<>();
        Map<DefinitionType<?>, List<DefinitionParser.Error>> errors = new LinkedHashMap<>();
        for (DefinitionType<?> type : DefinitionType.all()) {
            FileToIdConverter files = FileToIdConverter.json(type.directory());
            Map<ResourceLocation, JsonElement> entries = new TreeMap<>();
            List<DefinitionParser.Error> typeErrors = new ArrayList<>();
            for (Map.Entry<ResourceLocation, Resource> file : files.listMatchingResources(manager).entrySet()) {
                ResourceLocation id = files.fileToId(file.getKey());
                try (Reader reader = file.getValue().openAsReader()) {
                    entries.put(id, JsonParser.parseReader(reader));
                } catch (Exception e) {
                    typeErrors.add(new DefinitionParser.Error(id, "unreadable JSON in " + file.getKey() + " (" + file.getValue().sourcePackId() + "): " + e.getMessage()));
                }
            }
            json.put(type, entries);
            errors.put(type, typeErrors);
        }
        return new Prepared(json, errors);
    }

    @Override
    protected void apply(Prepared prepared, ResourceManager manager, ProfilerFiller profiler) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        prepared.json().forEach((type, json) -> load(type, json, prepared.readErrors().getOrDefault(type, List.of()), ops));
    }

    private static <T> void load(DefinitionType<T> type, Map<ResourceLocation, JsonElement> json, List<DefinitionParser.Error> readErrors, RegistryOps<JsonElement> ops) {
        DefinitionParser.Result<T> result = DefinitionParser.parse(type.codec(), ops, json);
        for (DefinitionParser.Error e : readErrors) Constants.LOG.error("Skipping {} definition {}: {}", type.name(), e.id(), e.message());
        for (DefinitionParser.Error e : result.errors()) Constants.LOG.error("Skipping {} definition {}: {}", type.name(), e.id(), e.message());
        type.acceptServer(result.values());
        Constants.LOG.debug("Loaded {} {} definitions", result.values().size(), type.name());
    }
}
