package com.richardsenger.piratesnships.crew.walk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.crew.walk.WalkAvoid.Meeting;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class WalkAvoidTest {

    /** A walkway at y 0 from x {@code x0} to {@code x1}, z 0..20. */
    private static Predicate<BlockPos> walkway(int x0, int x1) {
        return c -> c.getY() == 0 && c.getX() >= x0 && c.getX() <= x1 && c.getZ() >= 0 && c.getZ() <= 20;
    }

    /** The cells of a straight path along x {@code x} from z {@code z0} to {@code z1} (either way), both included. */
    private static List<BlockPos> line(int x, int z0, int z1) {
        List<BlockPos> out = new ArrayList<>();
        int step = z1 >= z0 ? 1 : -1;
        for (int z = z0; z != z1 + step; z += step) out.add(new BlockPos(x, 0, z));
        return out;
    }

    // ------------------------------------------------------------------ obstacles

    @Test
    void anOccupantHoldsItsColumnOneUpOrDown() {
        List<BlockPos> occ = List.of(new BlockPos(3, 5, 3));
        assertTrue(WalkAvoid.occupied(occ, new BlockPos(3, 5, 3)));
        assertTrue(WalkAvoid.occupied(occ, new BlockPos(3, 4, 3)));
        assertTrue(WalkAvoid.occupied(occ, new BlockPos(3, 6, 3)));
        assertFalse(WalkAvoid.occupied(occ, new BlockPos(3, 7, 3)), "two up is another deck");
        assertFalse(WalkAvoid.occupied(occ, new BlockPos(4, 5, 3)));
        assertEquals(new BlockPos(3, 5, 3), WalkAvoid.holder(occ, new BlockPos(3, 6, 3)));
    }

    @Test
    void looksTwoCellsAheadAndNeverAtItsOwnCell() {
        List<BlockPos> path = line(0, 0, 6);
        // the next node is still the walker's own cell: skipped
        assertEquals(List.of(new BlockPos(0, 0, 3), new BlockPos(0, 0, 4)), WalkAvoid.ahead(path, 2, new BlockPos(0, 0, 2)));
        assertEquals(List.of(new BlockPos(0, 0, 6)), WalkAvoid.ahead(path, 6, new BlockPos(0, 0, 5)));
        assertEquals(1, WalkAvoid.firstBlocked(WalkAvoid.ahead(path, 2, new BlockPos(0, 0, 2)), List.of(new BlockPos(0, 0, 4))));
        assertEquals(-1, WalkAvoid.firstBlocked(WalkAvoid.ahead(path, 2, new BlockPos(0, 0, 2)), List.of(new BlockPos(0, 0, 5))),
                "three cells ahead is not yet in the way");
    }

    // ------------------------------------------------------------------ the sidestep

    @Test
    void sidestepsIntoTheFreeLaneOfATwoWideWalkwayAndRejoinsBeyond() {
        List<BlockPos> path = line(0, 3, 10);
        BlockPos hand = new BlockPos(0, 0, 5);
        List<BlockPos> d = WalkAvoid.detour(new BlockPos(0, 0, 2), hand, path.subList(3, path.size()), walkway(0, 1), List.of(hand));
        assertNotNull(d);
        assertFalse(d.contains(hand), "walks through the hand: " + d);
        assertTrue(d.contains(new BlockPos(1, 0, 5)), "does not pass beside the hand: " + d);
        assertEquals(new BlockPos(0, 0, 6), d.get(d.size() - 1), "does not rejoin its path just beyond the hand: " + d);
        for (int i = 1; i < d.size(); i++) {
            assertEquals(1, d.get(i).distManhattan(d.get(i - 1)), "not one cell at a time: " + d);
        }
        // it keeps the path's direction: never a step back
        for (int i = 1; i < d.size(); i++) assertTrue(d.get(i).getZ() >= d.get(i - 1).getZ(), "steps back: " + d);
    }

    @Test
    void passesOnTheRightWhenBothSidesAreFree() {
        // heading south (+z): the walker's right is west (-x); heading north its right is east
        BlockPos hand = new BlockPos(0, 0, 5);
        List<BlockPos> south = WalkAvoid.detour(new BlockPos(0, 0, 4), hand, line(0, 6, 10), walkway(-1, 1), List.of(hand));
        assertNotNull(south);
        assertTrue(south.stream().anyMatch(c -> c.getX() == -1) && south.stream().noneMatch(c -> c.getX() == 1), "south: " + south);
        List<BlockPos> north = WalkAvoid.detour(new BlockPos(0, 0, 6), hand, line(0, 4, 0), walkway(-1, 1), List.of(hand));
        assertNotNull(north);
        assertTrue(north.stream().anyMatch(c -> c.getX() == 1) && north.stream().noneMatch(c -> c.getX() == -1), "north: " + north);
        assertEquals(Direction.EAST, Direction.NORTH.getClockWise(), "right of north is east");
    }

    @Test
    void noSidestepInAOneWideCorridorOrToAHeldRejoin() {
        BlockPos hand = new BlockPos(0, 0, 5);
        assertNull(WalkAvoid.detour(new BlockPos(0, 0, 4), hand, line(0, 6, 10), walkway(0, 0), List.of(hand)));
        assertNull(WalkAvoid.detour(new BlockPos(0, 0, 4), hand, List.of(hand), walkway(0, 1), List.of(hand)),
                "the only rejoin cell is the held one");
        // walls are never walked through: the second lane is not walkable at z 4..6
        Predicate<BlockPos> wall = walkway(0, 1).and(c -> !(c.getX() == 1 && c.getZ() >= 4 && c.getZ() <= 6));
        assertNull(WalkAvoid.detour(new BlockPos(0, 0, 3), hand, line(0, 6, 10), wall, List.of(hand)));
    }

    @Test
    void aSidestepStaysWithinItsRadius() {
        // the only way round is a long loop: refused
        Predicate<BlockPos> loop = c -> c.getY() == 0 && (c.getX() == 0 && c.getZ() >= 0 && c.getZ() <= 20
                || c.getZ() == 0 && c.getX() >= 0 && c.getX() <= 9 || c.getX() == 9 && c.getZ() >= 0 && c.getZ() <= 20
                || c.getZ() == 20 && c.getX() >= 0 && c.getX() <= 9);
        BlockPos hand = new BlockPos(0, 0, 10);
        assertNull(WalkAvoid.detour(new BlockPos(0, 0, 9), hand, line(0, 11, 15), loop, List.of(hand)));
    }

    // ------------------------------------------------------------------ head-on

    @Test
    void theLowerIdYieldsTheHigherHolds() {
        assertEquals(Meeting.YIELD, WalkAvoid.meeting(5, 9, true, true));
        assertEquals(Meeting.HOLD, WalkAvoid.meeting(9, 5, true, true));
        assertEquals(Meeting.NONE, WalkAvoid.meeting(5, 9, false, true), "a standing hand is no meeting");
        assertEquals(Meeting.NONE, WalkAvoid.meeting(5, 9, true, false), "a walker going the same way is no meeting");
        // stable: of any pair exactly one yields
        for (int a = 0; a < 6; a++) {
            for (int b = 0; b < 6; b++) {
                if (a == b) continue;
                boolean ay = WalkAvoid.meeting(a, b, true, true) == Meeting.YIELD;
                boolean by = WalkAvoid.meeting(b, a, true, true) == Meeting.YIELD;
                assertTrue(ay ^ by, a + " and " + b);
            }
        }
    }

    @Test
    void theYielderBacksOutOfAOneWideGangwayIntoTheNearestNiche() {
        // gangway x 0, z 0..20, with a niche at (1, 0, 1); the other walker comes from z 6 towards z 0
        Predicate<BlockPos> gangway = walkway(0, 0).or(c -> c.equals(new BlockPos(1, 0, 1)));
        BlockPos other = new BlockPos(0, 0, 6);
        List<BlockPos> way = line(0, 5, 0);
        List<BlockPos> r = WalkAvoid.refuge(new BlockPos(0, 0, 4), other, way, gangway, List.of(other));
        assertNotNull(r);
        assertEquals(List.of(new BlockPos(0, 0, 3), new BlockPos(0, 0, 2), new BlockPos(0, 0, 1), new BlockPos(1, 0, 1)), r);
    }

    @Test
    void theYielderStepsAsideInATwoWideWalkwayOnItsRight() {
        BlockPos other = new BlockPos(0, 0, 6);
        // the yielder at z 4 faces the other (south): its right is west
        List<BlockPos> r = WalkAvoid.refuge(new BlockPos(0, 0, 4), other, line(0, 5, 0), walkway(-1, 1), List.of(other));
        assertEquals(List.of(new BlockPos(-1, 0, 4)), r);
    }

    @Test
    void noRefugeInADeadEndAndNoneNeededOffTheWay() {
        BlockPos other = new BlockPos(0, 0, 6);
        assertNull(WalkAvoid.refuge(new BlockPos(0, 0, 4), other, line(0, 5, 0), walkway(0, 0), List.of(other)));
        assertEquals(List.of(), WalkAvoid.refuge(new BlockPos(1, 0, 4), other, line(0, 5, 0), walkway(0, 1), List.of(other)));
    }

    @Test
    void theOtherHasPassedWhenItsWayNoLongerComesNear() {
        BlockPos niche = new BlockPos(1, 0, 1);
        assertFalse(WalkAvoid.passed(niche, line(0, 3, 0)));
        assertFalse(WalkAvoid.passed(niche, line(0, 2, 0)));
        assertTrue(WalkAvoid.passed(niche, List.of(new BlockPos(0, 0, -1), new BlockPos(0, 0, -2))));
        assertTrue(WalkAvoid.passed(niche, List.of()));
    }

    // ------------------------------------------------------------------ waiting and the timeout

    @Test
    void waitsItsTicksUnlessTheWayClears() {
        assertFalse(WalkAvoid.waitOver(100, 119, 20, false));
        assertTrue(WalkAvoid.waitOver(100, 120, 20, false));
        assertTrue(WalkAvoid.waitOver(100, 101, 20, true));
    }

    @Test
    void sidestepsDoNotCountTowardsTheTimeoutButWaitingDoes() {
        assertEquals(70, WalkAvoid.timeoutElapsed(100, 30, 200));
        assertEquals(100, WalkAvoid.timeoutElapsed(100, 0, 200), "waiting and walking count in full");
        assertEquals(300, WalkAvoid.timeoutElapsed(500, 1000, 200), "credit capped at the timeout");
        assertEquals(0, WalkAvoid.timeoutElapsed(10, 30, 200));
    }
}
