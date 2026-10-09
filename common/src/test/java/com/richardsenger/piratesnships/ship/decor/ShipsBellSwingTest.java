package com.richardsenger.piratesnships.ship.decor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** BELL1: the bell's swing ({@link ShipsBellSwing}), its strike direction and the pose the renderer composes. */
class ShipsBellSwingTest {

    private static final Path BLOCK_MODELS = Path.of("src/main/resources/assets/pirates_n_ships/models/block");
    private static final int T = 20;
    private static final double A = 20.0;

    @Test
    void restsBeforeAndAfterTheRing() {
        for (double t : new double[]{-5, -0.5, 0, T, T + 0.5, T + 40}) {
            assertEquals(0.0, ShipsBellSwing.bellDegrees(t, T, A, 1.0), 1e-12, "bell at t " + t);
        }
        assertEquals(0.0, ShipsBellSwing.clapperDegrees(T + ShipsBellSwing.CLAPPER_LAG_TICKS, T, A, 1.0), 1e-12);
        assertTrue(!ShipsBellSwing.swinging(-1, T) && !ShipsBellSwing.swinging(T + 2, T) && ShipsBellSwing.swinging(3, T));
    }

    @Test
    void firstSwingReachesTheAmplitudeAwayFromTheStrikeThenComesBackSmaller() {
        double max = 0, min = 0, tMax = 0;
        for (double t = 0; t <= T; t += 0.01) {
            double d = ShipsBellSwing.bellDegrees(t, T, A, 1.0);
            if (d > max) { max = d; tMax = t; }
            min = Math.min(min, d);
        }
        assertEquals(A, max, 0.01, "the first swing is the configured amplitude");
        assertTrue(tMax > 3 && tMax < 7, "first peak at tick " + tMax + ", like vanilla's bell (about 5)");
        assertTrue(min < -0.2 * A && -min < 0.6 * A, "the swing back is smaller: " + min);
        // the other strike side mirrors it
        assertEquals(-ShipsBellSwing.bellDegrees(4.5, T, A, 1.0), ShipsBellSwing.bellDegrees(4.5, T, A, -1.0), 1e-12);
        // a side strike rocks it half as far
        assertEquals(0.5 * ShipsBellSwing.bellDegrees(4.5, T, A, 1.0),
                ShipsBellSwing.bellDegrees(4.5, T, A, ShipsBellSwing.SIDE_STRIKE_SCALE), 1e-12);
        // a longer ring keeps the same first swing
        assertEquals(ShipsBellSwing.bellDegrees(4.5, 60, A, 1.0), ShipsBellSwing.bellDegrees(4.5, T, A, 1.0), 1e-12);
    }

    @Test
    void swingIsSmoothAndEasesToRest() {
        for (int ticks : new int[]{2, 7, 20, 50, 200}) {
            double step = 0.001;
            double prev = ShipsBellSwing.bellDegrees(-step, ticks, A, 1.0);
            double prevClapper = ShipsBellSwing.clapperDegrees(-step, ticks, A, 1.0);
            for (double t = 0; t <= ticks + 3; t += step) {
                double d = ShipsBellSwing.bellDegrees(t, ticks, A, 1.0);
                double c = ShipsBellSwing.clapperDegrees(t, ticks, A, 1.0);
                assertTrue(Math.abs(d - prev) < 0.1, "bell jumps at t " + t + " of " + ticks);
                assertTrue(Math.abs(c - prevClapper) < 0.1, "clapper jumps at t " + t + " of " + ticks);
                prev = d;
                prevClapper = c;
            }
        }
        // in the last tick the bell is all but still
        assertTrue(Math.abs(ShipsBellSwing.bellDegrees(T - 0.5, T, A, 1.0)) < 0.05 * A);
    }

    @Test
    void clapperTrailsTheBellAndStaysInTheMouth() {
        for (double t = 0; t <= T + 2; t += 0.05) {
            double bell = ShipsBellSwing.bellDegrees(t, T, 30.0, 1.0);
            double clapper = ShipsBellSwing.clapperDegrees(t, T, 30.0, 1.0);
            assertTrue(Math.abs(clapper - bell) <= ShipsBellSwing.CLAPPER_MAX_LAG_DEGREES + 1e-9, "clapper off the mouth at t " + t);
        }
        assertTrue(ShipsBellSwing.clapperDegrees(1.0, T, A, 1.0) < ShipsBellSwing.bellDegrees(1.0, T, A, 1.0), "trails at first");
        assertEquals(ShipsBellSwing.bellDegrees(4.0, T, A, 1.0), ShipsBellSwing.clapperDegrees(4.0 + ShipsBellSwing.CLAPPER_LAG_TICKS, T, A, 1.0),
                ShipsBellSwing.CLAPPER_MAX_LAG_DEGREES);
    }

