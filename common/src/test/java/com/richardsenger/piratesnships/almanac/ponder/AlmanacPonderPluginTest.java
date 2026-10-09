package com.richardsenger.piratesnships.almanac.ponder;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.createmod.ponder.api.registration.StoryBoardEntry;
import net.createmod.ponder.foundation.registration.DefaultPonderSceneRegistrationHelper;
import net.createmod.ponder.foundation.registration.DefaultSharedTextRegistrationHelper;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.createmod.ponder.foundation.registration.PonderSceneRegistry;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Our plugin against Ponder's own registries (headless: registration needs no client): the scenes land under the
 * right items with the right schematics, the shared text has the keys our generated lang carries, and the lang key
 * formulas match Ponder's.
 */
class AlmanacPonderPluginTest {

    private static final String MOD = "pirates_n_ships";

    @Test
    void registersEveryScenesStoryboardForItsItem() {
        PonderLocalization localization = new PonderLocalization();
        PonderSceneRegistry registry = new PonderSceneRegistry(localization);
        AlmanacPonderPlugin plugin = new AlmanacPonderPlugin();
        assertEquals(MOD, plugin.getModId());
        plugin.registerScenes(new DefaultPonderSceneRegistrationHelper(plugin.getModId(), registry));

        List<StoryBoardEntry> entries = registry.getRegisteredEntries().stream().map(Map.Entry::getValue).toList();
        assertEquals(AlmanacScenes.ALL.size(), entries.size());
        for (AlmanacScenes.Scene scene : AlmanacScenes.ALL) {
            assertTrue(registry.doScenesExistForId(scene.component()), "no scenes for " + scene.component());
            StoryBoardEntry entry = entries.stream().filter(e -> e.getSchematicLocation().equals(scene.schematic()))
                    .findFirst().orElseThrow(() -> new AssertionError("no storyboard with schematic " + scene.schematic()));
            assertEquals(scene.component(), entry.getComponent());
            assertEquals(MOD, entry.getNamespace());
            assertNotNull(AlmanacPonderPlugin.STORYBOARDS.get(scene.id()));
        }
        assertTrue(registry.doScenesExistForId(ResourceLocation.parse("pirates_n_ships:cannon")),
                "the cannon item's Ponder key plays the scene");
    }

    @Test
    void sharedTextIsEveryLineOfTheScene() {
        PonderLocalization localization = new PonderLocalization();
        new AlmanacPonderPlugin().registerSharedText(new DefaultSharedTextRegistrationHelper(MOD, localization));
        Map<ResourceLocation, String> expected = new HashMap<>();
        for (CannonLoadingText.Line line : CannonLoadingText.Line.values()) {
            expected.put(line.key(), line.english());
        }
        assertEquals(expected, localization.shared);
    }

    @Test
    void langKeysFollowPondersFormulas() {
        ResourceLocation key = CannonLoadingText.Line.POWDER.key();
        assertEquals(PonderKeys.shared(key), CannonLoadingText.sharedLangKey(key));
        assertEquals(PonderKeys.title(AlmanacScenes.CANNON_LOADING), CannonLoadingText.titleLangKey(AlmanacScenes.CANNON_LOADING));
        assertEquals("pirates_n_ships.ponder.cannon_loading.header", CannonLoadingText.titleLangKey(AlmanacScenes.CANNON_LOADING));
        assertEquals("pirates_n_ships.ponder.shared.cannon_loading.powder", CannonLoadingText.sharedLangKey(key));
    }

    /** The generated en_us.json carries every line and the title (./gradlew :neoforge:runData). */
    @Test
    void generatedLangHasTheScene() throws IOException {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("common/src/generated/resources"))) {
            root = root.getParent();
        }
        assertNotNull(root, "repository root not found");
        Path file = root.resolve("common/src/generated/resources/assets/" + MOD + "/lang/en_us.json");
        JsonObject lang;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            lang = JsonParser.parseReader(reader).getAsJsonObject();
        }
        for (Map.Entry<String, String> e : CannonLoadingText.lang().entrySet()) {
            assertTrue(lang.has(e.getKey()), "run ./gradlew :neoforge:runData: no " + e.getKey());
            assertEquals(e.getValue(), lang.get(e.getKey()).getAsString(), e.getKey());
        }
        assertEquals(CannonLoadingText.Line.values().length + 1, CannonLoadingText.lang().size());
    }

    /** Ponder's own (protected) lang key formulas. */
    private static final class PonderKeys extends PonderLocalization {
        static String shared(ResourceLocation key) {
            return langKeyForShared(key);
        }

        static String title(ResourceLocation sceneId) {
            return langKeyForSpecific(sceneId, "header");
        }
    }
}
