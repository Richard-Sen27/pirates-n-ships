package com.richardsenger.piratesnships.sailing.anchor;

import com.richardsenger.piratesnships.sailing.force.AnchorState.Phase;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** GR4: the anchor's poses from its phase and chain, and the chain's shackle on the model's ring. */
class AnchorPoseTest {

    private static final Vector3f ARMS_X = new Vector3f(1f, 0f, 0f);

    private static Vector3f shank(Quaternionf q) {
        return q.transform(new Vector3f(0f, 1f, 0f));
    }

    private static Vector3f armsOf(Quaternionf q) {
        return q.transform(new Vector3f(1f, 0f, 0f));
    }

    /** A model point given in pixels ({@code AnchorRenderer.createLayer}) through the renderer's transform. */
    private static Vector3f world(Quaternionf q, AnchorPose.Kind kind, float px, float py, float pz) {
        Matrix4f m = AnchorPose.modelMatrix(q, kind);
        return m.transformPosition(new Vector3f(px / 16f, py / 16f, pz / 16f));
    }

    // ---- which pose ----

    @Test
    void thePhaseAndTheChainChooseThePose() {
        assertEquals(AnchorPose.Kind.STOWED, AnchorPose.of(false, Phase.RAISED, false, false));
        assertEquals(AnchorPose.Kind.STOWED, AnchorPose.of(true, Phase.RAISED, false, false));
        assertEquals(AnchorPose.Kind.DROPPING, AnchorPose.of(true, Phase.DROPPING, false, false), "falling");
        assertEquals(AnchorPose.Kind.HANGING, AnchorPose.of(true, Phase.DROPPING, false, true), "hanging at the chain's end");
        assertEquals(AnchorPose.Kind.HOLDING, AnchorPose.of(true, Phase.HOLDING, true, false), "on the bottom");
        assertEquals(AnchorPose.Kind.HOLDING, AnchorPose.of(true, Phase.HOLDING, true, true), "on the bottom, chain taut");
        assertEquals(AnchorPose.Kind.HOLDING, AnchorPose.of(true, Phase.RAISING, true, true), "heaved along the bottom");
        assertEquals(AnchorPose.Kind.HANGING, AnchorPose.of(true, Phase.RAISING, false, true), "broken out and rising");
    }

    // ---- orientations ----

    @Test
    void stowedAndHangingAreUprightWithTheArmsAlongTheShip() {
        for (AnchorPose.Kind k : new AnchorPose.Kind[]{AnchorPose.Kind.STOWED, AnchorPose.Kind.HANGING}) {
            Quaternionf q = AnchorPose.orientation(k, new Vector3f(3f, 10f, 1f), new Vector3f(0.6f, 0.1f, 0.8f));
            assertTrue(shank(q).distance(0f, 1f, 0f) < 1e-4f, k + ": upright " + shank(q));
            Vector3f a = armsOf(q);
            assertEquals(0f, a.y, 1e-4f, k + ": the arms level");
            assertTrue(Math.abs(a.x * 0.8f - a.z * 0.6f) < 0.02f, k + ": the arms along the ship's axis " + a);
        }
        // with the arms along x, the stowed pose is the model as drawn before GR4 (identity)
        Quaternionf q = AnchorPose.orientation(AnchorPose.Kind.STOWED, new Vector3f(), ARMS_X);
        assertTrue(new Quaternionf().equals(q, 1e-5f), "identity: " + q);
    }

    @Test
    void droppingTheShankLiesAlongTheChainUpToTheTiltLimit() {
        Vector3f toHawse = new Vector3f(2f, 6f, 0f);
        Quaternionf q = AnchorPose.orientation(AnchorPose.Kind.DROPPING, toHawse, new Vector3f(0f, 0f, 1f));
        assertTrue(shank(q).distance(new Vector3f(toHawse).normalize()) < 1e-4f, "along the chain: " + shank(q));
        assertEquals(0f, armsOf(q).dot(shank(q)), 1e-4f, "the arms square to the shank");
        assertTrue(Math.abs(armsOf(q).z) > 0.99f, "the arms still across the ship: " + armsOf(q));
        // a chain streaming out nearly level: lean no more than the limit, still flukes down
        Quaternionf flat = AnchorPose.orientation(AnchorPose.Kind.DROPPING, new Vector3f(10f, 0.5f, 0f), ARMS_X);
        double tilt = Math.toDegrees(Math.acos(shank(flat).y));
        assertEquals(AnchorPose.MAX_DROP_TILT, tilt, 0.01, "tilt");
        assertTrue(shank(flat).x > 0f, "toward the hawse");
        assertTrue(world(flat, AnchorPose.Kind.DROPPING, 0, 31, 0).y < world(flat, AnchorPose.Kind.DROPPING, 0, 0, 0).y, "the crown below the ring");
    }

