package com.richardsenger.piratesnships.world.structure;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.world.island.IslandData;
import com.richardsenger.piratesnships.world.island.IslandKeys;
import com.richardsenger.piratesnships.world.outpost.OutpostData;
import com.richardsenger.piratesnships.world.outpost.OutpostKeys;
import com.richardsenger.piratesnships.world.village.VillageData;
import com.richardsenger.piratesnships.world.village.VillageKeys;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * STR8 over the generated data: the shore tags, every kind's warm and cold structure and their two structure sets (one
 * grid, the same zones, vanilla's villages and mansions among them), the trails' new processor lists, and the straights
 * pools that a bend's, a fork's and a junction's way on draws from (read from the committed templates).
 */
class PortShoresDataTest {

    private static RegistryOps<JsonElement> ops;

    private record Kind(String structure, String set, String config, int salt, ResourceKey<Structure> key, ResourceKey<Structure> coldKey) {
    }

    private static final List<Kind> KINDS = List.of(
            new Kind("seafarer_village", "seafarer_villages", "seafarer_village", VillageData.SALT,
                    VillageKeys.SEAFARER_VILLAGE, VillageKeys.SEAFARER_VILLAGE_COLD),
            new Kind("pirate_island", "pirate_islands", "pirate_island", IslandData.SALT,
                    IslandKeys.PIRATE_ISLAND, IslandKeys.PIRATE_ISLAND_COLD),
            new Kind("navy_outpost", "navy_outposts", "navy_outpost", OutpostData.SALT,
                    OutpostKeys.NAVY_OUTPOST, OutpostKeys.NAVY_OUTPOST_COLD));

