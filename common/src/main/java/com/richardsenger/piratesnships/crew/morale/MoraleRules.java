package com.richardsenger.piratesnships.crew.morale;

/**
 * Morale arithmetic (HM1, docs/design.md §7.1, §7.3). Pure: no world access, the caller passes the config values in
 * {@link Settings}. Morale is an integer from {@link #MIN} to {@link #MAX}; a crew member that never had a value set
 * reads as {@link Settings#start()}.
 */
public final class MoraleRules {

    public static final int MIN = 0;
    public static final int MAX = 100;
    /** Stored value of a crew member whose morale was never set: reads as {@link Settings#start()}. */
    public static final int UNSET = -1;

    /** The {@code crew.morale} config values. */
    public record Settings(boolean enabled, int start, int hammockRestPerNight, int noHammockPerNight) {
        public static final Settings DEFAULTS = new Settings(true, 70, 5, 10);
    }

    private MoraleRules() {
    }

    public static int clamp(int value) {
        return Math.max(MIN, Math.min(MAX, value));
    }

    /**
     * The morale a crew member shows: its stored value, or {@code start} when it has none; always {@code start} while
     * morale is disabled (frozen).
     */
    public static int effective(Settings s, int stored) {
        if (!s.enabled() || stored == UNSET) {
            return clamp(s.start());
        }
        return clamp(stored);
    }

    /** Morale after {@code delta}, capped at {@link #MAX} and floored at {@link #MIN}; frozen at start while disabled. */
    public static int adjust(Settings s, int stored, int delta) {
        int current = effective(s, stored);
        return s.enabled() ? clamp(current + delta) : current;
    }

    /**
     * The hammock rule at dawn: a night in a hammock gains {@code hammock_rest_per_night}, a night on a ship without a
     * free hammock costs {@code no_hammock_per_night}, a night on duty (or none recorded) changes nothing. Zero while
     * morale is disabled.
     */
    public static int dawnDelta(Settings s, NightOutcome night) {
        if (!s.enabled()) {
            return 0;
        }
        return switch (night) {
            case SLEPT -> s.hammockRestPerNight();
            case NO_HAMMOCK -> -s.noHammockPerNight();
            case ON_DUTY, NONE -> 0;
        };
    }
}
