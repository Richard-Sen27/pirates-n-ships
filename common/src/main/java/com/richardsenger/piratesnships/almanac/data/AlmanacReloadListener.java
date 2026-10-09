package com.richardsenger.piratesnships.almanac.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.data.DefinitionParser;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Loads the Almanac's chapters from {@code data/<namespace>/almanac/chapters/*.json} (subfolders allowed) into
 * {@link AlmanacBook#server()}. Files are read off-thread, decoded on the server thread. A broken file is logged with
 * its id and reason and skipped; the reload never fails because of a chapter. Registered through
 * {@code CommonEvents.ADD_RELOAD_LISTENERS} ({@link AlmanacData}); the new book reaches clients through
 * {@code CommonEvents.DATAPACK_SYNC}, which fires for every player after the reload.
 */
public final class AlmanacReloadListener extends SimplePreparableReloadListener<AlmanacReloadListener.Prepared> {

    /** The datapack directory of chapter files. */
    public static final String DIRECTORY = "almanac/chapters";

    record Prepared(Map<ResourceLocation, JsonElement> json, List<DefinitionParser.Error> readErrors) {
    }

    @Override
    protected Prepared prepare(ResourceManager manager, ProfilerFiller profiler) {
        FileToIdConverter files = FileToIdConverter.json(DIRECTORY);
        Map<ResourceLocation, JsonElement> json = new TreeMap<>();
        List<DefinitionParser.Error> errors = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Resource> file : files.listMatchingResources(manager).entrySet()) {
            ResourceLocation id = files.fileToId(file.getKey());
            try (Reader reader = file.getValue().openAsReader()) {
                json.put(id, JsonParser.parseReader(reader));
            } catch (Exception e) {
                errors.add(new DefinitionParser.Error(id, "unreadable JSON in " + file.getKey() + " ("
                        + file.getValue().sourcePackId() + "): " + e.getMessage()));
            }
        }
        return new Prepared(json, errors);
    }

    @Override
    protected void apply(Prepared prepared, ResourceManager manager, ProfilerFiller profiler) {
        ChapterParser.Result result = ChapterParser.parse(prepared.json());
        for (DefinitionParser.Error e : prepared.readErrors()) Constants.LOG.error("Skipping almanac chapter {}: {}", e.id(), e.message());
        for (DefinitionParser.Error e : result.errors()) Constants.LOG.error("Skipping almanac chapter {}: {}", e.id(), e.message());
        AlmanacBook.setServer(result.book());
        Constants.LOG.debug("Loaded {} almanac chapters", result.book().size());
    }
}
