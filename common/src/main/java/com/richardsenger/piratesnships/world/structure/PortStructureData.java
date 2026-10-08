package com.richardsenger.piratesnships.world.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.trade.market.PortKind;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Raw-JSON datagen helpers shared by the port structures (WG1 village, WG2 pirate island): the structure of type
 * {@code pirates_n_ships:port_village}, its random-spread structure set, single-element template pools of one piece
 * group, and a biome tag. Vanilla formats; the JUnit tests parse the output with vanilla's codecs.
 */
public final class PortStructureData {

    /** The structure type's id ({@link PortStructures#PORT_STRUCTURE}). */
    public static final ResourceLocation TYPE = Constants.id("port_village");

    private PortStructureData() {
    }

    /** Fields of one port structure's JSON. {@code spawnOverrides} is the vanilla {@code spawn_overrides} object. */
    public record Spec(TagKey<Biome> biomes, ResourceKey<StructureTemplatePool> startPool, int size, int startHeight,
                       int maxDistanceFromCenter, ShoreAnchor shoreAnchor, PortKind portKind, JsonObject spawnOverrides) {
    }

    public static JsonObject structure(Spec spec) {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE.toString());
        json.addProperty("biomes", "#" + spec.biomes().location());
        json.addProperty("step", "surface_structures");
        json.add("spawn_overrides", spec.spawnOverrides());
        json.addProperty("terrain_adaptation", "none");
        json.addProperty("start_pool", spec.startPool().location().toString());
        json.addProperty("size", spec.size());
        json.addProperty("start_height", spec.startHeight());
        json.addProperty("max_distance_from_center", spec.maxDistanceFromCenter());
        JsonObject anchor = new JsonObject();
        anchor.addProperty("x", spec.shoreAnchor().x());
        anchor.addProperty("z", spec.shoreAnchor().z());
        json.add("shore_anchor", anchor);
        json.addProperty("port_kind", spec.portKind().getSerializedName());
        return json;
    }

    public static JsonObject structureSet(ResourceKey<Structure> structure, int salt, int spacing, int separation) {
        JsonObject entry = new JsonObject();
        entry.addProperty("structure", structure.location().toString());
        entry.addProperty("weight", 1);
        JsonArray structures = new JsonArray();
        structures.add(entry);
        JsonObject placement = new JsonObject();
        placement.addProperty("type", "minecraft:random_spread");
        placement.addProperty("salt", salt);
        placement.addProperty("spacing", spacing);
        placement.addProperty("separation", separation);
        JsonObject json = new JsonObject();
        json.add("structures", structures);
        json.add("placement", placement);
        return json;
    }

    public static void writeStructure(DataContributions data, ResourceKey<Structure> key, Spec spec) {
        data.json(PackOutput.Target.DATA_PACK, "worldgen/structure", key.location(), () -> structure(spec));
    }

    public static void writeStructureSet(DataContributions data, ResourceKey<StructureSet> key, ResourceKey<Structure> structure,
                                         int salt, int spacing, int separation) {
        data.json(PackOutput.Target.DATA_PACK, "worldgen/structure_set", key.location(),
                () -> structureSet(structure, salt, spacing, separation));
    }

    /**
     * A pool of single pool elements {@code pirates_n_ships:<group>/<piece>} with one projection. Every element names
     * the processor list {@link ConnectionsProcessor#LIST}, which connects the template's fences, panes, bars and walls
     * (jigsaw placement skips vanilla's neighbour-shape pass; WG4).
     */
    public static void pool(DataContributions data, ResourceKey<StructureTemplatePool> key, String group, String fallback,
                            String projection, Map<String, Integer> pieces) {
        data.json(PackOutput.Target.DATA_PACK, "worldgen/template_pool", key.location(), () -> {
            JsonArray elements = new JsonArray();
            pieces.forEach((piece, weight) -> {
                JsonObject element = new JsonObject();
                element.addProperty("element_type", "minecraft:single_pool_element");
                element.addProperty("location", Constants.id(group + "/" + piece).toString());
                element.addProperty("processors", ConnectionsProcessor.LIST.location().toString());
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

    /** A biome tag whose values are the given entries ({@code "#ns:tag"} or {@code "ns:biome"}). */
    public static void biomeTag(DataContributions data, TagKey<Biome> tag, String... values) {
        data.json(PackOutput.Target.DATA_PACK, "tags/worldgen/biome", tag.location(), () -> {
            JsonArray array = new JsonArray();
            for (String v : values) array.add(v);
            JsonObject json = new JsonObject();
            json.add("values", array);
            return (JsonElement) json;
        });
    }

    /** An ordered map from alternating (name, weight) pairs. */
    public static Map<String, Integer> weights(Object... pairs) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) out.put((String) pairs[i], (Integer) pairs[i + 1]);
        return out;
    }
}
