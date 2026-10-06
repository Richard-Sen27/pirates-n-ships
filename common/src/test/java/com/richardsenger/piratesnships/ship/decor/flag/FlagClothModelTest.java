package com.richardsenger.piratesnships.ship.decor.flag;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The cloth model JSON (pure) and, run from {@code common/}, the committed block state and models that use it. */
class FlagClothModelTest {

    private static final Path ASSETS = Path.of("src/generated/resources/assets/pirates_n_ships");

    private static List<FlagKind> shownKinds() {
        List<FlagKind> kinds = new ArrayList<>(List.of(FlagKind.values()));
        kinds.remove(FlagKind.NONE);
        return kinds;
    }

    private static float[] floats(JsonArray a) {
        float[] f = new float[a.size()];
        for (int i = 0; i < f.length; i++) f[i] = a.get(i).getAsFloat();
        return f;
    }

    @Test
    void clothIsOneBlockHighAndReachesTheModelLimitFromThePoleCenter() {
        JsonObject json = FlagClothModel.json("pirates_n_ships:block/flag_navy");
        JsonArray elements = json.getAsJsonArray("elements");
        assertEquals(1, elements.size());
        JsonObject e = elements.get(0).getAsJsonObject();
        float[] from = floats(e.getAsJsonArray("from"));
        float[] to = floats(e.getAsJsonArray("to"));
        assertEquals(List.of(7.5f, 0f, -16f), List.of(from[0], from[1], from[2]));
        assertEquals(List.of(8.5f, 16f, 8f), List.of(to[0], to[1], to[2]));
        assertEquals(16f, to[1] - from[1], "one block high");
        assertEquals(24, FlagClothModel.LENGTH);
        assertEquals(24f, to[2] - from[2], "1.5 blocks from the pole's center");
        assertTrue(from[2] <= 6f && to[2] >= 6f, "starts inside the pole (pole surface at z=6): no gap");
        assertEquals(1f, to[0] - from[0], "1 px thick");
    }

    @Test
    void facesUseTheTextureWithinItsBoundsAndTheBackIsMirrored() {
        JsonObject json = FlagClothModel.json("pirates_n_ships:block/flag_merchant");
        assertEquals("minecraft:cutout", json.get("render_type").getAsString());
        assertFalse(json.get("ambientocclusion").getAsBoolean());
        assertEquals("pirates_n_ships:block/flag_merchant", json.getAsJsonObject("textures").get("cloth").getAsString());
        JsonObject faces = json.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces");
        assertEquals(Set.of("east", "west", "north", "up", "down"), faces.keySet(), "no face hidden inside the pole");
        for (Map.Entry<String, JsonElement> f : faces.entrySet()) {
            JsonObject face = f.getValue().getAsJsonObject();
            assertEquals("#cloth", face.get("texture").getAsString());
            float[] uv = floats(face.getAsJsonArray("uv"));
            for (int i = 0; i < 4; i++) {
                // texture pixels: u * 2 over 32 columns, v over 16 rows
                float px = i % 2 == 0 ? uv[i] * FlagClothModel.TEXTURE_WIDTH / 16f : uv[i] * FlagClothModel.TEXTURE_HEIGHT / 16f;
                float max = i % 2 == 0 ? FlagClothModel.TEXTURE_WIDTH : FlagClothModel.TEXTURE_HEIGHT;
                assertTrue(px >= 0 && px <= max, f.getKey() + " uv " + i + " = " + px + " px outside 0.." + max);
            }
        }
        float[] east = floats(faces.getAsJsonObject("east").getAsJsonArray("uv"));
        float[] west = floats(faces.getAsJsonObject("west").getAsJsonArray("uv"));
        // 24 texture columns over 24 model pixels: square texels
        assertEquals(List.of(0f, 0f, 12f, 16f), List.of(east[0], east[1], east[2], east[3]));
        assertEquals(List.of(12f, 0f, 0f, 16f), List.of(west[0], west[1], west[2], west[3]));
    }

    @Test
    void builderRejectsWhatVanillaRejects() {
        assertThrows(IllegalArgumentException.class, () -> new ElementModel().element(0, 0, -16.5f, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ElementModel().element(0, 0, 0, 1, 1, 32.5f));
        assertThrows(IllegalArgumentException.class, () -> new ElementModel().element(2, 0, 0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ElementModel().element(0, 0, 0, 1, 1, 1)
                .face(ElementModel.Face.UP, 0, 0, 17, 1, "#t"));
        assertThrows(IllegalStateException.class, () -> new ElementModel().element(0, 0, 0, 1, 1, 1).end());
    }

    @Test
    void everyKindAndFacingHasADistinctModelAndRotation() {
        Set<String> models = new HashSet<>();
        Set<String> variants = new HashSet<>();
        for (FlagKind kind : shownKinds()) {
            assertTrue(models.add(FlagClothModel.modelId(kind).toString()));
            for (Direction d : Direction.Plane.HORIZONTAL) {
                assertTrue(variants.add(FlagClothModel.modelId(kind) + "@" + FlagClothModel.yRotation(d)));
            }
        }
        assertEquals(shownKinds().size() * 4, variants.size());
        assertThrows(IllegalArgumentException.class, () -> FlagClothModel.modelId(FlagKind.NONE));
    }

    /** The committed block state lists exactly one cloth per shown kind and facing, and nothing for {@code none}. */
    @Test
    void generatedBlockStateReferencesEveryClothVariant() throws IOException {
        JsonArray parts = JsonParser.parseString(Files.readString(ASSETS.resolve("blockstates/flagpole.json")))
                .getAsJsonObject().getAsJsonArray("multipart");
        for (FlagKind kind : FlagKind.values()) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                List<JsonObject> cloths = new ArrayList<>();
                for (JsonElement p : parts) {
                    JsonObject part = p.getAsJsonObject();
                    if (!part.has("when")) continue;
                    JsonObject when = part.getAsJsonObject("when");
                    if (when.get("flag").getAsString().equals(kind.getSerializedName()) && when.get("facing").getAsString().equals(d.getSerializedName())) {
                        cloths.add(part.getAsJsonObject("apply"));
                    }
                }
                if (kind == FlagKind.NONE) {
                    assertTrue(cloths.isEmpty(), "a struck flag shows no cloth");
                    continue;
                }
                assertEquals(1, cloths.size(), kind + " " + d);
                JsonObject apply = cloths.get(0);
                assertEquals(FlagClothModel.modelId(kind).toString(), apply.get("model").getAsString());
                assertEquals(FlagClothModel.yRotation(d), apply.has("y") ? apply.get("y").getAsInt() : 0);
                assertFalse(apply.has("uvlock") && apply.get("uvlock").getAsBoolean(), "no uvlock on the cloth");
            }
        }
        for (FlagKind kind : shownKinds()) {
            Path model = ASSETS.resolve("models/block/flagpole_flag_" + kind.getSerializedName() + ".json");
            assertEquals(FlagClothModel.json(kind), JsonParser.parseString(Files.readString(model)), "stale datagen output for " + kind);
        }
    }
}
