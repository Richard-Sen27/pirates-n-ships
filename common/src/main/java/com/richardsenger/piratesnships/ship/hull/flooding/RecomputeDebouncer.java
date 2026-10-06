package com.richardsenger.piratesnships.ship.hull.flooding;

/**
 * Coalesces hull changes into one re-analysis (docs/design.md §4.2, §18). Pure and tick-driven: call
 * {@link #markDirty()} on every relevant block change and {@link #tick()} once per server tick; when it returns
 * {@code true}, snapshot the grid and run the analysis (possibly off-thread).
 *
 * <p>Fires once the hull has been quiet for {@code quietTicks}, or at the latest {@code maxDelayTicks} after the first
 * unprocessed change, so a ship under constant fire still gets re-analyzed.
 */
public final class RecomputeDebouncer {

    private final int quietTicks;
    private final int maxDelayTicks;
    private boolean dirty;
    private int sinceLastChange;
    private int sinceFirstChange;

    public RecomputeDebouncer(int quietTicks, int maxDelayTicks) {
        if (quietTicks < 0 || maxDelayTicks < quietTicks) {
            throw new IllegalArgumentException("Need 0 <= quietTicks <= maxDelayTicks");
        }
        this.quietTicks = quietTicks;
        this.maxDelayTicks = maxDelayTicks;
    }

    public void markDirty() {
        if (!dirty) {
            dirty = true;
            sinceFirstChange = -1; // the tick of the change itself does not count
        }
        sinceLastChange = -1;
    }

    public boolean isDirty() {
        return dirty;
    }

    /** Advances one tick. Returns {@code true} exactly when a re-analysis should start now (and clears the flag). */
    public boolean tick() {
        if (!dirty) return false;
        sinceLastChange++;
        sinceFirstChange++;
        if (sinceLastChange >= quietTicks || sinceFirstChange >= maxDelayTicks) {
            dirty = false;
            return true;
        }
        return false;
    }
}
