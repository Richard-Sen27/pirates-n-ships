package com.richardsenger.piratesnships.ship.decor.railing;

import com.richardsenger.piratesnships.combat.cannon.CannonRules;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static net.minecraft.core.Direction.EAST;
import static net.minecraft.core.Direction.NORTH;
import static net.minecraft.core.Direction.SOUTH;
import static net.minecraft.core.Direction.WEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RAIL4's pure rules: the bulwark reuses the railing's edge and post rules (a run goes on only into its own kind), its
 * shapes, the gun port's shapes and lid rule, and the barrel line of a carriage cannon behind a bulwark and behind a gun
 * port at every default elevation.
 */
class BulwarkRulesTest {

    private static Set<Direction> set(Direction... ds) {
        return ds.length == 0 ? EnumSet.noneOf(Direction.class) : EnumSet.of(ds[0], ds);
    }

    private static double top(List<double[]> boxes) {
        return boxes.stream().mapToDouble(b -> b[4]).max().orElse(0);
    }

    // ------------------------------------------------------------------ the run

    @Test
    void aRunGoesOnOnlyIntoItsOwnKind() {
        assertTrue(BulwarkRules.continues(BulwarkRules.Kind.BULWARK, BulwarkRules.Kind.BULWARK, true));
        assertTrue(BulwarkRules.continues(BulwarkRules.Kind.RAILING, BulwarkRules.Kind.RAILING, true));
        assertFalse(BulwarkRules.continues(BulwarkRules.Kind.BULWARK, BulwarkRules.Kind.RAILING, true), "a wall ends against a rail");
        assertFalse(BulwarkRules.continues(BulwarkRules.Kind.RAILING, BulwarkRules.Kind.BULWARK, true), "a rail ends against a wall");
        assertFalse(BulwarkRules.continues(BulwarkRules.Kind.BULWARK, BulwarkRules.Kind.BULWARK, false), "a bulwark on another edge");
        assertFalse(BulwarkRules.continues(BulwarkRules.Kind.NONE, BulwarkRules.Kind.NONE, true));
    }

    /**
     * The railing's post rule with the bulwark's neighbours: a bulwark on the north edge between another bulwark (west)
     * and a railing (east) keeps only its east stanchion; a corner of two edges is one stanchion as on a railing.
     */
    @Test
    void theStanchionsFollowTheRailingsPostRule() {
        RailingRules.Neighbours n = (towards, edge) -> {
            BulwarkRules.Kind k = towards == WEST ? BulwarkRules.Kind.BULWARK : towards == EAST ? BulwarkRules.Kind.RAILING : BulwarkRules.Kind.NONE;
            return BulwarkRules.continues(BulwarkRules.Kind.BULWARK, k, edge == NORTH);
        };
        assertEquals(set(EAST), RailingRules.posts(set(NORTH), n), "only the north-east stanchion (against the railing)");
        assertEquals(set(EAST, SOUTH), RailingRules.posts(set(NORTH, EAST), n), "the corner and the east wall's south end");
    }

    // ------------------------------------------------------------------ shapes

    @Test
    void theBulwarksShapes() {
        List<double[]> outline = BulwarkRules.boxes(set(NORTH), set(NORTH), BulwarkRules.WALL_TOP, BulwarkRules.STANCHION_TOP, true);
        assertEquals(3, outline.size(), "wall, cap rail, stanchion");
        assertEquals(18.0, top(outline));
        double[] cap = outline.get(1);
        assertEquals(4.0, cap[5] - cap[2], "the cap rail is 4 px deep, 2 px proud of the 2 px wall");
        assertEquals(14.0, cap[1]);
        List<double[]> collision = BulwarkRules.boxes(set(EAST, SOUTH), set(SOUTH), 24, 24, false);
        assertEquals(3, collision.size(), "two walls and a stanchion, no cap");
        assertEquals(24.0, top(collision));
        // the east wall turned: x 14..16
        assertEquals(14.0, collision.stream().filter(b -> b[3] == 16 && b[2] == 0 && b[5] == 16).findFirst().orElseThrow()[0]);
    }

