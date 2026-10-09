package com.richardsenger.piratesnships.ship.decor;

import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Which name boards form one board (NAME2, docs/design.md §4.8 "Ship name boards"), pure: no world access, the world
 * is read through a {@link Lookup}. The rule follows the map tiles' (MAP3, {@code chart.tile.BoardRules}) for one row.
 *
 * <p><b>Joining.</b> Boards with the same {@code facing} (the direction the board's front looks, away from the hull)
 * that stand side by side at the same height, adjacent along the face (the axis across {@code facing}), join into one
 * board. Nothing else joins: a board above or below another, a board on the same face one block apart, a board facing
 * another way.
 *
 * <p><b>Reading order.</b> Seen from in front of the board (the viewer looks against {@code facing}), the viewer's left
 * is {@link #left} ({@code facing} turned clockwise seen from above) and the right is {@link #right} (counter-clockwise),
 * the same "right" as a map tile on a wall. So on the two sides of a hull the rows run in opposite world directions
 * and both read left to right from outside. The leftmost board is the row's <b>master</b> (index 0); it draws the name
 * across the whole row and knows the row's length; every other board knows its index, so its master is {@code index}
 * steps to its left. A board added at either end extends the row (at the left end it becomes the new master), a
 * removed board splits it into two rows.
 *
 * <p>The search stops after {@link #SCAN_LIMIT} boards in either direction, so a row is at most
 * {@code 2 * SCAN_LIMIT + 1} long as seen from any of its boards.
 */
public final class NameBoardRow {

    /** How far the row is followed from a board in each direction. */
    public static final int SCAN_LIMIT = 64;

    /** The facing of the name board at a block position, or {@code null} for anything else. */
    @FunctionalInterface
    public interface Lookup {
        @Nullable Direction facingAt(int x, int y, int z);
    }

    /**
     * A board's place in its row: its {@code index} from the left (0 = the master), the row's {@code length} and the
     * master's position.
     */
    public record Place(int index, int length, int masterX, int masterY, int masterZ) {

        public boolean master() {
            return index == 0;
        }

        /** Whether a board continues the row on the viewer's left. */
        public boolean joinedLeft() {
            return index > 0;
        }

        /** Whether a board continues the row on the viewer's right. */
        public boolean joinedRight() {
            return index < length - 1;
        }
    }

    private NameBoardRow() {
    }

    /** The viewer's left of a board facing {@code facing}: the way to the row's master. */
    public static Direction left(Direction facing) {
        return facing.getClockWise();
    }

    /** The viewer's right of a board facing {@code facing}: the way the row (and the name) runs. */
    public static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    /** Whether the block at {@code (x, y, z) + step} is a board with {@code facing}. */
    private static boolean same(Lookup lookup, Direction facing, int x, int y, int z, Direction step, int n) {
        return facing == lookup.facingAt(x + step.getStepX() * n, y, z + step.getStepZ() * n);
    }

    /** Whether the neighbour on the viewer's {@code side} ({@link #left} or {@link #right}) joins a board facing {@code facing}. */
    public static boolean joins(Lookup lookup, int x, int y, int z, Direction facing, Direction side) {
        return same(lookup, facing, x, y, z, side, 1);
    }

    /**
     * The place of the board at {@code (x, y, z)} in its row; the position must be a board ({@code lookup} returns its
     * facing).
     */
    public static Place find(Lookup lookup, int x, int y, int z) {
        Direction facing = lookup.facingAt(x, y, z);
        if (facing == null || facing.getAxis() == Direction.Axis.Y) {
            throw new IllegalArgumentException("no name board at " + x + "," + y + "," + z);
        }
        Direction l = left(facing);
        Direction r = right(facing);
        int index = 0;
        while (index < SCAN_LIMIT && same(lookup, facing, x, y, z, l, index + 1)) index++;
        int after = 0;
        while (after < SCAN_LIMIT && same(lookup, facing, x, y, z, r, after + 1)) after++;
        return new Place(index, index + after + 1, x + l.getStepX() * index, y, z + l.getStepZ() * index);
    }
}
