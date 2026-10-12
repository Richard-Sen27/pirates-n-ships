package com.richardsenger.piratesnships.world.outpost;

import com.richardsenger.piratesnships.world.structure.ConfiguredSpreadPlacement;
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
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
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
        assertEquals(TerrainAdjustment.BEARD_THIN, s.terrainAdaptation(), "STR4: the court sits on the slope");
        assertTrue(s.landPiecesOnLand(), "STR4: lanes over open water are dropped");
        assertEquals(OutpostData.SIZE, s.maxDepth());
        assertEquals(7, s.maxDepth());
        assertEquals(1, s.startHeight());
        assertEquals(new ShoreAnchor(7, 0), s.shoreAnchor());
        assertEquals(OutpostData.FORT_GATE, s.shoreAnchor());
        assertEquals(PortKind.NAVY_OUTPOST, s.portKind());
        assertTrue(s.maxDistanceFromCenter() >= 64, "room for a run of walls and a tower each way, and the court");
        assertEquals(Optional.of(OutpostKeys.START), s.startPool().unwrapKey());
        assertEquals(Optional.of(OutpostKeys.HAS_NAVY_OUTPOST), s.biomes().unwrapKey());
        assertTrue(s.spawnOverrides().isEmpty());
        assertTrue(PortStructure.CODEC.codec().encodeStart(ops, s).isSuccess(), "encodes again");
    }

    @Test
    void structureSetSpreadsOutpostsWiderThanVillages() throws IOException {
        StandAloneOps.ConfiguredSet set = StandAloneOps.configuredSet(ops, "navy_outposts");
        assertEquals(1, set.structures().size());
        assertEquals(Optional.of(OutpostKeys.NAVY_OUTPOST), set.structures().get(0).structure().unwrapKey());
        ConfiguredSpreadPlacement placement = set.placement();
        assertEquals("navy_outpost", placement.config());
        assertEquals(28, placement.spacing(), "WG6: denser than WG5's 40");
        assertEquals(10, placement.separation());
        assertTrue(placement.spacing() > StandAloneOps.configuredSet(ops, "seafarer_villages").placement().spacing(),
                "outposts are rarer than villages");
        assertEquals(WorldConfig.NAVY_OUTPOST.spacing().defaultValue(), placement.spacing());
        assertEquals(WorldConfig.NAVY_OUTPOST_SEPARATION.defaultValue(), placement.separation());
        assertEquals(3, Set.of(OutpostData.SALT, VillageData.SALT, IslandData.SALT).size(), "own salt");
    }

    @Test
    void poolsParseAndNameCommittedPieces() throws IOException {
        Map<String, Integer> weights = new TreeMap<>();
        Map<ResourceKey<StructureTemplatePool>, Map<String, Integer>> expected = Map.ofEntries(
                Map.entry(OutpostKeys.START, Map.of("fort_gate", 1)), Map.entry(OutpostKeys.QUAY, Map.of("quay", 1)),
                Map.entry(OutpostKeys.QUAY_EXTENSION, Map.of("quay_extension", 1)),
                Map.entry(OutpostKeys.WALLS_GATE, OutpostData.WALLS_GATE), Map.entry(OutpostKeys.WALLS_FLANK_HEAD, OutpostData.WALLS_FLANK_HEAD),
                Map.entry(OutpostKeys.WALLS, OutpostData.WALLS), Map.entry(OutpostKeys.WALLS_FLANK, OutpostData.WALLS_FLANK),
Map.entry(OutpostKeys.TERMINATORS, OutpostData.TERMINATORS),
                Map.entry(OutpostKeys.BUILDINGS, OutpostData.BUILDINGS), Map.entry(OutpostKeys.LANEHEADS, OutpostData.LANEHEADS),
                Map.entry(OutpostKeys.LANES, OutpostData.LANES), Map.entry(OutpostKeys.LANE_ENDS, OutpostData.LANE_ENDS),
                Map.entry(OutpostKeys.DECORATIONS, OutpostData.DECORATIONS));
        Set<ResourceKey<StructureTemplatePool>> lanes = Set.of(OutpostKeys.LANEHEADS, OutpostKeys.LANES, OutpostKeys.LANE_ENDS);
        for (ResourceKey<StructureTemplatePool> key : expected.keySet()) {
            Map<String, Integer> poolWeights = new TreeMap<>();
            JsonObject json = StandAloneOps.generated("worldgen/template_pool/" + key.location().getPath()).getAsJsonObject();
            StructureTemplatePool pool = StructureTemplatePool.DIRECT_CODEC.parse(ops, json).getOrThrow();
            int total = 0;
            for (JsonElement e : json.getAsJsonArray("elements")) {
                JsonObject entry = e.getAsJsonObject();
                JsonObject element = entry.getAsJsonObject("element");
                String location = element.get("location").getAsString();
                weights.put(location, entry.get("weight").getAsInt());
                poolWeights.put(location.substring("pirates_n_ships:navy_outpost/".length()), entry.get("weight").getAsInt());
                assertEquals(lanes.contains(key) ? "terrain_matching" : "rigid", element.get("projection").getAsString(),
                        location + " projection");
                total += entry.get("weight").getAsInt();
                ResourceLocation id = ResourceLocation.parse(location);
                Path nbt = root.resolve("common/src/main/resources/data/" + id.getNamespace() + "/structure/" + id.getPath() + ".nbt");
                assertTrue(Files.exists(nbt), key.location() + ": " + location + " has no committed NBT");
            }
            assertTrue(total > 0, key.location() + " has pieces");
            assertEquals(total, pool.getShuffledTemplates(RandomSource.create(0L)).size(), key.location().toString());
            assertEquals(new TreeMap<>(expected.get(key)), poolWeights, key.location().toString());
        }
        // every committed piece is in a pool, the WG3 weights of the ST3 buildings stand
        assertEquals(56, weights.size(), "pieces in the pools (STR7: the quay_extension)");
        assertEquals(2, weights.get("pirates_n_ships:navy_outpost/barracks"));
        assertEquals(1, weights.get("pirates_n_ships:navy_outpost/brig"));
        assertEquals(1, weights.get("pirates_n_ships:navy_outpost/watchtower"));
        for (String walls : List.of("walls_gate", "walls", "walls_flank_head", "walls_flank")) {
            assertEquals(OutpostKeys.TERMINATORS.location().toString(),
                    StandAloneOps.generated("worldgen/template_pool/navy_outpost/" + walls).getAsJsonObject().get("fallback").getAsString(), walls);
        }
        // the towers fall back to themselves: at the depth limit the flank's angle (whose outs draw the towers) still gets one
        assertEquals(OutpostKeys.TERMINATORS.location().toString(),
                StandAloneOps.generated("worldgen/template_pool/navy_outpost/terminators").getAsJsonObject().get("fallback").getAsString());
        for (String lane : List.of("laneheads", "lanes")) {
            assertEquals(OutpostKeys.LANE_ENDS.location().toString(),
                    StandAloneOps.generated("worldgen/template_pool/navy_outpost/" + lane).getAsJsonObject().get("fallback").getAsString(), lane);
        }
        // each run's state can turn inland only by an angle of its own run, and the shore state is more likely to turn
        // or bastion than to run on straight
        assertTrue(OutpostData.WALLS_GATE.keySet().stream().noneMatch(n -> n.contains("angle")), "the gate's walls never turn");
        assertTrue(OutpostData.WALLS_FLANK_HEAD.keySet().stream().noneMatch(n -> n.contains("angle") || n.contains("step")),
                "the flank's first piece runs on inland");
        int shoreTurns = OutpostData.WALLS.get("wall_angle_east") + OutpostData.WALLS.get("wall_bastion");
        int shoreEast = OutpostData.WALLS.values().stream().mapToInt(Integer::intValue).sum() - OutpostData.WALLS.get("wall_angle_west");
        assertTrue(2 * shoreTurns >= shoreEast, "an angle or a bastion is at least half the east run's draws");
        assertTrue(OutpostData.LANEHEADS.keySet().stream().noneMatch(n -> n.contains("end") || n.contains("bend") || n.contains("junction")),
                "the land gate's lane always grows inland");
    }

    @Test
    void biomeTagHasTheBeaches() throws IOException {
        JsonObject json = StandAloneOps.generated("tags/worldgen/biome/has_structure/navy_outpost").getAsJsonObject();
        assertEquals("#pirates_n_ships:port_shores", json.getAsJsonArray("values").get(0).getAsString(), "STR8");
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
