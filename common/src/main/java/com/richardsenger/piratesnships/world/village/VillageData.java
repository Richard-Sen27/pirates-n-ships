package com.richardsenger.piratesnships.world.village;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.world.WorldConfig;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Datagen of the seafarer village's worldgen files (vanilla formats, raw JSON): the structure, its structure set, the
 * five template pools and the biome tag.
 *
 * <p><b>Terrain adaptation {@code none}.</b> Vanilla's beardifier adds a beard for every rigid
 * {@code PoolElementStructurePiece} of a structure with the structure's one adaptation; it cannot exclude single
 * pieces (NeoForge's {@code PieceBeardifierModifier} could, but needs our own piece class and is loader-only). With
 * {@code beard_thin}, the pier (rigid, ground level at its deck) would get land raised under it and around the quay,
 * filling the berths. So the village uses {@code none}: streets follow the terrain (terrain matching), buildings sit
 * on the surface row, and on uneven ground buildings may stand partly in the hill or on a step.
 */
public final class VillageData {

    /** Fixed salt of the structure set's random spread (never change it: it moves every village of existing worlds). */
    public static final int SALT = 584_103_926;
    public static final int SIZE = 7;
    public static final int MAX_DISTANCE_FROM_CENTER = 80;
    public static final int START_HEIGHT = 1;

    /** Weights of the buildings pool (art/README.md "Structures (ST1)"). */
    public static final Map<String, Integer> BUILDINGS = weights("house_small", 3, "tavern", 1, "shipwright", 1);

    private VillageData() {
    }

    public static void gather(DataContributions data) {
        data.json(PackOutput.Target.DATA_PACK, "worldgen/structure", VillageKeys.SEAFARER_VILLAGE.location(), VillageData::structure);
        data.json(PackOutput.Target.DATA_PACK, "worldgen/structure_set", VillageKeys.SEAFARER_VILLAGES.location(), VillageData::structureSet);
        pool(data, VillageKeys.START, "minecraft:empty", "rigid", weights("dock_head", 1));
        pool(data, VillageKeys.STREETS, VillageKeys.TERMINATORS.location().toString(), "terrain_matching", weights("street", 1));
        pool(data, VillageKeys.BUILDINGS, "minecraft:empty", "rigid", BUILDINGS);
        pool(data, VillageKeys.PIER, "minecraft:empty", "rigid", weights("pier", 1));
        // The streets' fallback once the depth runs out. It must not be empty: vanilla skips a connector whose fallback
        // pool is empty (and not minecraft:empty), so an empty terminators pool would stop every street.
        pool(data, VillageKeys.TERMINATORS, "minecraft:empty", "terrain_matching", weights("street_end", 1));
        data.json(PackOutput.Target.DATA_PACK, "tags/worldgen/biome", VillageKeys.HAS_SEAFARER_VILLAGE.location(), () -> {
            JsonArray values = new JsonArray();
            values.add("#minecraft:is_beach");
            JsonObject json = new JsonObject();
            json.add("values", values);
            return json;
        });
    }

    static JsonObject structure() {
        JsonObject json = new JsonObject();
        json.addProperty("type", VillageKeys.PORT_VILLAGE_TYPE.toString());
        json.addProperty("biomes", "#" + VillageKeys.HAS_SEAFARER_VILLAGE.location());
        json.addProperty("step", "surface_structures");
        json.add("spawn_overrides", new JsonObject());
        json.addProperty("terrain_adaptation", "none");
        json.addProperty("start_pool", VillageKeys.START.location().toString());
        json.addProperty("size", SIZE);
        json.addProperty("start_height", START_HEIGHT);
        json.addProperty("max_distance_from_center", MAX_DISTANCE_FROM_CENTER);
        return json;
    }

    static JsonObject structureSet() {
        JsonObject entry = new JsonObject();
        entry.addProperty("structure", VillageKeys.SEAFARER_VILLAGE.location().toString());
        entry.addProperty("weight", 1);
        JsonArray structures = new JsonArray();
        structures.add(entry);
        JsonObject placement = new JsonObject();
        placement.addProperty("type", "minecraft:random_spread");
        placement.addProperty("salt", SALT);
        placement.addProperty("spacing", WorldConfig.SEAFARER_VILLAGE.spacing().defaultValue());
        placement.addProperty("separation", WorldConfig.SEAFARER_VILLAGE_SEPARATION.defaultValue());
        JsonObject json = new JsonObject();
        json.add("structures", structures);
        json.add("placement", placement);
        return json;
    }

    private static void pool(DataContributions data, ResourceKey<StructureTemplatePool> key, String fallback, String projection,
                             Map<String, Integer> pieces) {
        data.json(PackOutput.Target.DATA_PACK, "worldgen/template_pool", key.location(), () -> {
            JsonArray elements = new JsonArray();
            pieces.forEach((piece, weight) -> {
                JsonObject element = new JsonObject();
                element.addProperty("element_type", "minecraft:single_pool_element");
                element.addProperty("location", Constants.id("village/" + piece).toString());
                element.addProperty("processors", "minecraft:empty");
                element.addProperty("projection", projection);
                JsonObject entry = new JsonObject();
                entry.add("element", element);
                entry.addProperty("weight", weight);
                elements.add(entry);
            });
            JsonObject json = new JsonObject();
            json.addProperty("fallback", fallback);
            json.add("elements", elements);
            return json;
        });
    }

    private static Map<String, Integer> weights(Object... pairs) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) out.put((String) pairs[i], (Integer) pairs[i + 1]);
        return out;
    }
}