    @BeforeAll
    static void setUp() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = StandAloneOps.create();
    }

    @Test
    void theShoreTagsSplitTheBeaches() throws IOException {
        JsonObject shores = StandAloneOps.generated("tags/worldgen/biome/port_shores").getAsJsonObject();
        assertEquals("#minecraft:is_beach", shores.getAsJsonArray("values").get(0).getAsString());
        JsonObject cold = StandAloneOps.generated("tags/worldgen/biome/cold_shores").getAsJsonObject();
        Set<String> values = new TreeSet<>();
        Set<String> optional = new TreeSet<>();
        for (JsonElement e : cold.getAsJsonArray("values")) {
            if (e.isJsonPrimitive()) {
                values.add(e.getAsString());
            } else {
                assertFalse(e.getAsJsonObject().get("required").getAsBoolean(), e.toString());
                optional.add(e.getAsJsonObject().get("id").getAsString());
            }
        }
        assertTrue(values.containsAll(List.of("minecraft:snowy_beach", "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean",
                "minecraft:frozen_river", "minecraft:snowy_plains")), values.toString());
        assertFalse(values.contains("minecraft:beach"));
        assertEquals(Set.of("#c:is_snowy", "#c:is_icy"), optional);
    }

    @Test
    void everyKindHasAColdStructureOnTheSameGrid() throws IOException {
        for (Kind kind : KINDS) {
            PortStructure warm = structure(kind.structure());
            PortStructure cold = structure(kind.structure() + "_cold");
            assertEquals(Optional.of(PortStructureData.COLD_SHORES), warm.coldBiomes(), kind.structure());
            assertEquals(Optional.of(PortStructureData.COLD_SHORES), cold.coldBiomes(), kind.structure() + "_cold");
            assertFalse(warm.cold(), kind.structure());
            assertTrue(cold.cold(), kind.structure() + "_cold");
            assertEquals(warm.portKind(), cold.portKind());
            assertEquals(warm.startPool().unwrapKey(), cold.startPool().unwrapKey());
            assertEquals(warm.extensionPool().flatMap(h -> h.unwrapKey()), cold.extensionPool().flatMap(h -> h.unwrapKey()));
            assertEquals(warm.biomes().unwrapKey(), cold.biomes().unwrapKey(), "both start on the port shores");

            StandAloneOps.ConfiguredSet set = StandAloneOps.configuredSet(ops, kind.set());
            StandAloneOps.ConfiguredSet coldSet = StandAloneOps.configuredSet(ops, kind.set() + "_cold");
            assertEquals(Optional.of(kind.key()), set.structures().get(0).structure().unwrapKey());
            assertEquals(Optional.of(kind.coldKey()), coldSet.structures().get(0).structure().unwrapKey());
            ConfiguredSpreadPlacement a = set.placement();
            ConfiguredSpreadPlacement b = coldSet.placement();
            assertEquals(kind.config(), b.config(), "the cold set reads the kind's spacing");
            assertEquals(kind.salt(), a.salt());
            assertEquals(a.salt(), b.salt(), "one grid: the cold set never starts beside the warm one");
            assertEquals(a.jsonSpacing(), b.jsonSpacing());
            assertEquals(a.jsonSeparation(), b.jsonSeparation());
            assertEquals(zones(a), zones(b), "the same exclusion zones");
        }
    }

    @Test
    void everyPortKeepsAwayFromVanillaVillagesAndMansions() throws IOException {
        for (Kind kind : KINDS) {
            List<String> zones = zones(StandAloneOps.configuredSet(ops, kind.set()).placement());
            assertTrue(zones.contains("minecraft:villages 6"), kind.set() + ": " + zones);
            assertTrue(zones.contains("minecraft:woodland_mansions 10"), kind.set() + ": " + zones);
        }
        assertEquals(List.of("minecraft:villages 6", "minecraft:woodland_mansions 10"),
                zones(StandAloneOps.configuredSet(ops, "seafarer_villages").placement()));
        assertEquals(List.of("pirates_n_ships:seafarer_villages 8", "minecraft:villages 6", "minecraft:woodland_mansions 10"),
                zones(StandAloneOps.configuredSet(ops, "navy_outposts").placement()), "STR6's zone stays the first");
        assertEquals(List.of("pirates_n_ships:seafarer_villages 6", "pirates_n_ships:navy_outposts 6", "minecraft:villages 6",
                "minecraft:woodland_mansions 10"), zones(StandAloneOps.configuredSet(ops, "pirate_islands").placement()));
    }

    @Test
    void theTrailsNameTheLevelProcessorLists() throws IOException {
        Map<String, List<String>> lists = Map.of(
                "village_street_level", List.of("connections", "trail_bed", "boardwalk"),
                "pirate_island_trail_level", List.of("connections", "trail_bed", "boardwalk"),
                "navy_outpost_lane", List.of("connections", "loot_chests", "trail_bed"),
                "village_street", List.of("connections", "boardwalk"),
                "pirate_island_trail", List.of("connections", "boardwalk"));
        for (Map.Entry<String, List<String>> e : lists.entrySet()) {
            JsonObject json = StandAloneOps.generated("worldgen/processor_list/" + e.getKey()).getAsJsonObject();
            List<String> types = new ArrayList<>();
            for (JsonElement p : json.getAsJsonArray("processors")) {
                types.add(p.getAsJsonObject().get("processor_type").getAsString().replace("pirates_n_ships:", ""));
            }
            assertEquals(e.getValue(), types, e.getKey());
        }
        for (String pool : List.of("village/streets", "village/streets_straight", "village/streetheads", "village/terminators")) {
            assertProcessors(pool, "pirates_n_ships:village_street_level");
        }
        for (String pool : List.of("pirate_island/paths", "pirate_island/paths_straight", "pirate_island/trailheads",
                "pirate_island/terminators")) {
            assertProcessors(pool, "pirates_n_ships:pirate_island_trail_level");
        }
        for (String pool : List.of("navy_outpost/lanes", "navy_outpost/lanes_straight", "navy_outpost/laneheads",
                "navy_outpost/lane_ends")) {
            assertProcessors(pool, "pirates_n_ships:navy_outpost_lane");
        }
    }

    @Test
    void turnsLeadIntoTheStraights() throws IOException {
        assertEquals(Set.of("street", "street_long"), elements("village/streets_straight"));
        assertEquals(Set.of("path", "path_long"), elements("pirate_island/paths_straight"));
        assertEquals(Set.of("lane", "lane_long"), elements("navy_outpost/lanes_straight"));
        Map<String, String> turns = Map.ofEntries(
                Map.entry("village/street_bend_left", "village/streets_straight"),
                Map.entry("village/street_bend_right", "village/streets_straight"),
                Map.entry("village/street_fork_left", "village/streets_straight"),
                Map.entry("village/street_fork_right", "village/streets_straight"),
                Map.entry("village/street_crossroads", "village/streets_straight"),
                Map.entry("pirate_island/path_bend_left", "pirate_island/paths_straight"),
                Map.entry("pirate_island/path_bend_right", "pirate_island/paths_straight"),
                Map.entry("pirate_island/path_fork_left", "pirate_island/paths_straight"),
                Map.entry("pirate_island/path_fork_right", "pirate_island/paths_straight"),
                Map.entry("pirate_island/path_junction", "pirate_island/paths_straight"),
                Map.entry("navy_outpost/lane_bend_left", "navy_outpost/lanes_straight"),
                Map.entry("navy_outpost/lane_bend_right", "navy_outpost/lanes_straight"),
                Map.entry("navy_outpost/lane_fork_left", "navy_outpost/lanes_straight"),
                Map.entry("navy_outpost/lane_fork_right", "navy_outpost/lanes_straight"),
                Map.entry("navy_outpost/lane_junction", "navy_outpost/lanes_straight"));
        for (Map.Entry<String, String> e : turns.entrySet()) {
            List<String> ways = waysOn(e.getKey());
            assertFalse(ways.isEmpty(), e.getKey());
            for (String pool : ways) assertEquals("pirates_n_ships:" + e.getValue(), pool, e.getKey());
        }
        // the straights lead on into the full pools
        assertEquals(List.of("pirates_n_ships:village/streets"), waysOn("village/street"));
        assertEquals(List.of("pirates_n_ships:pirate_island/paths"), waysOn("pirate_island/path_long"));
        assertEquals(List.of("pirates_n_ships:navy_outpost/lanes"), waysOn("navy_outpost/lane"));
    }

    // --- helpers ----------------------------------------------------------------------------------------------

    private static PortStructure structure(String name) throws IOException {
        JsonObject json = StandAloneOps.generated("worldgen/structure/" + name).getAsJsonObject();
        // the pirate is no entity type in JUnit (IslandDataTest checks the overrides as JSON)
        json.add("spawn_overrides", new JsonObject());
        return PortStructure.CODEC.codec().parse(ops, json).getOrThrow();
    }

    private static List<String> zones(ConfiguredSpreadPlacement placement) {
        List<String> out = new ArrayList<>();
        List<StructurePlacement.ExclusionZone> all = new ArrayList<>();
        placement.exclusionZone().ifPresent(all::add);
        all.addAll(placement.exclusionZones());
        for (StructurePlacement.ExclusionZone zone : all) {
            out.add(zone.otherSet().unwrapKey().orElseThrow().location() + " " + zone.chunkCount());
        }
        return out;
    }

    private static void assertProcessors(String pool, String list) throws IOException {
        JsonObject json = StandAloneOps.generated("worldgen/template_pool/" + pool).getAsJsonObject();
        for (JsonElement e : json.getAsJsonArray("elements")) {
            assertEquals(list, e.getAsJsonObject().getAsJsonObject("element").get("processors").getAsString(), pool);
        }
    }

    private static Set<String> elements(String pool) throws IOException {
        JsonObject json = StandAloneOps.generated("worldgen/template_pool/" + pool).getAsJsonObject();
        Set<String> out = new TreeSet<>();
        for (JsonElement e : json.getAsJsonArray("elements")) {
            String location = e.getAsJsonObject().getAsJsonObject("element").get("location").getAsString();
            out.add(location.substring(location.lastIndexOf('/') + 1));
        }
        return out;
    }

    /** The pools of a committed template's street, path or lane connectors (the ways on), sorted. */
    private static List<String> waysOn(String piece) throws IOException {
        Path file = StandAloneOps.root().resolve("common/src/main/resources/data/pirates_n_ships/structure/" + piece + ".nbt");
        CompoundTag nbt = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        List<String> out = new ArrayList<>();
        for (Tag t : nbt.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag block = (CompoundTag) t;
            if (!block.contains("nbt")) continue;
            CompoundTag data = block.getCompound("nbt");
            String target = data.getString("target");
            if (target.endsWith(":street_in") || target.endsWith(":path_in") || target.endsWith(":lane_in")) {
                out.add(data.getString("pool"));
            }
        }
        out.sort(String::compareTo);
        return out;
    }
}
