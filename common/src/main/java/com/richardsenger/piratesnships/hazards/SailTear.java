package com.richardsenger.piratesnships.hazards;

import com.richardsenger.piratesnships.sailing.force.SailTrim;
import org.jetbrains.annotations.Nullable;

/**
 * The waterspout's wind tearing at sails (docs/design.md §12 "damage sails"), pure. A sail inside the funnel loses one
 * trim step when its roll succeeds: full to half, half to furled. Furled sails are safe. There is no sail damage state
 * yet, so the "damage" is this forced furling; the winch can set the sails again once the spout has passed.
 */
public final class SailTear {

    private SailTear() {
    }

    /** One step down, or null when the sail is already furled. */
    public static @Nullable SailTrim torn(SailTrim trim) {
        return switch (trim) {
            case FULL -> SailTrim.HALF;
            case HALF -> SailTrim.FURLED;
            case FURLED -> null;
        };
    }

    /** Whether a set sail tears this second: {@code roll} uniform in [0, 1). */
    public static boolean tears(SailTrim trim, double chance, double roll) {
        return trim != SailTrim.FURLED && roll < chance;
    }
}
