package com.richardsenger.piratesnships.ship.decor.flag;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cloth geometry the renderer draws (pure) and, run from {@code common/}, the committed block state, which since
 * FL1 shows only the pole.
 */
class FlagClothModelTest {

    private static final Path ASSETS = Path.of("src/generated/resources/assets/pirates_n_ships");
    private static final float EPS = 1.0e-6f;

    private static Map<String, FlagClothModel.Face> faces() {
        Map<String, FlagClothModel.Face> m = new HashMap<>();
        for (FlagClothModel.Face f : FlagClothModel.faces()) m.put(f.name(), f);
        return m;
    }

    private static float min(int axis) {
        float v = Float.MAX_VALUE;
        for (FlagClothModel.Face f : FlagClothModel.faces()) for (float[] c : f.corners()) v = Math.min(v, c[axis]);
        return v;
    }

    private static float max(int axis) {
        float v = -Float.MAX_VALUE;
        for (FlagClothModel.Face f : FlagClothModel.faces()) for (float[] c : f.corners()) v = Math.max(v, c[axis]);
        return v;
    }

    @Test
    void clothIsOneBlockHighAndOneAndAHalfLongFromThePoleAxis() {
        assertEquals(0f, min(1), EPS);
        assertEquals(1f, max(1), EPS, "one block high");
        assertEquals(0f, max(2), EPS, "starts on the pole's axis: no gap at the pole");
        assertEquals(-1.5f, min(2), EPS, "1.5 blocks from the pole's axis, pointing north at yaw 0");
        assertEquals(24, FlagClothModel.LENGTH);
        assertEquals(1f / 16f, max(0) - min(0), EPS, "1 px thick");
        assertEquals(0f, max(0) + min(0), EPS, "centred on the pole");
    }

    @Test
    void facesAreTheFrontBackTipAndEdgesWithOutwardNormals() {
        assertEquals(Set.of("east", "west", "north", "up", "down"),
                FlagClothModel.faces().stream().map(FlagClothModel.Face::name).collect(Collectors.toSet()),
                "no face hidden inside the pole");
        for (FlagClothModel.Face f : FlagClothModel.faces()) {
            float[][] c = f.corners();
            assertEquals(4, c.length);
            Vector3f a = new Vector3f(c[0]), b = new Vector3f(c[1]), d = new Vector3f(c[3]);
            Vector3f n = new Vector3f(b).sub(a).cross(new Vector3f(d).sub(a)).normalize();
            assertEquals(f.nx(), n.x, EPS, f.name());
            assertEquals(f.ny(), n.y, EPS, f.name());
            assertEquals(f.nz(), n.z, EPS, f.name());
            for (int i = 0; i < 4; i++) {
                assertTrue(f.u()[i] >= 0f && f.u()[i] <= 1f && f.v()[i] >= 0f && f.v()[i] <= 1f, f.name() + " uv " + i);
            }
        }
    }

    @Test
    void frontAndBackShowTheClothWithSquareTexelsAndTheHoistAtThePole() {
        Map<String, FlagClothModel.Face> faces = faces();
        for (String side : List.of("east", "west")) {
            FlagClothModel.Face f = faces.get(side);
            for (int i = 0; i < 4; i++) {
                float z = f.corners()[i][2];
                float y = f.corners()[i][1];
                // column = distance from the pole in pixels: 1 texel per model pixel along the cloth
                assertEquals(-z * 16f, f.u()[i] * FlagClothModel.TEXTURE_WIDTH, 1.0e-4f, side + " corner " + i);
                // row 0 at the top, 16 rows over the block height
                assertEquals((1f - y) * 16f, f.v()[i] * FlagClothModel.TEXTURE_HEIGHT, 1.0e-4f, side + " corner " + i);
            }
        }
        // The front faces +X and the back -X: the same columns seen from opposite sides read mirrored.
        assertEquals(1f, faces.get("east").nx());
        assertEquals(-1f, faces.get("west").nx());
    }

    @Test
    void tipAndEdgesUseTheLastColumnAndTheSwatch() {
        Map<String, FlagClothModel.Face> faces = faces();
        for (float u : faces.get("north").u()) {
            assertTrue(u >= FlagClothModel.CLOTH_U1 - FlagClothModel.COLUMN_U - EPS && u <= FlagClothModel.CLOTH_U1 + EPS, "tip u " + u);
        }
        for (String edge : List.of("up", "down")) {
            for (float u : faces.get(edge).u()) {
                assertTrue(u >= FlagClothModel.CLOTH_U1 - EPS, edge + " u " + u + " must sample the swatch, not the cloth");
            }
        }
    }

    @Test
    void texturesAndTint() {
        assertEquals("pirates_n_ships:textures/block/flag_navy.png", FlagClothModel.textureFile(FlagKind.NAVY).toString());
        assertEquals("pirates_n_ships:block/flag_jolly_roger", FlagClothModel.texture(FlagKind.JOLLY_ROGER).toString());
        assertThrows(IllegalArgumentException.class, () -> FlagClothModel.texture(FlagKind.NONE));
        for (FlagKind kind : FlagKind.values()) {
            if (kind == FlagKind.NONE) continue;
            assertTrue(Files.exists(Path.of("src/main/resources/assets/pirates_n_ships")
                    .resolve(FlagClothModel.textureFile(kind).getPath())), "missing texture for " + kind);
        }
    }

    /** The committed block state shows the hand-made pole in every state and no cloth model (FL1). */
    @Test
    void generatedBlockStateShowsOnlyThePole() throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(ASSETS.resolve("blockstates/flagpole.json"))).getAsJsonObject();
        assertFalse(root.has("multipart"), "the cloth is no longer part of the block model");
        JsonObject variants = root.getAsJsonObject("variants");
        assertEquals(1, variants.size());
        assertEquals("pirates_n_ships:block/flagpole", variants.entrySet().iterator().next().getValue().getAsJsonObject().get("model").getAsString());
        for (FlagKind kind : FlagKind.values()) {
            assertFalse(Files.exists(ASSETS.resolve("models/block/flagpole_flag_" + kind.getSerializedName() + ".json")),
                    "stale cloth model for " + kind);
        }
    }
}
