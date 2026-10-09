package com.richardsenger.piratesnships.almanac.client;

import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Composes {@link AlmanacBookPose} with the real part transforms of vanilla's {@code BookModel} (the covers' boxes,
 * hinge offsets and the y turns {@code setupAnim} gives them at time 0; {@code ModelPart} translates by its offset in
 * px / 16, turns ZYX and draws its box in px / 16) and checks where the covers end up in the block, facing north.
 */
class AlmanacBookPoseTest {

    private static final float PX = 1.0f / 16.0f;
    /** Tolerance: a twentieth of a pixel (the covers are 0.005 px thick). */
    private static final float EPS = 0.05f * PX;

    /** The eight corners of a cover in block space. {@code right}: the right lid, else the left one. */
    private static List<Vector3f> cover(Direction facing, float open, boolean right) {
        float f = AlmanacBookPose.LID_ANGLE * open;
        Matrix4f m = AlmanacBookPose.pose(facing, open)
                .translate(0.0f, 0.0f, (right ? 1.0f : -1.0f) * PX)
                .rotate(new Quaternionf().rotationZYX(0.0f, right ? -f : (float) Math.PI + f, 0.0f));
        float x0 = right ? 0.0f : -6.0f;
        List<Vector3f> out = new ArrayList<>();
        for (float x : new float[]{x0, x0 + 6.0f}) {
            for (float y : new float[]{-5.0f, 5.0f}) {
                for (float z : new float[]{-0.005f, 0.0f}) {
                    out.add(m.transformPosition(new Vector3f(x * PX, y * PX, z * PX)));
                }
            }
        }
        return out;
    }

    private static float min(List<Vector3f> vs, int axis) {
        return (float) vs.stream().mapToDouble(v -> v.get(axis)).min().orElseThrow();
    }

    private static float max(List<Vector3f> vs, int axis) {
        return (float) vs.stream().mapToDouble(v -> v.get(axis)).max().orElseThrow();
    }

    private static List<Vector3f> both(Direction facing, float open) {
        List<Vector3f> all = new ArrayList<>(cover(facing, open, true));
        all.addAll(cover(facing, open, false));
        return all;
    }

    @Test
    void closedItLiesFlatOnItsRightCoverCentredOnTheBlock() {
        List<Vector3f> right = cover(Direction.NORTH, 0.0f, true);
        List<Vector3f> left = cover(Direction.NORTH, 0.0f, false);
        assertEquals(0.0f, max(right, 1) - min(right, 1), EPS, "the right cover is level");
        assertEquals(0.0f, max(left, 1) - min(left, 1), EPS, "the left cover is level");
        assertTrue(min(right, 1) >= -EPS && min(right, 1) < 0.2f * PX, "the right cover rests on the floor: " + min(right, 1));
        assertTrue(min(left, 1) > max(right, 1), "the left cover lies on top");
        List<Vector3f> all = both(Direction.NORTH, 0.0f);
        assertEquals(0.5f, (min(all, 0) + max(all, 0)) / 2.0f, 0.5f * PX, "centred across");
        assertEquals(0.5f, (min(all, 2) + max(all, 2)) / 2.0f, EPS, "centred along the spine");
    }

    @Test
    void openItLiesSymmetricWithTheSpineAlongTheFacingAxis() {
        List<Vector3f> right = cover(Direction.NORTH, 1.0f, true);
        List<Vector3f> left = cover(Direction.NORTH, 1.0f, false);
        assertEquals(max(right, 1), max(left, 1), EPS, "both covers rise alike");
        assertTrue(min(right, 1) >= -EPS && min(left, 1) >= -EPS, "nothing below the floor");
        float rise = (float) Math.toDegrees(Math.atan2(max(right, 1) - min(right, 1), max(right, 0) - min(right, 0)));
        assertEquals(90.0 - Math.toDegrees(AlmanacBookPose.LID_ANGLE), rise, 1.0, "each cover rises 90° - LID_ANGLE");
        List<Vector3f> all = both(Direction.NORTH, 1.0f);
        assertEquals(0.5f, (min(all, 0) + max(all, 0)) / 2.0f, EPS, "centred across");
        // the spine runs north-south (the facing axis): the long side of the spread is across, in x
        assertEquals(10.0f * AlmanacBookPose.SCALE * PX, max(all, 2) - min(all, 2), EPS, "the spine's length along z");
        // inside the block's shape (x 0.5..15.5, z 2..14, y 0..4 px)
        assertTrue(min(all, 0) >= 0.5f * PX - EPS && max(all, 0) <= 15.5f * PX + EPS, "within the shape across: " + min(all, 0) * 16 + ".." + max(all, 0) * 16);
        assertTrue(min(all, 2) >= 2.0f * PX && max(all, 2) <= 14.0f * PX, "within the shape along the spine");
        assertTrue(max(all, 1) <= 4.0f * PX, "within the shape's height");
    }

    @Test
    void neitherCoverDipsBelowTheFloorWhileOpening() {
        for (int i = 0; i <= 20; i++) {
            float open = i / 20.0f;
            for (boolean right : new boolean[]{true, false}) {
                List<Vector3f> c = cover(Direction.NORTH, open, right);
                assertTrue(min(c, 1) >= -EPS, "open " + open + (right ? " right" : " left") + " cover at " + min(c, 1) * 16 + " px");
            }
            // the right cover never tips past level towards the floor
            List<Vector3f> r = cover(Direction.NORTH, open, true);
            assertTrue(max(r, 1) - min(r, 1) >= -EPS);
        }
    }

    @Test
    void eastFacingTurnsTheSpineOntoTheXAxis() {
        List<Vector3f> all = both(Direction.EAST, 1.0f);
        assertEquals(10.0f * AlmanacBookPose.SCALE * PX, max(all, 0) - min(all, 0), EPS, "the spine's length along x");
        assertEquals(0.5f, (min(all, 2) + max(all, 2)) / 2.0f, EPS, "centred across in z");
    }
}
