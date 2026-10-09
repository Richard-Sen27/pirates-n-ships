package com.richardsenger.piratesnships.ship.decor.flag;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A block a flagpole can stand in (FLG3, docs/design.md §4.7 "Flag pedestal and the pole on the crow's nest"): the
 * flag pedestal and the crow's nest. A footing has the {@link #POLE} property, true while a flagpole stands on it,
 * which swaps its model for the one with the pole stub and the halyard's cleat ({@link FlagpoleFoot}); when the
 * footing is broken (not when Sable moves it, see {@link #dropPole}) the pole on it comes down.
 */
public interface FlagpoleFooting {

    /** Whether a flagpole stands on the footing (the socketed model with the cleat). */
    BooleanProperty POLE = BooleanProperty.create("pole");

    /** Which footing this is ({@link FlagpoleFoot.Support#PEDESTAL} or {@link FlagpoleFoot.Support#CROWS_NEST}). */
    FlagpoleFoot.Support footing();

    /** {@code state} with {@link #POLE} set from the block above {@code pos}. */
    static BlockState withPole(BlockState state, BlockGetter level, BlockPos pos) {
        if (state == null || !state.hasProperty(POLE)) return state;
        return state.setValue(POLE, FlagpoleRun.isPole(level, pos.above()));
    }

    /**
     * The footing at {@code pos} was broken: the flagpole blocks standing on it break too, from the head down, and drop
     * themselves and the flag. Nothing happens while Sable moves the blocks into or out of a ship ({@code moved}: its
     * {@code moveBlocks} replaces the footing with {@code isMoving} set, and the pole moves with it).
     */
    static void dropPole(Level level, BlockPos pos, boolean moved) {
        if (level.isClientSide || moved) return;
        int height = FlagpoleRun.countAbove(level, pos, FlagpoleRun.WALK_LIMIT);
        for (int dy = height; dy >= 1; dy--) level.destroyBlock(pos.above(dy), true);
    }
}
