package com.richardsenger.piratesnships.combat.melee.client.anim;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.richardsenger.piratesnships.combat.firearms.FirearmsConfig;
import com.richardsenger.piratesnships.combat.firearms.client.anim.FirearmAnimationMapping;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.loading.UniversalAnimLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The player animations under {@code assets/pirates_n_ships/player_animations/} (run from {@code common/}):
 * every animation {@link MeleeAnimationMapping} or {@link FirearmAnimationMapping} can play has a file with PAL's GeckoLib/Bedrock top-level fields, only
 * animates PAL's player bones, and loads with PAL's own loader at the expected length; no stray files.
 */
class PalAnimationFilesTest {

    static final Path DIR = Path.of("src/main/resources/assets/pirates_n_ships/player_animations");
    /** PAL's player bones (docs.zigythebird.com/pal/features/bones, {@code PlayerAnimationController.registerBones}). */
    static final Set<String> BONES = Set.of("head", "body", "torso", "right_arm", "left_arm", "right_leg", "left_leg",
            "right_item", "left_item", "cape", "elytra");
    static final Set<String> CHANNELS = Set.of("rotation", "position", "scale", "bend");
    static final Set<String> LOOPS = Set.of("hold_on_last_frame", "loop", "play_once");
    /** Every animation of every PAL layer of the mod. */
    static final List<String> ALL = Stream.of(MeleeAnimationMapping.ALL, FirearmAnimationMapping.ALL)
            .flatMap(List::stream).collect(Collectors.toList());

    static JsonObject read(String name) throws IOException {
        Path file = DIR.resolve(name + ".json");
        assertTrue(Files.isRegularFile(file), "missing animation file " + file);
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    @Test
    void everyMappedAnimationHasAFileAndNothingElseIsThere() throws IOException {
        Set<String> files = new TreeSet<>();
        try (Stream<Path> s = Files.list(DIR)) {
            s.forEach(p -> files.add(p.getFileName().toString().replaceFirst("\\.json$", "")));
        }
        assertEquals(new TreeSet<>(ALL), files);
    }

    @Test
    void filesHaveThePalTopLevelFields() throws IOException {
        for (String name : ALL) {
            JsonObject root = read(name);
            assertEquals("1.8.0", root.get("format_version").getAsString(), name);
            JsonObject animations = root.getAsJsonObject("animations");
            assertNotNull(animations, name + ": no animations");
            // the animation id is <namespace>:<key>, so the key must be the name the mapping uses
            assertEquals(Set.of(name), animations.keySet(), name);
            JsonObject anim = animations.getAsJsonObject(name);
            float length = anim.get("animation_length").getAsFloat();
            assertTrue(length > 0f, name + ": animation_length");
            JsonElement loop = anim.get("loop");
            assertTrue(loop.getAsJsonPrimitive().isBoolean() || LOOPS.contains(loop.getAsString()), name + ": loop " + loop);
            JsonObject bones = anim.getAsJsonObject("bones");
            assertFalse(bones.isEmpty(), name + ": no bones");
            for (Map.Entry<String, JsonElement> bone : bones.entrySet()) {
                assertTrue(BONES.contains(bone.getKey()), name + ": unknown bone " + bone.getKey());
                for (Map.Entry<String, JsonElement> channel : bone.getValue().getAsJsonObject().entrySet()) {
                    assertTrue(CHANNELS.contains(channel.getKey()), name + ": unknown channel " + channel.getKey());
                    for (Map.Entry<String, JsonElement> frame : channel.getValue().getAsJsonObject().entrySet()) {
                        float t = Float.parseFloat(frame.getKey());
                        assertTrue(t >= 0f && t <= length + 1e-4f, name + ": keyframe at " + t + " outside 0.." + length);
                        assertEquals(3, frame.getValue().getAsJsonArray().size(), name + ": keyframe vector");
                    }
                }
            }
        }
    }

    @Test
    void palLoadsEveryFile() throws IOException {
        for (String name : ALL) {
            Map<String, Animation> loaded;
            try (InputStream in = Files.newInputStream(DIR.resolve(name + ".json"))) {
                loaded = UniversalAnimLoader.loadAnimations(in);
            }
            assertEquals(Set.of(name), loaded.keySet(), name);
            Animation a = loaded.get(name);
            float seconds = read(name).getAsJsonObject("animations").getAsJsonObject(name).get("animation_length").getAsFloat();
            // PAL works in ticks (20 per second); the speed math in MeleeAnimationMapping relies on that
            assertEquals(seconds * 20f, a.length(), 1e-3, name);
            assertFalse(a.boneAnimations().isEmpty(), name);
        }
    }

    @Test
    void reloadsLastAboutTheDefaultReloadTimeAndAimsHoldTheirPose() throws IOException {
        // the speed modifier should barely stretch a reload at the default reload times
        assertReloadLength(FirearmAnimationMapping.PISTOL_RELOAD, FirearmsConfig.PISTOL_RELOAD.get());
        assertReloadLength(FirearmAnimationMapping.MUSKET_RELOAD, FirearmsConfig.MUSKET_RELOAD.get());
        for (String aim : List.of(FirearmAnimationMapping.PISTOL_AIM, FirearmAnimationMapping.MUSKET_AIM)) {
            assertEquals("hold_on_last_frame", read(aim).getAsJsonObject("animations").getAsJsonObject(aim).get("loop").getAsString(), aim);
        }
    }

    private static void assertReloadLength(String name, int defaultReloadTicks) throws IOException {
        JsonObject anim = read(name).getAsJsonObject("animations").getAsJsonObject(name);
        float ticks = anim.get("animation_length").getAsFloat() * 20f;
        assertEquals(defaultReloadTicks, ticks, defaultReloadTicks * 0.1f, name + " length in ticks");
        var loop = anim.get("loop").getAsJsonPrimitive();
        assertTrue(loop.isBoolean() ? !loop.getAsBoolean() : "play_once".equals(loop.getAsString()), name + " must play once");
    }
}
