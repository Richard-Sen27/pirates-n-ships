package com.richardsenger.piratesnships.crew.hammock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A two-half bed a player sleeps in on a {@link HammockSeat} (SLP1, docs/design.md §7.1): the hammock everywhere, the
 * sea cot ({@code ship.decor.SeaCotBlock}) on an assembled ship. Both are laid out like a vanilla bed: the
 * {@link BedPart#FOOT} half, then the {@link BedPart#HEAD} half one block further in {@code FACING}
 * ({@link HorizontalDirectionalBlock#FACING}, property {@link BlockStateProperties#BED_PART}). The sleeper's sleeping
 * position is the head half, as at a vanilla bed, so vanilla draws the body from the head over the foot half.
 */
public interface Bunk {

    /** Height of the sleeper's feet above the bottom of the head half, in pixels (vanilla's bed: 11). */
    double lyingHeight();

    /** Whether lying down needs headroom above both halves, like vanilla's bed ({@code OBSTRUCTED}). */
    boolean needsHeadroom();

    /**
     * Whether a player sleeping in this bunk at {@code pos} lies on a {@link HammockSeat} (our ship sleeping) rather than
     * in vanilla's bed handling: always in a hammock, in the cot only on an assembled ship.
     */
    boolean sleepsOnSeat(Level level, BlockPos pos);

    /** The foot half of the bunk whose half {@code state} is at {@code pos}. */
    static BlockPos foot(BlockState state, BlockPos pos) {
        return state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT ? pos.immutable()
                : pos.relative(facing(state).getOpposite());
    }

    /** The head half of the bunk whose half {@code state} is at {@code pos}. */
    static BlockPos head(BlockState state, BlockPos pos) {
        return state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD ? pos.immutable()
                : pos.relative(facing(state));
    }

    /** Whether {@code state} is the foot half of a bunk. */
    static boolean isFoot(BlockState state) {
        return state.getBlock() instanceof Bunk && state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT;
    }

    /** The direction from foot to head of the bunk half {@code state}. */
    static Direction facing(BlockState state) {
        return state.getValue(HorizontalDirectionalBlock.FACING);
    }
}
