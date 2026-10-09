package com.richardsenger.piratesnships.ship.decor.railing;

import com.richardsenger.piratesnships.combat.cannon.CannonRules;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.minecraft.core.Direction.EAST;
import static net.minecraft.core.Direction.NORTH;
import static net.minecraft.core.Direction.SOUTH;
import static net.minecraft.core.Direction.WEST;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The railing's pure rules (RAIL1): the connection rule (posts and corners), the placement rule and the port's clear line. */
class RailingRulesTest {

    // corner keys: NORTH = north-west, EAST = north-east, SOUTH = south-east, WEST = south-west
    private static final Direction NW = NORTH, NE = EAST, SE = SOUTH, SW = WEST;

    /** A row of railings on a grid: cell (x, z) -> its edges; nothing outside the map. */
    private static final class Grid {
        final Map<Long, Set<Direction>> cells = new HashMap<>();

        Grid put(int x, int z, Direction... edges) {
            cells.put(key(x, z), edges.length == 0 ? EnumSet.noneOf(Direction.class) : EnumSet.of(edges[0], edges));
            return this;
        }

        Set<Direction> posts(int x, int z) {
            Set<Direction> edges = cells.get(key(x, z));
            return RailingRules.posts(edges, (towards, edge) -> {
                Set<Direction> n = cells.get(key(x + towards.getStepX(), z + towards.getStepZ()));
                return n != null && n.contains(edge);
            });
        }

        private static long key(int x, int z) {
            return ((long) x << 32) | (z & 0xffffffffL);
        }
    }

    private static Set<Direction> set(Direction... ds) {
        return ds.length == 0 ? EnumSet.noneOf(Direction.class) : EnumSet.of(ds[0], ds);
    }

    // ------------------------------------------------------------------ the connection rule

    @Test
    void aLoneEdgeHasAPostAtEitherEnd() {
        Grid g = new Grid().put(0, 0, NORTH);
        assertEquals(set(NW, NE), g.posts(0, 0));
    }

    @Test
    void aStraightRunHasPostsOnlyAtItsEnds() {
        Grid g = new Grid().put(0, 0, NORTH).put(1, 0, NORTH).put(2, 0, NORTH);
        assertEquals(set(NW), g.posts(0, 0), "west end");
        assertEquals(set(), g.posts(1, 0), "middle");
        assertEquals(set(NE), g.posts(2, 0), "east end");
    }

    @Test
    void aOneBlockGapGetsAPostOnBothSides() {
        Grid g = new Grid().put(0, 0, EAST).put(0, 1, EAST).put(0, 3, EAST).put(0, 4, EAST);
        // the run along the east edge goes north to south (z grows southward); the gap is at z = 2
        assertEquals(set(NE), g.posts(0, 0), "the north end of the first run");
        assertEquals(set(SE), g.posts(0, 1), "the first run's end at the gap");
        assertEquals(set(NE), g.posts(0, 3), "the second run's end at the gap");
        assertEquals(set(SE), g.posts(0, 4));
    }

    @Test
    void twoEdgesInOneBlockMakeOneCornerPost() {
        Grid g = new Grid().put(0, 0, NORTH, WEST).put(1, 0, NORTH).put(0, 1, WEST);
        assertEquals(set(NW), g.posts(0, 0), "one corner post, the far ends run on");
        assertEquals(set(NE), g.posts(1, 0));
        assertEquals(set(SW), g.posts(0, 1));
    }

    @Test
    void aBlockWithEveryEdgeHasFourCornerPosts() {
        assertEquals(set(NW, NE, SE, SW), new Grid().put(0, 0, NORTH, EAST, SOUTH, WEST).posts(0, 0));
    }

    @Test
    void aHatchSurroundOfFourSidesHasTwoPostsEachSide() {
        // a 1 x 1 hatch at (1, 1): the blocks around it rail the side that faces the hatch
        Grid g = new Grid().put(1, 0, SOUTH).put(2, 1, WEST).put(1, 2, NORTH).put(0, 1, EAST);
        assertEquals(set(SE, SW), g.posts(1, 0), "north of the hatch");
        assertEquals(set(NW, SW), g.posts(2, 1), "east of the hatch");
        assertEquals(set(NW, NE), g.posts(1, 2), "south of the hatch");
        assertEquals(set(NE, SE), g.posts(0, 1), "west of the hatch");
    }

