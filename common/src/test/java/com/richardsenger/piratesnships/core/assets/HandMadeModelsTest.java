package com.richardsenger.piratesnships.core.assets;

import com.google.gson.JsonArray;
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
import java.util.HashMap;
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
    static final List<String> BLOCK_MODELS = List.of("bilge_pump", "brig_bars", "brig_bars_post", "brig_bars_side",
            "brig_bars_side_alt", "brig_door_bottom_left", "brig_door_bottom_left_locked",
            "brig_door_bottom_left_open_locked", "brig_door_bottom_right", "brig_door_bottom_right_locked",
            "brig_door_bottom_right_open_locked", "brig_door_top_left", "brig_door_top_right", "cannon",
            "cannon_loaded", "cannon_powder", "capstan", "cargo_barrel", "cargo_crate", "cleat", "figurehead_eagle",
            "figurehead_lion", "figurehead_mermaid", "figurehead_skull", "flagpole", "harbor_desk", "helm", "nameplate",
            "pantry", "sail_winch", "sea_chest", "swivel_gun", "swivel_gun_barrel", "swivel_gun_barrel_loaded", "swivel_gun_yoke",
            "water_barrel", "water_barrel_fill0", "water_barrel_fill1", "water_barrel_fill2",
            "water_barrel_fill3", "yard");
    static final List<String> ITEM_MODELS = List.of("bounty_proof", "brig_door", "brig_key", "cannonball", "captains_whistle", "cloth", "cutlass", "doubloon",
            "grappling_hook", "hardtack", "lead_shot", "lime", "musket", "musket_loaded", "pistol",
            "pistol_loaded", "rapier", "rum", "saber", "salt_pork",
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

    /** The guns that show a cocked-hammer variant while loaded (P6), and the lock parts the variant moves or adds. */
    static final List<String> LOADED_VARIANTS = List.of("musket", "pistol");
    private static final Set<String> LOCK_PARTS = Set.of("cock", "cock_jaw", "flint", "frizzen", "pan_cover");

    /**
     * The pistol and the musket switch to their {@code _loaded} model through an item model override on the
     * {@code pirates_n_ships:loaded} property ({@code FirearmsClient}). The variant keeps the base's display entries
     * verbatim (the F8h grip) and its textures, and differs only in the lock: every other element is identical.
     */
    @Test
    void loadedGunsOverrideToTheirCockedVariant() throws IOException {
        for (String gun : LOADED_VARIANTS) {
            JsonObject base = itemModel(gun);
            JsonObject loaded = itemModel(gun + "_loaded");
            JsonArray overrides = base.getAsJsonArray("overrides");
            assertNotNull(overrides, gun + ": no overrides");
            assertEquals(1, overrides.size(), gun + ": overrides");
            JsonObject override = overrides.get(0).getAsJsonObject();
            JsonObject predicate = override.getAsJsonObject("predicate");
            assertEquals(Set.of("pirates_n_ships:loaded"), predicate.keySet(), gun + ": predicate");
            assertEquals(1f, predicate.get("pirates_n_ships:loaded").getAsFloat(), gun + ": predicate value");
            assertEquals("pirates_n_ships:item/" + gun + "_loaded", override.get("model").getAsString(), gun + ": override model");
            assertFalse(loaded.has("overrides"), gun + "_loaded: overrides of an override target are never read");
            assertEquals(base.get("display"), loaded.get("display"), gun + "_loaded: display entries differ from the base");
            assertEquals(base.get("textures"), loaded.get("textures"), gun + "_loaded: textures differ from the base");
            assertEquals(otherElements(base, gun), otherElements(loaded, gun + "_loaded"),
                    gun + "_loaded: elements outside the lock differ from the base");
            List<String> baseNames = elementNames(base);
            List<String> loadedNames = elementNames(loaded);
            assertTrue(loadedNames.containsAll(List.of("cock", "cock_jaw", "flint", "frizzen")), gun + "_loaded: lock parts");
            for (String name : loadedNames) {
                assertTrue(baseNames.contains(name) || LOCK_PARTS.contains(name), gun + "_loaded: unexpected element " + name);
            }
        }
    }

    /** Every override of a hand-made item model points at an existing hand-made model of ours. */
    @Test
    void everyItemModelOverrideTargetExists() throws IOException {
        for (Path file : jsonFiles(MAIN_MODELS.resolve("item"))) {
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (!json.has("overrides")) continue;
            for (JsonElement o : json.getAsJsonArray("overrides")) {
                String model = o.getAsJsonObject().get("model").getAsString();
                assertTrue(model.startsWith("pirates_n_ships:item/"), file + ": override target " + model);
                Path target = MAIN_MODELS.resolve(model.substring("pirates_n_ships:".length()) + ".json");
                assertTrue(Files.isRegularFile(target), file + ": override target " + model + " has no hand-made file");
                assertFalse(o.getAsJsonObject().getAsJsonObject("predicate").isEmpty(), file + ": empty predicate");
            }
        }
    }

    private static JsonObject itemModel(String name) throws IOException {
        return JsonParser.parseString(Files.readString(MAIN_MODELS.resolve("item/" + name + ".json"))).getAsJsonObject();
    }

    private static List<String> elementNames(JsonObject model) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : model.getAsJsonArray("elements")) out.add(e.getAsJsonObject().get("name").getAsString());
        return out;
    }

    private static Map<String, JsonElement> otherElements(JsonObject model, String what) {
        Map<String, JsonElement> out = new HashMap<>();
        for (JsonElement e : model.getAsJsonArray("elements")) {
            String name = e.getAsJsonObject().get("name").getAsString();
            if (LOCK_PARTS.contains(name)) continue;
            assertFalse(out.containsKey(name), what + ": duplicate element name " + name);
            out.put(name, e);
        }
        return out;
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
