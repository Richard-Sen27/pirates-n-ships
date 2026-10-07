package com.richardsenger.piratesnships.station;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Where an occupant stands at a station: the first horizontal neighbour (north, east, south, west) of a station block
 * with room for a humanoid (two free cells) and a floor below, trying the station's blocks in order (a multi-block
 * station's master first, then its other blocks) and never one of those blocks; otherwise on top of the station block.
 * Pure, the world is queried through the predicates.
 */
public final class StationSpot {

    private StationSpot() {
    }

    /**
     * @param free  true when a cell has no collision (an occupant can stand in it)
     * @param floor true when a cell can be stood on
     */
    public static BlockPos choose(BlockPos station, Predicate<BlockPos> free, Predicate<BlockPos> floor) {
        return choose(List.of(station), free, floor);
    }

    /**
     * The spot beside one of {@code footprint} (the station block first): its neighbours are tried block by block,
     * cells of the footprint itself are skipped. On top of the first block when none has room.
     */
    public static BlockPos choose(List<BlockPos> footprint, Predicate<BlockPos> free, Predicate<BlockPos> floor) {
        for (BlockPos block : footprint) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos p = block.relative(d);
                if (!footprint.contains(p) && free.test(p) && free.test(p.above()) && floor.test(p.below())) {
                    return p;
                }
            }
        }
        return footprint.get(0).above();
    }
}
