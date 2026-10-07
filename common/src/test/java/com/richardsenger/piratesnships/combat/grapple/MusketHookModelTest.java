package com.richardsenger.piratesnships.combat.grapple;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The generated placeholder {@code musket_hook} model (GR3). Runs from {@code common/}. */
class MusketHookModelTest {

    private static final Path LOADED = Path.of("src/main/resources/assets/pirates_n_ships/models/item/musket_loaded.json");
    private static final Path GENERATED = Path.of("src/generated/resources/assets/pirates_n_ships/models/item/musket_hook.json");

    private static JsonObject read(Path p) throws IOException {
        return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
    }

    @Test
    void copiesTheLoadedMusketAndAddsTheHook() throws IOException {
        JsonObject base = read(LOADED);
        JsonObject hook = MusketHookModel.withHook(base);
        assertEquals(base.get("display"), hook.get("display"), "the grip must not change");
        assertEquals(base.get("textures"), hook.get("textures"));
        assertFalse(hook.has("overrides"));
        JsonArray baseElements = base.getAsJsonArray("elements");
        JsonArray elements = hook.getAsJsonArray("elements");
        assertEquals(baseElements.size() + MusketHookModel.HOOK_ELEMENTS.length, elements.size());
        for (int i = 0; i < baseElements.size(); i++) {
            assertEquals(baseElements.get(i), elements.get(i), "base element " + i + " changed");
        }
        List<String> added = new ArrayList<>();
        for (int i = baseElements.size(); i < elements.size(); i++) {
            JsonObject e = elements.get(i).getAsJsonObject();
            added.add(e.get("name").getAsString());
            for (String corner : new String[]{"from", "to"}) {
                for (JsonElement c : e.getAsJsonArray(corner)) {
                    double v = c.getAsDouble();
                    assertTrue(v >= -16 && v <= 32, "outside vanilla's model bounds: " + e);
                }
            }
            JsonArray from = e.getAsJsonArray("from");
            JsonArray to = e.getAsJsonArray("to");
            for (int a = 0; a < 3; a++) {
                assertTrue(from.get(a).getAsDouble() < to.get(a).getAsDouble(), "empty element " + e);
            }
            assertEquals(-45.0, e.getAsJsonObject("rotation").get("angle").getAsDouble(), "along the barrel");
            assertEquals(6, e.getAsJsonObject("faces").size());
        }
        assertEquals(List.of(MusketHookModel.HOOK_ELEMENTS), added);
    }

    @Test
    void theGeneratedFileIsUpToDate() throws IOException {
        assertTrue(Files.isRegularFile(GENERATED), "run ./gradlew :neoforge:runData");
        JsonObject generated = read(GENERATED);
        assertEquals(MusketHookModel.withHook(read(LOADED)), generated, "musket_hook.json is stale: run ./gradlew :neoforge:runData");
    }
}
