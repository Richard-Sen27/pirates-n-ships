package com.richardsenger.piratesnships.sailing.rope;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A block that ropes tie onto (RP1, docs/design.md §5.2): the cleat ({@code sailing.block.CleatBlock}) and the mooring
 * ring ({@code combat.grapple.MooringRingBlock}). One interface serves the three kinds of rope:
 * <ul>
 *   <li><b>Sail stays</b> (F5b) run between two cleats ({@link Kind#CLEAT}).</li>
 *   <li><b>The grappling rope</b> (GR1): a flying hook passing close to an anchor latches onto its {@link #anchorPoint},
 *       and the rope's near end can be tied off on it. The grapple talks to this interface only, and gates each
 *       {@link Kind} by its own config.</li>
 *   <li><b>Decorative rope lines</b> ({@link RopeLines}): between any two anchors on one body; the rope ends live in
 *       the anchors' {@link RopeAnchorBlockEntity}.</li>
 * </ul>
 * Anchors are mounted like levers ({@link FaceAttachedHorizontalDirectionalBlock}): the tie point is {@link #anchorInset}
 * blocks from the block center toward the support ({@link RopeAnchorMath}), and rope ends are stored in the frame of
 * the horizontal facing, so they turn with the block.
 */
public interface RopeAnchor {

    /** What kind of anchor a block is (the grapple's config is per kind). */
    enum Kind { CLEAT, RING }

    Kind anchorKind();

    /** Distance of the tie point from the block center toward the support [blocks]. */
    double anchorInset();

    /** The tie point of the anchor in {@code state} at {@code pos}, in the coordinates of {@code pos} (plot coordinates on a ship). */
    default Vec3 anchorPoint(BlockState state, BlockPos pos) {
        double[] p = state.hasProperty(FaceAttachedHorizontalDirectionalBlock.FACE)
                ? RopeAnchorMath.point(state.getValue(FaceAttachedHorizontalDirectionalBlock.FACE),
                state.getValue(FaceAttachedHorizontalDirectionalBlock.FACING), anchorInset())
                : new double[] {0.5, 0.5, 0.5};
        return new Vec3(pos.getX() + p[0], pos.getY() + p[1], pos.getZ() + p[2]);
    }

    /** The facing as clockwise quarter turns ({@code Direction#get2DDataValue}), the frame rope ends are stored in. */
    default int quarterTurns(BlockState state) {
        return state.hasProperty(FaceAttachedHorizontalDirectionalBlock.FACING)
                ? state.getValue(FaceAttachedHorizontalDirectionalBlock.FACING).get2DDataValue() : Direction.SOUTH.get2DDataValue();
    }

    /** The anchor in {@code state}, or null. */
    static @Nullable RopeAnchor of(BlockState state) {
        return state.getBlock() instanceof RopeAnchor a ? a : null;
    }

    static boolean isAnchor(BlockGetter level, @Nullable BlockPos pos) {
        return pos != null && level.getBlockState(pos).getBlock() instanceof RopeAnchor;
    }

    /** The tie point of the anchor at {@code pos}, or the block center when there is none. */
    static Vec3 point(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof RopeAnchor a ? a.anchorPoint(state, pos) : Vec3.atCenterOf(pos);
    }
}
