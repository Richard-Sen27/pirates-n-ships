package com.richardsenger.piratesnships.world.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.WorldConfig;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Raw-JSON datagen helpers shared by the port structures (WG1 village, WG2 pirate island): the structure of type
 * {@code pirates_n_ships:port_village}, its random-spread structure set, single-element template pools of one piece
 * group, and a biome tag. Vanilla formats; the JUnit tests parse the output with vanilla's codecs.
 */
public final class PortStructureData {

    /** The structure type's id ({@link PortStructures#PORT_STRUCTURE}). */
    public static final ResourceLocation TYPE = Constants.id("port_village");

    /** The structure sets' placement type's id ({@link PortStructures#CONFIGURED_SPREAD}, WG5). */
    public static final ResourceLocation PLACEMENT_TYPE = Constants.id("configured_spread");

    /**
     * STR8: the shores a port may start on, warm and cold alike ({@code #minecraft:is_beach}); every kind's
     * {@code has_structure} tag names it. Which of a kind's two structures takes a site is decided by
     * {@link #COLD_SHORES} (a tag cannot subtract the snowy beach from {@code is_beach}).
     */
    public static final TagKey<Biome> PORT_SHORES = TagKey.create(net.minecraft.core.registries.Registries.BIOME,
            Constants.id("port_shores"));
    /**
     * STR8 (the human: "don't create the structures right in ice biomes, that doesn't fit ... though a rare chance for
     * that would still be good"): the snowy and frozen biomes. A site whose start or whose sea in front of it lies in one
     * is cold: the kind's cold structure ({@code <kind>_cold}, its own structure set, {@code cold_frequency}) takes it,
     * the kind's other structure never does ({@code PortStructure}'s {@code cold_biomes} and {@code cold}).
     */
    public static final TagKey<Biome> COLD_SHORES = TagKey.create(net.minecraft.core.registries.Registries.BIOME,
            Constants.id("cold_shores"));
    /** The cold shores' vanilla biomes; the convention tags {@code c:is_snowy} and {@code c:is_icy} join as optional entries. */
    public static final List<String> COLD_BIOMES = List.of("minecraft:snowy_beach", "minecraft:frozen_ocean",
            "minecraft:deep_frozen_ocean", "minecraft:frozen_river", "minecraft:snowy_plains", "minecraft:ice_spikes",
            "minecraft:snowy_taiga", "minecraft:snowy_slopes", "minecraft:grove", "minecraft:frozen_peaks", "minecraft:jagged_peaks");

