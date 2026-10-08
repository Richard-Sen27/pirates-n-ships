package com.richardsenger.piratesnships.combat.boarding;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * The pure rules of a boarding plank run (BRD1, docs/design.md §8.3): where it starts, how long it is and what it
 * lands on, and when it breaks. No world access; {@link BoardingPlanks} feeds it from the two ships.
 *
 * <p>A run is a straight line of plank cells in the plot of ship A (the one it is laid from), cell 0 beside the clicked
 * gunwale block. Clicking a side face starts the run in the cell beside that face, at the clicked block's height,
 * running outward; clicking the top face starts it one block up and one block out in the player's facing, so the plank
 * lies level with the top of the gunwale (rail top to rail top). The run takes the first cell (at most
 * {@code max_length} out) that lands on another ship B, every cell up to it free:
 * <ul>
 *   <li>{@link Landing#LEVEL}: B's sturdy block right under the cell (the plank rests on B's deck);</li>
 *   <li>{@link Landing#STEP_DOWN}: nothing under the cell, B's sturdy block one further down (B's deck one block
 *       lower; the plank's end hangs a block above it);</li>
 *   <li>{@link Landing#STEP_UP}: B's sturdy block in the next cell out, with room above it (B's deck one block
 *       higher; the plank ends against its edge).</li>
 * </ul>
 */
public final class PlankRun {

    /** Cells a run can have; the block state's {@code segment} property counts 0..MAX_SEGMENTS-1. */
    public static final int MAX_SEGMENTS = 4;

    /** How the far end meets the other ship. */
    public enum Landing { LEVEL, STEP_DOWN, STEP_UP }

    /** Why no run was found. */
    public enum Failure { NONE, BLOCKED, NO_DECK }

    /** The first cell of a run (plot position) and the direction it runs in (horizontal). */
    public record Start(BlockPos first, Direction direction) {
        /** Plot position of cell {@code i}. */
        public BlockPos cell(int i) {
            return first.relative(direction, i);
        }
    }

    /** A found run: {@code length} cells, the last one landing as {@code landing}; or a failure. */
    public record Result(int length, @Nullable Landing landing, Failure failure) {
        public boolean found() {
            return failure == Failure.NONE;
        }

        /** Index of the far cell. */
        public int tip() {
            return length - 1;
        }
    }

    /** What the run sees, per cell index. */
    public interface Probe {
        /** Cell {@code i} is free: replaceable in A's plot, nothing solid of the world or another ship there. */
        boolean free(int i);

        /** How cell {@code i} lands on another ship, or null when it lands on none. Only asked for free cells. */
        @Nullable Landing landing(int i);
    }

    private PlankRun() {
    }

    /**
     * Where a run laid by clicking {@code face} of the gunwale block {@code clicked} starts; {@code playerFacing} is the
     * player's horizontal facing (used for the top face). Null for the bottom face.
     */
    public static @Nullable Start start(BlockPos clicked, Direction face, Direction playerFacing) {
        if (face.getAxis().isHorizontal()) {
            return new Start(clicked.relative(face), face);
        }
        if (face == Direction.UP && playerFacing.getAxis().isHorizontal()) {
            return new Start(clicked.above().relative(playerFacing), playerFacing);
        }
        return null;
    }

    /** The shortest run of at most {@code maxLength} cells (clamped to 1..{@link #MAX_SEGMENTS}) that lands. */
    public static Result compute(int maxLength, Probe probe) {
        int max = Math.max(1, Math.min(MAX_SEGMENTS, maxLength));
        for (int i = 0; i < max; i++) {
            if (!probe.free(i)) {
                return new Result(0, null, i == 0 ? Failure.BLOCKED : Failure.NO_DECK);
            }
            Landing landing = probe.landing(i);
            if (landing != null) {
                return new Result(i + 1, landing, Failure.NONE);
            }
        }
        return new Result(0, null, Failure.NO_DECK);
    }

    /**
     * How a free plank cell lands on another ship, from that ship's blocks around it (in the other ship's frame).
     *
     * @param underSturdy     the block under the cell has a sturdy top
     * @param underSolid      the block under the cell has any collision shape
     * @param twoUnderSturdy  the block two under the cell has a sturdy top
     * @param aheadSturdy     the block in the next cell out (same height) has a sturdy top
     * @param overAheadSolid  the block above that one has a collision shape (no room to step up)
     */
    public static @Nullable Landing classify(boolean underSturdy, boolean underSolid, boolean twoUnderSturdy,
                                             boolean aheadSturdy, boolean overAheadSolid) {
        if (underSturdy) {
            return Landing.LEVEL;
        }
        if (!underSolid && twoUnderSturdy) {
            return Landing.STEP_DOWN;
        }
        if (aheadSturdy && !overAheadSolid) {
            return Landing.STEP_UP;
        }
        return null;
    }

    /** Whether segment {@code segment} of a run of {@code length} cells is its far end. */
    public static boolean isTip(int segment, int length) {
        return segment == length - 1;
    }

    /**
     * Whether a laid run breaks: the far ship is gone, or the far end's world position is farther than
     * {@code breakDistance} from the spot on the far ship it was laid onto.
     */
    public static boolean breaks(boolean farShipPresent, double farEndDistance, double breakDistance) {
        return !farShipPresent || !(farEndDistance <= breakDistance);
    }
}
