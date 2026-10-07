package com.richardsenger.piratesnships.world.island;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.WorldConfig;
import com.richardsenger.piratesnships.world.structure.PortStructure;
import com.richardsenger.piratesnships.world.structure.ShoreAnchor;
import com.richardsenger.piratesnships.world.structure.StandAloneOps;
import net.minecraft.SharedConstants;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generated worldgen files of the pirate island parse with vanilla's codecs (the structure through our own codec)
 * and every pool entry is a committed piece. Mod items and entity types are not registered in JUnit, so the spawn
 * overrides and the loot table are checked as JSON here and parsed for real by {@code PirateIslandGameTests}.
 */
class IslandDataTest {

    private static Path root;
    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void setUp() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        root = StandAloneOps.root();
        ops = StandAloneOps.create();
    }

    @Test
    void structureParses() throws IOException {
        JsonObject json = StandAloneOps.generated("worldgen/structure/pirate_island").getAsJsonObject();
        assertEquals("pirates_n_ships:port_village", json.get("type").getAsString());
        assertEquals("pirate_island", json.get("port_kind").getAsString());
        // pirates_n_ships:pirate is no entity type in JUnit: parse without the overrides, check them as JSON below
        JsonObject withoutOverrides = json.deepCopy();
        withoutOverrides.add("spawn_overrides", new JsonObject());
        PortStructure s = PortStructure.CODEC.codec().parse(ops, withoutOverrides).getOrThrow();
        assertEquals(GenerationStep.Decoration.SURFACE_STRUCTURES, s.step());
        assertEquals(TerrainAdjustment.NONE, s.terrainAdaptation());
        assertEquals(IslandData.SIZE, s.maxDepth());
        assertEquals(1, s.startHeight());
        assertEquals(ShoreAnchor.PIRATE_CAMP, s.shoreAnchor());
        assertEquals(PortKind.PIRATE_ISLAND, s.portKind());
        assertEquals(Optional.of(IslandKeys.START), s.startPool().unwrapKey());
        assertEquals(Optional.of(IslandKeys.HAS_PIRATE_ISLAND), s.biomes().unwrapKey());
        assertTrue(PortStructure.CODEC.codec().encodeStart(ops, s).isSuccess(), "encodes again");
    }

    @Test
    void spawnOverridesListPiratesInsideThePieces() throws IOException {
        JsonObject overrides = StandAloneOps.generated("worldgen/structure/pirate_island").getAsJsonObject().getAsJsonObject("spawn_overrides");
        assertEquals(List.of("monster"), List.copyOf(overrides.keySet()));
        JsonObject monster = overrides.getAsJsonObject("monster");
        assertEquals("piece", monster.get("bounding_box").getAsString());
        assertEquals(StructureSpawnOverride.BoundingBoxType.PIECE.getSerializedName(), monster.get("bounding_box").getAsString());
        JsonArray spawns = monster.getAsJsonArray("spawns");
        assertEquals(1, spawns.size());
        JsonObject pirate = spawns.get(0).getAsJsonObject();
        assertEquals("pirates_n_ships:pirate", pirate.get("type").getAsString());
        assertEquals(10, pirate.get("weight").getAsInt());
        assertEquals(1, pirate.get("minCount").getAsInt());
        assertEquals(3, pirate.get("maxCount").getAsInt());
    }

    @Test
    void structureSetParsesWithAWiderSpreadThanVillages() throws IOException {
        StructureSet set = StructureSet.DIRECT_CODEC.parse(ops, StandAloneOps.generated("worldgen/structure_set/pirate_islands")).getOrThrow();
        assertEquals(1, set.structures().size());
        assertEquals(Optional.of(IslandKeys.PIRATE_ISLAND), set.structures().get(0).structure().unwrapKey());
        RandomSpreadStructurePlacement placement = (RandomSpreadStructurePlacement) set.placement();
        assertEquals(64, placement.spacing());
        assertEquals(24, placement.separation());
        assertEquals(WorldConfig.PIRATE_ISLAND.spacing().defaultValue(), placement.spacing());
        assertEquals(WorldConfig.PIRATE_ISLAND_SEPARATION.defaultValue(), placement.separation());
        RandomSpreadStructurePlacement village = (RandomSpreadStructurePlacement) StructureSet.DIRECT_CODEC
                .parse(ops, StandAloneOps.generated("worldgen/structure_set/seafarer_villages")).getOrThrow().placement();
        assertTrue(placement.spacing() > village.spacing(), "islands are rarer than villages");
        assertNotEquals(IslandData.SALT, com.richardsenger.piratesnships.world.village.VillageData.SALT, "own salt");
    }

    @Test
    void poolsParseAndNameCommittedPieces() throws IOException {
        Map<String, Integer> weights = new TreeMap<>();
        Map<String, String> projections = new HashMap<>();
        for (ResourceKey<StructureTemplatePool> key : List.of(IslandKeys.START, IslandKeys.PATHS, IslandKeys.HUTS,
                IslandKeys.JETTY, IslandKeys.TERMINATORS)) {
            JsonObject json = StandAloneOps.generated("worldgen/template_pool/" + key.location().getPath()).getAsJsonObject();
            StructureTemplatePool pool = StructureTemplatePool.DIRECT_CODEC.parse(ops, json).getOrThrow();
            int total = 0;
            for (JsonElement e : json.getAsJsonArray("elements")) {
                JsonObject entry = e.getAsJsonObject();
                JsonObject element = entry.getAsJsonObject("element");
                String location = element.get("location").getAsString();
                weights.put(location, entry.get("weight").getAsInt());
                projections.put(location, element.get("projection").getAsString());
                total += entry.get("weight").getAsInt();
                ResourceLocation id = ResourceLocation.parse(location);
                Path nbt = root.resolve("common/src/main/resources/data/" + id.getNamespace() + "/structure/" + id.getPath() + ".nbt");
                assertTrue(Files.exists(nbt), key.location() + ": " + location + " has no committed NBT");
            }
            assertTrue(total > 0, key.location() + " has pieces (vanilla skips connectors with an empty fallback)");
            assertEquals(total, pool.getShuffledTemplates(RandomSource.create(0L)).size(), key.location().toString());
        }
        assertEquals(Map.of("pirates_n_ships:pirate_island/camp_start", 1, "pirates_n_ships:pirate_island/path", 1,
                "pirates_n_ships:pirate_island/tent", 3, "pirates_n_ships:pirate_island/tavern_hut", 1,
                "pirates_n_ships:pirate_island/captains_hut", 1, "pirates_n_ships:pirate_island/treasure_spot", 1,
                "pirates_n_ships:pirate_island/jetty", 1, "pirates_n_ships:pirate_island/path_end", 1), weights);
        assertEquals("terrain_matching", projections.get("pirates_n_ships:pirate_island/path"));
        assertEquals("terrain_matching", projections.get("pirates_n_ships:pirate_island/path_end"));
        assertEquals("rigid", projections.get("pirates_n_ships:pirate_island/jetty"));
        assertEquals("rigid", projections.get("pirates_n_ships:pirate_island/treasure_spot"));
        assertEquals(IslandKeys.TERMINATORS.location().toString(),
                StandAloneOps.generated("worldgen/template_pool/pirate_island/paths").getAsJsonObject().get("fallback").getAsString());
    }

    @Test
    void biomeTagHasTheBeaches() throws IOException {
        JsonObject json = StandAloneOps.generated("tags/worldgen/biome/has_structure/pirate_island").getAsJsonObject();
        assertEquals("#minecraft:is_beach", json.getAsJsonArray("values").get(0).getAsString());
    }

    @Test
    void buriedTreasureLootTableHasOnePoolOfThreeToFiveRolls() throws IOException {
        JsonObject json = StandAloneOps.generated("loot_table/chests/buried_treasure").getAsJsonObject();
        assertEquals("minecraft:chest", json.get("type").getAsString());
        JsonArray pools = json.getAsJsonArray("pools");
        assertEquals(1, pools.size());
        JsonObject pool = pools.get(0).getAsJsonObject();
        JsonObject rolls = pool.getAsJsonObject("rolls");
        assertEquals("minecraft:uniform", rolls.get("type").getAsString());
        assertEquals(3.0, rolls.get("min").getAsDouble());
        assertEquals(5.0, rolls.get("max").getAsDouble());
        Map<String, Integer> weights = new TreeMap<>();
        for (JsonElement e : pool.getAsJsonArray("entries")) {
            JsonObject entry = e.getAsJsonObject();
            // The loot codec omits "weight" when it is the default (1).
            weights.put(entry.get("name").getAsString(), entry.has("weight") ? entry.get("weight").getAsInt() : 1);
        }
        assertEquals(Map.of("pirates_n_ships:doubloon", 25, "pirates_n_ships:rum", 15, "pirates_n_ships:salt_pork", 15,
                "minecraft:iron_ingot", 12, "minecraft:emerald", 8, "pirates_n_ships:lead_shot", 6, "pirates_n_ships:pistol", 3,
                "pirates_n_ships:kraken_ink", 1), weights);
    }
}