    /** STR8: vanilla's village structure set, which every port keeps away from. */
    public static final ResourceKey<StructureSet> VANILLA_VILLAGES = ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE_SET,
            net.minecraft.resources.ResourceLocation.withDefaultNamespace("villages"));
    /** STR8: vanilla's woodland mansion structure set (the human: "navy_post directly next to mansion"). */
    public static final ResourceKey<StructureSet> WOODLAND_MANSIONS = ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE_SET,
            net.minecraft.resources.ResourceLocation.withDefaultNamespace("woodland_mansions"));
    /** STR8: no port starts within this many chunks of a vanilla village's placement chunk. */
    public static final int VANILLA_VILLAGE_EXCLUSION_CHUNKS = 6;
    /** STR8: no port starts within this many chunks of a woodland mansion's placement chunk. */
    public static final int MANSION_EXCLUSION_CHUNKS = 10;

    /** STR8: the exclusion zones every port's structure sets carry against vanilla's villages and mansions. */
    public static List<Exclusion> vanillaExclusions() {
        return List.of(new Exclusion(VANILLA_VILLAGES, VANILLA_VILLAGE_EXCLUSION_CHUNKS), new Exclusion(WOODLAND_MANSIONS, MANSION_EXCLUSION_CHUNKS));
    }

    private PortStructureData() {
    }

    /** STR8: the shore tags ({@link #PORT_SHORES}, {@link #COLD_SHORES}). */
    public static void gatherShores(DataContributions data) {
        biomeTag(data, PORT_SHORES, "#minecraft:is_beach");
        data.json(PackOutput.Target.DATA_PACK, "tags/worldgen/biome", COLD_SHORES.location(), () -> {
            JsonArray array = new JsonArray();
            COLD_BIOMES.forEach(array::add);
            for (String tag : new String[] {"#c:is_snowy", "#c:is_icy"}) {
                JsonObject optional = new JsonObject();
                optional.addProperty("id", tag);
                optional.addProperty("required", false);
                array.add(optional);
            }
            JsonObject json = new JsonObject();
            json.add("values", array);
            return (JsonElement) json;
        });
    }

    /**
     * STR8: a kind's structure JSON with the cold rule: {@code cold_biomes} {@link #COLD_SHORES} and, for the cold
     * structure, {@code cold: true}.
     */
    public static JsonObject withCold(JsonObject structure, boolean cold) {
        structure.addProperty("cold_biomes", "#" + COLD_SHORES.location());
        if (cold) structure.addProperty("cold", true);
        return structure;
    }

    /**
     * STR8: writes a kind's two structures (the JSON from {@code structure}, the cold one at {@code coldKey} with
     * {@code cold: true}) and their two structure sets: the same placement (salt, spacing, exclusion zones), so they share
     * the grid and the zones, and at most one of them takes a site (the biome decides).
     */
    public static void writePort(DataContributions data, ResourceKey<Structure> key, ResourceKey<Structure> coldKey,
                                 java.util.function.Supplier<JsonObject> structure, ResourceKey<StructureSet> set,
                                 ResourceKey<StructureSet> coldSet, String config, int salt, List<Exclusion> exclusions) {
        data.json(PackOutput.Target.DATA_PACK, "worldgen/structure", key.location(), () -> withCold(structure.get(), false));
        data.json(PackOutput.Target.DATA_PACK, "worldgen/structure", coldKey.location(), () -> withCold(structure.get(), true));
        writeStructureSet(data, set, key, config, salt, exclusions);
        writeStructureSet(data, coldSet, coldKey, config, salt, exclusions);
    }

    /** A processor list of our processor types, in order ({@code connections}, {@code trail_bed}, ...). */
    public static void processorList(DataContributions data, ResourceKey<StructureProcessorList> key, String... types) {
        data.json(PackOutput.Target.DATA_PACK, "worldgen/processor_list", key.location(), () -> {
            JsonArray processors = new JsonArray();
            for (String type : types) {
                JsonObject processor = new JsonObject();
                processor.addProperty("processor_type", Constants.id(type).toString());
                processors.add(processor);
            }
            JsonObject json = new JsonObject();
            json.add("processors", processors);
            return json;
        });
    }

    /**
     * Fields of one port structure's JSON. {@code spawnOverrides} is the vanilla {@code spawn_overrides} object,
     * {@code terrainAdaptation} vanilla's {@code terrain_adaptation} ({@code none} unless a structure asks for more;
     * STR2: the pirate island uses {@code beard_thin}), {@code landPiecesOnLand} {@link PortStructure}'s
     * {@code land_pieces_on_land} (STR2: land pieces over open water are dropped, {@link LandFilter}).
     */
    public record Spec(TagKey<Biome> biomes, ResourceKey<StructureTemplatePool> startPool, int size, int startHeight,
                       int maxDistanceFromCenter, ShoreAnchor shoreAnchor, PortKind portKind, JsonObject spawnOverrides,
                       String terrainAdaptation, boolean landPiecesOnLand) {

        /** A spec without terrain adaptation ({@code none}) and without the land filter, as WG1 and WG3 wrote theirs. */
        public Spec(TagKey<Biome> biomes, ResourceKey<StructureTemplatePool> startPool, int size, int startHeight,
                    int maxDistanceFromCenter, ShoreAnchor shoreAnchor, PortKind portKind, JsonObject spawnOverrides) {
            this(biomes, startPool, size, startHeight, maxDistanceFromCenter, shoreAnchor, portKind, spawnOverrides, "none", false);
        }
    }

    public static JsonObject structure(Spec spec) {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE.toString());
        json.addProperty("biomes", "#" + spec.biomes().location());
        json.addProperty("step", "surface_structures");
        json.add("spawn_overrides", spec.spawnOverrides());
        json.addProperty("terrain_adaptation", spec.terrainAdaptation());
        json.addProperty("start_pool", spec.startPool().location().toString());
        json.addProperty("size", spec.size());
        json.addProperty("start_height", spec.startHeight());
        json.addProperty("max_distance_from_center", spec.maxDistanceFromCenter());
        JsonObject anchor = new JsonObject();
        anchor.addProperty("x", spec.shoreAnchor().x());
        anchor.addProperty("z", spec.shoreAnchor().z());
        json.add("shore_anchor", anchor);
        json.addProperty("port_kind", spec.portKind().getSerializedName());
        if (spec.landPiecesOnLand()) json.addProperty("land_pieces_on_land", true);
        return json;
    }

    /**
     * A structure set of one structure with a {@code pirates_n_ships:configured_spread} placement (WG5): the server
     * config section {@code world.structures.<config>} sets spacing and separation at runtime, {@code spacing} and
     * {@code separation} here are the fallback while the config is not loaded (datagen passes the config defaults).
     */
    public static JsonObject structureSet(ResourceKey<Structure> structure, String config, int salt, int spacing, int separation) {
        return structureSet(structure, config, salt, spacing, separation, List.of());
    }

    /**
     * An exclusion zone of a structure set (STR6): no start within {@code chunkCount} chunks (1..16, a square as vanilla's)
     * of a placement chunk of {@code otherSet}.
     */
    public record Exclusion(ResourceKey<StructureSet> otherSet, int chunkCount) {
        public Exclusion {
            if (chunkCount < 1 || chunkCount > 16) throw new IllegalArgumentException("chunk_count 1..16: " + chunkCount);
        }

        JsonObject json() {
            JsonObject zone = new JsonObject();
            zone.addProperty("other_set", otherSet.location().toString());
            zone.addProperty("chunk_count", chunkCount);
            return zone;
        }
    }

    /**
     * As {@link #structureSet(ResourceKey, String, int, int, int)} with exclusion zones (STR6): the first goes into
     * vanilla's {@code exclusion_zone}, any further ones into {@link ConfiguredSpreadPlacement}'s {@code exclusion_zones}.
     */
    public static JsonObject structureSet(ResourceKey<Structure> structure, String config, int salt, int spacing, int separation,
                                          List<Exclusion> exclusions) {
        JsonObject entry = new JsonObject();
        entry.addProperty("structure", structure.location().toString());
        entry.addProperty("weight", 1);
        JsonArray structures = new JsonArray();
        structures.add(entry);
        JsonObject placement = new JsonObject();
        placement.addProperty("type", PLACEMENT_TYPE.toString());
        placement.addProperty("config", config);
        placement.addProperty("salt", salt);
        placement.addProperty("spacing", spacing);
        placement.addProperty("separation", separation);
        placement.addProperty("spread_type", "linear");
        placement.addProperty("frequency", 1.0);
        if (!exclusions.isEmpty()) placement.add("exclusion_zone", exclusions.get(0).json());
        if (exclusions.size() > 1) {
            JsonArray more = new JsonArray();
            for (Exclusion e : exclusions.subList(1, exclusions.size())) more.add(e.json());
            placement.add("exclusion_zones", more);
        }
        JsonObject json = new JsonObject();
        json.add("structures", structures);
        json.add("placement", placement);
        return json;
    }

    public static void writeStructure(DataContributions data, ResourceKey<Structure> key, Spec spec) {
        data.json(PackOutput.Target.DATA_PACK, "worldgen/structure", key.location(), () -> structure(spec));
    }

    /** As {@link #writeStructure(DataContributions, ResourceKey, Spec)} with the pier's extension pool (STR7). */
    public static void writeStructure(DataContributions data, ResourceKey<Structure> key, Spec spec,
                                      ResourceKey<StructureTemplatePool> extensionPool) {
        data.json(PackOutput.Target.DATA_PACK, "worldgen/structure", key.location(), () -> withExtension(structure(spec), extensionPool));
    }

    /** Adds {@code extension_pool} ({@code PortStructure}, STR7: the sections a shallow pier grows by) to a structure. */
    public static JsonObject withExtension(JsonObject structure, ResourceKey<StructureTemplatePool> extensionPool) {
        structure.addProperty("extension_pool", extensionPool.location().toString());
        return structure;
    }

    /** Writes {@link #structureSet} with the spacing and separation defaults of {@code WorldConfig.spread(config)}. */
    public static void writeStructureSet(DataContributions data, ResourceKey<StructureSet> key, ResourceKey<Structure> structure,
                                         String config, int salt) {
        writeStructureSet(data, key, structure, config, salt, List.of());
    }

    /** As {@link #writeStructureSet(DataContributions, ResourceKey, ResourceKey, String, int)} with exclusion zones (STR6). */
    public static void writeStructureSet(DataContributions data, ResourceKey<StructureSet> key, ResourceKey<Structure> structure,
                                         String config, int salt, List<Exclusion> exclusions) {
        WorldConfig.Spread spread = WorldConfig.spread(config).orElseThrow(() -> new IllegalArgumentException("no spread " + config));
        data.json(PackOutput.Target.DATA_PACK, "worldgen/structure_set", key.location(),
                () -> structureSet(structure, config, salt, spread.spacing().defaultValue(), spread.separation().defaultValue(),
                        exclusions));
    }

    /**
     * A pool of single pool elements {@code pirates_n_ships:<group>/<piece>} with one projection. Every element names
     * the processor list {@link ConnectionsProcessor#LIST}, which connects the template's fences, panes, bars and walls
     * (jigsaw placement skips vanilla's neighbour-shape pass; WG4).
     */
    public static void pool(DataContributions data, ResourceKey<StructureTemplatePool> key, String group, String fallback,
                            String projection, Map<String, Integer> pieces) {
        pool(data, key, group, fallback, projection, ConnectionsProcessor.LIST, pieces);
    }

    /**
     * As {@link #pool(DataContributions, ResourceKey, String, String, String, Map)} with another processor list for
     * every element (STR2: the island's trails); the list must run {@link ConnectionsProcessor} as well.
     */
    public static void pool(DataContributions data, ResourceKey<StructureTemplatePool> key, String group, String fallback,
                            String projection, ResourceKey<StructureProcessorList> processors, Map<String, Integer> pieces) {
        data.json(PackOutput.Target.DATA_PACK, "worldgen/template_pool", key.location(), () -> {
            JsonArray elements = new JsonArray();
            pieces.forEach((piece, weight) -> {
                JsonObject element = new JsonObject();
                element.addProperty("element_type", "minecraft:single_pool_element");
                element.addProperty("location", Constants.id(group + "/" + piece).toString());
                element.addProperty("processors", processors.location().toString());
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
