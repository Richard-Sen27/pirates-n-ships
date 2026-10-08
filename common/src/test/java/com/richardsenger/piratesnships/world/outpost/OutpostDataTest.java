package com.richardsenger.piratesnships.world.outpost;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.WorldConfig;
import com.richardsenger.piratesnships.world.island.IslandData;
import com.richardsenger.piratesnships.world.structure.PortStructure;
import com.richardsenger.piratesnships.world.structure.ShoreAnchor;
import com.richardsenger.piratesnships.world.structure.StandAloneOps;
import com.richardsenger.piratesnships.world.village.VillageData;
import net.minecraft.SharedConstants;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generated worldgen files of the navy outpost (WG3) parse with vanilla's codecs (the structure through our own
 * codec), every pool entry is a committed piece, and the outpost's config section has the decided defaults.
 */
class OutpostDataTest {

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
        JsonObject json = StandAloneOps.generated("worldgen/structure/navy_outpost").getAsJsonObject();
        assertEquals("pirates_n_ships:port_village", json.get("type").getAsString());
        assertEquals("navy_outpost", json.get("port_kind").getAsString());
        assertEquals(0, json.getAsJsonObject("spawn_overrides").size(), "no spawn overrides: the garrison is placed");
        PortStructure s = PortStructure.CODEC.codec().parse(ops, json).getOrThrow();
        assertEquals(GenerationStep.Decoration.SURFACE_STRUCTURES, s.step());
        assertEquals(TerrainAdjustment.NONE, s.terrainAdaptation());
        assertEquals(5, s.maxDepth());
        assertEquals(1, s.startHeight());
        assertEquals(new ShoreAnchor(7, 0), s.shoreAnchor());
        assertEquals(OutpostData.FORT_GATE, s.shoreAnchor());
        assertEquals(PortKind.NAVY_OUTPOST, s.portKind());
        assertTrue(s.maxDistanceFromCenter() >= 56, "room for five walls and a tower each way");
        assertEquals(Optional.of(OutpostKeys.START), s.startPool().unwrapKey());
        assertEquals(Optional.of(OutpostKeys.HAS_NAVY_OUTPOST), s.biomes().unwrapKey());
        assertTrue(s.spawnOverrides().isEmpty());
        assertTrue(PortStructure.CODEC.codec().encodeStart(ops, s).isSuccess(), "encodes again");
    }

    @Test
    void structureSetSpreadsOutpostsWiderThanVillages() throws IOException {
        StructureSet set = StructureSet.DIRECT_CODEC.parse(ops, StandAloneOps.generated("worldgen/structure_set/navy_outposts")).getOrThrow();
        assertEquals(1, set.structures().size());
        assertEquals(Optional.of(OutpostKeys.NAVY_OUTPOST), set.structures().get(0).structure().unwrapKey());
        RandomSpreadStructurePlacement placement = (RandomSpreadStructurePlacement) set.placement();
        assertEquals(48, placement.spacing());
        assertEquals(20, placement.separation());
        assertEquals(WorldConfig.NAVY_OUTPOST.spacing().defaultValue(), placement.spacing());
        assertEquals(WorldConfig.NAVY_OUTPOST_SEPARATION.defaultValue(), placement.separation());
        assertEquals(3, Set.of(OutpostData.SALT, VillageData.SALT, IslandData.SALT).size(), "own salt");
    }

    @Test
    void poolsParseAndNameCommittedPieces() throws IOException {
        Map<String, Integer> weights = new TreeMap<>();
        for (ResourceKey<StructureTemplatePool> key : List.of(OutpostKeys.START, OutpostKeys.WALLS, OutpostKeys.BUILDINGS,
                OutpostKeys.QUAY, OutpostKeys.TERMINATORS)) {
            JsonObject json = StandAloneOps.generated("worldgen/template_pool/" + key.location().getPath()).getAsJsonObject();
            StructureTemplatePool pool = StructureTemplatePool.DIRECT_CODEC.parse(ops, json).getOrThrow();
            int total = 0;
            for (JsonElement e : json.getAsJsonArray("elements")) {
                JsonObject entry = e.getAsJsonObject();
                JsonObject element = entry.getAsJsonObject("element");
                String location = element.get("location").getAsString();
                weights.put(location, entry.get("weight").getAsInt());
                assertEquals("rigid", element.get("projection").getAsString(), location + " is rigid");
                total += entry.get("weight").getAsInt();
                ResourceLocation id = ResourceLocation.parse(location);
                Path nbt = root.resolve("common/src/main/resources/data/" + id.getNamespace() + "/structure/" + id.getPath() + ".nbt");
                assertTrue(Files.exists(nbt), key.location() + ": " + location + " has no committed NBT");
            }
            assertTrue(total > 0, key.location() + " has pieces");
            assertEquals(total, pool.getShuffledTemplates(RandomSource.create(0L)).size(), key.location().toString());
        }
        assertEquals(Map.of("pirates_n_ships:navy_outpost/fort_gate", 1, "pirates_n_ships:navy_outpost/wall", 1,
                "pirates_n_ships:navy_outpost/barracks", 2, "pirates_n_ships:navy_outpost/brig", 1,
                "pirates_n_ships:navy_outpost/watchtower", 1, "pirates_n_ships:navy_outpost/quay", 1,
                "pirates_n_ships:navy_outpost/wall_tower", 1), weights);
        assertEquals(OutpostKeys.TERMINATORS.location().toString(),
                StandAloneOps.generated("worldgen/template_pool/navy_outpost/walls").getAsJsonObject().get("fallback").getAsString());
    }

    @Test
    void biomeTagHasTheBeaches() throws IOException {
        JsonObject json = StandAloneOps.generated("tags/worldgen/biome/has_structure/navy_outpost").getAsJsonObject();
        assertEquals("#minecraft:is_beach", json.getAsJsonArray("values").get(0).getAsString());
    }

    @Test
    void configDefaults() {
        assertEquals(true, WorldConfig.NAVY_OUTPOST_ENABLED.defaultValue());
        assertEquals(0.8, WorldConfig.NAVY_OUTPOST.frequency().defaultValue());
        assertEquals(6, WorldConfig.NAVY_OUTPOST_GARRISON_SOLDIERS.defaultValue());
        assertEquals(1, WorldConfig.NAVY_OUTPOST_GARRISON_OFFICERS.defaultValue());
        WorldConfig.PortPlacement placement = WorldConfig.placement(PortKind.NAVY_OUTPOST).orElseThrow();
        assertEquals(WorldConfig.NAVY_OUTPOST_ENABLED, placement.enabled());
        assertEquals(WorldConfig.NAVY_OUTPOST_SEPARATION, placement.separation());
        assertEquals(24, placement.shoreProbeBlocks().defaultValue());
        assertEquals(12, placement.maxDistanceFromWater().defaultValue());
        assertEquals(4, placement.maxShoreHeight().defaultValue());
    }
}
