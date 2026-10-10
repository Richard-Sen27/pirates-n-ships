package com.richardsenger.piratesnships.ship.decor;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.jetbrains.annotations.Nullable;

/**
 * Which sea cots join into one wide berth (ART20, pure: no world access). The rule follows the name board's row
 * (NAME2, {@link NameBoardRow}): a cot half joins the half beside it, across its {@code facing}, when that is a sea cot
 * half (of any wood) with the same {@code facing} and the same {@code part}, so two cots placed side by side join head
 * to head and foot to foot, and a row of any length joins pairwise. A cot beside a crosswise cot, or one shifted by a
 * block along its length (its head beside the other's foot), does not join. Each cot stays its own bed: joining only
 * changes the look (the inner side rails and posts drop, mattress, blanket, pillow and the head- and footboard run
 * across) and the outline.
 *
 * <p><b>Sides.</b> Seen from the cot's foot looking along {@code facing} (towards the head), {@link #left} is
 * {@code facing} turned counter-clockwise seen from above and {@link #right} clockwise; the north-facing models put
 * the left side at x 0 (west).
 */
public final class SeaCotBerth {

    private SeaCotBerth() {
    }

    /** The cot's left side: {@code facing} turned counter-clockwise (north: west). */
    public static Direction left(Direction facing) {
        return facing.getCounterClockWise();
    }

    /** The cot's right side: {@code facing} turned clockwise (north: east). */
    public static Direction right(Direction facing) {
        return facing.getClockWise();
    }

    /**
     * Whether a cot half ({@code facing}, {@code part}) joins its neighbour; {@code otherFacing} and {@code otherPart}
     * are the neighbour's, both {@code null} when the neighbour is no sea cot.
     */
    public static boolean joins(Direction facing, BedPart part, @Nullable Direction otherFacing, @Nullable BedPart otherPart) {
        return otherFacing == facing && otherPart == part;
    }
}
