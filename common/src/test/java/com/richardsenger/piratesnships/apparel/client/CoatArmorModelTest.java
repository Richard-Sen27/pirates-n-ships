package com.richardsenger.piratesnships.apparel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.richardsenger.piratesnships.apparel.CoatTails;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ART9: the coat model with tails, baked from its real {@link LayerDefinition}, and the tails' pose composed through
 * the real {@link ModelPart#translateAndRotate} chain (body, then tail) against the boot-clad leg of vanilla's outer
 * armour model: the leg never passes through the tail on its side, walking, sprinting or sneaking.
 */
class CoatArmorModelTest {

    private static CoatArmorModel coat() {
        return new CoatArmorModel(CoatArmorModel.createLayer().bakeRoot());
    }

    /** Vanilla's outer armour model (boots draw on its legs: inflated by 0.9). */
    private static HumanoidModel<LivingEntity> boots() {
        return new HumanoidModel<>(LayerDefinition.create(HumanoidArmorModel.createBodyLayer(new CubeDeformation(1.0F)), 64, 32).bakeRoot());
    }

    @Test
    void tailsAreChildrenOfTheBody() {
        CoatArmorModel m = coat();
        assertSame(m.tailRight, m.body.getChild(CoatArmorModel.TAIL_RIGHT));
        assertSame(m.tailLeft, m.body.getChild(CoatArmorModel.TAIL_LEFT));
        assertEquals(CoatArmorModel.HINGE_Y, m.tailRight.y);
        assertEquals(CoatArmorModel.HINGE_Z, m.tailLeft.z);
        assertFalse(CoatArmorModel.createLayer().bakeRoot().hasChild(CoatArmorModel.TAIL_RIGHT), "not on the root: they follow the body's pose");
    }

    /** Every tail face lies on the 64x32 texture and off the body (16,16) and arm (40,16) areas. */
    @Test
    void tailUvsStayInTheHeadArea() {
        CoatArmorModel m = coat();
        List<float[]> uvs = new ArrayList<>();
        PoseStack ps = new PoseStack();
        for (ModelPart tail : List.of(m.tailRight, m.tailLeft)) {
            // what the cube hands the renderer: one setUv per vertex
            VertexConsumer capture = new VertexConsumer() {
                @Override public VertexConsumer addVertex(float x, float y, float z) { return this; }
                @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
                @Override public VertexConsumer setUv(float u, float v) { uvs.add(new float[]{u * 64, v * 32}); return this; }
                @Override public VertexConsumer setUv1(int u, int v) { return this; }
                @Override public VertexConsumer setUv2(int u, int v) { return this; }
                @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
            };
            tail.visit(ps, (pose, path, index, cube) -> cube.compile(pose, capture, 0, 0, -1));
        }
        assertEquals(2 * 2 * 6 * 4, uvs.size(), "two cubes per tail");
        for (float[] uv : uvs) {
            assertTrue(uv[0] >= 0 && uv[0] <= 40 && uv[1] >= 0 && uv[1] <= 16, "tail uv " + uv[0] + "," + uv[1] + " outside 0..40 x 0..16");
        }
    }

    @Test
    void tailsHangAtRestAndSwingBackWithTheirLeg() {
        CoatArmorModel m = coat();
        m.poseTails();
        assertEquals(CoatTails.REST, m.tailRight.xRot, 1e-6);
        m.rightLeg.xRot = 0.6f;
        m.leftLeg.xRot = -0.6f;
        m.poseTails();
        assertTrue(m.tailRight.xRot > 0.6f, "the right tail swings back past its leg: " + m.tailRight.xRot);
        assertTrue(m.tailLeft.xRot < 0.2f && m.tailLeft.xRot >= CoatTails.REST, "the left tail barely moves: " + m.tailLeft.xRot);
    }

    /**
     * Walking and sprinting strides (leg pitch -0.8..0.8: vanilla's walk animation speed reaches about 0.55 of
     * {@code HumanoidModel}'s 1.4), standing and sneaking: no leg point inside the lower 5 px of a tail (its top 2 px
     * meet the leg at the hip, inside the coat's skirt, which a 3 px offset hinge can never avoid).
     */
    @Test
    void legsNeverPassThroughTheTails() {
        for (boolean sneaking : new boolean[]{false, true}) {
            for (float pitch = -0.8f; pitch <= 0.8f + 1e-4f; pitch += 0.05f) {
                CoatArmorModel m = coat();
                HumanoidModel<LivingEntity> legs = boots();
                if (sneaking) {
                    // HumanoidModel#setupAnim's crouch
                    m.body.xRot = 0.5f; m.body.y = 3.2f;
                    for (ModelPart leg : List.of(m.rightLeg, m.leftLeg, legs.rightLeg, legs.leftLeg)) { leg.y = 12.2f; leg.z = 4.0f; }
                }
                m.rightLeg.xRot = legs.rightLeg.xRot = pitch;
                m.leftLeg.xRot = legs.leftLeg.xRot = -pitch;
                m.poseTails();
                assertClear(m, m.tailRight, legs.rightLeg, -5.5f, -0.5f, "right, pitch " + pitch + (sneaking ? ", sneaking" : ""));
                assertClear(m, m.tailLeft, legs.leftLeg, 0.5f, 5.5f, "left, pitch " + -pitch + (sneaking ? ", sneaking" : ""));
            }
        }
    }

    /**
     * Points of the leg's back face (box z = 2 + 0.9) from the hip down, taken into the tail's frame: where they fall
     * inside the back plate's x span and its lower 5 px they must lie in front of its front face (z 0.1).
     */
    private static void assertClear(CoatArmorModel m, ModelPart tail, ModelPart leg, float x0, float x1, String what) {
        PoseStack tp = new PoseStack();
        m.body.translateAndRotate(tp);
        tail.translateAndRotate(tp);
        Matrix4f toTail = new Matrix4f(tp.last().pose()).invert();
        PoseStack lp = new PoseStack();
        leg.translateAndRotate(lp);
        Matrix4f legPose = lp.last().pose();
        for (float y = -0.9f; y <= 12.9f; y += 0.25f) {
            for (float x = -2.9f; x <= 2.9f; x += 0.5f) {
                Vector3f p = legPose.transformPosition(new Vector3f(x / 16f, y / 16f, 2.9f / 16f));
                Vector3f q = toTail.transformPosition(p).mul(16f);
                if (q.y >= 2 && q.y <= 7 && q.x >= x0 && q.x <= x1) {
                    assertTrue(q.z <= 0.1f + 0.05f, what + ": leg point (" + x + ", " + y + ") at tail z " + q.z);
                }
            }
        }
    }
}
