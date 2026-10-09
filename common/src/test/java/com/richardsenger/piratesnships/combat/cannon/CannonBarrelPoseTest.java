package com.richardsenger.piratesnships.combat.cannon;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CAN2: the drawn barrel angle and the quoin's pose (pure geometry of {@link CannonBarrelPose}). */
class CannonBarrelPoseTest {

    private static final Path BLOCK_MODELS = Path.of("src/main/resources/assets/pirates_n_ships/models/block");

    @Test
    void drawnAngleIsTheElevationWithinTheModelsLimits() {
        for (int step = 0; step < 6; step++) {
            double deg = CannonRules.elevationDegrees(step, 6, -5, 20);
            assertEquals(deg, CannonBarrelPose.drawnDegrees(deg), 1e-12, "default steps are drawn as they are");
        }
        assertEquals(20.0, CannonBarrelPose.drawnDegrees(45.0));
        assertEquals(-20.0, CannonBarrelPose.drawnDegrees(-45.0));
        assertEquals(0.0, CannonBarrelPose.drawnDegrees(Double.NaN));
    }

    /** The same turn the renderer applies (JOML, like PoseStack: translate to the pivot, rotate about +x, back). */
    private static Vector3f turned(double deg, float x, float y, float z) {
        float py = (float) CannonBarrelPose.PIVOT_Y;
        float pz = (float) CannonBarrelPose.PIVOT_Z;
        Matrix4f m = new Matrix4f().translate(0, py, pz).rotateX((float) Math.toRadians(deg)).translate(0, -py, -pz);
        return m.transformPosition(new Vector3f(x, y, z));
    }

    @Test
    void aPositiveAngleRaisesTheMuzzleLikeTheShot() {
        // the muzzle face centre (z -15) and the cascabel (z 26) on the axis, turned the way the renderer turns them
        Vector3f muzzle = turned(20, 8, 14, -15);
        Vector3f cascabel = turned(20, 8, 14, 26);
        assertTrue(muzzle.y > 14 + 7 && muzzle.z > -15, "the muzzle rises: " + muzzle);
        assertTrue(cascabel.y < 14 - 5, "the breech drops: " + cascabel);
        // the muzzle lies along the shot's direction from the pivot (north = -z in the north model)
        var dir = CannonRules.muzzleDirection(0, -1, 20);
        double len = Math.hypot(muzzle.y - 14, muzzle.z - 8);
        assertEquals(dir.y, (muzzle.y - 14) / len, 1e-5);
        assertEquals(dir.z, (muzzle.z - 8) / len, 1e-5);
        // the analytic underside agrees with the turned point of the base ring's rear bottom edge
        Vector3f ring = turned(20, 8, 14 - 4.15f, 22.6f);
        double under = CannonBarrelPose.undersideY(20, ring.z - 0.05, ring.z + 0.05);
        assertEquals(ring.y, under, 0.02);
    }

    @Test
    void atRestTheQuoinIsAsModelledAndJustUnderTheBreech() throws IOException {
        CannonBarrelPose.Quoin q = CannonBarrelPose.quoin(0);
        assertEquals(1.0, q.scale(), 1e-9);
        assertEquals(0.0, q.slide(), 1e-9);
        double under = CannonBarrelPose.undersideY(0, CannonBarrelPose.QUOIN_Z0, CannonBarrelPose.QUOIN_Z1);
        assertTrue(under > CannonBarrelPose.QUOIN_TOP && under - CannonBarrelPose.QUOIN_TOP < 0.5,
                "the base ring rests just above the quoin: " + under);
        // the numbers match the model files
        JsonObject quoin = element("cannon_quoin", "quoin");
        assertEquals(CannonBarrelPose.STOOL_TOP, quoin.getAsJsonArray("from").get(1).getAsDouble(), 1e-9);
        assertEquals(CannonBarrelPose.QUOIN_TOP, quoin.getAsJsonArray("to").get(1).getAsDouble(), 1e-9);
        assertEquals(CannonBarrelPose.QUOIN_Z0, quoin.getAsJsonArray("from").get(2).getAsDouble(), 1e-9);
        assertEquals(CannonBarrelPose.QUOIN_Z1, element("cannon_quoin", "quoin_handle").getAsJsonArray("to").get(2).getAsDouble(), 1e-9);
        assertEquals(CannonBarrelPose.STOOL_TOP, element("cannon_carriage", "stool_bed").getAsJsonArray("to").get(1).getAsDouble(), 1e-9);
        assertEquals(8.0, element("cannon_barrel", "trunnion_a").getAsJsonArray("from").get(2).getAsDouble()
                + (element("cannon_barrel", "trunnion_a").getAsJsonArray("to").get(2).getAsDouble()
                - element("cannon_barrel", "trunnion_a").getAsJsonArray("from").get(2).getAsDouble()) / 2, 1e-3, "trunnion z");
    }

