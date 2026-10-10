package com.richardsenger.piratesnships.ship.decor.railing;

import com.richardsenger.piratesnships.ship.decor.railing.RailingRules.GateNeighbour;
import org.junit.jupiter.api.Test;

import java.util.List;

import static net.minecraft.core.Direction.EAST;
import static net.minecraft.core.Direction.NORTH;
import static net.minecraft.core.Direction.SOUTH;
import static net.minecraft.core.Direction.WEST;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RAIL7 (docs/design.md "Wide ports, double gates and the net's foot (RAIL7, RL6)"): the pure rules of wide ports and
 * double gates: which posts a port keeps beside other ports, the boxes without the posts, which side a gate latches on,
 * when two gates pair, and the hinge a gate placed beside other gates takes.
 */
class RailingPairRulesTest {

    @Test
    void aPortDropsThePostTowardAnotherPortOnly() {
        assertArrayEquals(new boolean[]{true, true}, RailingRules.portPosts(false, false));
        assertArrayEquals(new boolean[]{false, true}, RailingRules.portPosts(true, false));
        assertArrayEquals(new boolean[]{true, false}, RailingRules.portPosts(false, true));
        assertArrayEquals(new boolean[]{false, false}, RailingRules.portPosts(true, true));
    }

    @Test
    void leftAndRightAreLookingOutboard() {
        assertEquals(WEST, RailingRules.leftOf(NORTH));
        assertEquals(EAST, RailingRules.rightOf(NORTH));
        assertEquals(NORTH, RailingRules.leftOf(EAST));
        assertEquals(WEST, RailingRules.rightOf(SOUTH));
    }

    @Test
    void aPortWithoutAPostRunsItsSillToTheBlocksSide() {
        List<double[]> both = RailingRules.portBoxes(NORTH, 17, 3, true, true);
        assertEquals(3, both.size());
        assertArrayEquals(RailingRules.portBoxes(NORTH, 17, 3).get(2), both.get(2), "the lone port's sill is unchanged");
        List<double[]> middle = RailingRules.portBoxes(NORTH, 17, 3, false, false);
        assertEquals(1, middle.size(), "a middle port is only its sill");
        assertArrayEquals(new double[]{0, 0, 0, 16, RailingRules.SILL_TOP, RailingRules.RAIL_DEPTH}, middle.get(0));
        List<double[]> leftEnd = RailingRules.portBoxes(NORTH, 17, 3, true, false);
        assertEquals(2, leftEnd.size());
        assertArrayEquals(new double[]{0, 0, 0, 3, 17, 3}, leftEnd.get(0), "the left (west) post stands");
        assertArrayEquals(new double[]{3, 0, 0, 16, RailingRules.SILL_TOP, RailingRules.RAIL_DEPTH}, leftEnd.get(1));
        // turned: an east-facing port's left end is north (z 0)
        List<double[]> east = RailingRules.portBoxes(EAST, 17, 3, false, true);
        assertArrayEquals(new double[]{13, 0, 13, 16, 17, 16}, east.get(0), "the right (south) post of an east port");
        assertArrayEquals(new double[]{14, 0, 0, 16, RailingRules.SILL_TOP, 13}, east.get(1));
    }

    @Test
    void aGateLatchesOppositeItsHinge() {
        assertEquals(EAST, RailingRules.latchSide(NORTH, true));
        assertEquals(WEST, RailingRules.latchSide(NORTH, false));
        assertEquals(SOUTH, RailingRules.latchSide(EAST, true));
    }

    @Test
    void twoGatesPairOnlyFacingTheSameWayWithMirroredHinges() {
        assertTrue(RailingRules.pairs(NORTH, true, true, NORTH, false));
        assertFalse(RailingRules.pairs(NORTH, true, true, NORTH, true), "same hinge: a hinge post stands between");
        assertFalse(RailingRules.pairs(NORTH, true, true, SOUTH, false), "another facing");
        assertFalse(RailingRules.pairs(NORTH, true, false, null, false), "no gate");
    }

    @Test
    void aPairedGateLosesItsLatchPostAndItsLeafReachesTheMiddle() {
        List<double[]> single = RailingRules.gateBoxes(NORTH, false, 24, 24, 3, true, false);
        assertEquals(4, single.size());
        for (int i = 0; i < 4; i++) {
            assertArrayEquals(RailingRules.gateBoxes(NORTH, false, 24, 24, 3).get(i), single.get(i), "a single gate is unchanged");
        }
        List<double[]> left = RailingRules.gateBoxes(NORTH, false, 24, 24, 3, true, true);
        assertEquals(3, left.size(), "hinge post, sill, leaf");
        assertArrayEquals(new double[]{0, 0, 0, 3, 24, 3}, left.get(0), "the hinge post (west)");
        assertArrayEquals(new double[]{3, 0, 0, 16, 24, RailingRules.RAIL_DEPTH}, left.get(2), "the leaf runs to x 16");
        List<double[]> right = RailingRules.gateBoxes(NORTH, false, 24, 24, 3, false, true);
        assertArrayEquals(new double[]{13, 0, 0, 16, 24, 3}, right.get(0), "the hinge post (east)");
        assertArrayEquals(new double[]{0, 0, 0, 13, 24, RailingRules.RAIL_DEPTH}, right.get(2), "the leaf runs to x 0");
        List<double[]> open = RailingRules.gateBoxes(NORTH, true, 17, 0, 2, true, true);
        assertEquals(2, open.size(), "open: the hinge post and the sill");
        assertArrayEquals(new double[]{3, 0, 0, 16, RailingRules.SILL_TOP, RailingRules.RAIL_DEPTH}, open.get(1));
    }

    private static GateNeighbour free(boolean hingeToward) {
        return new GateNeighbour(true, hingeToward, false);
    }

    @Test
    void aGatePlacedBesideAFreeGateTakesTheHingeAwayFromIt() {
        GateNeighbour none = GateNeighbour.NONE;
        // alone: the clicked half decides
        assertTrue(RailingRules.placedHingeLeft(true, none, none));
        assertFalse(RailingRules.placedHingeLeft(false, none, none));
        // a free gate on the right latching toward the new one: hang on the left post whatever the click
        assertTrue(RailingRules.placedHingeLeft(false, none, free(false)));
        assertFalse(RailingRules.flipsNeighbour(free(false)));
        // a free gate on the left hung on the new gate's side: hang on the right post, the neighbour turns round
        assertFalse(RailingRules.placedHingeLeft(true, free(true), none));
        assertTrue(RailingRules.flipsNeighbour(free(true)));
        // one latching toward the new gate beats one that has to turn round
        assertTrue(RailingRules.placedHingeLeft(false, free(true), free(false)));
        assertFalse(RailingRules.placedHingeLeft(true, free(false), free(true)));
        // two equally good: the click decides
        assertTrue(RailingRules.placedHingeLeft(true, free(false), free(false)));
        assertFalse(RailingRules.placedHingeLeft(false, free(false), free(false)));
    }

    @Test
    void aGateAlreadyInAPairIsNeverTaken() {
        GateNeighbour paired = new GateNeighbour(true, true, true);
        assertTrue(RailingRules.placedHingeLeft(true, paired, GateNeighbour.NONE), "the click decides");
        assertFalse(RailingRules.placedHingeLeft(false, paired, GateNeighbour.NONE));
        assertFalse(RailingRules.flipsNeighbour(paired));
        // a third gate beside a pair pairs with a free gate on its other side
        assertFalse(RailingRules.placedHingeLeft(true, free(false), paired));
    }
}