    /** Open, the gun port leaves a 12 px gap between its 2 px collision jambs above the sill; shut, it is a wall. */
    @Test
    void theGunPortsShapes() {
        List<double[]> open = BulwarkRules.portBoxes(NORTH, true, true, 24);
        assertEquals(3, open.size());
        for (double[] b : open) {
            boolean jamb = b[3] <= BulwarkRules.COLLISION_JAMB || b[0] >= 16 - BulwarkRules.COLLISION_JAMB;
            assertTrue(jamb || b[4] <= BulwarkRules.SILL_TOP, "a box in the opening: " + java.util.Arrays.toString(b));
        }
        List<double[]> shut = BulwarkRules.portBoxes(NORTH, false, true, 24);
        assertEquals(1, shut.size());
        assertEquals(16.0, shut.get(0)[3] - shut.get(0)[0]);
        assertEquals(24.0, shut.get(0)[4]);
        // the drawn notch: jambs x 0..3 and 13..16, the lid only while shut
        List<double[]> drawnOpen = BulwarkRules.portBoxes(NORTH, true, false, 16);
        List<double[]> drawnShut = BulwarkRules.portBoxes(NORTH, false, false, 16);
        assertEquals(drawnOpen.size() + 1, drawnShut.size());
        for (double[] b : drawnOpen) {
            boolean inNotch = b[0] < BulwarkRules.OPENING_RIGHT && b[3] > BulwarkRules.OPENING_LEFT && b[4] > BulwarkRules.SILL_TOP;
            assertFalse(inNotch, "a drawn box in the open notch: " + java.util.Arrays.toString(b));
        }
    }

    // ------------------------------------------------------------------ the lid

    @Test
    void theLidOpensForACannonFacingItOrAPlayer() {
        assertFalse(BulwarkRules.lidOpen(false, false));
        assertTrue(BulwarkRules.lidOpen(true, false), "a cannon inboard opens it");
        assertTrue(BulwarkRules.lidOpen(false, true), "propped open");
        assertTrue(BulwarkRules.lidOpen(true, true));
        assertTrue(BulwarkRules.cannonFacesPort(EAST, EAST));
        assertFalse(BulwarkRules.cannonFacesPort(EAST, WEST), "a cannon pointing away");
        assertFalse(BulwarkRules.cannonFacesPort(EAST, NORTH), "a cannon pointing along the wall");
    }

    // ------------------------------------------------------------------ the barrel

    /** Whether the segment {@code a}..{@code b} (blocks) crosses any of {@code boxes} (pixels of the block at the origin). */
    private static boolean crosses(Vec3 a, Vec3 b, List<double[]> boxes) {
        for (double[] p : boxes) {
            AABB box = new AABB(p[0] / 16, p[1] / 16, p[2] / 16, p[3] / 16, p[4] / 16, p[5] / 16);
            if (box.contains(a) || box.clip(a, b).isPresent()) return true;
        }
        return false;
    }

    /**
     * A cannon whose master is the block west of the bulwark (east edge) at the six default elevations (−5° to 20°):
     * the barrel's line from its pivot to its muzzle runs through the bulwark's collision (1.5 blocks) every time, which
     * is what {@code CannonService#barrelBlocked} tests; it crosses the wall's inboard face 12 to 22 px above the deck.
     * Behind an open gun port the same line misses the jambs and the sill every time.
     */
    @Test
    void theBarrelRunsIntoABulwarkAndThroughAGunPortAtEveryElevation() {
        List<double[]> bulwark = BulwarkRules.boxes(set(EAST), set(), 24, 24, false);
        List<double[]> port = BulwarkRules.portBoxes(EAST, true, true, 24);
        for (int i = 0; i < 6; i++) {
            double e = CannonRules.elevationDegrees(i, 6, -5, 20);
            Vec3 dir = CannonRules.muzzleDirection(1, 0, e);
            Vec3 pivot = CannonRules.pivot(-1, 0, 0);
            Vec3 muzzle = CannonRules.muzzle(-1, 0, 0, dir);
            assertTrue(crosses(pivot, muzzle, bulwark), "the barrel clears the bulwark at " + e + "°");
            assertFalse(crosses(pivot, muzzle, port), "the barrel hits the open gun port at " + e + "°");
            double atWall = (pivot.y + (14 / 16.0 - pivot.x) * dir.y / dir.x) * 16;
            assertTrue(atWall > 11.5 && atWall < 22.5, "the barrel crosses the wall at " + atWall + " px");
        }
    }
}
