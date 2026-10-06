package com.richardsenger.piratesnships.station;

import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * The shared station life cycle of docs/design.md §6, {@code occupy → operate → release}, as a pure state machine
 * (no world access). One occupant at a time; an order takes a number of ticks; releasing or interrupting a station
 * that is operating drops the order without effect.
 *
 * <pre>
 *  FREE --occupy--> OCCUPIED --start(order, ticks)--> OPERATING --tick()×ticks--> OCCUPIED (order returned as done)
 *    ^                 |                                  |  start(other) replaces the order
 *    +----release------+-----------release---------------+  interrupt() → OCCUPIED (order dropped)
 * </pre>
 *
 * @param <O> the order type of the station kind (e.g. {@code SailOrder} for the sail winch)
 */
public final class StationState<O> {

    public enum Phase { FREE, OCCUPIED, OPERATING }

    /** Who occupies a station: a player or a crew member, by entity UUID. */
    public record Occupant(UUID id, boolean player) {
        public Occupant {
            Objects.requireNonNull(id, "id");
        }
    }

    public enum OccupyResult {
        /** The station was free and is now occupied by the caller. */
        OCCUPIED,
        /** The caller already occupies it. */
        ALREADY,
        /** Somebody else occupies it. */
        TAKEN
    }

    private @Nullable Occupant occupant;
    private @Nullable O order;
    private int remaining;

    public Phase phase() {
        if (occupant == null) return Phase.FREE;
        return order == null ? Phase.OCCUPIED : Phase.OPERATING;
    }

    public @Nullable Occupant occupant() {
        return occupant;
    }

    public boolean isOccupiedBy(UUID id) {
        return occupant != null && occupant.id().equals(id);
    }

    /** The order being carried out, null unless {@link Phase#OPERATING}. */
    public @Nullable O order() {
        return order;
    }

    /** Ticks left until the current order is done (0 when not operating). */
    public int remaining() {
        return order == null ? 0 : remaining;
    }

    public OccupyResult occupy(Occupant who) {
        if (occupant == null) {
            occupant = who;
            return OccupyResult.OCCUPIED;
        }
        return occupant.id().equals(who.id()) ? OccupyResult.ALREADY : OccupyResult.TAKEN;
    }

    /**
     * Starts an order that is done after {@code ticks} calls of {@link #tick()} (at least one). Replaces an order in
     * progress. Returns false (and changes nothing) when the station is free.
     */
    public boolean start(O newOrder, int ticks) {
        Objects.requireNonNull(newOrder, "order");
        if (occupant == null) {
            return false;
        }
        order = newOrder;
        remaining = Math.max(1, ticks);
        return true;
    }

    /** Advances the order in progress by one tick. Returns the order on the tick it is done, otherwise null. */
    public @Nullable O tick() {
        if (order == null) {
            return null;
        }
        if (--remaining > 0) {
            return null;
        }
        O done = order;
        order = null;
        remaining = 0;
        return done;
    }

    /** Stops the order in progress without effect; the occupant stays. Returns the dropped order, or null. */
    public @Nullable O interrupt() {
        O dropped = order;
        order = null;
        remaining = 0;
        return dropped;
    }

    /**
     * Frees the station if {@code id} occupies it (the order in progress is dropped). Returns false if the station is
     * free or occupied by somebody else.
     */
    public boolean release(UUID id) {
        if (!isOccupiedBy(id)) {
            return false;
        }
        interrupt();
        occupant = null;
        return true;
    }

    /** Frees the station whoever occupies it (station broken, ship gone). */
    public void clear() {
        interrupt();
        occupant = null;
    }
}
