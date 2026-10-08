package com.richardsenger.piratesnships.hazards.waves;

import java.util.Locale;
import java.util.Optional;

/**
 * The four sea states of docs/design.md §5.4, each with its wave amplitude (the highest crest above the still water,
 * in blocks, before the {@code waves.amplitude} multiplier). Pure.
 */
public enum SeaState {
    CALM(0.1),
    MODERATE(0.3),
    ROUGH(0.7),
    STORM(1.2);

    private final double amplitude;

    SeaState(double amplitude) {
        this.amplitude = amplitude;
    }

    /** Crest height of this state [blocks], before the config multiplier. */
    public double amplitude() {
        return amplitude;
    }

    /** Lower-case name for commands and logs. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Whether the bow throws spray in this state (rough and storm). */
    public boolean spray() {
        return this.compareTo(ROUGH) >= 0;
    }

    public static Optional<SeaState> byId(String id) {
        for (SeaState s : values()) {
            if (s.id().equals(id)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    /** The state whose amplitude is nearest to {@code amplitude} (an eased sea between two states). */
    public static SeaState nearest(double amplitude) {
        SeaState best = CALM;
        for (SeaState s : values()) {
            if (Math.abs(s.amplitude - amplitude) < Math.abs(best.amplitude - amplitude)) {
                best = s;
            }
        }
        return best;
    }

    /** Ordinal-safe lookup for payloads. */
    public static SeaState byOrdinal(int ordinal) {
        SeaState[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, ordinal))];
    }
}
