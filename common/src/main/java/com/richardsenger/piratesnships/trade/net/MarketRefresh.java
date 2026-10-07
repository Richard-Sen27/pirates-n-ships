package com.richardsenger.piratesnships.trade.net;

import java.util.Optional;

/**
 * When {@link MarketBackend} re-sends an open market session's state: on refresh ticks, and only if the view differs
 * from the last one sent to that session (prices, stock, the viewer's doubloons, offers, contracts or the quantity).
 */
public final class MarketRefresh {

    private MarketRefresh() {
    }

    /** Whether server tick {@code tick} is a refresh tick for an interval of {@code interval} ticks (below 1 = every tick). */
    public static boolean due(long tick, int interval) {
        return interval <= 1 || tick % interval == 0;
    }

    /** Whether {@code now} must be sent to a session that last received {@code lastSent}. */
    public static boolean changed(Optional<MarketView> lastSent, MarketView now) {
        return lastSent.isEmpty() || !lastSent.get().equals(now);
    }
}
