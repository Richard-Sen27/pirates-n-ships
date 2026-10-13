package com.richardsenger.piratesnships.combat.grapple;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GR8: the body's lean along the rope ({@link RopeLean}) pushed through the real transform chain: vanilla's
 * {@code setupRotations} turn by {@code 180 - bodyYaw} about +Y ({@code Axis.YP.rotationDegrees}), then PAL's body
 * transform as {@code PlayerRendererMixin#applyBodyTransforms} (PAL 1.1.6) applies it: {@code translate(-px/16,
 * py/16 + 0.75, pz/16)}, the bone's x and y rotation negated, {@code rotateZYX(rz, ry, rx)}, {@code translate(0, -0.75,
 * 0)}. Points in world blocks relative to the feet.
 */
class RopeLeanTest {

    private static Matrix4f chain(RopeLean.Lean l, float bodyYaw) {
        return new Matrix4f()
                .rotateY((float) Math.toRadians(180.0 - bodyYaw))
                .translate(-l.px() / 16f, l.py() / 16f + 0.75f, l.pz() / 16f)
                .rotateZYX(l.rz(), -0f, -l.rx())
                .translate(0f, -0.75f, 0f);
    }

    private static Vec3 point(Matrix4f m, double x, double y, double z) {
        Vector4f v = m.transform(new Vector4f((float) x, (float) y, (float) z, 1f));
        return new Vec3(v.x, v.y, v.z);
    }

    private static Vec3 direction(Matrix4f m, double x, double y, double z) {
        Vector3f v = m.transformDirection(new Vector3f((float) x, (float) y, (float) z));
        return new Vec3(v.x, v.y, v.z);
    }

    /** Vanilla's facing direction of a yaw. */
    private static Vec3 facing(float yaw) {
        double r = Math.toRadians(yaw);
        return new Vec3(-Math.sin(r), 0, Math.cos(r));
    }

    @Test
    void aVerticalRopeLeavesTheBodyUpright() {
        RopeLean.Lean l = RopeLean.of(new Vec3(0, 3, 0), 37f);
        assertEquals(0f, l.rx(), 1e-6);
        assertEquals(0f, l.rz(), 1e-6);
        assertEquals(0f, l.px(), 1e-5);
        assertEquals(0f, l.py(), 1e-5);
        assertEquals(0f, l.pz(), 1e-5);
    }

    @Test
    void theBodysUpPointsAlongTheRopeForAnyYawAndDirection() {
        for (float yaw : new float[]{0f, 90f, -90f, 180f, 33f, -140f}) {
            for (Vec3 rope : new Vec3[]{new Vec3(1, 3, 0), new Vec3(0, 3, -1.5), new Vec3(-2, 4, 1), new Vec3(0.3, 2, 0.8)}) {
                RopeLean.Lean l = RopeLean.of(rope, yaw);
                Vec3 up = direction(chain(l, yaw), 0, 1, 0).normalize();
                Vec3 want = rope.normalize();
                assertEquals(0.0, up.distanceTo(want), 1e-4, "yaw " + yaw + ", rope " + rope + ": body up " + up);
            }
        }
    }

    @Test
    void theGripStaysOnTheRopesEnd() {
        for (float yaw : new float[]{0f, 90f, 180f, -45f}) {
            Matrix4f upright = chain(RopeLean.Lean.NONE, yaw);
            Vec3 grip = point(upright, 0, RopeLean.GRIP_HEIGHT, 0);
            RopeLean.Lean l = RopeLean.of(new Vec3(1.5, 3, -1), yaw);
            Vec3 leaned = point(chain(l, yaw), 0, RopeLean.GRIP_HEIGHT, 0);
            assertEquals(0.0, leaned.distanceTo(grip), 1e-4, "yaw " + yaw + ": the hands moved off the rope to " + leaned);
            assertEquals(0.0, grip.distanceTo(new Vec3(0, RopeLean.GRIP_HEIGHT, 0)), 1e-4, "the upright grip is over the feet");
        }
    }

    @Test
    void aHookAheadTipsTheHeadForwardAndTheFeetBack() {
        float yaw = 30f;
        Vec3 ahead = facing(yaw);
        RopeLean.Lean l = RopeLean.of(ahead.add(0, 3, 0), yaw);
        Matrix4f m = chain(l, yaw);
        Vec3 head = point(m, 0, 1.6, 0);
        Vec3 feet = point(m, 0, 0, 0);
        assertTrue(head.dot(ahead) > feet.dot(ahead) + 0.3, "the head is not ahead of the feet: " + head + " / " + feet);
        assertTrue(feet.dot(ahead) < -0.1, "the feet did not swing back under the grip: " + feet);
        assertTrue(l.rx() > 0, "a forward lean is a positive x angle of the body bone: " + l.rx());
    }

    @Test
    void aHookToTheRightTipsTheBodyRight() {
        float yaw = 0f; // facing +z: the player's right is -x
        Vec3 right = new Vec3(-1, 0, 0);
        RopeLean.Lean l = RopeLean.of(right.add(0, 3, 0), yaw);
        Matrix4f m = chain(l, yaw);
        Vec3 head = point(m, 0, 1.6, 0);
        Vec3 feet = point(m, 0, 0, 0);
        // the body hangs from its hands: the feet swing out to the left, under the line from the hook through the grip
        assertTrue(feet.dot(right) < -0.3, "the feet do not swing left: " + feet);
        assertTrue(head.dot(right) > feet.dot(right) + 0.5, "the body does not lean right: head " + head + ", feet " + feet);
    }

    @Test
    void theTiltIsLimitedAndAHookBelowDoesNotLean() {
        RopeLean.Lean flat = RopeLean.of(new Vec3(5, 0.1, 0), 0f);
        Vec3 up = direction(chain(flat, 0f), 0, 1, 0).normalize();
        assertEquals(RopeLean.MAX_TILT_DEGREES, Math.toDegrees(Math.acos(up.y)), 0.1);
        assertEquals(RopeLean.Lean.NONE, RopeLean.of(new Vec3(1, -2, 0), 0f));
        assertEquals(RopeLean.Lean.NONE, RopeLean.of(Vec3.ZERO, 0f));
    }
}
