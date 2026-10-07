package com.richardsenger.piratesnships.hazards;

import java.util.Locale;
import java.util.Optional;

/** The two sea hazards of work package H1 (the kraken will be a mob, not one of these). */
public enum HazardKind {
    WATERSPOUT,
    WHIRLPOOL;

    /** Lower-case id: the command argument and the entity id. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<HazardKind> byId(String id) {
        for (HazardKind k : values()) {
            if (k.id().equals(id)) {
                return Optional.of(k);
            }
        }
        return Optional.empty();
    }
}
