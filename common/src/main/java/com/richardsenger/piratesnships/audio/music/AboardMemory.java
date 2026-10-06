package com.richardsenger.piratesnships.audio.music;

/**
 * Keeps "aboard" true for a short grace time after the player last stood on a ship. Sable forgets the ship an entity
 * stands on as soon as it leaves the deck (a jump, a step over the rail), so without this the situation would flicker
 * between {@link MusicSituation#ABOARD} and {@link MusicSituation#AT_SEA}. Pure; tested by {@code AboardMemoryTest}.
 */
public final class AboardMemory {

    /** Three seconds. */
    public static final int GRACE_TICKS = 60;

    private long lastAboardTick = Long.MIN_VALUE;

    /** Feeds this tick's raw observation and returns the smoothed one. */
    public boolean update(boolean aboardNow, long tick) {
        if (aboardNow) lastAboardTick = tick;
        return lastAboardTick != Long.MIN_VALUE && tick - lastAboardTick <= GRACE_TICKS && tick >= lastAboardTick;
    }

    public void reset() {
        lastAboardTick = Long.MIN_VALUE;
    }
}
