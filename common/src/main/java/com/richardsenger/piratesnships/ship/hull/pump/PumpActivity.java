package com.richardsenger.piratesnships.ship.hull.pump;

/**
 * Server side of the pump handle's sync (PMP1, docs/design.md §4.8 "Visual backlog 2", item 3): whether a bilge pump
 * counts as being worked, and when that flag goes to clients. The hull runtime reports every tick the pump drains
 * ({@link #worked}); the flag stays on for {@link #HOLD_TICKS} after the last such tick, so the hand-over between two
 * crew orders or two repeated uses does not flicker. A change is sent at once, but never sooner than
 * {@link #MIN_SYNC_TICKS} after the previous one, so a pump that is worked continuously sends one update when it starts
 * and one when it stops, and quick taps send at most one a second. Pure state on game ticks, no world access; one per
 * {@link BilgePumpBlockEntity}, unit tested.
 */
public final class PumpActivity {

    /** Ticks the flag stays on after the last working tick (a held use key repeats every 4 ticks). */
    public static final int HOLD_TICKS = 5;
    /** Shortest time between two updates of the flag on clients (once a second). */
    public static final int MIN_SYNC_TICKS = 20;

    private static final long NEVER = Long.MIN_VALUE / 4;

    private long lastWorked = NEVER;
    private long lastSync = NEVER;
    private boolean synced;
    private int syncs;

    /** The pump worked (drained water) in game tick {@code now}. */
    public void worked(long now) {
        lastWorked = Math.max(lastWorked, now);
    }

    /** Whether the pump counts as being worked at {@code now}: it worked within the last {@link #HOLD_TICKS}. */
    public boolean working(long now) {
        return now - lastWorked <= HOLD_TICKS;
    }

    /**
     * One server tick at {@code now}: brings the synced flag up to {@link #working} when the last update is at least
     * {@link #MIN_SYNC_TICKS} old.
     *
     * @return true when the flag changed and an update must go to clients
     */
    public boolean tick(long now) {
        boolean want = working(now);
        if (want == synced || now - lastSync < MIN_SYNC_TICKS) {
            return false;
        }
        synced = want;
        lastSync = now;
        syncs++;
        return true;
    }

    /** The flag as clients last got it (the update tag's value). */
    public boolean synced() {
        return synced;
    }

    /** Number of flag changes sent so far, for tests. */
    public int syncs() {
        return syncs;
    }
}
