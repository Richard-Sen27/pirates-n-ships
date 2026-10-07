package com.richardsenger.piratesnships.crew.npc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.richardsenger.piratesnships.crew.hammock.HammockSeat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ART1d: the {@code sleep} animation lays the crew member into the hammock model. Composed in file coordinates (y up,
 * the model faces -z, so the feet point to -z and the head to +z; 1 unit = 1 px): the rider's feet are on the
 * {@link HammockSeat} at the canvas top of the seam, the root drops and shifts the body, {@code waist} (pivot y 12)
 * leans the upper body back and {@code right_leg}/{@code left_leg} (pivot y 12) swing the legs forward. The hips' back
 * must rest on the canvas (at most 1 px sunk in, never floating), and the body must stay within the two blocks.
 */
class CrewSleepPoseTest {

    private static final Path ANIMATIONS = Path.of("src/main/resources/assets/pirates_n_ships/animations/crew_member.animation.json");
    /** The seat entity's height (its passenger attachment point), px. */
    private static final double SEAT_HEIGHT = 0.01 * 16;

    private static JsonObject sleep() throws IOException {
        return JsonParser.parseString(Files.readString(ANIMATIONS)).getAsJsonObject()
                .getAsJsonObject("animations").getAsJsonObject("sleep");
    }

    private static double[] first(JsonObject bones, String bone, String channel) {
        JsonObject keys = bones.getAsJsonObject(bone).getAsJsonObject(channel);
        JsonArray v = keys.getAsJsonObject("0.0").getAsJsonArray("vector");
        return new double[]{v.get(0).getAsDouble(), v.get(1).getAsDouble(), v.get(2).getAsDouble()};
    }

    @Test
    void theSleeperLiesOnTheCanvasWithinTheHammock() throws IOException {
        JsonObject bones = sleep().getAsJsonObject("bones");
        double[] root = first(bones, "root", "position");
        double waist = first(bones, "waist", "rotation")[0];
        double leg = first(bones, "right_leg", "rotation")[0];
        // lying on the back: the upper body more than 45 degrees back, the legs more than 45 degrees forward
        assertTrue(waist < -45 && leg < -90, "not lying: waist " + waist + ", legs " + leg);

        double hipY = SEAT_HEIGHT + 12 + root[1];      // above the seam's canvas top
        double hipZ = root[2];
        double up = Math.toRadians(-90 - waist);       // upper body raised above the horizontal (towards +z)
        double legUp = Math.toRadians(-leg - 90);      // legs raised above the horizontal (towards -z)
        double backAtHips = hipY - 2 * Math.min(Math.cos(up), Math.cos(legUp));
        assertTrue(backAtHips <= 0.25 && backAtHips >= -1.0, "hips' back " + backAtHips + " px from the canvas");

        double headTop = hipZ + 20 * Math.cos(up);
        double feet = hipZ - 12 * Math.cos(legUp);
        assertTrue(headTop <= 16 && feet >= -16, "body " + feet + ".." + headTop + " px sticks out of the hammock");
        assertEquals(0, (headTop + feet) / 2, 1.0, "the body is centred on the seam");
        assertEquals(4, HammockSeat.SEAM_CANVAS_TOP, 1e-9, "the seat sits on the canvas top at the seam (hammock models)");
    }
}
