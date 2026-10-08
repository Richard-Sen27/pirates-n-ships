package com.richardsenger.piratesnships.station.helm;

/**
 * Which way the helmsman is turning the wheel (ART7), for his animation: the NPC helmsman sets the wheel in steps
 * (every {@code crew_stations.course.update_interval_ticks}, {@link HelmCourses}) and a player's click moves it in
 * steps too, so a change of the wheel angle starts a turn that is shown for {@link #HOLD_TICKS} (one loop of
 * {@code helm_turn_left/right}) and renewed by every further change the same way. Pure: one instance per helm.
 */
public final class HelmTurn {

    /** Ticks a turn is shown after the wheel last moved. */
    public static final int HOLD_TICKS = 20;
    /** Changes of the wheel angle smaller than this [degrees] are not a turn (rounding, easing). */
    static final double MIN_CHANGE = 0.5;

    private double last = Double.NaN;
    private int direction;
    private long until = Long.MIN_VALUE;
    private long seen;

    /**
     * Records the wheel angle of game tick {@code now}.
     *
     * @param wheel the wheel angle in degrees (positive = starboard = clockwise as the helmsman sees it)
     * @return +1 while the wheel turns to starboard, -1 to port, 0 when it is held
     */
    public int update(double wheel, long now) {
        seen = now;
        if (!Double.isNaN(last) && Math.abs(wheel - last) >= MIN_CHANGE) {
            direction = wheel > last ? 1 : -1;
            until = now + HOLD_TICKS;
        }
        last = wheel;
        return now < until ? direction : 0;
    }

    /** Game tick of the last {@link #update}. */
    public long lastSeen() {
        return seen;
    }
}
