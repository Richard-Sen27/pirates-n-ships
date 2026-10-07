package com.richardsenger.piratesnships.combat.grapple.client.anim;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.loading.UniversalAnimLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The rope slide animation (GR2, run from {@code common/}): PAL loads it, it holds its last frame, only player bones. */
class RopeSlideAnimationFileTest {

    static final Path FILE = Path.of("src/main/resources/assets/pirates_n_ships/rope_animations/" + RopeSlidePoses.ANIMATION + ".json");
    static final Set<String> BONES = Set.of("head", "body", "torso", "right_arm", "left_arm", "right_leg", "left_leg",
            "right_item", "left_item");

    @Test
    void palLoadsTheRopeSlideAndItHoldsItsLastFrame() throws IOException {
        Map<String, Animation> loaded;
        try (InputStream in = Files.newInputStream(FILE)) {
            loaded = UniversalAnimLoader.loadAnimations(in);
        }
        assertEquals(Set.of(RopeSlidePoses.ANIMATION), loaded.keySet());
        Animation a = loaded.get(RopeSlidePoses.ANIMATION);
        assertEquals(0.25f * 20f, a.length(), 1e-3);
        assertFalse(a.boneAnimations().isEmpty());

        JsonObject root = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
        assertEquals("1.8.0", root.get("format_version").getAsString());
        JsonObject anim = root.getAsJsonObject("animations").getAsJsonObject(RopeSlidePoses.ANIMATION);
        assertEquals("hold_on_last_frame", anim.get("loop").getAsString());
        JsonObject bones = anim.getAsJsonObject("bones");
        assertTrue(BONES.containsAll(bones.keySet()), "unknown bones " + bones.keySet());
        assertTrue(bones.has("right_arm") && bones.has("left_arm") && bones.has("right_leg") && bones.has("left_leg"));
        // both arms end raised above the head (vanilla angle: -180 = straight up)
        for (String arm : new String[]{"right_arm", "left_arm"}) {
            float x = bones.getAsJsonObject(arm).getAsJsonObject("rotation").getAsJsonArray("0.25").get(0).getAsFloat();
            assertTrue(x < -150f, arm + " is not raised: " + x);
        }
    }
}
