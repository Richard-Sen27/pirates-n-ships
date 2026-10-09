package com.richardsenger.piratesnships.ship.decor.railing;

import com.richardsenger.piratesnships.combat.boarding.PlankRun;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static net.minecraft.core.Direction.EAST;
import static net.minecraft.core.Direction.NORTH;
import static net.minecraft.core.Direction.SOUTH;
import static net.minecraft.core.Direction.WEST;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RAIL3's pure rules: a gate continues a railing run (no double posts), the hinge from the click, the gate's shapes
 * open and closed, the plank's opening rule (open gates only) and the support shape a lantern or chain sees.
 */
class RailingGateRulesTest {

    // ------------------------------------------------------------------ the run

    /**
     * Railing at x 0 on the north edge, a gate facing north at x 1 (east of it): the railing's east end continues into
     * the gate, so it keeps only its west end post; the gate's own posts close the run. The same with a gate facing
     * another edge: the railing ends in a post.
     */
    @Test
    void aGateFacingTheEdgeContinuesTheRun() {
        Set<Direction> edges = EnumSet.of(NORTH);
        // the gate lies toward east; it "carries" the north edge as RailingBlock.carries says for a gate facing north
        RailingRules.Neighbours withGate = (towards, edge) -> towards == EAST && edge == NORTH;
        assertEquals(EnumSet.of(NORTH), RailingRules.posts(edges, withGate), "only the west end post (corner key NORTH)");
        RailingRules.Neighbours turnedGate = (towards, edge) -> towards == EAST && edge == SOUTH;
        assertEquals(EnumSet.of(NORTH, EAST), RailingRules.posts(edges, turnedGate), "a gate on another edge ends the run");
    }

    // ------------------------------------------------------------------ hinge

    @Test
    void theHingeGoesToTheHalfClicked() {
        // facing north (outboard), left is west: a click at x 0.2 hinges left, at x 0.8 right
        assertTrue(RailingRules.gateHingeLeft(0.2, 0.1, NORTH));
        assertFalse(RailingRules.gateHingeLeft(0.8, 0.1, NORTH));
        // facing south, left is east
        assertTrue(RailingRules.gateHingeLeft(0.8, 0.9, SOUTH));
        assertFalse(RailingRules.gateHingeLeft(0.2, 0.9, SOUTH));
        // facing east, left is north; facing west, left is south
        assertTrue(RailingRules.gateHingeLeft(0.9, 0.2, EAST));
        assertTrue(RailingRules.gateHingeLeft(0.1, 0.8, WEST));
        assertFalse(RailingRules.gateHingeLeft(0.1, 0.2, WEST));
    }

    // ------------------------------------------------------------------ shapes

    @Test
    void aClosedGateIsTheRailingsFullEdgeAndAnOpenOneThePorts() {
        List<double[]> closed = RailingRules.gateBoxes(NORTH, false, 24, 24, RailingRules.POST);
        assertEquals(4, closed.size());
        assertArrayEquals(new double[]{3, 0, 0, 13, 24, 2}, closed.get(3), "the leaf between the posts, up to the collision height");
        List<double[]> open = RailingRules.gateBoxes(NORTH, true, 17, 0, RailingRules.PORT_COLLISION_POST);
        assertEquals(3, open.size(), "posts and sill only");
        for (int i = 0; i < 3; i++) {
            assertArrayEquals(RailingRules.portBoxes(NORTH, 17, RailingRules.PORT_COLLISION_POST).get(i), open.get(i));
        }
        // turned east: the leaf on the east edge
        assertArrayEquals(new double[]{14, 0, 3, 16, 16, 13}, RailingRules.gateBoxes(EAST, false, 17, 16, 3).get(3));
    }

    // ------------------------------------------------------------------ the gangway

    @Test
    void onlyAnOpenGateIsAGangwayOpening() {
        assertTrue(PlankRun.opening(true, false, false), "a port (not openable) is always an opening");
        assertTrue(PlankRun.opening(true, true, true), "an open gate is");
        assertFalse(PlankRun.opening(true, true, false), "a closed gate is not");
        assertFalse(PlankRun.opening(false, true, true), "an untagged block never is");
        assertFalse(PlankRun.opening(false, false, false));
    }

    // ------------------------------------------------------------------ hanging from a post

    @Test
    void aPostCarriesALanternAboveAndBelowAPlainRailDoesNot() {
        List<double[]> post = RailingRules.supportBoxes(RailingRules.boxes(EnumSet.of(NORTH), EnumSet.of(NORTH, EAST),
                RailingRules.RAIL_TOP, RailingRules.RAIL_TOP), true);
        assertTrue(RailingRules.supportsCentre(post, true), "no centre support on the post's top");
        assertTrue(RailingRules.supportsCentre(post, false), "no centre support under the post");
        for (double[] b : post) assertTrue(b[4] <= 16, "the support reaches above the block");
        List<double[]> plain = RailingRules.supportBoxes(RailingRules.boxes(EnumSet.of(NORTH), EnumSet.noneOf(Direction.class),
                RailingRules.RAIL_TOP, RailingRules.RAIL_TOP), false);
        assertFalse(RailingRules.supportsCentre(plain, true), "a plain rail carries a lantern");
        assertFalse(RailingRules.supportsCentre(plain, false), "a lantern hangs under a plain rail");
        // the collision height (24 px) is cut to the block
        List<double[]> tall = RailingRules.supportBoxes(List.of(new double[]{0, 0, 0, 3, 24, 3}), true);
        assertEquals(16, tall.get(0)[4]);
    }
}
