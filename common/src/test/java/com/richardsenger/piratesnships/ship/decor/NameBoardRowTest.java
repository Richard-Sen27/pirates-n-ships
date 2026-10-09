package com.richardsenger.piratesnships.ship.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which name boards join into one board and who is the row's master (NAME2, pure). */
class NameBoardRowTest {

    /** A tiny world of name boards. */
    private static final class Boards implements NameBoardRow.Lookup {
        final Map<BlockPos, Direction> at = new HashMap<>();

        Boards put(int x, int y, int z, Direction facing) {
            at.put(new BlockPos(x, y, z), facing);
            return this;
        }

        Boards remove(int x, int y, int z) {
            at.remove(new BlockPos(x, y, z));
            return this;
        }

        @Override
        public Direction facingAt(int x, int y, int z) {
            return at.get(new BlockPos(x, y, z));
        }

        NameBoardRow.Place find(int x, int y, int z) {
            return NameBoardRow.find(this, x, y, z);
        }
    }

    /** A row of {@code n} boards facing north along x from {@code x0}. */
    private static Boards northRow(int x0, int n) {
        Boards b = new Boards();
        for (int i = 0; i < n; i++) b.put(x0 + i, 5, 0, Direction.NORTH);
        return b;
    }

    private static void assertPlace(NameBoardRow.Place p, int index, int length, int mx, int my, int mz) {
        assertEquals(index, p.index(), "index of " + p);
        assertEquals(length, p.length(), "length of " + p);
        assertEquals(new BlockPos(mx, my, mz), new BlockPos(p.masterX(), p.masterY(), p.masterZ()), "master of " + p);
    }

    @Test
    void theViewersLeftAndRight() {
        // a viewer north of a north-facing board looks south: east is on the left, west on the right
        assertEquals(Direction.EAST, NameBoardRow.left(Direction.NORTH));
        assertEquals(Direction.WEST, NameBoardRow.right(Direction.NORTH));
        // the other side of the hull reads the other way round
        assertEquals(Direction.WEST, NameBoardRow.left(Direction.SOUTH));
        assertEquals(Direction.EAST, NameBoardRow.right(Direction.SOUTH));
        assertEquals(Direction.SOUTH, NameBoardRow.left(Direction.EAST));
        assertEquals(Direction.NORTH, NameBoardRow.left(Direction.WEST));
        for (Direction d : Direction.Plane.HORIZONTAL) {
            assertEquals(NameBoardRow.left(d).getOpposite(), NameBoardRow.right(d));
        }
    }

    @Test
    void aRowOfThreeHasOneMasterAtTheViewersLeft() {
        Boards b = northRow(0, 3);
        // facing north the left is east: x 2 is the master
        assertPlace(b.find(2, 5, 0), 0, 3, 2, 5, 0);
        assertPlace(b.find(1, 5, 0), 1, 3, 2, 5, 0);
        assertPlace(b.find(0, 5, 0), 2, 3, 2, 5, 0);
        assertTrue(b.find(2, 5, 0).master());
        assertFalse(b.find(2, 5, 0).joinedLeft());
        assertTrue(b.find(2, 5, 0).joinedRight());
        assertTrue(b.find(1, 5, 0).joinedLeft() && b.find(1, 5, 0).joinedRight());
        assertFalse(b.find(0, 5, 0).joinedRight());
    }

    @Test
    void theOtherSideOfTheHullMirrorsTheOrder() {
        // the same three blocks facing south: the left is west, so x 0 is the master
        Boards b = new Boards().put(0, 5, 0, Direction.SOUTH).put(1, 5, 0, Direction.SOUTH).put(2, 5, 0, Direction.SOUTH);
        assertPlace(b.find(0, 5, 0), 0, 3, 0, 5, 0);
        assertPlace(b.find(2, 5, 0), 2, 3, 0, 5, 0);
        // along z: facing east the left is south (+z), facing west it is north (-z)
        Boards east = new Boards().put(4, 5, 0, Direction.EAST).put(4, 5, 1, Direction.EAST);
        assertPlace(east.find(4, 5, 0), 1, 2, 4, 5, 1);
        Boards west = new Boards().put(4, 5, 0, Direction.WEST).put(4, 5, 1, Direction.WEST);
        assertPlace(west.find(4, 5, 1), 1, 2, 4, 5, 0);
    }

