package com.richardsenger.piratesnships.combat.cannon.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ART15: the chain shot's in-flight tumble and the model axis {@link CannonballRenderer} relies on. */
class ShotTumbleTest {

    private static final float EPS = 1.0e-3f;

    @Test
    void startsAtZeroAndTurnsSteadily() {
        assertEquals(0.0f, ShotTumble.angle(0, 0.0f), EPS);
        assertEquals(ShotTumble.DEGREES_PER_TICK, ShotTumble.angle(1, 0.0f), EPS);
        assertEquals(ShotTumble.DEGREES_PER_TICK * 2.5f, ShotTumble.angle(2, 0.5f), EPS);
    }

    @Test
    void isContinuousAcrossTicks() {
        for (int age = 0; age < 400; age++) {
            float end = ShotTumble.angle(age, 1.0f);
            float next = ShotTumble.angle(age + 1, 0.0f);
            assertEquals(0.0f, wrappedDifference(end, next), EPS, "tick " + age);
        }
    }

    @Test
    void neverJumpsWithinAFrameStep() {
        float step = 0.05f;
        float max = ShotTumble.DEGREES_PER_TICK * step + EPS;
        float previous = ShotTumble.angle(0, 0.0f);
        for (int age = 0; age < 200; age++) {
            for (float t = step; t <= 1.0f + 1.0e-6f; t += step) {
                float now = ShotTumble.angle(age, Math.min(t, 1.0f));
                assertTrue(Math.abs(wrappedDifference(previous, now)) <= max, "age " + age + " t " + t);
                previous = now;
            }
        }
    }

    @Test
    void staysInOneTurn() {
        for (int age = 0; age < 2000; age += 7) {
            for (float t = 0.0f; t < 1.0f; t += 0.1f) {
                float a = ShotTumble.angle(age, t);
                assertTrue(a >= 0.0f && a < 360.0f, "angle " + a);
            }
        }
    }

    /**
     * The renderer lays the chain along the flight by the model's x axis and tumbles it about z, centred on the block
     * centre ({@code ItemDisplayContext.NONE}): the model must run along x and be centred on (8, 8, 8).
     */
    @Test
    void chainShotModelRunsAlongXAboutTheCentre() throws IOException {
        Path file = Path.of("src/main/resources/assets/pirates_n_ships/models/item/chain_shot.json");
        JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (JsonElement e : json.getAsJsonArray("elements")) {
            JsonObject element = e.getAsJsonObject();
            for (String key : new String[]{"from", "to"}) {
                var v = element.getAsJsonArray(key);
                for (int i = 0; i < 3; i++) {
                    min[i] = Math.min(min[i], v.get(i).getAsFloat());
                    max[i] = Math.max(max[i], v.get(i).getAsFloat());
                }
            }
        }
        for (int i = 0; i < 3; i++) {
            assertEquals(8.0f, (min[i] + max[i]) / 2, 0.05f, "centre on axis " + i);
        }
        float length = max[0] - min[0];
        assertTrue(length > 2 * (max[1] - min[1]) && length > 2 * (max[2] - min[2]), "chain along x, length " + length);
    }

    private static float wrappedDifference(float a, float b) {
        float d = (b - a) % 360.0f;
        if (d > 180.0f) d -= 360.0f;
        if (d < -180.0f) d += 360.0f;
        return d;
    }
}
