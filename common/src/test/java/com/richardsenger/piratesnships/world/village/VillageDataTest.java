package com.richardsenger.piratesnships.world.village;

import com.richardsenger.piratesnships.world.structure.ConfiguredSpreadPlacement;
import com.richardsenger.piratesnships.world.structure.StandAloneOps;
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
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
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
import java.util.Set;
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
        assertEquals(TerrainAdjustment.BEARD_THIN, s.terrainAdaptation(), "STR3: the buildings sit on the slope");
        assertTrue(s.landPiecesOnLand(), "STR3: no streets out over the sea");
        assertTrue(s.unbeardedStart(), "STR3: the dock head (and the pier) stay out of the beard");
        assertEquals(Optional.of(VillageKeys.START), s.startPool().unwrapKey());
        assertEquals(VillageData.SIZE, s.maxDepth());
        assertEquals(Optional.of(VillageKeys.HAS_SEAFARER_VILLAGE), s.biomes().unwrapKey());
        assertEquals(ShoreAnchor.VILLAGE, s.shoreAnchor());
        assertEquals(PortKind.SEAFARER_VILLAGE, s.portKind());
        assertTrue(PortStructure.CODEC.codec().encodeStart(ops, s).isSuccess(), "encodes again");
    }

    @Test
    void structureSetParses() throws IOException {
        StandAloneOps.ConfiguredSet set = StandAloneOps.configuredSet(ops, "seafarer_villages");
        assertEquals(1, set.structures().size());
        assertEquals(Optional.of(VillageKeys.SEAFARER_VILLAGE), set.structures().get(0).structure().unwrapKey());
        ConfiguredSpreadPlacement placement = set.placement();
        assertEquals("seafarer_village", placement.config());
        assertEquals(30, placement.spacing(), "WG5: a little denser than 36");
        assertEquals(10, placement.separation());
        assertTrue(placement.separation() < placement.spacing());
    }

    @Test
    void poolsParseAndNameCommittedPieces() throws IOException {
        Map<String, Integer> weights = new TreeMap<>();
        Map<String, String> projections = new java.util.HashMap<>();
        Map<String, String> processors = new java.util.HashMap<>();
        for (ResourceKey<StructureTemplatePool> key : List.of(VillageKeys.START, VillageKeys.STREETS,
                VillageKeys.BUILDINGS, VillageKeys.PIER, VillageKeys.TERMINATORS, VillageKeys.STREETHEADS, VillageKeys.DECORATIONS)) {
            JsonObject json = generated("worldgen/template_pool/" + key.location().getPath()).getAsJsonObject();
            StructureTemplatePool pool = StructureTemplatePool.DIRECT_CODEC.parse(ops, json).getOrThrow();
            List<String> locations = new ArrayList<>();
            for (JsonElement e : json.getAsJsonArray("elements")) {
                JsonObject entry = e.getAsJsonObject();
                JsonObject element = entry.getAsJsonObject("element");
                String location = element.get("location").getAsString();
                locations.add(location);
                weights.put(location, entry.get("weight").getAsInt());
                projections.put(location, element.get("projection").getAsString());
                processors.put(location, element.get("processors").getAsString());
                ResourceLocation id = ResourceLocation.parse(location);
                Path nbt = root.resolve("common/src/main/resources/data/" + id.getNamespace() + "/structure/" + id.getPath() + ".nbt");
                assertTrue(Files.exists(nbt), key.location() + ": " + location + " has no committed NBT");
            }
            int total = json.getAsJsonArray("elements").asList().stream().mapToInt(e -> e.getAsJsonObject().get("weight").getAsInt()).sum();
            assertEquals(total, pool.getShuffledTemplates(net.minecraft.util.RandomSource.create(0L)).size(), key.location().toString());
            // vanilla skips a connector whose fallback pool is empty (unless it is minecraft:empty)
            assertTrue(!locations.isEmpty(), key.location() + " has pieces");
        }
        Map<String, Integer> expected = new TreeMap<>();
        expected.put("pirates_n_ships:village/dock_head", 1);
        expected.put("pirates_n_ships:village/pier", 1);
        for (Map<String, Integer> pool : List.of(VillageData.STREETS, VillageData.BUILDINGS, VillageData.TERMINATORS,
                VillageData.STREETHEADS, VillageData.DECORATIONS)) {
            pool.forEach((piece, weight) -> expected.put("pirates_n_ships:village/" + piece, weight));
        }
        assertEquals(expected, weights);
    }

    /**
     * STR3's network (design.md §10.1, "Natural layouts"): bends, forks, a crossroads and squares besides the
     * straights, which are the minority of the draws; a street piece leads on to about 1.2 street pieces; three to five
     * looks of the house, two of the tavern and of the shipwright, the new buildings, ten decorations; the streetheads
     * branch at once and lead on inland; streets are terrain matching with the boardwalk, buildings and decorations rigid.
     */
    @Test
    void naturalLayoutWeights() throws IOException {
        Map<String, Integer> streets = VillageData.STREETS;
        for (String kind : List.of("street_bend_left", "street_bend_right", "street_fork_left", "street_fork_right",
                "street_crossroads", "street_square_well", "street_square_market", "street_long", "street")) {
            assertTrue(streets.containsKey(kind), kind);
        }
        int total = streets.values().stream().mapToInt(Integer::intValue).sum();
        int straights = streets.get("street") + streets.get("street_long");
        double share = (double) straights / total;
        assertTrue(share >= 0.25 && share <= 0.4, "straights are about a third of the draws: " + straights + " of " + total);
        Map<String, Integer> leads = Map.of("street", 1, "street_long", 1, "street_bend_left", 1, "street_bend_right", 1,
                "street_fork_left", 2, "street_fork_right", 2, "street_crossroads", 3, "street_square_well", 1,
                "street_square_market", 0);
        double mean = streets.entrySet().stream().mapToDouble(e -> e.getValue() * leads.get(e.getKey())).sum() / total;
        assertTrue(mean >= 1.1 && mean <= 1.35, "a street piece leads on to about 1.2: " + mean);
        long houses = VillageData.BUILDINGS.keySet().stream().filter(k -> k.startsWith("house_")).count();
        assertTrue(houses >= 3 && houses <= 5, "house looks " + houses);
        assertTrue(VillageData.BUILDINGS.keySet().containsAll(List.of("tavern", "inn", "shipwright", "slipway", "warehouse",
                "net_loft", "fish_market", "lighthouse", "chapel", "well")), VillageData.BUILDINGS.keySet().toString());
        assertEquals(10, VillageData.DECORATIONS.size(), VillageData.DECORATIONS.keySet().toString());
        assertTrue(VillageData.TERMINATORS.size() >= 2, "street ends " + VillageData.TERMINATORS.keySet());
        VillageData.STREETHEADS.forEach((piece, weight) -> {
            assertEquals(streets.get(piece), weight, piece);
            assertTrue(piece.contains("fork") || piece.contains("crossroads"), piece + " does not branch at the dock head");
        });
        for (String pool : List.of("streets", "streetheads", "terminators", "buildings", "decorations")) {
            JsonObject json = generated("worldgen/template_pool/village/" + pool).getAsJsonObject();
            for (JsonElement e : json.getAsJsonArray("elements")) {
                JsonObject element = e.getAsJsonObject().getAsJsonObject("element");
                boolean street = pool.startsWith("street") || pool.equals("terminators");
                assertEquals(street ? "terrain_matching" : "rigid", element.get("projection").getAsString(), pool);
                assertEquals(street ? VillageKeys.STREET_PROCESSORS.location().toString() : "pirates_n_ships:connections",
                        element.get("processors").getAsString(), pool);
            }
        }
        JsonObject list = generated("worldgen/processor_list/village_street").getAsJsonObject();
        List<String> types = list.getAsJsonArray("processors").asList().stream()
                .map(e -> e.getAsJsonObject().get("processor_type").getAsString()).toList();
        assertEquals(List.of("pirates_n_ships:connections", "pirates_n_ships:boardwalk"), types);
        // every committed village piece is drawn by some pool
        try (var files = Files.list(root.resolve("common/src/main/resources/data/pirates_n_ships/structure/village"))) {
            Set<String> drawn = new java.util.HashSet<>(List.of("dock_head", "pier"));
            for (Map<String, Integer> pool : List.of(streets, VillageData.BUILDINGS, VillageData.TERMINATORS, VillageData.DECORATIONS)) {
                drawn.addAll(pool.keySet());
            }
            files.forEach(f -> {
                String name = f.getFileName().toString().replace(".nbt", "");
                assertTrue(drawn.contains(name), name + " is in no pool");
            });
        }
    }

    @Test
    void biomeTagHasTheBeaches() throws IOException {
        JsonObject json = generated("tags/worldgen/biome/has_structure/seafarer_village").getAsJsonObject();
        assertEquals("#minecraft:is_beach", json.getAsJsonArray("values").get(0).getAsString());
    }
}
