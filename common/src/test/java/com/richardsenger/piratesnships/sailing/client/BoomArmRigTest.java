package com.richardsenger.piratesnships.sailing.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import com.richardsenger.piratesnships.sailing.sail.BoomRules;
import com.richardsenger.piratesnships.sailing.sail.TriangleCloth;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * SAIL5 rig test of the boom's arm and the boomed cloth: composes the real transforms {@link BoomRenderer} applies
 * ({@link BoomRenderer#placeArm}, {@link BoomRenderer#placePiece}, with {@code Axis.YP}) and checks, for every facing
 * and every sheet step, that the arm starts at the pivot and ends where the cloth renderer puts the sail's foot corner
 * ({@link TriangleCloth#corner}), that a positive sheet turns the boom clockwise seen from above, and that the cloth
 * bellies the way the boom swung ({@link StayClothRenderer#heldSide}).
 */
class BoomArmRigTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** A model point of piece {@code i} ({@code piece} long) of an arm along {@code (x, z)}, in block coordinates. */
    private static Vector3f place(double x, double z, int i, float piece, float mx, float my, float mz) {
        PoseStack pose = new PoseStack();
        BoomRenderer.placeArm(pose, x, z);
        BoomRenderer.placePiece(pose, i, piece);
        return pose.last().pose().transformPosition(mx, my, mz, new Vector3f());
    }

    @Test
    void theArmRunsFromThePivotToTheBoomsEndForEveryFacingAndStep() {
        int[][] arms = {{0, -5}, {4, 0}, {0, 3}, {-6, 0}, {3, -4}, null};
        for (int[] arm : arms) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                for (int step = 0; step < BoomRules.STEPS; step++) {
                    double sheet = BoomRules.angle(step);
                    double[] end = BoomRenderer.direction(arm, facing, sheet);
                    float length = (float) Math.hypot(end[0], end[1]);
                    String at = "arm " + (arm == null ? "none" : arm[0] + "," + arm[1]) + " facing " + facing + " step " + step;
                    // the near end of the first piece is the pivot, the block's centre
                    Vector3f near = place(end[0], end[1], 0, Math.min(1f, length), 0.5f, 0.5f, 1f);
                    assertEquals(0.5f, near.x, 1e-4f, at);
                    assertEquals(0.5f, near.y, 1e-4f, at);
                    assertEquals(0.5f, near.z, 1e-4f, at);
                    // the far end of the last piece is the boom's end
                    int last = (int) Math.ceil(length - 1e-4) - 1;
                    Vector3f far = place(end[0], end[1], last, length - last, 0.5f, 0.5f, 0f);
                    assertEquals(0.5f + (float) end[0], far.x, 1e-4f, at);
                    assertEquals(0.5f, far.y, 1e-4f, at);
                    assertEquals(0.5f + (float) end[1], far.z, 1e-4f, at);
                }
            }
        }
    }

    @Test
    void theBoomsEndIsTheClothsFootCornerBelowTheHead() {
        // a head 5 above the boom, the tack 6 below the head and 4 east, 3 north
        TriangleCloth cloth = new TriangleCloth(4, -6, -3, 5, true);
        int[] arm = {cloth.tackX(), cloth.tackZ()}; // what TriangularSails.refresh gives the boom
        for (int step = 0; step < BoomRules.STEPS; step++) {
            double sheet = BoomRules.angle(step);
            float[] corner = cloth.corner(sheet); // relative to the head
            double[] end = BoomRenderer.direction(arm, Direction.NORTH, sheet); // relative to the boom, 5 below the head
            assertEquals(corner[0], (float) end[0], 1e-5f, "x at step " + step);
            assertEquals(corner[2], (float) end[1], 1e-5f, "z at step " + step);
            assertEquals(-cloth.drop(), corner[1], 1e-6f, "the foot runs level at the boom's height");
        }
    }

    @Test
    void aPositiveSheetTurnsTheBoomClockwiseSeenFromAbove() {
        // a boom pointing north: one step clockwise (seen from above, north up) swings its end east
        double[] end = BoomRenderer.direction(new int[] {0, -4}, Direction.NORTH, BoomRules.angle(BoomRules.CENTRE + 1));
        assertTrue(end[0] > 0 && end[1] < 0, "north-east: " + end[0] + ", " + end[1]);
        // and the drawn arm goes there (not mirrored by the pose's y turn)
        Vector3f far = place(end[0], end[1], 3, 1f, 0.5f, 0.5f, 0f);
        assertTrue(far.x > 0.5f && far.z < 0.5f, "drawn north-east: " + far);
    }

    @Test
    void theClothBelliesTheWayTheBoomSwung() {
        TriangleCloth cloth = new TriangleCloth(0, -6, -5, 5, true);
        for (int step = 0; step < BoomRules.STEPS; step++) {
            float sheet = (float) BoomRules.angle(step);
            float[] c = cloth.corner(sheet);
            Vector3f normal = new Vector3f(c[2], 0f, -c[0]).normalize(); // as StayClothRenderer builds it
            int side = StayClothRenderer.heldSide(cloth, sheet);
            if (step == BoomRules.CENTRE) {
                assertEquals(0, side, "a centred boom leaves the side to the wind");
                continue;
            }
            // where the boom's end went from the centre line
            float[] centred = cloth.corner(0);
            Vector3f moved = new Vector3f(c[0] - centred[0], 0f, c[2] - centred[2]);
            assertTrue(normal.mul(side, new Vector3f()).dot(moved) > 0, "step " + step + ": the belly goes against the swing");
        }
        assertEquals(0, StayClothRenderer.heldSide(new TriangleCloth(0, -6, -5, 5), 30f), "a jib has no boom");
    }
}
