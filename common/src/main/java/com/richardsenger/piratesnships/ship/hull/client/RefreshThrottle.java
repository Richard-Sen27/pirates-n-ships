package com.richardsenger.piratesnships.ship.hull.client;

/**
 * Lets a periodic job run at most once per {@code interval} ticks (the plant culling refresh, HV1). The first call is
 * always ready; {@link #reset} makes the next call ready again (a level change).
 */
public final class RefreshThrottle {

    private long last = Long.MIN_VALUE;

    /** Whether the job may run at tick {@code now}; if so, the tick is remembered. An interval below 1 counts as 1. */
    public boolean ready(long now, int interval) {
        if (last != Long.MIN_VALUE && now - last < Math.max(1, interval) && now >= last) {
            return false;
        }
        last = now;
        return true;
    }

    public void reset() {
        last = Long.MIN_VALUE;
    }
}
