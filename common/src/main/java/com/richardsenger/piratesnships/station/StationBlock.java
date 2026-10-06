package com.richardsenger.piratesnships.station;

/**
 * A block that is a station (docs/design.md §6). Crew can be assigned to it; on a ship it is found through its plot
 * position ({@link StationRef}). When the block is removed it must call {@link Stations#onStationRemoved} (e.g. from
 * {@code onRemove}), which frees the station and removes its seat.
 */
public interface StationBlock {

    StationKind<?> stationKind();

    /** Items implementing this skip the block's own use action so that their {@code useOn} runs (the captain's whistle). */
    interface Tool {
    }
}