    @Test
    void aBoardOnItsOwnIsARowOfOne() {
        Boards b = new Boards().put(0, 0, 0, Direction.EAST);
        assertPlace(b.find(0, 0, 0), 0, 1, 0, 0, 0);
        assertFalse(b.find(0, 0, 0).joinedLeft() || b.find(0, 0, 0).joinedRight());
    }

    @Test
    void aBoardAtEitherEndExtendsTheRow() {
        Boards b = northRow(0, 3);
        b.put(-1, 5, 0, Direction.NORTH); // on the right
        assertPlace(b.find(-1, 5, 0), 3, 4, 2, 5, 0);
        assertPlace(b.find(2, 5, 0), 0, 4, 2, 5, 0);
        b.put(3, 5, 0, Direction.NORTH); // on the left: the new master
        assertPlace(b.find(3, 5, 0), 0, 5, 3, 5, 0);
        assertPlace(b.find(2, 5, 0), 1, 5, 3, 5, 0);
        assertPlace(b.find(-1, 5, 0), 4, 5, 3, 5, 0);
    }

    @Test
    void removingTheMiddleBoardSplitsTheRow() {
        Boards b = northRow(0, 5).remove(2, 5, 0);
        assertPlace(b.find(4, 5, 0), 0, 2, 4, 5, 0);
        assertPlace(b.find(3, 5, 0), 1, 2, 4, 5, 0);
        assertPlace(b.find(1, 5, 0), 0, 2, 1, 5, 0);
        assertPlace(b.find(0, 5, 0), 1, 2, 1, 5, 0);
        // removing the master hands the row to the next board
        Boards c = northRow(0, 3).remove(2, 5, 0);
        assertPlace(c.find(1, 5, 0), 0, 2, 1, 5, 0);
    }

    @Test
    void onlyTheSameFaceAtTheSameHeightJoins() {
        // a vertical stack does not join
        Boards stack = new Boards().put(0, 5, 0, Direction.NORTH).put(0, 6, 0, Direction.NORTH);
        assertPlace(stack.find(0, 5, 0), 0, 1, 0, 5, 0);
        assertPlace(stack.find(0, 6, 0), 0, 1, 0, 6, 0);
        // side by side but facing another way
        Boards turned = new Boards().put(0, 5, 0, Direction.NORTH).put(1, 5, 0, Direction.SOUTH);
        assertPlace(turned.find(0, 5, 0), 0, 1, 0, 5, 0);
        // one block apart along the face
        Boards gap = new Boards().put(0, 5, 0, Direction.NORTH).put(2, 5, 0, Direction.NORTH);
        assertPlace(gap.find(0, 5, 0), 0, 1, 0, 5, 0);
        // in front of each other (along the facing): not along the face
        Boards front = new Boards().put(0, 5, 0, Direction.NORTH).put(0, 5, -1, Direction.NORTH);
        assertPlace(front.find(0, 5, -1), 0, 1, 0, 5, -1);
        // the joins as the block state sees them
        Boards row = northRow(0, 2);
        assertTrue(NameBoardRow.joins(row, 0, 5, 0, Direction.NORTH, Direction.EAST));
        assertFalse(NameBoardRow.joins(row, 0, 5, 0, Direction.NORTH, Direction.WEST));
        assertFalse(NameBoardRow.joins(row, 0, 5, 0, Direction.SOUTH, Direction.EAST));
    }

    @Test
    void longRowsAreFollowedToTheScanLimit() {
        int n = NameBoardRow.SCAN_LIMIT + 1;
        Boards b = northRow(0, n);
        assertPlace(b.find(0, 5, 0), n - 1, n, n - 1, 5, 0);
        assertPlace(b.find(n - 1, 5, 0), 0, n, n - 1, 5, 0);
    }

    @Test
    void findNeedsABoard() {
        assertThrows(IllegalArgumentException.class, () -> new Boards().find(0, 0, 0));
    }
}
