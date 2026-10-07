package com.richardsenger.piratesnships.sailing.rope;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Where ropes tie onto anchors (RP1): the tie point for every face and facing, with the cleat's horn inset (0.27) and
 * the mooring ring's eye inset (0.35), both compile-time constants of the block classes.
 */
class RopeAnchorMathTest {

    private static final double EPS = 1e-9;
    private static final double CLEAT = com.richardsenger.piratesnships.sailing.block.CleatBlock.HORN_INSET;
    private static final double RING = com.richardsenger.piratesnships.combat.grapple.MooringRingBlock.RING_INSET;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void floorAndCeilingAnchorsTieOnTheMiddleLineNearTheirSupport() {
        for (Direction f : Direction.Plane.HORIZONTAL) {
            assertArrayEquals(new double[] {0.5, 0.5 - CLEAT, 0.5}, RopeAnchorMath.point(AttachFace.FLOOR, f, CLEAT), EPS, "floor " + f);
            assertArrayEquals(new double[] {0.5, 0.5 + CLEAT, 0.5}, RopeAnchorMath.point(AttachFace.CEILING, f, CLEAT), EPS, "ceiling " + f);
            assertArrayEquals(new double[] {0.5, 0.5 - RING, 0.5}, RopeAnchorMath.point(AttachFace.FLOOR, f, RING), EPS, "ring floor " + f);
            assertArrayEquals(new double[] {0.5, 0.5 + RING, 0.5}, RopeAnchorMath.point(AttachFace.CEILING, f, RING), EPS, "ring ceiling " + f);
        }
    }

    @Test
    void wallAnchorsTieTowardTheWallBehindThem() {
        // the facing points away from the supporting block
        assertArrayEquals(new double[] {0.5, 0.5, 0.5 + CLEAT}, RopeAnchorMath.point(AttachFace.WALL, Direction.NORTH, CLEAT), EPS);
        assertArrayEquals(new double[] {0.5, 0.5, 0.5 - CLEAT}, RopeAnchorMath.point(AttachFace.WALL, Direction.SOUTH, CLEAT), EPS);
        assertArrayEquals(new double[] {0.5 - CLEAT, 0.5, 0.5}, RopeAnchorMath.point(AttachFace.WALL, Direction.EAST, CLEAT), EPS);
        assertArrayEquals(new double[] {0.5 + CLEAT, 0.5, 0.5}, RopeAnchorMath.point(AttachFace.WALL, Direction.WEST, CLEAT), EPS);
        assertArrayEquals(new double[] {0.5, 0.5, 0.5 + RING}, RopeAnchorMath.point(AttachFace.WALL, Direction.NORTH, RING), EPS);
        assertArrayEquals(new double[] {0.5 + RING, 0.5, 0.5}, RopeAnchorMath.point(AttachFace.WALL, Direction.WEST, RING), EPS);
    }

    @Test
    void outwardMatchesVanillasConnectedDirection() {
        assertEquals(Direction.UP, RopeAnchorMath.outward(AttachFace.FLOOR, Direction.EAST));
        assertEquals(Direction.DOWN, RopeAnchorMath.outward(AttachFace.CEILING, Direction.EAST));
        for (Direction f : Direction.Plane.HORIZONTAL) {
            assertEquals(f, RopeAnchorMath.outward(AttachFace.WALL, f));
        }
    }

    @Test
    void thePointStaysInsideTheBlock() {
        for (AttachFace face : AttachFace.values()) {
            for (Direction f : Direction.Plane.HORIZONTAL) {
                for (double c : RopeAnchorMath.point(face, f, RING)) {
                    org.junit.jupiter.api.Assertions.assertTrue(c > 0 && c < 1, face + " " + f);
                }
            }
        }
    }
}
