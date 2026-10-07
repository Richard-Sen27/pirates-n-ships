package com.richardsenger.piratesnships.crew.morale;

import java.util.Locale;

/**
 * How a crew member spent the current night, recorded at nightfall and settled at dawn by the hammock rule (HM1,
 * docs/design.md §7.1): {@link MoraleRules#dawnDelta}. Saved on the crew member.
 */
public enum NightOutcome {
    /** No night recorded (daytime, or it was not on a ship at nightfall). */
    NONE,
    /** Turned in to a hammock of its ship. */
    SLEPT,
    /** On a ship, free, but no free hammock was left for it. */
    NO_HAMMOCK,
    /** At a station at nightfall, or got up for an order: on duty, no change. */
    ON_DUTY;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static NightOutcome byId(String id) {
        for (NightOutcome o : values()) {
            if (o.id().equals(id)) return o;
        }
        return NONE;
    }
}
