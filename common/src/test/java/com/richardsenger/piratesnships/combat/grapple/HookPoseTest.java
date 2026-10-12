package com.richardsenger.piratesnships.combat.grapple;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** GR4: the hook's poses from its velocity, the face it bit and its state, and the rope's tie point on the model. */
class HookPoseTest {

    private static final float EPS = 1e-4f;

    private static Vector3f unit(double x, double y, double z) {
        return new Vector3f((float) x, (float) y, (float) z).normalize();
    }

    private static void assertVec(Vector3f expected, Vector3f actual, String msg) {
        assertTrue(expected.distance(actual) < 1e-3f, msg + ": expected " + expected + ", got " + actual);
    }

    // ---- flight ----

    @Test
    void inFlightThePointLeadsAlongTheVelocity() {
        double[][] vs = {{1, 0, 0}, {0, 0, 1}, {-1, 0, 0}, {0, 0, -1}, {1, 1, 0}, {0.3, -0.8, -0.5}, {-2, 0.4, 1.1}, {0, -1, 0}, {0, 1, 0}};
        for (double[] v : vs) {
            Quaternionf q = HookPose.flight(v[0], v[1], v[2]);
            assertVec(unit(v[0], v[1], v[2]), HookPose.point(q), "velocity " + v[0] + "," + v[1] + "," + v[2]);
            // the eye trails behind the crown: the rope comes after the hook
            assertTrue(HookPose.eye(q).dot(unit(v[0], v[1], v[2])) < -0.99f * HookPose.EYE_FROM_CROWN, "the eye leads");
        }
    }

    @Test
    void yawAndPitchAreVanillasProjectileAngles() {
        assertEquals(90f, HookPose.yaw(1, 0), EPS, "east is +90 (atan2(x, z))");
        assertEquals(0f, HookPose.yaw(0, 1), EPS);
        assertEquals(45f, HookPose.pitch(1, 1, 0), EPS, "up is positive");
        assertEquals(-90f, HookPose.pitch(0, -1, 0), EPS);
        // a pose built from the entity's synced angles points like one built from the velocity
        Quaternionf a = HookPose.fromYawPitch(HookPose.yaw(0.4, -0.7), HookPose.pitch(0.4, 0.25, -0.7));
        assertVec(HookPose.point(HookPose.flight(0.4, 0.25, -0.7)), HookPose.point(a), "angles against velocity");
    }

    @Test
    void aStillHookPointsDown() {
        assertVec(new Vector3f(0, -1, 0), HookPose.point(HookPose.flight(0, 0, 0)), "no velocity");
        assertVec(new Vector3f(0, -1, 0), HookPose.point(HookPose.falling(73f)), "falling free");
        assertTrue(HookPose.eye(HookPose.falling(10f)).y > 0.99f * HookPose.EYE_FROM_CROWN, "falling: the eye above the crown");
    }

    // ---- latched ----

    @Test
    void theFaceAndTheBlockChooseThePose() {
        assertEquals(HookPose.Kind.HANGING, HookPose.latchKind(0, 1, 0, true), "a rail's top");
        assertEquals(HookPose.Kind.HANGING, HookPose.latchKind(-1, 0, 0, true), "a rail's side");
        assertEquals(HookPose.Kind.FLAT, HookPose.latchKind(0, 1, 0, false), "a deck");
        assertEquals(HookPose.Kind.BITING, HookPose.latchKind(1, 0, 0, false), "a wall");
        assertEquals(HookPose.Kind.BITING, HookPose.latchKind(0, -1, 0, false), "a ceiling");
        assertEquals(HookPose.Kind.BITING, HookPose.latchKind(0.8, 0.6, 0, false), "a steep slope is a wall");
    }

    @Test
    void onARailTheShankHangsStraightDownFacingTheFlight() {
        Quaternionf q = HookPose.latched(HookPose.Kind.HANGING, -1, 0, 0, 0.6, -0.1, 0);
        assertVec(new Vector3f(0, -HookPose.EYE_FROM_CROWN, 0), HookPose.eye(q), "the eye under the crown");
        assertVec(new Vector3f(0, 1, 0), HookPose.point(q), "the flukes up over the rail");
        // the hook's +Z fluke (its heading) still faces along the flight
        Vector3f heading = q.transform(new Vector3f(0, 0, 1));
        assertTrue(heading.x < -0.99f || heading.x > 0.99f, "turned to the flight's heading: " + heading);
    }