    @Test
    void strikeDirectionPicksTheSwing() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            assertEquals(1.0, ShipsBellSwing.direction(facing, facing.getOpposite()), "pushed from the front: " + facing);
            assertEquals(-1.0, ShipsBellSwing.direction(facing, facing), "pushed from the back: " + facing);
            assertEquals(ShipsBellSwing.SIDE_STRIKE_SCALE, ShipsBellSwing.direction(facing, facing.getClockWise()));
            assertEquals(-ShipsBellSwing.SIDE_STRIKE_SCALE, ShipsBellSwing.direction(facing, facing.getCounterClockWise()));
            assertEquals(1.0, ShipsBellSwing.direction(facing, null), "a raid alarm rings it as from the front");
            assertEquals(1.0, ShipsBellSwing.direction(facing, Direction.UP));
        }
    }

    /**
     * The renderer's real transform ({@link ShipsBellSwing#partPose}): at rest the mouth hangs under the pin; a positive
     * angle swings it to the back of the mount, i.e. away from a player who struck the front, for every facing and both
     * mounts, and the pin itself never moves.
     */
    @Test
    void poseSwingsTheMouthAwayFromTheStriker() {
        Vector3f mouth = new Vector3f(8 / 16f, (float) ((ShipsBellSwing.PIN_Y - 7.8) / 16.0), 8 / 16f);
        Vector3f pin = new Vector3f(8 / 16f, (float) (ShipsBellSwing.PIN_Y / 16.0), (float) (ShipsBellSwing.PIN_Z / 16.0));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (boolean wall : new boolean[]{false, true}) {
                double drop = wall ? ShipsBellSwing.WALL_DROP / 16.0 : 0.0;
                Vector3f rest = ShipsBellSwing.partPose(facing, wall, 0).transformPosition(new Vector3f(mouth));
                assertEquals(0.5, rest.x, 1e-6);
                assertEquals(0.5, rest.z, 1e-6);
                assertEquals(mouth.y - drop, rest.y, 1e-6);
                Vector3f swung = ShipsBellSwing.partPose(facing, wall, 20).transformPosition(new Vector3f(mouth));
                Vector3f moved = swung.sub(rest);
                // facing is the mount's front: the mouth goes the other way, by 7.8 px * sin 20
                double along = moved.x * facing.getStepX() + moved.z * facing.getStepZ();
                assertEquals(-7.8 / 16.0 * Math.sin(Math.toRadians(20)), along, 1e-5, facing + (wall ? " wall" : " post"));
                Vector3f pinNow = ShipsBellSwing.partPose(facing, wall, 20).transformPosition(new Vector3f(pin));
                Vector3f pinRest = ShipsBellSwing.partPose(facing, wall, 0).transformPosition(new Vector3f(pin));
                assertTrue(pinNow.distance(pinRest) < 1e-6, "the pin stays put");
            }
        }
    }

    /** The constants match the models: the yoke round the post's pin, and the wall bracket's pin {@code WALL_DROP} lower. */
    @Test
    void pinMatchesTheModels() throws IOException {
        assertCentre(element("ships_bell_bell", "yoke"), ShipsBellSwing.PIN_Y, ShipsBellSwing.PIN_Z);
        assertCentre(element("ships_bell_post", "pin"), ShipsBellSwing.PIN_Y, ShipsBellSwing.PIN_Z);
        assertCentre(element("ships_bell_wall", "pin"), ShipsBellSwing.PIN_Y - ShipsBellSwing.WALL_DROP, ShipsBellSwing.PIN_Z);
    }

    private static void assertCentre(JsonObject e, double y, double z) {
        JsonArray from = e.getAsJsonArray("from");
        JsonArray to = e.getAsJsonArray("to");
        assertEquals(y, (from.get(1).getAsDouble() + to.get(1).getAsDouble()) / 2, 1e-6, e.get("name") + " y");
        assertEquals(z, (from.get(2).getAsDouble() + to.get(2).getAsDouble()) / 2, 1e-6, e.get("name") + " z");
    }

    private static JsonObject element(String model, String name) throws IOException {
        JsonObject json = JsonParser.parseString(Files.readString(BLOCK_MODELS.resolve(model + ".json"))).getAsJsonObject();
        for (JsonElement e : json.getAsJsonArray("elements")) {
            if (e.getAsJsonObject().get("name").getAsString().equals(name)) return e.getAsJsonObject();
        }
        throw new AssertionError(model + " has no element " + name);
    }
}
