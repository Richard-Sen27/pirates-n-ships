package com.richardsenger.piratesnships.ship.rigging;

import com.richardsenger.piratesnships.ship.rigging.BoardingNetRules.Cuts;
import com.richardsenger.piratesnships.ship.rigging.BoardingNetRules.Hold;
import com.richardsenger.piratesnships.ship.rigging.BoardingNetRules.Layout;
import com.richardsenger.piratesnships.ship.rigging.BoardingNetRules.Step;
import com.richardsenger.piratesnships.ship.rigging.BoardingNetRules.Verdict;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.Cell;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.Point;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RAIL5: the boarding net's pure rules ({@link BoardingNetRules}). */
class BoardingNetRulesTest {

    private static final Set<Direction> N = EnumSet.of(Direction.NORTH);
    private static final Set<Direction> S = EnumSet.of(Direction.SOUTH);
    private static final Set<Direction> E = EnumSet.of(Direction.EAST);
    private static final Set<Direction> NE = EnumSet.of(Direction.NORTH, Direction.EAST);

    @Test
    void twoChainplatesOnOneRailEdgeMakeABoardingNet() {
        Layout l = BoardingNetRules.layout(new BlockPos(1, 9, 4), N, new BlockPos(6, 9, 4), N, 8);
        assertEquals(Verdict.OK, l.verdict());
        assertEquals(Direction.NORTH, l.outboard());
        assertEquals(5, l.length());
        Layout z = BoardingNetRules.layout(new BlockPos(3, 9, 2), E, new BlockPos(3, 9, 10), NE, 8);
        assertEquals(Verdict.OK, z.verdict(), "along z on an east edge, a corner at one end");
        assertEquals(Direction.EAST, z.outboard());
    }

    @Test
    void anythingButOneRowAlongOneSharedEdgeIsRefused() {
        assertEquals(Verdict.NOT_ALONG, BoardingNetRules.layout(new BlockPos(1, 9, 4), N, new BlockPos(6, 9, 4), S, 8).verdict(), "two edges");
        assertEquals(Verdict.NOT_ALONG, BoardingNetRules.layout(new BlockPos(1, 9, 4), E, new BlockPos(6, 9, 4), E, 8).verdict(),
                "a row along x on east edges");
        assertEquals(Verdict.NOT_ALONG, BoardingNetRules.layout(new BlockPos(1, 9, 4), N, new BlockPos(6, 9, 5), N, 8).verdict(), "not one row");
        assertEquals(Verdict.NOT_ALONG, BoardingNetRules.layout(new BlockPos(1, 9, 4), N, new BlockPos(6, 10, 4), N, 8).verdict(), "two heights");
        assertEquals(Verdict.TOO_SHORT, BoardingNetRules.layout(new BlockPos(1, 9, 4), N, new BlockPos(2, 9, 4), N, 8).verdict());
        assertEquals(Verdict.TOO_LONG, BoardingNetRules.layout(new BlockPos(1, 9, 4), N, new BlockPos(10, 9, 4), N, 8).verdict());
        assertEquals(Verdict.OK, BoardingNetRules.layout(new BlockPos(1, 9, 4), N, new BlockPos(9, 9, 4), N, 8).verdict(), "eight is fine");
    }

    @Test
    void theNetsCellsAreTheRowAboveTheRailBetweenTheChainplates() {
        // chainplate rings at the block centre, 0.3 up (NetFitting.RING_FROM_SUPPORT)
        List<Cell> cells = RatlineNetRules.netCells(new Point(1.5, 10.3, 4.5), new Point(6.5, 10.3, 4.5));
        assertEquals(List.of(new Cell(2, 10, 4), new Cell(3, 10, 4), new Cell(4, 10, 4), new Cell(5, 10, 4)), cells);
    }

    @Test
    void onlyTheShipsOwnCrewPass() {
        UUID ship = UUID.randomUUID(), other = UUID.randomUUID(), owner = UUID.randomUUID();
        assertTrue(BoardingNetRules.passes(true, ship, ship, Optional.empty(), Optional.empty()), "crew of this ship");
        assertFalse(BoardingNetRules.passes(true, ship, other, Optional.empty(), Optional.of(owner)), "crew of another ship");
        assertTrue(BoardingNetRules.passes(true, ship, other, Optional.of(owner), Optional.of(owner)),
                "the owner's crew coming back from the other ship");
        assertFalse(BoardingNetRules.passes(true, ship, other, Optional.of(UUID.randomUUID()), Optional.of(owner)), "someone else's crew");
        assertFalse(BoardingNetRules.passes(true, ship, null, Optional.empty(), Optional.empty()), "crew of no ship");
        assertFalse(BoardingNetRules.passes(false, ship, ship, Optional.empty(), Optional.empty()), "not crew: a mob or a player");
        assertFalse(BoardingNetRules.passes(true, null, null, Optional.empty(), Optional.empty()), "a net ashore lets nobody through");
    }

