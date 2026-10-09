package com.richardsenger.piratesnships.sailing.ship;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * The client's copy of each ship's bow (VIS1c), by ship id, from {@link ShipBowPayload}; the sail renderers read it
 * beside the ship's render pose ({@code ship.sable.ClientShipPoses#shipId}). It uses no client classes, so GameTests
 * keep their own copy; the game uses {@link #INSTANCE}, cleared on disconnect.
 *
 * <p>An entry outlives the client's sub-level on purpose: a ship's bow never changes under its id except by a new
 * payload, and keeping it means a player who leaves Sable's tracking range and comes back between two server ticks (so
 * that {@link ShipBowSync} never saw him leave) still has it. The map grows by one small entry per ship seen in a
 * session.
 */
public final class ClientShipBows {

    /** The game's copy (the client main thread writes, the render thread reads). */
    public static final ClientShipBows INSTANCE = new ClientShipBows();

    private final Map<UUID, BowFrame> bows = new ConcurrentHashMap<>();

    /** Takes a received bow (an unknown index forgets the ship's bow). */
    public void accept(ShipBowPayload payload) {
        BowFrame b = payload.bowFrame();
        if (b == null) {
            bows.remove(payload.ship());
        } else {
            bows.put(payload.ship(), b);
        }
    }

    /** The bow of ship {@code ship}, or null when the server has not sent it (yet), or for no ship. */
    public @Nullable BowFrame bow(@Nullable UUID ship) {
        return ship == null ? null : bows.get(ship);
    }

    public void clear() {
        bows.clear();
    }
}
