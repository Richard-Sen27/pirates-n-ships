package com.richardsenger.piratesnships.world.village;

import com.google.gson.JsonElement;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.structure.PortStructure;
import com.richardsenger.piratesnships.world.structure.ShoreAnchor;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generated worldgen files of the seafarer village parse with vanilla's codecs (our structure through its own
 * codec, since our structure type is not in the JUnit registry) and every pool entry is a committed piece. Holders are
 * resolved as unbound stand-alone references, so the test checks the format, not the cross-file references (the
 * GameTests load them in a real server).
 */
class VillageDataTest {

    private static Path root;
    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void setUp() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("tools/schem_to_structure.py"))) root = root.getParent();
        assertNotNull(root, "repository root");
        ops = RegistryOps.create(JsonOps.INSTANCE, new RegistryOps.RegistryInfoLookup() {
            private final Map<ResourceKey<?>, RegistryOps.RegistryInfo<?>> infos = new java.util.HashMap<>();

            @Override
            @SuppressWarnings("unchecked")
            public <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
                return Optional.of((RegistryOps.RegistryInfo<T>) infos.computeIfAbsent(key, k -> standAlone()));
            }

            private <T> RegistryOps.RegistryInfo<T> standAlone() {
                HolderOwner<T> owner = new HolderOwner<>() { };
                HolderGetter<T> getter = new HolderGetter<>() {
                    @Override
                    public Optional<Holder.Reference<T>> get(ResourceKey<T> element) {
                        return Optional.of(Holder.Reference.createStandAlone(owner, element));
                    }

                    @Override
                    public Optional<HolderSet.Named<T>> get(TagKey<T> tag) {
                        return Optional.of(HolderSet.emptyNamed(owner, tag));
                    }
                };
                return new RegistryOps.RegistryInfo<>(owner, getter, Lifecycle.stable());
            }
        });
    }

    private static JsonElement generated(String path) throws IOException {
        Path file = root.resolve("common/src/generated/resources/data/pirates_n_ships/" + path + ".json");
        assertTrue(Files.exists(file), "missing " + file);
        try (Reader r = Files.newBufferedReader(file)) {
            return JsonParser.parseReader(r);
        }
    }

    @Test
    void structureParses() throws IOException {
        JsonObject json = generated("worldgen/structure/seafarer_village").getAsJsonObject();
        assertEquals("pirates_n_ships:port_village", json.get("type").getAsString());
        PortStructure s = PortStructure.CODEC.codec().parse(ops, json).getOrThrow();
        assertEquals(GenerationStep.Decoration.SURFACE_STRUCTURES, s.step());
        assertEquals(TerrainAdjustment.NONE, s.terrainAdaptation());
        assertEquals(VillageData.SIZE, s.maxDepth());
        assertEquals(Optional.of(VillageKeys.HAS_SEAFARER_VILLAGE), s.biomes().unwrapKey());
        assertEquals(ShoreAnchor.VILLAGE, s.shoreAnchor());
        assertEquals(PortKind.SEAFARER_VILLAGE, s.portKind());
        assertTrue(PortStructure.CODEC.codec().encodeStart(ops, s).isSuccess(), "encodes again");
    }

    @Test
    void structureSetParses() throws IOException {
        StructureSet set = StructureSet.DIRECT_CODEC.parse(ops, generated("worldgen/structure_set/seafarer_villages")).getOrThrow();
        assertEquals(1, set.structures().size());
        assertEquals(Optional.of(VillageKeys.SEAFARER_VILLAGE), set.structures().get(0).structure().unwrapKey());
        RandomSpreadStructurePlacement placement = (RandomSpreadStructurePlacement) set.placement();
        assertEquals(36, placement.spacing());
        assertEquals(12, placement.separation());
        assertTrue(placement.separation() < placement.spacing());
    }

    @Test
    void poolsParseAndNameCommittedPieces() throws IOException {
        Map<String, Integer> weights = new TreeMap<>();
        for (ResourceKey<StructureTemplatePool> key : List.of(VillageKeys.START, VillageKeys.STREETS,
                VillageKeys.BUILDINGS, VillageKeys.PIER, VillageKeys.TERMINATORS)) {
            JsonObject json = generated("worldgen/template_pool/" + key.location().getPath()).getAsJsonObject();
            StructureTemplatePool pool = StructureTemplatePool.DIRECT_CODEC.parse(ops, json).getOrThrow();
            List<String> locations = new ArrayList<>();
            for (JsonElement e : json.getAsJsonArray("elements")) {
                JsonObject entry = e.getAsJsonObject();
                String location = entry.getAsJsonObject("element").get("location").getAsString();
                locations.add(location);
                weights.put(location, entry.get("weight").getAsInt());
                ResourceLocation id = ResourceLocation.parse(location);
                Path nbt = root.resolve("common/src/main/resources/data/" + id.getNamespace() + "/structure/" + id.getPath() + ".nbt");
                assertTrue(Files.exists(nbt), key.location() + ": " + location + " has no committed NBT");
            }
            int total = json.getAsJsonArray("elements").asList().stream().mapToInt(e -> e.getAsJsonObject().get("weight").getAsInt()).sum();
            assertEquals(total, pool.getShuffledTemplates(net.minecraft.util.RandomSource.create(0L)).size(), key.location().toString());
            // vanilla skips a connector whose fallback pool is empty (unless it is minecraft:empty)
            assertTrue(!locations.isEmpty(), key.location() + " has pieces");
        }
        assertEquals(Map.of("pirates_n_ships:village/dock_head", 1, "pirates_n_ships:village/house_small", 3,
                "pirates_n_ships:village/pier", 1, "pirates_n_ships:village/shipwright", 1, "pirates_n_ships:village/street", 1,
                "pirates_n_ships:village/street_end", 1, "pirates_n_ships:village/tavern", 1), weights);
    }

    @Test
    void biomeTagHasTheBeaches() throws IOException {
        JsonObject json = generated("tags/worldgen/biome/has_structure/seafarer_village").getAsJsonObject();
        assertEquals("#minecraft:is_beach", json.getAsJsonArray("values").get(0).getAsString());
    }
}