    @Test
    void aBoarderFromOutboardIsHeldForTheHoldThenPasses() {
        int hold = 20;
        Hold h = null;
        int held = 0;
        long t = 100;
        for (; t < 100 + hold; t++) {
            Step s = BoardingNetRules.contact(h, t, true, hold);
            assertTrue(s.holds(), "tick " + t + " not held");
            held++;
            h = s.next();
        }
        assertEquals(hold, held);
        Step free = BoardingNetRules.contact(h, t, false, hold);
        assertFalse(free.holds(), "still held after the hold");
        h = free.next();
        for (int k = 1; k <= BoardingNetRules.FREE_TICKS; k++) {
            Step s = BoardingNetRules.contact(h, t + k, k % 2 == 0, hold);
            assertFalse(s.holds(), "held again while crossing at " + k);
            h = s.next();
        }
    }

    @Test
    void comingFromInboardIsNoAttemptAndTwoCellsCountOnce() {
        Step in = BoardingNetRules.contact(null, 10, false, 20);
        assertFalse(in.holds(), "a foreign mob leaving over the rail is not held");
        Step a = BoardingNetRules.contact(null, 10, true, 20);
        Step b = BoardingNetRules.contact(a.next(), 10, true, 20);
        assertTrue(a.holds() && b.holds());
        assertEquals(1, b.next().held(), "two cells in one tick counted twice");
    }

    @Test
    void aPauseStartsANewAttemptAndAHoldOfZeroHoldsNobody() {
        Hold h = BoardingNetRules.contact(null, 10, true, 20).next();
        h = BoardingNetRules.contact(h, 11, true, 20).next();
        assertEquals(2, h.held());
        Step again = BoardingNetRules.contact(h, 11 + BoardingNetRules.RESET_TICKS + 1, true, 20);
        assertTrue(again.holds());
        assertEquals(1, again.next().held(), "a new attempt starts from the beginning");
        assertFalse(BoardingNetRules.contact(null, 10, true, 0).holds(), "hold 0");
    }

    @Test
    void theWallKeepsAHeldBoarderOutboardAndOnlyItsInboardMotionGoes() {
        double w = 0.3;
        double limit = 0.5 - BoardingNetRules.WALL + w;
        assertEquals(0.0, BoardingNetRules.pushOut(limit + 0.1, w), 1e-12, "clear of the wall");
        assertEquals(0.2, BoardingNetRules.pushOut(limit - 0.2, w), 1e-12, "pushed back out");
        // outboard is +z here: moving -z is inboard
        assertArrayEquals(new double[] {0.3, 0.0}, BoardingNetRules.withoutInboard(0.3, -0.2, 0, 1), 1e-12, "inboard part removed");
        assertArrayEquals(new double[] {0.3, 0.2}, BoardingNetRules.withoutInboard(0.3, 0.2, 0, 1), 1e-12, "outboard part kept");
        assertTrue(BoardingNetRules.HELD_ACROSS > 0.25 && BoardingNetRules.HELD_ACROSS < 1, "half a cobweb across");
        assertTrue(BoardingNetRules.HELD_VERTICAL > 0.05 && BoardingNetRules.HELD_VERTICAL < 1, "half a cobweb up and down");
    }

    @Test
    void cutlassHitsCountUntilALongPause() {
        Cuts c = BoardingNetRules.hit(null, 0);
        c = BoardingNetRules.hit(c, 10);
        c = BoardingNetRules.hit(c, 20);
        assertEquals(3, c.hits());
        Cuts later = BoardingNetRules.hit(c, 20 + BoardingNetRules.CUT_RESET_TICKS + 1);
        assertEquals(1, later.hits());
    }

    @Test
    void theMeshHangsFromTheRailToABlockUpWithASaggingTop() {
        Point a = new Point(1.5, 10.3, 4.5), b = new Point(6.5, 10.3, 4.5);
        List<Point[]> mesh = BoardingNetRules.mesh(a, b, 1.0 / 3.0);
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (Point[] seg : mesh) {
            for (Point p : seg) {
                lo = Math.min(lo, p.y());
                hi = Math.max(hi, p.y());
                assertEquals(4.5, p.z(), 1e-9, "the net leaves its line");
                assertTrue(p.x() >= 1.5 - 1e-9 && p.x() <= 6.5 + 1e-9, "beyond the chainplates");
            }
        }
        assertEquals(10.0 + BoardingNetRules.FOOT, lo, 1e-9, "the foot rope lies on the rail");
        assertEquals(11.0, hi, 1e-9, "the top rope is a block up at the chainplates");
        assertEquals(1.0 - BoardingNetRules.SAG, BoardingNetRules.topAt(0.5), 1e-9, "the top sags in the middle");
        long uprights = mesh.stream().filter(s -> Math.abs(s[0].x() - s[1].x()) < 1e-9).count();
        assertEquals(16, uprights, "an upright every third of a block, both ends included");
    }
}
