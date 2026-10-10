package com.richardsenger.piratesnships.ship.decor.wood;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generated data of the wood families (WOOD1, run from {@code common/}): every family has a member in every wood
 * (lang, block state, item model, loot table, recipe), every file of a mod's wood loads only with the mod on both
 * loaders, and every tag entry of a mod's wood is optional.
 */
class WoodDataTest {

    private static final Path GENERATED = Path.of("src/generated/resources");
    private static final Path DATA = GENERATED.resolve("data/pirates_n_ships");
    private static final Path ASSETS = GENERATED.resolve("assets/pirates_n_ships");
    /** The families WOOD1 converts; WOOD2-WOOD4 add theirs. */
    private static final List<String> FAMILIES = List.of("railing", "railing_port", "railing_gate", "bulwark", "bulwark_port", "rope_railing", "oar_bench");

    private static JsonObject read(Path p) throws IOException {
        return JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static boolean compat(String path) {
        String suffix = WoodIds.suffixOf(path);
        if (suffix == null) return false;
        ShipWood w = ShipWood.byId(path.substring(0, path.length() - suffix.length() - 1));
        return w != null && !w.isVanilla();
    }

    @Test
    void everyFamilyHasAMemberInEveryWood() throws IOException {
        JsonObject lang = read(ASSETS.resolve("lang/en_us.json"));
        List<String> missing = new ArrayList<>();
        for (String family : FAMILIES) {
            for (ShipWood wood : ShipWood.all()) {
                String id = WoodFamily.name(wood, family);
                if (!lang.has("block.pirates_n_ships." + id)) missing.add("lang " + id);
                if (!Files.exists(ASSETS.resolve("blockstates/" + id + ".json"))) missing.add("blockstate " + id);
                if (!Files.exists(ASSETS.resolve("models/item/" + id + ".json"))) missing.add("item model " + id);
                if (!Files.exists(DATA.resolve("loot_table/blocks/" + id + ".json"))) missing.add("loot " + id);
                if (!Files.exists(DATA.resolve("recipe/" + id + ".json"))) missing.add("recipe " + id);
            }
        }
        assertTrue(missing.isEmpty(), missing.size() + " missing: " + missing.subList(0, Math.min(20, missing.size())));
        assertEquals("Spruce Railing", lang.get("block.pirates_n_ships.spruce_railing").getAsString());
        assertEquals("Redwood Railing", lang.get("block.pirates_n_ships.biomesoplenty_redwood_railing").getAsString());
    }

    @Test
    void everyCompatRecipeAdvancementAndLootTableCarriesBothLoadersConditions() throws IOException {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (Path dir : List.of(DATA.resolve("recipe"), DATA.resolve("loot_table/blocks"), DATA.resolve("advancement/recipes"))) {
            List<Path> files;
            try (Stream<Path> s = Files.walk(dir)) {
                files = s.filter(f -> f.toString().endsWith(".json")).toList();
            }
            for (Path f : files) {
                String path = f.getFileName().toString().replace(".json", "");
                JsonObject json = read(f);
                boolean hasNeo = json.has(WoodConditions.NEOFORGE), hasFabric = json.has(WoodConditions.FABRIC);
                if (!compat(path)) {
                    if (hasNeo || hasFabric) problems.add(f + ": conditions on a vanilla wood's or other file");
                    continue;
                }
                checked++;
                ShipWood wood = ShipWood.byId(path.substring(0, path.length() - WoodIds.suffixOf(path).length() - 1));
                if (!hasNeo || !hasFabric) {
                    problems.add(f + ": missing " + (hasNeo ? "" : WoodConditions.NEOFORGE + " ") + (hasFabric ? "" : WoodConditions.FABRIC));
                    continue;
                }
                JsonObject neoMod = json.getAsJsonArray(WoodConditions.NEOFORGE).get(0).getAsJsonObject();
                if (!"neoforge:mod_loaded".equals(neoMod.get("type").getAsString()) || !wood.modId().equals(neoMod.get("modid").getAsString())) {
                    problems.add(f + ": NeoForge mod condition " + neoMod);
                }
                JsonObject fabricMod = json.getAsJsonArray(WoodConditions.FABRIC).get(0).getAsJsonObject();
                if (!"fabric:all_mods_loaded".equals(fabricMod.get("condition").getAsString())
                        || !fabricMod.getAsJsonArray("values").contains(new com.google.gson.JsonPrimitive(wood.modId()))) {
                    problems.add(f + ": Fabric mod condition " + fabricMod);
                }
                if (json.toString().contains("minecraft:barrier") || json.toString().contains("minecraft:structure_void")
                        || json.toString().contains("minecraft:debug_stick")) {
                    problems.add(f + ": a stand-in item is left");
                }
            }
        }
        int expected = FAMILIES.size() * WoodCompat.WOODS.size() * 3;
        assertEquals(expected, checked, "compat recipes, advancements and loot tables checked");
        assertTrue(problems.isEmpty(), problems.size() + " problems: " + problems.subList(0, Math.min(20, problems.size())));
    }

    @Test
    void everyTagEntryOfACompatWoodIsOptional() throws IOException {
        List<String> problems = new ArrayList<>();
        Set<String> tags = new TreeSet<>();
        int optional = 0;
        List<Path> files;
        try (Stream<Path> s = Files.walk(GENERATED.resolve("data"))) {
            files = s.filter(f -> f.toString().contains("/tags/") && f.toString().endsWith(".json")).toList();
        }
        for (Path f : files) {
            JsonArray values = read(f).getAsJsonArray("values");
            for (JsonElement v : values) {
                String id = v.isJsonObject() ? v.getAsJsonObject().get("id").getAsString() : v.getAsString();
                if (!id.startsWith("pirates_n_ships:")) continue;
                if (!compat(id.substring("pirates_n_ships:".length()))) continue;
                tags.add(f.getFileName().toString());
                if (!v.isJsonObject() || v.getAsJsonObject().get("required").getAsBoolean()) problems.add(f + ": " + v + " is required");
                else optional++;
            }
        }
        assertTrue(optional >= WoodCompat.WOODS.size() * FAMILIES.size(), "only " + optional + " optional entries");
        assertTrue(problems.isEmpty(), problems.size() + " problems: " + problems.subList(0, Math.min(20, problems.size())));
        assertTrue(tags.contains("axe.json"), tags.toString());
    }
}
