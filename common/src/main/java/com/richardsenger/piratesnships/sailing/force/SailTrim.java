package com.richardsenger.piratesnships.sailing.force;

/**
 * Sail trim state set at the sail winch station (docs/design.md §5.2). Also the {@code trim} block state property of
 * the sail blocks ({@link StringRepresentable}, lower-case names).
 */
public enum SailTrim implements net.minecraft.util.StringRepresentable {
    FURLED,
    HALF,
    FULL;

    /** The winch cycle: furled → half → full → furled. */
    public SailTrim next() {
        return values()[(ordinal() + 1) % values().length];
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Fraction of the sail area that catches wind: 0, {@link SailingParams#halfTrimFactor()}, 1. */
    public double factor(SailingParams p) {
        return switch (this) {
            case FURLED -> 0.0;
            case HALF -> p.halfTrimFactor();
            case FULL -> 1.0;
        };
    }
}
