package com.richardsenger.piratesnships.station;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A block that is a station (docs/design.md §6). Crew can be assigned to it; on a ship it is found through its plot
 * position ({@link StationRef}). When the block is removed it must call {@link Stations#onStationRemoved} (e.g. from
 * {@code onRemove}), which frees the station and removes its seat.
 *
 * <p>A station that spans several blocks (the two-block cannon) is one station at its master block: a click on any of
 * its blocks resolves to {@link #stationPos} ({@link Stations#at}), and the seat is chosen beside any of its
 * {@link #footprint} blocks.
 */
public interface StationBlock {

    StationKind<?> stationKind();

    /** The block that is the station for a click on {@code pos} with {@code state}: itself, or a multi-block's master. */
    default BlockPos stationPos(BlockState state, BlockPos pos) {
        return pos;
    }

    /**
     * Every block the station at {@code stationPos} (its {@link #stationPos}) occupies, the station block first. The
     * seat stands beside one of them, never on one of them.
     */
    default List<BlockPos> footprint(BlockState state, BlockPos stationPos) {
        return List.of(stationPos);
    }

    /**
     * Where the seat of the station at {@code stationPos} stands (plot position, the occupant's feet at its bottom), or
     * null to choose a spot beside the {@link #footprint} ({@link StationSpot}). The crow's nest (CN1) seats its lookout
     * inside its own block.
     */
    default @Nullable BlockPos seatSpot(BlockState state, BlockPos stationPos) {
        return null;
    }

    /** Items implementing this skip the block's own use action so that their {@code useOn} runs (the captain's whistle). */
    interface Tool {
    }
}
