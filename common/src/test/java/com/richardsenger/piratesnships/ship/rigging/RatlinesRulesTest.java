package com.richardsenger.piratesnships.ship.rigging;

import com.richardsenger.piratesnships.ship.rigging.RatlinesRules.Kind;
import com.richardsenger.piratesnships.ship.rigging.RatlinesRules.Placement;
import com.richardsenger.piratesnships.ship.rigging.RatlinesRules.Supports;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link RatlinesRules}: placement order, support and the shapes of the ratlines (RL1). */
class RatlinesRulesTest {

    /** A player looking east and slightly down: east, down, north, south, up, west. */
    private static final Direction[] LOOK_EAST = {Direction.EAST, Direction.DOWN, Direction.NORTH, Direction.SOUTH,
            Direction.UP, Direction.WEST};

    private static Supports supports(boolean wall, boolean floor, boolean below, boolean run, boolean lean) {
        return new Supports(wall, floor, below, run, lean);
    }

    @Test
    void sideFaceHangsTheNetOnThatFaceFirst() {
        // the player looks east at the west side of a mast and clicks it: the net hangs facing west, back to the mast
        List<Placement> c = RatlinesRules.candidates(Direction.WEST, Direction.EAST, LOOK_EAST);
        assertEquals(new Placement(Kind.WALL, Direction.WEST), c.get(0));
        assertEquals(new Placement(Kind.SLOPE, Direction.EAST), c.get(1), "then sloped, rising the way the player looks");
        // then the ladder order; WALL WEST is not repeated
        assertEquals(List.of(new Placement(Kind.WALL, Direction.WEST), new Placement(Kind.SLOPE, Direction.EAST),
                new Placement(Kind.WALL, Direction.SOUTH), new Placement(Kind.WALL, Direction.NORTH),
                new Placement(Kind.WALL, Direction.EAST)), c);
    }

    @Test
    void topFaceLaysASlopeRisingTheWayThePlayerLooks() {
        for (Direction look : Direction.Plane.HORIZONTAL) {
            List<Placement> c = RatlinesRules.candidates(Direction.UP, look, new Direction[]{look, Direction.DOWN});
            assertEquals(new Placement(Kind.SLOPE, look), c.get(0), "looking " + look);
            assertEquals(new Placement(Kind.WALL, look.getOpposite()), c.get(1), "then hung on what the player looks at");
        }
    }

    @Test
    void bottomFaceHangsLikeALadderBeforeSloping() {
        List<Placement> c = RatlinesRules.candidates(Direction.DOWN, Direction.EAST, LOOK_EAST);
        assertEquals(new Placement(Kind.WALL, Direction.WEST), c.get(0));
        assertEquals(new Placement(Kind.SLOPE, Direction.EAST), c.get(c.size() - 1));
    }

    @Test
    void candidatesHaveNoDuplicates() {
        for (Direction face : Direction.values()) {
            List<Placement> c = RatlinesRules.candidates(face, Direction.NORTH, Direction.values());
            assertEquals(c.size(), c.stream().distinct().count(), "clicked " + face);
        }
    }

    @Test
    void aHungNetNeedsItsWallAndNothingElse() {
        assertTrue(RatlinesRules.survives(Kind.WALL, supports(true, false, false, false, false)));
        assertFalse(RatlinesRules.survives(Kind.WALL, supports(false, true, true, true, true)),
                "floor, ratlines below and a lean do not hold a hung net");
    }

    @Test
    void aSlopedNetStandsOnAnyOfItsFourSupports() {
        assertFalse(RatlinesRules.survives(Kind.SLOPE, supports(true, false, false, false, false)),
                "a wall behind does not hold a sloped net");
        assertTrue(RatlinesRules.survives(Kind.SLOPE, supports(false, true, false, false, false)), "deck or gunwale below");
        assertTrue(RatlinesRules.survives(Kind.SLOPE, supports(false, false, true, false, false)), "ratlines below");
        assertTrue(RatlinesRules.survives(Kind.SLOPE, supports(false, false, false, true, false)), "the previous link of the run");
        assertTrue(RatlinesRules.survives(Kind.SLOPE, supports(false, false, false, false, true)), "leaning on the mast");
    }

    @Test
    void aRunRisesStraightUpOrOneUpAndForward() {
        assertEquals(new Vec3i(0, 1, 0), RatlinesRules.next(Kind.WALL, Direction.EAST));
        assertEquals(new Vec3i(1, 1, 0), RatlinesRules.next(Kind.SLOPE, Direction.EAST));
        assertEquals(new Vec3i(0, 1, -1), RatlinesRules.next(Kind.SLOPE, Direction.NORTH));
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Vec3i n = RatlinesRules.next(Kind.SLOPE, d);
            Vec3i p = RatlinesRules.previous(d);
            assertEquals(Vec3i.ZERO, n.offset(p), "previous undoes next for " + d);
        }
    }

    @Test
    void theHungNetIsALadderPlateAgainstItsWall() {
        List<double[]> boxes = RatlinesRules.boxes(Kind.WALL);
        assertEquals(1, boxes.size());
        // facing north: the support is south, the plate at z 13..16
        assertEquals(List.of(0.0, 0.0, 13.0, 16.0, 16.0, 16.0), toList(boxes.get(0)));
    }

    @Test
    void theSlopedTreadsClimbFourPixelsPerFourPixelsFromTheSouthBottomEdge() {
        List<double[]> treads = RatlinesRules.boxes(Kind.SLOPE);
        assertEquals(4, treads.size());
        for (int k = 0; k < 4; k++) {
            double[] t = treads.get(k);
            double top = t[4];
            double midZ = (t[2] + t[5]) / 2;
            assertEquals(4 * k + 2, top, 1e-9, "tread " + k + " top");
            // the tread's middle lies on the 45 degree line y = 16 - z
            assertEquals(16 - top, midZ, 1e-9, "tread " + k + " sits on the diagonal");
            assertEquals(RatlinesRules.TREAD_THICKNESS, t[4] - t[1], 1e-9);
            assertTrue(t[0] == 0 && t[3] == 16, "a tread spans the block's width");
        }
        // across a block boundary the step stays 4 px: the last top (14) plus 4 is the next block's first (2 + 16)
        assertEquals(treads.get(0)[4] + 16, treads.get(3)[4] + RatlinesRules.RATLINE_SPACING, 1e-9);
        // a player's auto step (0.6 blocks = 9.6 px) climbs each tread
        assertTrue(RatlinesRules.RATLINE_SPACING < 9.6);
    }

    private static List<Double> toList(double[] a) {
        return java.util.Arrays.stream(a).boxed().toList();
    }
}
