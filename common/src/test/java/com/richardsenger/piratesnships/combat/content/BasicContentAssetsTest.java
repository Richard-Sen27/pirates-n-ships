package com.richardsenger.piratesnships.combat.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.richardsenger.piratesnships.crew.content.CrewContentGameTests;
import com.richardsenger.piratesnships.law.content.LawContentGameTests;
import com.richardsenger.piratesnships.ship.decor.ShipDecorGameTests;
import com.richardsenger.piratesnships.trade.content.TradeContentGameTests;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the committed datagen output and the placeholder textures against each other (run from {@code common/}):
 * every texture in our namespace that a generated model references exists as a PNG, and every basic content id
 * (the lists the GameTests walk) has an item model, a lang entry, and for blocks a block state and a loot table.
 */
class BasicContentAssetsTest {

    private static final String NS = "pirates_n_ships";
    private static final Path GENERATED = Path.of("src/generated/resources");
    private static final Path ASSETS = GENERATED.resolve("assets/" + NS);
    private static final Path TEXTURES = Path.of("src/main/resources/assets/" + NS + "/textures");

    @Test
    void everyReferencedTextureExists() throws IOException {
        List<String> missing = new ArrayList<>();
        try (Stream<Path> models = Files.walk(ASSETS.resolve("models"))) {
            for (Path model : models.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject json = JsonParser.parseString(Files.readString(model)).getAsJsonObject();
                if (!json.has("textures")) continue;
                for (Map.Entry<String, JsonElement> t : json.getAsJsonObject("textures").entrySet()) {
                    String ref = t.getValue().getAsString();
                    if (!ref.startsWith(NS + ":")) continue;
                    Path png = TEXTURES.resolve(ref.substring(NS.length() + 1) + ".png");
                    if (!Files.exists(png)) missing.add(model.getFileName() + " -> " + ref);
                }
            }
        }
        assertTrue(missing.isEmpty(), "models reference missing textures: " + missing);
    }

    @Test
    void everyBasicContentIdHasAssets() throws IOException {
        JsonObject lang = JsonParser.parseString(Files.readString(ASSETS.resolve("lang/en_us.json"))).getAsJsonObject();
        List<String> problems = new ArrayList<>();
        List<List<String>> items = List.of(CombatContentGameTests.ITEM_IDS, TradeContentGameTests.ITEM_IDS, CrewContentGameTests.ITEM_IDS,
                LawContentGameTests.ITEM_IDS, ShipDecorGameTests.ITEM_IDS);
        List<List<String>> blocks = List.of(CombatContentGameTests.BLOCK_IDS, TradeContentGameTests.BLOCK_IDS, CrewContentGameTests.BLOCK_IDS,
                LawContentGameTests.BLOCK_IDS, ShipDecorGameTests.BLOCK_IDS);
        items.stream().flatMap(List::stream).forEach(id -> {
            if (!Files.exists(ASSETS.resolve("models/item/" + id + ".json"))) problems.add(id + ": no item model");
            if (!lang.has("item." + NS + "." + id)) problems.add(id + ": no lang entry");
        });
        blocks.stream().flatMap(List::stream).forEach(id -> {
            if (!Files.exists(ASSETS.resolve("models/item/" + id + ".json"))) problems.add(id + ": no item model");
            if (!Files.exists(ASSETS.resolve("blockstates/" + id + ".json"))) problems.add(id + ": no block state");
            if (!lang.has("block." + NS + "." + id)) problems.add(id + ": no lang entry");
            if (!Files.exists(GENERATED.resolve("data/" + NS + "/loot_table/blocks/" + id + ".json"))) problems.add(id + ": no loot table");
        });
        assertTrue(problems.isEmpty(), "missing generated assets: " + problems);
    }
}
