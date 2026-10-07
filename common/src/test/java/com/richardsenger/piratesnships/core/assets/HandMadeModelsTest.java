package com.richardsenger.piratesnships.core.assets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hand-made Blockbench models (design.md §4.8) under {@code src/main/resources/assets/pirates_n_ships/models}
 * (run from {@code common/}): each one parses with vanilla's model loader, keeps its elements inside the vanilla limits
 * (coordinates −16..32, one rotation axis with an angle of 0, ±22.5 or ±45 degrees) and resolves every face texture
 * through its own texture map, so nothing renders as the missing texture.
 */
class HandMadeModelsTest {

    static final Path MAIN_MODELS = Path.of("src/main/resources/assets/pirates_n_ships/models");
    private static final Set<Float> ANGLES = Set.of(0f, 22.5f, -22.5f, 45f, -45f);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    static List<Path> jsonFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
    }

    /** Every hand-made model by name; a new Blockbench model is added here, a missing or stray file fails. */
    static final List<String> BLOCK_MODELS = List.of("capstan", "cargo_crate", "figurehead_eagle", "figurehead_lion", "figurehead_mermaid",
            "figurehead_skull", "flagpole", "helm", "nameplate", "sail_winch", "yard");
    static final List<String> ITEM_MODELS = List.of("cannonball", "captains_whistle", "cloth", "cutlass",
            "grappling_hook", "hardtack", "lead_shot", "lime", "musket", "pistol", "rapier", "rum", "saber", "salt_pork",
            "salted_fish", "shackles", "spices", "tobacco");

    /** The display slots a hand-made item model copies from vanilla's {@code item/handheld} and {@code item/generated}. */
    private static final List<String> ITEM_DISPLAY_SLOTS = List.of("thirdperson_righthand", "thirdperson_lefthand",
            "firstperson_righthand", "firstperson_lefthand", "ground", "head", "fixed");

    @Test
    void everyHandMadeModelIsListedByName() throws IOException {
        assertEquals(BLOCK_MODELS, names("block"), "hand-made block models");
        assertEquals(ITEM_MODELS, names("item"), "hand-made item models");
    }

    private static List<String> names(String folder) throws IOException {
        return jsonFiles(MAIN_MODELS.resolve(folder)).stream()
                .map(p -> p.getFileName().toString().replace(".json", "")).sorted().toList();
    }

    /**
     * A hand-made item model must not inherit from {@code item/generated} or {@code item/handheld}: vanilla's
     * {@code ModelBakery} bakes every model whose root parent is {@code builtin/generated} from its {@code layer0}
     * sprite and ignores the elements. So the model has no parent and carries the vanilla handheld display transforms
     * itself, plus {@code gui_light: front} like a flat item.
     */
    @Test
    void handMadeItemModelsBringTheirOwnHandheldTransforms() throws IOException {
        for (Path file : jsonFiles(MAIN_MODELS.resolve("item"))) {
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            assertFalse(json.has("parent"), file + ": a parent would root in builtin/generated and drop the elements");
            assertEquals("front", json.get("gui_light").getAsString(), file + ": gui_light");
            JsonObject display = json.getAsJsonObject("display");
            assertNotNull(display, file + ": no display transforms");
            for (String slot : ITEM_DISPLAY_SLOTS) {
                assertTrue(display.has(slot), file + ": missing display slot " + slot);
            }
        }
    }

    @Test
    void everyHandMadeModelParsesAndStaysInsideTheVanillaLimits() throws IOException {
        List<Path> models = jsonFiles(MAIN_MODELS);
        assertFalse(models.isEmpty(), "no hand-made models found under " + MAIN_MODELS.toAbsolutePath());
        for (Path file : models) {
            String text = Files.readString(file);
            BlockModel model = assertDoesNotThrow(() -> BlockModel.fromString(text), file + " does not parse");
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            assertTrue(json.has("elements"), file + ": a hand-made model brings its own elements");
            List<BlockElement> elements = model.getElements();
            assertFalse(elements.isEmpty(), file + ": no elements");
            for (BlockElement e : elements) {
                for (float v : new float[]{e.from.x(), e.from.y(), e.from.z(), e.to.x(), e.to.y(), e.to.z()}) {
                    assertTrue(v >= -16f && v <= 32f, file + ": coordinate " + v + " outside -16..32");
                }
                if (e.rotation != null) {
                    assertTrue(ANGLES.contains(e.rotation.angle()), file + ": rotation " + e.rotation.angle());
                }
            }
            Map<String, JsonElement> textures = json.has("textures") ? json.getAsJsonObject("textures").asMap() : Map.of();
            assertTrue(textures.containsKey("particle"), file + ": no particle texture (breaking particles)");
            for (BlockElement e : elements) {
                for (BlockElementFace face : e.faces.values()) {
                    assertResolves(file, textures, face.texture(), new ArrayList<>());
                }
            }
        }
    }

    /** Follows {@code #key} references through the model's own texture map down to a texture id. */
    private static void assertResolves(Path file, Map<String, JsonElement> textures, String ref, List<String> seen) {
        if (!ref.startsWith("#")) return;
        String key = ref.substring(1);
        assertFalse(seen.contains(key), file + ": texture reference loop " + seen);
        seen.add(key);
        JsonElement value = textures.get(key);
        assertTrue(value != null && value.isJsonPrimitive(), file + ": unresolved texture " + ref);
        assertResolves(file, textures, value.getAsString(), seen);
    }
}
