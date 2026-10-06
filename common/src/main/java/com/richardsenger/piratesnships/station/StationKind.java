package com.richardsenger.piratesnships.station;

import net.minecraft.server.level.ServerLevel;

/**
 * What a kind of station does with an order (docs/design.md §6). The shared life cycle (occupant, timing,
 * interruption) is {@link StationState}, driven by {@link Stations}; a kind only says how long an order takes and what
 * happens when it is done. Another station (cannon, capstan, pump, crow's nest) implements this with its own order
 * type, e.g. {@code CapstanStation implements StationKind<AnchorOrder>}, and its block implements {@link StationBlock}.
 *
 * @param <O> the order type
 */
public interface StationKind<O> {

    /** Stable id, e.g. {@code "sail_winch"}. */
    String id();

    Class<O> orderType();

    /**
     * Work time of {@code order} at {@code station} in ticks: 0 when there is nothing to do, negative when the order
     * can't be carried out here (e.g. a winch on a ship without sails).
     */
    int durationTicks(ServerLevel level, StationRef station, O order);

    /** Applies a finished order to the world. */
    void complete(ServerLevel level, StationRef station, O order);
}
