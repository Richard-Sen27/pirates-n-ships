package com.richardsenger.piratesnships.audio;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assumptions;
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
 * Checks the committed sound resources against each other (run from {@code common/}): every file a generated
 * {@code sounds.json} entry names in our namespace exists as an {@code .ogg}, every {@code minecraft:} file exists in
 * vanilla's {@code sounds.json} (only when the Gradle asset cache is there), and every manifest target was converted.
 */
class SoundResourcesTest {

    private static final String NS = "pirates_n_ships";
    private static final Path SOUNDS_JSON = Path.of("src/generated/resources/assets/" + NS + "/sounds.json");
    private static final Path SOUNDS = Path.of("src/main/resources/assets/" + NS + "/sounds");
    private static final Path MANIFEST = Path.of("../tools/sounds/manifest.json");
    private static final Path ASSET_CACHE = Path.of(System.getProperty("user.home"), ".gradle/caches/neoformruntime/assets");

    private static JsonObject soundsJson() throws IOException {
        return JsonParser.parseString(Files.readString(SOUNDS_JSON)).getAsJsonObject();
    }

    /** Sound file names of every entry, skipping {@code "type": "event"} references. */
    private static List<String> fileNames(JsonObject root) {
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : root.entrySet()) {
            for (JsonElement s : e.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
                if (s.isJsonPrimitive()) {
                    names.add(s.getAsString());
                } else if (!"event".equals(s.getAsJsonObject().has("type") ? s.getAsJsonObject().get("type").getAsString() : "file")) {
                    names.add(s.getAsJsonObject().get("name").getAsString());
                }
            }
        }
        return names;
    }

    @Test
    void everyOwnSoundFileExists() throws IOException {
        List<String> missing = new ArrayList<>();
        for (String name : fileNames(soundsJson())) {
            if (!name.startsWith(NS + ":")) continue;
            Path ogg = SOUNDS.resolve(name.substring(NS.length() + 1) + ".ogg");
            if (!Files.exists(ogg)) missing.add(name);
        }
        assertTrue(missing.isEmpty(), "sounds.json names missing .ogg files: " + missing);
    }

    @Test
    void soundsJsonHasEveryModulesEntries() throws IOException {
        JsonObject root = soundsJson();
        for (String key : List.of("ship.creak", "anchor.chain", "anchor.splash", "anchor.thud", "music.sea", "music.shanty",
                "combat.pistol_shot", "combat.pistol_empty", "combat.cannon_shot", "combat.cannon_volley")) {
            assertTrue(root.has(key), "sounds.json misses " + key + " (run ./gradlew :neoforge:runData)");
        }
        for (String music : List.of("music.sea", "music.shanty")) {
            for (JsonElement s : root.getAsJsonObject(music).getAsJsonArray("sounds")) {
                assertTrue(s.isJsonObject() && s.getAsJsonObject().get("stream").getAsBoolean(), music + " must stream: " + s);
            }
        }
    }

    @Test
    void everyManifestTargetExists() throws IOException {
        JsonObject manifest = JsonParser.parseString(Files.readString(MANIFEST)).getAsJsonObject();
        List<String> missing = new ArrayList<>();
        int n = 0;
        for (JsonElement e : manifest.getAsJsonArray("sounds")) {
            String target = e.getAsJsonObject().get("target").getAsString();
            n++;
            if (!Files.exists(SOUNDS.resolve(target))) missing.add(target);
        }
        assertFalse(n == 0, "the manifest lists no sounds");
        assertTrue(missing.isEmpty(), "manifest targets not converted (run python3 tools/convert_sounds.py): " + missing);
    }

    @Test
    void everyVanillaSoundFileExists() throws IOException {
        Path index = ASSET_CACHE.resolve("indexes/17.json");
        Assumptions.assumeTrue(Files.exists(index), "no vanilla asset cache at " + ASSET_CACHE);
        JsonObject objects = JsonParser.parseString(Files.readString(index)).getAsJsonObject().getAsJsonObject("objects");
        List<String> missing = new ArrayList<>();
        for (String name : fileNames(soundsJson())) {
            if (!name.startsWith("minecraft:")) continue;
            if (!objects.has("minecraft/sounds/" + name.substring("minecraft:".length()) + ".ogg")) missing.add(name);
        }
        assertTrue(missing.isEmpty(), "sounds.json names vanilla sound files that don't exist: " + missing);
    }
}
