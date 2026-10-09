package com.richardsenger.piratesnships.crew.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.richardsenger.piratesnships.crew.content.client.CrewContentClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * ITC1: the generated water barrel item model (run from {@code common/}) draws the water slot with greyscale
 * {@code water_still}, not the old {@code blue_ice} stand-in, and inherits the full block model, whose water faces use
 * that slot with the tint index the item colour handler colours.
 */
class WaterBarrelItemModelTest {

    private static final Path MAIN_MODELS = Path.of("src/main/resources/assets/pirates_n_ships/models");
    private static final Path GENERATED_MODELS = Path.of("src/generated/resources/assets/pirates_n_ships/models");

    private static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    @Test
    void itemModelDrawsTintedWater() throws IOException {
        JsonObject item = read(GENERATED_MODELS.resolve("item/water_barrel.json"));
        assertEquals("pirates_n_ships:block/water_barrel", item.get("parent").getAsString());
        assertEquals("minecraft:block/water_still", CrewContentModule.WATER_TEXTURE);
        assertEquals(CrewContentModule.WATER_TEXTURE, item.getAsJsonObject("textures").get(CrewContentModule.WATER_TEXTURE_SLOT).getAsString());

        JsonObject block = read(MAIN_MODELS.resolve("block/water_barrel.json"));
        String slotRef = "#" + CrewContentModule.WATER_TEXTURE_SLOT;
        int waterFaces = 0;
        for (JsonElement element : block.getAsJsonArray("elements")) {
            for (Map.Entry<String, JsonElement> face : element.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                JsonObject f = face.getValue().getAsJsonObject();
                if (!f.get("texture").getAsString().equals(slotRef)) continue;
                waterFaces++;
                assertTrue(f.has("tintindex"), "a water face without a tint index would draw grey water");
                assertEquals(CrewContentClient.WATER_TINT_INDEX, f.get("tintindex").getAsInt());
            }
        }
        assertTrue(waterFaces > 0, "the full barrel shows a water surface");
    }
}
