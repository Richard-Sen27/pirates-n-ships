package com.richardsenger.piratesnships.ship.rigging;

import com.richardsenger.piratesnships.ship.rigging.RatlinesRules.Kind;
import com.richardsenger.piratesnships.ship.rigging.RatlinesRules.Placement;
import com.richardsenger.piratesnships.ship.rigging.RatlinesRules.Supports;
import com.richardsenger.piratesnships.sailing.sail.SquareSail;
import com.richardsenger.piratesnships.sailing.sail.YardRow;
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

    // ------------------------------------------------------------------ RL1b: the square sail's cloth

    /** The starter sloop's sail: yards along x at z 13, the upper at y 16 (x 1..7), the lower at y 10 (x 0..8). */
    private static final SquareSail SLOOP = new SquareSail(new YardRow(true, 16, 13, 1, 7), new YardRow(true, 10, 13, 0, 8));

    @Test
    void theYardsEndFacesAreOutsideTheCloth() {
        // upper yard x 1..7: its end faces are x 0 and x 8 at y 16
        assertFalse(RatlinesRules.inCloth(SLOOP, 0, 16, 13));
        assertFalse(RatlinesRules.inCloth(SLOOP, 8, 16, 13));
        // lower yard x 0..8: x -1 and x 9 at y 10
        assertFalse(RatlinesRules.inCloth(SLOOP, -1, 10, 13));
        assertFalse(RatlinesRules.inCloth(SLOOP, 9, 10, 13));
    }

    @Test
    void theForeAndAftFacesOfASailsYardsAreInTheCloth() {
        for (int x = 1; x <= 7; x++) {
            assertTrue(RatlinesRules.inCloth(SLOOP, x, 16, 12), "fore of the upper yard at x " + x);
            assertTrue(RatlinesRules.inCloth(SLOOP, x, 16, 14), "aft of the upper yard at x " + x);
        }
        for (int x = 0; x <= 8; x++) {
            assertTrue(RatlinesRules.inCloth(SLOOP, x, 10, 14), "aft of the lower yard at x " + x);
        }
        // between the yards, in the plane and a block to either side
        assertTrue(RatlinesRules.inCloth(SLOOP, 4, 13, 13));
        assertTrue(RatlinesRules.inCloth(SLOOP, 4, 13, 12));
        assertTrue(RatlinesRules.inCloth(SLOOP, 4, 13, 14));
    }

    @Test
    void aboveBelowBesideAndFarOffTheClothIsFree() {
        assertFalse(RatlinesRules.inCloth(SLOOP, 3, 17, 13), "above the upper yard");
        assertFalse(RatlinesRules.inCloth(SLOOP, 3, 17, 14), "above the upper yard, aft");
        assertFalse(RatlinesRules.inCloth(SLOOP, 4, 9, 14), "below the lower yard");
        assertFalse(RatlinesRules.inCloth(SLOOP, 4, 13, 15), "two blocks aft of the yards' plane");
        assertFalse(RatlinesRules.inCloth(SLOOP, 4, 13, 11), "two blocks fore");
        // the trapezoid widens from 7 to 9 downward: at y 13 (half way) its edge is 4 blocks out, x 0 and 8 just touch
        assertTrue(RatlinesRules.inCloth(SLOOP, 0, 13, 13), "the widening cloth at x 0, y 13");
        assertTrue(RatlinesRules.inCloth(SLOOP, 0, 15, 13), "the widening cloth reaches into x 0 one block down");
        assertFalse(RatlinesRules.inCloth(SLOOP, 0, 16, 12), "fore of the cell beyond the upper yard's end");
        assertFalse(RatlinesRules.inCloth(SLOOP, -1, 13, 13), "beyond the lower yard's end");
    }

    @Test
    void aYardAlongZUsesXAsItsPlane() {
        SquareSail s = new SquareSail(new YardRow(false, 8, 5, 10, 12), new YardRow(false, 5, 5, 10, 12));
        assertTrue(RatlinesRules.inCloth(s, 6, 8, 11), "east of the upper yard's middle");
        assertFalse(RatlinesRules.inCloth(s, 5, 8, 13), "the upper yard's south end face");
        assertFalse(RatlinesRules.inCloth(s, 7, 8, 11), "two blocks east");
    }

    @Test
    void onlyANetThatNeedsTheYardIsRefusedInTheCloth() {
        Supports yardOnly = supports(true, false, false, false, false);
        Supports none = supports(false, false, false, false, false);
        assertTrue(RatlinesRules.refusedByCloth(Kind.WALL, yardOnly, none, true), "hung on the yard in the cloth");
        assertFalse(RatlinesRules.refusedByCloth(Kind.WALL, yardOnly, none, false), "hung on the yard outside the cloth");
        // a sloped link of a run leaning on the yard also stands on the link below: kept
        Supports runAndYard = supports(false, false, false, true, true);
        Supports run = supports(false, false, false, true, false);
        assertFalse(RatlinesRules.refusedByCloth(Kind.SLOPE, runAndYard, run, true));
        // a lone sloped net leaning only on the yard in the cloth: refused
        assertTrue(RatlinesRules.refusedByCloth(Kind.SLOPE, supports(false, false, false, false, true), none, true));
        // nothing holds it at all: not a cloth refusal (it simply cannot stand)
        assertFalse(RatlinesRules.refusedByCloth(Kind.WALL, none, none, true));
    }

    private static List<Double> toList(double[] a) {
        return java.util.Arrays.stream(a).boxed().toList();
    }
}
