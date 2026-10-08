package com.richardsenger.piratesnships.combat.grapple.client.anim;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.loading.UniversalAnimLoader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The haul animation (ART7 for GR5, run from {@code common/}): PAL loads it, it loops, it keys only player bones, and
 * the chest leans back over the hips (the hips stay put). Composed in PAL's convention ({@code art/README.md},
 * "Player animations"): a file rotation is the vanilla {@code ModelPart} angle; a position moves the part in the
 * model frame with y up and z backwards; the vanilla model frame has y down and the player facing -z. The torso pivot
 * is the neck; its hips are 12 px below it.
 */
class HaulAnimationFileTest {

    static final Path FILE = Path.of("src/main/resources/assets/pirates_n_ships/rope_animations/" + RopeSlidePoses.HAUL + ".json");

    @Test
    void palLoadsTheHaulAndItLoops() throws IOException {
        Map<String, Animation> loaded;
        try (InputStream in = Files.newInputStream(FILE)) {
            loaded = UniversalAnimLoader.loadAnimations(in);
        }
        assertEquals(Set.of(RopeSlidePoses.HAUL), loaded.keySet());
        Animation a = loaded.get(RopeSlidePoses.HAUL);
        assertEquals(1.0f * 20f, a.length(), 1e-3);
        assertFalse(a.boneAnimations().isEmpty());
        JsonObject anim = haul();
        assertTrue(anim.get("loop").getAsBoolean(), "the haul loops");
        assertTrue(RopeSlideAnimationFileTest.BONES.containsAll(anim.getAsJsonObject("bones").keySet()),
                "unknown bones " + anim.getAsJsonObject("bones").keySet());
    }

    @Test
    void theChestLeansBackOverTheHips() throws IOException {
        JsonObject torso = haul().getAsJsonObject("bones").getAsJsonObject("torso");
        JsonObject rot = torso.getAsJsonObject("rotation"), pos = torso.getAsJsonObject("position");
        for (String t : rot.keySet()) {
            double theta = Math.toRadians(vec(rot, t).get(0).getAsDouble());
            double py = vec(pos, t).get(1).getAsDouble(), pz = vec(pos, t).get(2).getAsDouble();
            // vanilla model frame (y down, front -z): the torso's origin (the neck) moved by the position, the hips
            // 12 px below it turned with the torso's x rotation
            double neckY = -py, neckZ = pz;
            double hipY = neckY + 12 * Math.cos(theta), hipZ = neckZ + 12 * Math.sin(theta);
            assertEquals(12, hipY, 0.1, "the hips stay at their height at " + t);
            assertEquals(0, hipZ, 0.1, "the hips stay over the feet at " + t);
            assertTrue(neckZ - hipZ > 3, "the chest leans back (neck " + (neckZ - hipZ) + " px behind the hips) at " + t);
        }
    }

    @Test
    void theArmsGoHandOverHand() throws IOException {
        JsonObject bones = haul().getAsJsonObject("bones");
        JsonObject right = bones.getAsJsonObject("right_arm").getAsJsonObject("rotation");
        JsonObject left = bones.getAsJsonObject("left_arm").getAsJsonObject("rotation");
        // at the start the right arm reaches forward and up the rope while the left pulls in, half a loop later the reverse
        assertTrue(vec(right, "0.0").get(0).getAsDouble() < vec(left, "0.0").get(0).getAsDouble() - 40);
        assertTrue(vec(left, "0.5").get(0).getAsDouble() < vec(right, "0.5").get(0).getAsDouble() - 40);
    }

    @Test
    void hauledWhenTheOwnRopeIsTautInTheHandWhileSneaking() {
        assertTrue(RopeSlidePoses.hauls(true, true, false, true, false));
        assertFalse(RopeSlidePoses.hauls(false, true, false, true, false), "not latched");
        assertFalse(RopeSlidePoses.hauls(true, false, false, true, false), "slack");
        assertFalse(RopeSlidePoses.hauls(true, true, true, true, false), "tied to a ring or cleat");
        assertFalse(RopeSlidePoses.hauls(true, true, false, false, false), "not sneaking");
        assertFalse(RopeSlidePoses.hauls(true, true, false, true, true), "riding");
        assertEquals(RopeSlidePoses.ANIMATION, RopeSlidePoses.pose(true, true), "sliding wins");
        assertEquals(RopeSlidePoses.HAUL, RopeSlidePoses.pose(false, true));
        assertNull(RopeSlidePoses.pose(false, false));
    }

    private static JsonObject haul() throws IOException {
        return JsonParser.parseString(Files.readString(FILE)).getAsJsonObject().getAsJsonObject("animations")
                .getAsJsonObject(RopeSlidePoses.HAUL);
    }

    private static JsonArray vec(JsonObject channel, String time) {
        return channel.getAsJsonArray(time);
    }
}
