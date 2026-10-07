package com.richardsenger.piratesnships.ship.hull.net;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player send rule of the ship status (HUD1), pure. It is asked once per sync interval for every player aboard:
 * a payload goes out when it differs from the last one that player got (another ship, or any rounded value changed),
 * and otherwise every {@link #KEEPALIVE_INTERVALS}th interval, so the client's 3 s freshness never runs out while the
 * player stays aboard.
 */
public final class ShipStatusThrottle {

    /** An unchanged status is still sent every this many intervals. */
    public static final int KEEPALIVE_INTERVALS = 5;

    private record Sent(ShipStatusPayload payload, int quietIntervals) {
    }

    private final Map<UUID, Sent> sent = new HashMap<>();

    /** Whether to send {@code payload} to {@code player} at this interval; records it when so. */
    public boolean offer(UUID player, ShipStatusPayload payload) {
        Sent last = sent.get(player);
        if (last == null || !last.payload().equals(payload) || last.quietIntervals() + 1 >= KEEPALIVE_INTERVALS) {
            sent.put(player, new Sent(payload, 0));
            return true;
        }
        sent.put(player, new Sent(last.payload(), last.quietIntervals() + 1));
        return false;
    }

    /** The player is not aboard any more: the next status goes out at once. */
    public void forget(UUID player) {
        sent.remove(player);
    }

    /** Forgets every player not in {@code players} (logged out, changed level). */
    public void retain(Collection<UUID> players) {
        sent.keySet().retainAll(players);
    }

    public void clear() {
        sent.clear();
    }

    public int size() {
        return sent.size();
    }
}
