package com.richardsenger.piratesnships.crew.hammock;

import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * Where a hammock's halves and supports are (HM1, docs/design.md §7.1). Pure: the caller tells what kind of support a
 * block is ({@link Support}); the world side is {@link HammockBlock#supportAt}.
 * <p>
 * A hammock lies along its horizontal {@code facing} like a bed: the {@link BedPart#FOOT} at the clicked block, the
 * {@link BedPart#HEAD} one block further in {@code facing}. It <em>hangs</em>: the block beyond each end (behind the
 * foot, in front of the head) must be a support at the same height. The blocks below may be air.
 */
public final class HammockRules {

    /** What the block beyond an end of the hammock is. */
    public enum Support {
        /** Nothing to tie the hammock to (air, a slab, a chest, ...). */
        NONE,
        /** A post: fence, wall or log ({@code #pirates_n_ships:hammock_supports}). */
        POST,
        /** Any block with a full solid face toward the hammock (a plank wall, a hull side). */
        SOLID;

        public boolean holds() {
            return this != NONE;
        }
    }

    private HammockRules() {
    }

    /** The head of a hammock whose foot is at {@code foot}. */
    public static BlockPos head(BlockPos foot, Direction facing) {
        return foot.relative(facing);
    }

    /** From a half toward the other half. */
    public static Direction towardOther(BedPart part, Direction facing) {
        return part == BedPart.FOOT ? facing : facing.getOpposite();
    }

    /** From a half outward, toward the support at its end. */
    public static Direction outward(BedPart part, Direction facing) {
        return towardOther(part, facing).getOpposite();
    }

    /** The other half of the hammock that has {@code part} at {@code pos}. */
    public static BlockPos otherHalf(BlockPos pos, BedPart part, Direction facing) {
        return pos.relative(towardOther(part, facing));
    }

    /** The foot of the hammock that has {@code part} at {@code pos}. */
    public static BlockPos foot(BlockPos pos, BedPart part, Direction facing) {
        return part == BedPart.FOOT ? pos : otherHalf(pos, part, facing);
    }

    /** The support block at the outer end of the half {@code part} at {@code pos}. */
    public static BlockPos supportOf(BlockPos pos, BedPart part, Direction facing) {
        return pos.relative(outward(part, facing));
    }

    /**
     * Whether a hammock with its foot at {@code foot} along {@code facing} can hang: {@code facing} is horizontal and
     * both ends have a support. {@code supports} gets the support block and the face of it that points at the hammock.
     */
    public static boolean canHang(BlockPos foot, Direction facing, BiFunction<BlockPos, Direction, Support> supports) {
        if (!facing.getAxis().isHorizontal()) {
            return false;
        }
        BlockPos head = head(foot, facing);
        return supports.apply(supportOf(foot, BedPart.FOOT, facing), facing).holds()
                && supports.apply(supportOf(head, BedPart.HEAD, facing), facing.getOpposite()).holds();
    }

    /**
     * Whether a half stays: the other half is there and its own end ({@link #supportOf}) is supported. Checked by both
     * halves, so losing either support or either half brings the whole hammock down.
     */
    public static boolean survives(boolean otherHalfPresent, Support ownSupport) {
        return otherHalfPresent && ownSupport.holds();
    }
}