    @Test
    void theQuoinNeverCutsIntoTheBarrelAndTheBarrelNeverIntoTheStool() {
        for (double deg = CannonBarrelPose.MIN_DRAWN_DEGREES; deg <= CannonBarrelPose.MAX_DRAWN_DEGREES; deg += 0.25) {
            CannonBarrelPose.Quoin q = CannonBarrelPose.quoin(deg);
            double top = CannonBarrelPose.STOOL_TOP + q.scale() * (CannonBarrelPose.QUOIN_TOP - CannonBarrelPose.STOOL_TOP);
            double under = CannonBarrelPose.undersideY(deg, CannonBarrelPose.QUOIN_Z0 + q.slide(), CannonBarrelPose.QUOIN_Z1 + q.slide());
            assertTrue(top < under, deg + "°: quoin top " + top + " cuts into the barrel at " + under);
            // the stool bed spans z 18.5..26.5
            assertTrue(CannonBarrelPose.undersideY(deg, 18.5, 26.5) > CannonBarrelPose.STOOL_TOP,
                    deg + "°: the barrel cuts into the stool bed");
            assertTrue(q.slide() >= 0 && q.slide() <= CannonBarrelPose.MAX_SLIDE, "slide " + q.slide());
        }
    }

    @Test
    void theQuoinThinsAndDrawsBackAsTheMuzzleRises() {
        double last = Double.POSITIVE_INFINITY;
        double lastSlide = -1;
        for (int deg = -5; deg <= 20; deg += 5) {
            CannonBarrelPose.Quoin q = CannonBarrelPose.quoin(deg);
            assertTrue(q.scale() <= last, deg + "°: the quoin grows again");
            assertTrue(q.slide() >= lastSlide, deg + "°: the quoin slides forward again");
            last = q.scale();
            lastSlide = q.slide();
        }
        assertTrue(CannonBarrelPose.quoin(-5).scale() > 1.0, "muzzle down, the quoin grows under the rising breech");
        CannonBarrelPose.Quoin top = CannonBarrelPose.quoin(20);
        assertEquals(CannonBarrelPose.MIN_SCALE, top.scale(), 1e-9, "at 20° the breech rests on the stool");
        assertTrue(top.slide() > 1.6, "and the quoin is drawn back");
    }

    @Test
    void theCheeksLeaveRoomForTheWidestRing() throws IOException {
        double inner = element("cannon_carriage", "cheek_step2_l").getAsJsonArray("to").get(0).getAsDouble();
        double ringHalf = 8.3 / 2;
        assertTrue(8 - ringHalf > inner, "the base ring (x " + (8 - ringHalf) + ") clears the left cheek (x " + inner + ")");
        double innerR = element("cannon_carriage", "cheek_step2_r").getAsJsonArray("from").get(0).getAsDouble();
        assertTrue(8 + ringHalf < innerR, "the base ring clears the right cheek");
    }

    private static JsonObject element(String model, String name) throws IOException {
        JsonObject json = JsonParser.parseString(Files.readString(BLOCK_MODELS.resolve(model + ".json"))).getAsJsonObject();
        for (JsonElement e : json.getAsJsonArray("elements")) {
            if (e.getAsJsonObject().get("name").getAsString().equals(name)) return e.getAsJsonObject();
        }
        throw new AssertionError(model + " has no element " + name);
    }
}
