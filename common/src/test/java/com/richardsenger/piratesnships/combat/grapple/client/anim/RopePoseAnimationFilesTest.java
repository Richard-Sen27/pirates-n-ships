package com.richardsenger.piratesnships.combat.grapple.client.anim;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.richardsenger.piratesnships.combat.grapple.RopePose;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.loading.UniversalAnimLoader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GR8's rope poses (written by {@code tools/gen_rope_animations.py}, run from {@code common/}): every {@link RopePose}
 * has its file in {@code rope_animations/}, PAL loads it at its length, it keys only PAL's player bones and channels with
 * keyframes inside its length, and the poses do what they say: the hang and the swing hold the hands above the head, the
 * climb goes hand over hand and plays once per pull, the pull reaches up the rope ahead while standing.
 */
class RopePoseAnimationFilesTest {

    static final Path DIR = Path.of("src/main/resources/assets/pirates_n_ships/rope_animations");
    static final Set<String> CHANNELS = Set.of("rotation", "position", "scale", "bend");

    static JsonObject anim(String name) throws IOException {
        return JsonParser.parseString(Files.readString(DIR.resolve(name + ".json"))).getAsJsonObject()
                .getAsJsonObject("animations").getAsJsonObject(name);
    }

    static float key(JsonObject anim, String bone, String time, int axis) {
        return anim.getAsJsonObject("bones").getAsJsonObject(bone).getAsJsonObject("rotation").getAsJsonArray(time).get(axis).getAsFloat();
    }

    @Test
    void everyPoseHasAFileThatPalLoads() throws IOException {
        for (RopePose pose : RopePose.values()) {
            String name = pose.animation();
            if (name == null) continue;
            Path file = DIR.resolve(name + ".json");
            assertTrue(Files.isRegularFile(file), "missing " + file);
            Map<String, Animation> loaded;
            try (InputStream in = Files.newInputStream(file)) {
                loaded = UniversalAnimLoader.loadAnimations(in);
            }
            assertEquals(Set.of(name), loaded.keySet(), name);
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            assertEquals("1.8.0", root.get("format_version").getAsString(), name);
            JsonObject a = anim(name);
            float length = a.get("animation_length").getAsFloat();
            assertEquals(length * 20f, loaded.get(name).length(), 1e-3, name);
            assertFalse(loaded.get(name).boneAnimations().isEmpty(), name);
            for (Map.Entry<String, JsonElement> bone : a.getAsJsonObject("bones").entrySet()) {
                assertTrue(RopeSlideAnimationFileTest.BONES.contains(bone.getKey()), name + ": unknown bone " + bone.getKey());
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
    void theHangAndTheSwingHoldBothHandsAboveTheHead() throws IOException {
        for (RopePose pose : new RopePose[]{RopePose.HANG, RopePose.SWING}) {
            JsonObject a = anim(pose.animation());
            assertEquals("hold_on_last_frame", a.get("loop").getAsString(), pose.animation());
            for (String arm : new String[]{"right_arm", "left_arm"}) {
                assertTrue(key(a, arm, "0.25", 0) < -150f, pose.animation() + ": " + arm + " is not raised");
            }
            assertTrue(a.getAsJsonObject("bones").has("right_leg") && a.getAsJsonObject("bones").has("left_leg"), pose.animation());
        }
        // the hang's legs are pressed together and crossed (right leg in, left leg in), the swing's trail back
        JsonObject hang = anim(RopePose.HANG.animation());
        assertTrue(key(hang, "right_leg", "0.25", 2) < 0f && key(hang, "left_leg", "0.25", 2) > 0f, "the hang's legs are not crossed");
        JsonObject swing = anim(RopePose.SWING.animation());
        assertTrue(key(swing, "right_leg", "0.25", 0) > 0f && key(swing, "left_leg", "0.25", 0) > 0f, "the swing's legs do not trail");
    }

    @Test
    void theClimbGoesHandOverHandOncePerPull() throws IOException {
        JsonObject a = anim(RopePose.CLIMB.animation());
        assertEquals("play_once", a.get("loop").getAsString());
        // the moves fit in one pull's PULL_TICKS (the file runs a little longer and holds the hang)
        assertTrue(a.get("animation_length").getAsFloat() * 20f >= RopePose.PULL_TICKS);
        // first the right hand reaches higher, then the left; the knee of the reaching side draws up
        assertTrue(key(a, "right_arm", "0.15", 0) < key(a, "left_arm", "0.15", 0) - 20f, "the right hand does not reach first");
        assertTrue(key(a, "left_arm", "0.35", 0) < key(a, "right_arm", "0.35", 0) - 20f, "the left hand does not reach second");
        assertTrue(key(a, "right_leg", "0.15", 0) < key(a, "left_leg", "0.15", 0) - 20f, "the right knee does not draw up");
        assertTrue(key(a, "left_leg", "0.35", 0) < key(a, "right_leg", "0.35", 0) - 20f, "the left knee does not draw up");
        // it starts and ends in the hang, so pulls in a row join up
        JsonObject hang = anim(RopePose.HANG.animation());
        for (String arm : new String[]{"right_arm", "left_arm"}) {
            assertEquals(key(hang, arm, "0.25", 0), key(a, arm, "0.0", 0), 1e-3, arm);
            assertEquals(key(hang, arm, "0.25", 0), key(a, arm, "0.5", 0), 1e-3, arm);
        }
    }

    @Test
    void thePullReachesUpTheRopeAheadWhileStanding() throws IOException {
        JsonObject a = anim(RopePose.PULL.animation());
        assertEquals("play_once", a.get("loop").getAsString());
        assertTrue(key(a, "right_arm", "0.15", 0) < key(a, "left_arm", "0.15", 0) - 30f, "the right hand does not reach first");
        assertTrue(key(a, "left_arm", "0.35", 0) < key(a, "right_arm", "0.35", 0) - 30f, "the left hand does not reach second");
        for (String t : new String[]{"0.0", "0.15", "0.35", "0.5"}) {
            float lean = key(a, "torso", t, 0);
            assertTrue(lean < 0f && lean > -20f, "the pull leans " + lean + " at " + t);
            for (String arm : new String[]{"right_arm", "left_arm"}) {
                float x = key(a, arm, t, 0);
                assertTrue(x < -80f && x > -150f, arm + " at " + t + " is not ahead and up: " + x);
            }
        }
    }
}
