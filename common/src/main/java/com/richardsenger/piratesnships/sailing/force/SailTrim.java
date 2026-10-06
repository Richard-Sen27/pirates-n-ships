package com.richardsenger.piratesnships.sailing.force;

/** Sail trim state set at the sail winch station (docs/design.md §5.2). */
public enum SailTrim {
    FURLED,
    HALF,
    FULL;

    /** Fraction of the sail area that catches wind: 0, {@link SailingParams#halfTrimFactor()}, 1. */
    public double factor(SailingParams p) {
        return switch (this) {
            case FURLED -> 0.0;
            case HALF -> p.halfTrimFactor();
            case FULL -> 1.0;
        };
    }
}