    @Test
    void onTheBottomItLiesTowardTheShipWithOneFlukeDugIn() {
        Vector3f toHawse = new Vector3f(-8f, 6f, 3f); // slack: the chain's angle does not lift it
        Quaternionf q = AnchorPose.orientation(AnchorPose.Kind.HOLDING, toHawse, ARMS_X);
        Vector3f s = shank(q);
        Vector3f h = new Vector3f(-8f, 0f, 3f).normalize();
        assertTrue(new Vector3f(s.x, 0f, s.z).normalize().distance(h) < 1e-3f, "the shank points at the ship: " + s);
        // a taut chain at 37° lifts the shank to the cap
        assertEquals(AnchorPose.MAX_HOLD_RISE, Math.toDegrees(Math.asin(s.y)), 0.01, "rise toward the chain");
        Quaternionf slack = AnchorPose.orientation(AnchorPose.Kind.HOLDING, new Vector3f(-8f, -0.5f, 3f), ARMS_X);
        assertEquals(AnchorPose.HOLD_RISE_SLACK, Math.toDegrees(Math.asin(shank(slack).y)), 0.01, "slack: lying on the bed");
        // the bills (model pixels ±11.85, 20.1): one dug in below the crown point, one in the water above it
        Vector3f left = world(slack, AnchorPose.Kind.HOLDING, 11.85f, 20.1f, 0f);
        Vector3f right = world(slack, AnchorPose.Kind.HOLDING, -11.85f, 20.1f, 0f);
        float low = Math.min(left.y, right.y), high = Math.max(left.y, right.y);
        assertTrue(low < -0.4f, "one fluke dug into the bed: " + low);
        assertTrue(high > 0.6f, "the other in the water: " + high);
        // the stock (model z ±8.5 at pixel y 10) lies flat on the bed
        Vector3f stockA = world(slack, AnchorPose.Kind.HOLDING, 0f, 10f, 8.5f);
        Vector3f stockB = world(slack, AnchorPose.Kind.HOLDING, 0f, 10f, -8.5f);
        assertTrue(Math.abs(stockA.y - stockB.y) < 0.35f, "the stock lies nearly flat: " + stockA.y + " / " + stockB.y);
        // the crown's tip rests on the bed
        assertEquals(0f, world(slack, AnchorPose.Kind.HOLDING, 0f, 31f, 0f).y, 0.12f, "the crown on the bed");
    }

    @Test
    void theHawseRightAboveStillLaysTheAnchorDown() {
        Quaternionf q = AnchorPose.orientation(AnchorPose.Kind.HOLDING, new Vector3f(0f, 9f, 0f), ARMS_X);
        assertTrue(shank(q).y < 0.6f, "it lies, along the ship: " + shank(q));
        assertFalse(Float.isNaN(q.x + q.y + q.z + q.w));
    }

    // ---- the chain on the ring (the rig test: the renderer's own transform) ----

    @Test
    void theChainShacklesOnTheModelsRingInEveryPose() {
        Random r = new Random(9);
        for (AnchorPose.Kind k : AnchorPose.Kind.values()) {
            for (int i = 0; i < 50; i++) {
                Vector3f toHawse = new Vector3f(r.nextFloat() * 20f - 10f, r.nextFloat() * 12f - 2f, r.nextFloat() * 20f - 10f);
                Vector3f arms = new Vector3f(r.nextFloat() - 0.5f, 0f, r.nextFloat() - 0.5f);
                Quaternionf q = AnchorPose.orientation(k, toHawse, arms);
                assertEquals(1f, q.lengthSquared(), 1e-4f, "a rotation");
                Vector3f ring = world(q, k, 0f, 0f, 0f); // the ring's top in the model
                assertTrue(ring.distance(AnchorPose.ring(q, k)) < 1e-4f, k + ": ring " + ring + " vs " + AnchorPose.ring(q, k));
                assertEquals(AnchorTravel.HEIGHT, ring.distance(AnchorPose.lift(k)), 1e-4, "the shank is the anchor's height");
            }
        }
    }

    @Test
    void turningEasesTowardTheTarget() {
        assertEquals(0f, AnchorPose.turn(0f));
        assertEquals(1f - Math.exp(-1.0), AnchorPose.turn(AnchorPose.TURN_TICKS), 1e-5);
        assertTrue(AnchorPose.turn(40f) > 0.99f);
    }
}