    @Test
    void aWiderHatchSurroundRunsOnAlongEachSide() {
        // a 2 x 1 hatch at x 1..2, z 1: two blocks rail its north side and run on between them
        Grid g = new Grid().put(1, 0, SOUTH).put(2, 0, SOUTH);
        assertEquals(set(SW), g.posts(1, 0));
        assertEquals(set(SE), g.posts(2, 0));
    }

    @Test
    void aNeighbourWithAnotherEdgeDoesNotContinueTheRun() {
        Grid g = new Grid().put(0, 0, NORTH).put(1, 0, SOUTH);
        assertEquals(set(NW, NE), g.posts(0, 0));
    }

    @Test
    void noEdgesNoPosts() {
        assertEquals(set(), new Grid().put(0, 0).posts(0, 0));
    }

    @Test
    void cornerKeysNameTheClockwiseEdge() {
        assertEquals(NW, RailingRules.cornerOf(NORTH, WEST));
        assertEquals(NW, RailingRules.cornerOf(WEST, NORTH));
        assertEquals(NE, RailingRules.cornerOf(NORTH, EAST));
        assertEquals(SE, RailingRules.cornerOf(SOUTH, EAST));
        assertEquals(SW, RailingRules.cornerOf(SOUTH, WEST));
    }

    // ------------------------------------------------------------------ placement

    @Test
    void aClickRailsTheNearestEdge() {
        assertEquals(NORTH, RailingRules.edgeForClick(0.5, 0.1, true, SOUTH));
        assertEquals(SOUTH, RailingRules.edgeForClick(0.4, 0.95, true, NORTH));
        assertEquals(WEST, RailingRules.edgeForClick(0.05, 0.6, true, EAST));
        assertEquals(EAST, RailingRules.edgeForClick(0.8, 0.5, true, WEST), "just outside the centre square");
    }

    @Test
    void aClickNearTheCentreRailsTheEdgeThePlayerFaces() {
        assertEquals(EAST, RailingRules.edgeForClick(0.5, 0.5, true, EAST));
        assertEquals(NORTH, RailingRules.edgeForClick(0.74, 0.27, true, NORTH), "within 4 px on both axes");
        assertEquals(WEST, RailingRules.edgeForClick(0.6, 0.4, true, WEST));
    }

    @Test
    void aSideClickRailsTheEdgeAgainstTheClickedBlock() {
        // the click point of a side face lies on the target block's boundary
        assertEquals(WEST, RailingRules.edgeForClick(0.0, 0.5, false, EAST));
        assertEquals(SOUTH, RailingRules.edgeForClick(0.5, 1.0, false, NORTH));
        assertEquals(NORTH, RailingRules.edgeForClick(0.5, 0.0, false, NORTH), "the centre rule is for top clicks only");
    }

    @Test
    void aSecondPlacementTakesTheNearestFreeEdge() {
        assertEquals(EAST, RailingRules.nearestFree(0.9, 0.1, set(NORTH)), "north is taken, east is next nearest");
        assertEquals(NORTH, RailingRules.nearestFree(0.9, 0.1, set(EAST)));
        assertEquals(SOUTH, RailingRules.nearestFree(0.5, 0.1, set(NORTH, EAST, WEST)), "the only free edge");
        assertNull(RailingRules.nearestFree(0.5, 0.5, set(NORTH, EAST, SOUTH, WEST)), "a full block takes no more");
    }

    // ------------------------------------------------------------------ shapes

    @Test
    void theRailIsTwoPixelsInFromTheEdgeAndPostsAreThreeSquare() {
        List<double[]> north = RailingRules.boxes(set(NORTH), set(), 16, 17);
        assertArrayEquals(new double[]{0, 0, 0, 16, 16, 2}, north.get(0));
        assertArrayEquals(new double[]{14, 0, 0, 16, 16, 16}, RailingRules.boxes(set(EAST), set(), 16, 17).get(0));
        assertArrayEquals(new double[]{0, 0, 0, 2, 24, 16}, RailingRules.boxes(set(WEST), set(), 24, 24).get(0));
        assertArrayEquals(new double[]{13, 0, 13, 16, 17, 16}, RailingRules.boxes(set(), set(SE), 16, 17).get(0));
        assertArrayEquals(new double[]{0, 0, 13, 3, 17, 16}, RailingRules.boxes(set(), set(SW), 16, 17).get(0));
    }

