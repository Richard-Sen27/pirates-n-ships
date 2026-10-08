package com.richardsenger.piratesnships.rpg.career;

import com.richardsenger.piratesnships.Constants;

import java.util.Locale;
import java.util.Optional;

/**
 * What a career counts (docs/design.md §15, CAR1), fed from deeds by {@link CareerRules#counters} and kept in the
 * {@link CareerRecord}. The navy ladder reads the pirates defeated (killed plus turned in) and the navy quests, the
 * infamy ladder the plunder fenced and the captures (merchants plundered, ships captured, navy officers killed).
 */
public enum CareerCounter {
    /** Pirates killed; a pirate captain counts {@code careers.captain_weight} times. */
    PIRATES_KILLED,
    /** Pirate captains killed (each also counts in {@link #PIRATES_KILLED}). */
    CAPTAINS_KILLED,
    /** Pirates delivered alive to the navy. */
    PIRATES_TURNED_IN,
    NAVY_KILLED,
    /** Navy officers killed (each also counts in {@link #NAVY_KILLED}). */
    OFFICERS_KILLED,
    MERCHANTS_PLUNDERED,
    /** Doubloons received for plunder at fences. */
    PLUNDER_COINS,
    /** Bounties claimed. No deed reports this yet (a law follow-up); kept so saves need no migration. */
    BOUNTIES_CLAIMED,
    NAVY_QUESTS,
    PIRATE_QUESTS,
    VILLAGE_QUESTS,
    /** Ships captured; fed by the world simulation's voyage ends later (WS3b). */
    SHIPS_CAPTURED;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String nameKey() {
        return Constants.MOD_ID + ".career.counter." + id();
    }

    public static Optional<CareerCounter> byId(String id) {
        for (CareerCounter c : values()) if (c.id().equals(id)) return Optional.of(c);
        return Optional.empty();
    }
}
