package com.richardsenger.piratesnships.ship.hull.net;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player send rule of the ship status (HUD1, HUD2), pure. It is asked once per sync interval for every player
 * aboard: a payload goes out when it differs from the last one that player got (another ship, or any rounded value
 * changed), and otherwise once {@link #keepaliveIntervals} intervals have passed (about every {@link #KEEPALIVE_TICKS}).
 * Each payload tells the client how long it stays fresh ({@link #freshTicks}): three keepalive periods and a second, so
 * one or two lost sends and a lagging server never let the HUD go stale while the player stays aboard.
 *
 * <p>HUD2 fix: HUD1 kept an unchanged status back for five intervals (100 ticks at the default interval of 20), while
 * the client dropped a status after 60 ticks; a ship at rest or holding a steady course showed its HUD for 3 s, hid it
 * for 2 s, and so on.
 */
public final class ShipStatusThrottle {

    /** An unchanged status is still sent about this often [ticks]. */
    public static final int KEEPALIVE_TICKS = 40;
    /** How many keepalive periods a status stays fresh on the client, plus {@link #FRESH_MARGIN_TICKS}. */
    public static final int FRESH_PERIODS = 3;
    public static final int FRESH_MARGIN_TICKS = 20;

    private record Sent(ShipStatusPayload payload, int quietIntervals) {
    }

    private final Map<UUID, Sent> sent = new HashMap<>();

    /** Intervals after which an unchanged status goes out again: about {@link #KEEPALIVE_TICKS}, at least one. */
    public static int keepaliveIntervals(int intervalTicks) {
        int interval = Math.max(1, intervalTicks);
        return Math.max(1, KEEPALIVE_TICKS / interval);
    }

    /** Longest gap [ticks] between two statuses to a player who stays aboard. */
    public static int keepalivePeriodTicks(int intervalTicks) {
        return keepaliveIntervals(intervalTicks) * Math.max(1, intervalTicks);
    }

    /** How long [client ticks] a status sent at this interval stays fresh. */
    public static int freshTicks(int intervalTicks) {
        return FRESH_PERIODS * keepalivePeriodTicks(intervalTicks) + FRESH_MARGIN_TICKS;
    }

    /**
     * Whether to send {@code payload} to {@code player} at this interval; records it when so.
     *
     * @param keepaliveIntervals {@link #keepaliveIntervals} of the current sync interval
     */
    public boolean offer(UUID player, ShipStatusPayload payload, int keepaliveIntervals) {
        Sent last = sent.get(player);
        if (last == null || !last.payload().equals(payload) || last.quietIntervals() + 1 >= keepaliveIntervals) {
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