    @Test
    void thePortIsTwoPostsAndASill() {
        List<double[]> east = RailingRules.portBoxes(EAST, 17);
        assertEquals(3, east.size());
        assertArrayEquals(new double[]{13, 0, 0, 16, 17, 3}, east.get(0));
        assertArrayEquals(new double[]{13, 0, 13, 16, 17, 16}, east.get(1));
        assertArrayEquals(new double[]{14, 0, 3, 16, 4, 13}, east.get(2), "the 4 px sill, 2 px deep at the edge");
        // the collision's posts leave a player (9.6 px) and Sable's step-up margin (0.05 blocks a side) room
        List<double[]> collision = RailingRules.portBoxes(EAST, 17, RailingRules.PORT_COLLISION_POST);
        double opening = collision.get(1)[2] - collision.get(0)[5];
        assertTrue(opening > 0.6 * 16 + 2 * 0.05 * 16, "opening " + opening + " px");
    }

    // ------------------------------------------------------------------ the port's clear line

    /** Whether the point (block units, in the block at the origin) lies inside one of the boxes (pixels). */
    private static boolean inside(Vec3 p, List<double[]> boxes) {
        for (double[] b : boxes) {
            if (p.x * 16 > b[0] && p.x * 16 < b[3] && p.y * 16 > b[1] && p.y * 16 < b[4] && p.z * 16 > b[2] && p.z * 16 < b[5]) {
                return true;
            }
        }
        return false;
    }

    /** Whether the segment {@code a..b} (block units) passes through a box (pixels), sampled every 1/64 px. */
    private static boolean crosses(Vec3 a, Vec3 b, List<double[]> boxes) {
        int n = (int) Math.ceil(a.distanceTo(b) * 16 * 64);
        for (int i = 0; i <= n; i++) {
            if (inside(a.lerp(b, (double) i / n), boxes)) return true;
        }
        return false;
    }

    /**
     * A cannon whose master is the block west of the port fires east through it at every elevation the default config
     * allows (6 steps, −5° to 20°): the muzzle point and the line out to the port's far side miss the posts and the sill.
     * Positions are taken relative to the port's block.
     */
    @Test
    void aCannonInboardOfAPortFiresThroughItAtEveryElevation() {
        List<double[]> port = RailingRules.portBoxes(EAST, RailingRules.POST_TOP);
        for (int i = 0; i < 6; i++) {
            double e = CannonRules.elevationDegrees(i, 6, -5, 20);
            Vec3 dir = CannonRules.muzzleDirection(1, 0, e);
            Vec3 muzzle = CannonRules.muzzle(-1, 0, 0, dir);
            assertFalse(inside(muzzle, port), "the muzzle is in the port at " + e + "°");
            double toFarSide = (1.0 - muzzle.x) / dir.x;
            assertFalse(crosses(muzzle, muzzle.add(dir.scale(Math.max(0, toFarSide))), port), "the shot hits the port at " + e + "°");
            assertTrue(muzzle.y * 16 > RailingRules.SILL_TOP, "the line runs over the sill at " + e + "°");
        }
    }

    /**
     * The same cannon behind a plain railing on the east edge, with the default 1.5-block collision: vanilla's
     * projectile clip only tests the blocks the line runs through, starting with the muzzle's own block. The muzzle lies
     * inside the railing's block (x < 1, y < 1) and inside the rail's collision only at −5°, where the shot is stopped;
     * at 0° it starts on the block's outer face (x = 1) and from 5° up it starts in the block above the railing, so those
     * shots leave freely. Pinned so a change to either geometry shows.
     */
    @Test
    void aPlainRailingStopsTheCannonsShotOnlyWhenTheBarrelIsDepressed() {
        List<double[]> railing = RailingRules.boxes(set(EAST), set(), 24, 24);
        for (int i = 0; i < 6; i++) {
            double e = CannonRules.elevationDegrees(i, 6, -5, 20);
            Vec3 muzzle = CannonRules.muzzle(-1, 0, 0, CannonRules.muzzleDirection(1, 0, e));
            boolean inRailingsBlock = muzzle.x < 1 && muzzle.y < 1;
            boolean stopped = inRailingsBlock && inside(muzzle, railing);
            assertEquals(e < 0, stopped, "at " + e + "° (muzzle " + muzzle + ")");
        }
    }
}
