package com.richardsenger.piratesnships.core.assets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The committed assets fit together (run from {@code common/}): every block state of ours points only at models of
 * ours that exist, generated or hand-made (design.md §4.8: datagen writes the block state, Blockbench the model), and
 * every model's textures and parents in our namespace exist. Vanilla references are not checked here (the vanilla
 * assets are not on disk); the hand-made models' own checks are in {@link HandMadeModelsTest}.
 */
class AssetReferencesTest {

    private static final String NS = "pirates_n_ships";
    private static final Path GENERATED = Path.of("src/generated/resources/assets/" + NS);
    private static final Path MAIN = Path.of("src/main/resources/assets/" + NS);

    private static boolean modelExists(ResourceLocation id) {
        String path = "models/" + id.getPath() + ".json";
        return Files.isRegularFile(GENERATED.resolve(path)) || Files.isRegularFile(MAIN.resolve(path));
    }

    /** All {@code model} values of a variants or multipart block state ({@code apply} may be one object or a list). */
    private static List<String> models(JsonObject state) {
        List<String> out = new ArrayList<>();
        List<JsonElement> entries = new ArrayList<>();
        if (state.has("variants")) entries.addAll(state.getAsJsonObject("variants").asMap().values());
        if (state.has("multipart")) {
            for (JsonElement part : state.getAsJsonArray("multipart")) entries.add(part.getAsJsonObject().get("apply"));
        }
        for (JsonElement entry : entries) {
            List<JsonElement> variants = entry.isJsonArray() ? entry.getAsJsonArray().asList() : List.of(entry);
            for (JsonElement v : variants) out.add(v.getAsJsonObject().get("model").getAsString());
        }
        return out;
    }

    @Test
    void everyBlockStateReferencesExistingModels() throws IOException {
        List<Path> states = HandMadeModelsTest.jsonFiles(GENERATED.resolve("blockstates"));
        assertFalse(states.isEmpty(), "no generated block states under " + GENERATED.toAbsolutePath());
        for (Path file : states) {
            List<String> models = models(JsonParser.parseString(Files.readString(file)).getAsJsonObject());
            assertFalse(models.isEmpty(), file + " references no model");
            for (String model : models) {
                ResourceLocation id = ResourceLocation.parse(model);
                if (id.getNamespace().equals(NS)) assertTrue(modelExists(id), file + " references missing model " + id);
            }
        }
    }

    @Test
    void everyModelTextureAndParentOfOursExists() throws IOException {
        List<Path> models = new ArrayList<>(HandMadeModelsTest.jsonFiles(GENERATED.resolve("models")));
        models.addAll(HandMadeModelsTest.jsonFiles(MAIN.resolve("models")));
        assertFalse(models.isEmpty());
        for (Path file : models) {
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (json.has("parent")) {
                ResourceLocation parent = ResourceLocation.parse(json.get("parent").getAsString());
                if (parent.getNamespace().equals(NS)) assertTrue(modelExists(parent), file + ": missing parent " + parent);
            }
            if (!json.has("textures")) continue;
            for (Map.Entry<String, JsonElement> t : json.getAsJsonObject("textures").entrySet()) {
                String value = t.getValue().getAsString();
                if (value.startsWith("#")) continue;
                ResourceLocation id = ResourceLocation.parse(value);
                if (!id.getNamespace().equals(NS)) continue;
                assertTrue(Files.isRegularFile(MAIN.resolve("textures/" + id.getPath() + ".png")),
                        file + ": texture " + t.getKey() + " = " + id + " has no PNG");
            }
        }
    }
}
