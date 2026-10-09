package com.richardsenger.piratesnships.ship.rigging;

import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.Point;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** A block a ratline net is strung between (RL2): the chainplate and the mast hoop. */
public interface NetFitting {

    /** Distance of a fitting's ring centre from the face it is mounted on [blocks]; the models put the ring there. */
    double RING_FROM_SUPPORT = 0.3;

    /** The middle of the fitting's ring, where the net is tied (block coordinates of {@code pos}). */
    Point ring(BlockState state, BlockPos pos);
}
