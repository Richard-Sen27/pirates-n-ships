package com.richardsenger.piratesnships.sailing.wind;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * A fixed wind per dimension that replaces the wind field until cleared (operator command {@code /pirates wind set},
 * GameTests, playtests). {@link WindService#sample} honors it, so it reaches clients through the normal wind sync.
 * Server memory only: it does not survive a restart and is cleared when the server stops. Pure apart from the shared
 * map; keyed by the dimension id string ({@code minecraft:overworld}).
 */
public final class WindOverride {

    /** A set override: wind blowing from {@code fromDegrees} (compass) at {@code strength} blocks/s, until a game time. */
    public record Entry(double fromDegrees, double strength, long untilGameTime) {
        public WindSample sample() {
            return WindSample.of(fromDegrees + 180.0, strength, 1.0, 0.0);
        }
    }

    /** No expiry. */
    public static final long FOREVER = Long.MAX_VALUE;

    private static final Map<String, Entry> OVERRIDES = new ConcurrentHashMap<>();

    private WindOverride() {
    }

    /**
     * Sets the wind of {@code dimension} to blow <em>from</em> {@code fromDegrees} (0 = from the north, i.e. toward +Z)
     * at {@code strength} blocks/s until {@code untilGameTime} (exclusive), or {@link #FOREVER}.
     */
    public static void set(String dimension, double fromDegrees, double strength, long untilGameTime) {
        OVERRIDES.put(dimension, new Entry(WindSample.normalizeDegrees(fromDegrees), Math.max(0.0, strength), untilGameTime));
    }

    public static void clear(String dimension) {
        OVERRIDES.remove(dimension);
    }

    public static void clearAll() {
        OVERRIDES.clear();
    }

    /** The active override of {@code dimension} at {@code gameTime}, or null. Expired entries are dropped. */
    public static @Nullable Entry get(String dimension, long gameTime) {
        Entry e = OVERRIDES.get(dimension);
        if (e != null && gameTime >= e.untilGameTime()) {
            OVERRIDES.remove(dimension, e);
            return null;
        }
        return e;
    }
}
