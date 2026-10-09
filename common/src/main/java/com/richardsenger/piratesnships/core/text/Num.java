package com.richardsenger.piratesnships.core.text;

import java.util.Locale;

/**
 * Numbers in player-facing text (FIX2). The mod's texts are English and its lang file writes a dot, so every number
 * is formatted with {@link Locale#ROOT}: a player on a German system reads "6.3", never "6,3". Use these instead of
 * {@code String.format} without a locale; {@code LocaleSafeFormattingTest} guards the player-facing packages.
 */
public final class Num {

    private Num() {
    }

    /** {@code pattern} with {@code args}, formatted with {@link Locale#ROOT}. */
    public static String format(String pattern, Object... args) {
        return String.format(Locale.ROOT, pattern, args);
    }

    /** Rounded to a whole number: {@code 6.5} reads "7" (half up, like {@code %.0f}). */
    public static String whole(double v) {
        return format("%.0f", v);
    }

    /** One decimal: {@code 6.25} reads "6.3". */
    public static String one(double v) {
        return format("%.1f", v);
    }

    /** Two decimals: {@code 0.5} reads "0.50". */
    public static String two(double v) {
        return format("%.2f", v);
    }

    /** A whole number bare, anything else with one decimal: {@code 8.0} reads "8", {@code 7.5} reads "7.5". */
    public static String trim(double v) {
        return v == Math.rint(v) && !Double.isInfinite(v) ? Long.toString((long) v) : one(v);
    }
}
