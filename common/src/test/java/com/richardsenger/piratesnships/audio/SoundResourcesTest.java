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
                "combat.pistol_shot", "combat.pistol_empty", "combat.cannon_shot", "combat.cannon_volley",
                "combat.melee.miss", "combat.melee.hit", "combat.melee.hit_heavy", "combat.melee.clash", "combat.melee.hit_armor",
                "combat.melee.unsheathe", "combat.melee.disarm", "combat.melee.weapon_break")) {
            assertTrue(root.has(key), "sounds.json misses " + key + " (run ./gradlew :neoforge:runData)");
        }
        for (String music : List.of("music.sea", "music.shanty")) {
            for (JsonElement s : root.getAsJsonObject(music).getAsJsonArray("sounds")) {
                assertTrue(s.isJsonObject() && s.getAsJsonObject().get("stream").getAsBoolean(), music + " must stream: " + s);
            }
        }
    }

    /** The sword miss plays our own whooshes (A2), not a reference to vanilla's sweep sound event. */
    @Test
    void meleeMissPlaysOwnFiles() throws IOException {
        JsonObject root = soundsJson();
        JsonObject miss = new JsonObject();
        miss.add("combat.melee.miss", root.get("combat.melee.miss"));
        List<String> files = fileNames(miss);
        assertTrue(files.size() == root.getAsJsonObject("combat.melee.miss").getAsJsonArray("sounds").size(),
                "combat.melee.miss still references a sound event: " + root.get("combat.melee.miss"));
        List<String> expected = new ArrayList<>();
        for (int i = 1; i <= 6; i++) expected.add(NS + ":combat/melee/miss_" + i);
        assertTrue(files.equals(expected), "combat.melee.miss should play " + expected + ", plays " + files);
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

    /** Each manifest file is listed in sounds.json under the event the manifest names (variants share an event). */
    @Test
    void everyManifestFileBelongsToItsEvent() throws IOException {
        JsonObject root = soundsJson();
        JsonObject manifest = JsonParser.parseString(Files.readString(MANIFEST)).getAsJsonObject();
        List<String> problems = new ArrayList<>();
        for (JsonElement e : manifest.getAsJsonArray("sounds")) {
            JsonObject m = e.getAsJsonObject();
            String event = m.get("event").getAsString();
            String target = m.get("target").getAsString();
            String name = NS + ":" + target.substring(0, target.length() - ".ogg".length());
            if (!root.has(event)) {
                problems.add(target + ": event " + event + " is not in sounds.json");
                continue;
            }
            JsonObject only = new JsonObject();
            only.add(event, root.get(event));
            if (!fileNames(only).contains(name)) problems.add(target + ": not a sound of " + event);
        }
        assertTrue(problems.isEmpty(), "manifest and sounds.json disagree: " + problems);
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
