package com.richardsenger.piratesnships.station;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Where an occupant stands at a station block: the first horizontal neighbour (north, east, south, west) with room
 * for a humanoid (two free cells) and a floor below; otherwise on top of the station. Pure, the world is queried
 * through the predicates.
 */
public final class StationSpot {

    private StationSpot() {
    }

    /**
     * @param free  true when a cell has no collision (an occupant can stand in it)
     * @param floor true when a cell can be stood on
     */
    public static BlockPos choose(BlockPos station, Predicate<BlockPos> free, Predicate<BlockPos> floor) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos p = station.relative(d);
            if (free.test(p) && free.test(p.above()) && floor.test(p.below())) {
                return p;
            }
        }
        return station.above();
    }
}