    @Test
    void onADeckItLiesFlatPointingAlongTheThrow() {
        Quaternionf q = HookPose.latched(HookPose.Kind.FLAT, 0, 1, 0, 0.5, -0.5, 0.0);
        Vector3f p = HookPose.point(q);
        assertTrue(p.x > 0.98f, "the point along the throw: " + p);
        assertEquals(-Math.sin(Math.toRadians(HookPose.FLAT_DIP)), p.y, 1e-3, "dipped into the planks");
        Vector3f eye = HookPose.eye(q);
        assertTrue(eye.y > 0f && eye.y < 0.1f, "the eye just off the deck: " + eye);
        assertTrue(eye.x < -0.9f * HookPose.EYE_FROM_CROWN, "the eye toward the thrower: " + eye);
    }

    @Test
    void inAWallThePointBitesAlongTheFace() {
        double[][] normals = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};
        for (double[] n : normals) {
            Quaternionf q = HookPose.latched(HookPose.Kind.BITING, n[0], n[1], n[2], -n[0] * 0.8 + 0.1, -0.2, -n[2] * 0.8 + 0.1);
            assertVec(unit(-n[0], -n[1], -n[2]), HookPose.point(q), "into face " + n[0] + "," + n[1] + "," + n[2]);
            assertVec(new Vector3f((float) n[0], (float) n[1], (float) n[2]).mul(HookPose.EYE_FROM_CROWN), HookPose.eye(q), "the eye out of the face");
        }
    }

    @Test
    void theSettleStartsTiltedAndComesToRest() {
        assertEquals(HookPose.SETTLE_DEGREES, HookPose.settleDegrees(0f), EPS);
        assertEquals(0f, HookPose.settleDegrees(HookPose.SETTLE_TICKS), 0f);
        assertEquals(0f, HookPose.settleDegrees(-1f), 0f);
        assertTrue(Math.abs(HookPose.settleDegrees(HookPose.SETTLE_TICKS - 0.01f)) < 0.01f, "no jump at the end");
        float max = 0f;
        for (float t = 6f; t < HookPose.SETTLE_TICKS; t += 0.25f) max = Math.max(max, Math.abs(HookPose.settleDegrees(t)));
        assertTrue(max < 3f, "half way it hardly moves: " + max);
        // the settle turns the hook about its crown: the crown stays put, the pose returns to the latched one
        Quaternionf q = HookPose.latched(HookPose.Kind.BITING, 1, 0, 0, -1, 0, 0);
        assertVec(HookPose.eye(q), HookPose.eye(HookPose.settled(q, HookPose.SETTLE_TICKS)), "settled");
        assertTrue(HookPose.eye(q).distance(HookPose.eye(HookPose.settled(q, 0f))) > 0.05f, "tilted at first");
    }

    // ---- the model and the rope (the rig test: the renderer's own transform) ----

    @Test
    void theRopeTiesOnAtTheModelsEyeInEveryPose() {
        Random r = new Random(4);
        for (int i = 0; i < 200; i++) {
            Quaternionf q = new Quaternionf().rotationXYZ(r.nextFloat() * 7f, r.nextFloat() * 7f, r.nextFloat() * 7f);
            Matrix4f m = HookPose.modelMatrix(q);
            Vector3f eye = m.transformPosition(new Vector3f(0f, HookPose.EYE_Y, 0f));
            assertVec(HookPose.eye(q), eye, "the eye ring of the model");
            Vector3f crown = m.transformPosition(new Vector3f(0f, HookPose.CROWN_Y, 0f));
            assertTrue(crown.length() < 1e-5f, "the crown sits at the hook's position: " + crown);
        }
    }

    @Test
    void theModelsEyeRingIsWhereTheEyeConstantSaysAndTheFlukesLead() {
        // art/models/grappling_hook.bbmodel: eye ring pixel y 12.45..15.45, crown 2.1..3.9, the fluke arms down to -0.05
        assertEquals(14f / 16f - 0.5f, HookPose.EYE_Y, EPS);
        assertEquals(0.375f, HookPose.EYE_FROM_CROWN, EPS);
        Quaternionf q = HookPose.flight(1, 0, 0);
        Matrix4f m = HookPose.modelMatrix(q);
        Vector3f flukeTip = m.transformPosition(new Vector3f(0f, -0.05f / 16f - 0.5f, 0f));
        Vector3f eyeTop = m.transformPosition(new Vector3f(0f, 15.45f / 16f - 0.5f, 0f));
        assertTrue(flukeTip.x > eyeTop.x + 0.4f, "the crown end flies first: " + flukeTip + " vs " + eyeTop);
    }
}
