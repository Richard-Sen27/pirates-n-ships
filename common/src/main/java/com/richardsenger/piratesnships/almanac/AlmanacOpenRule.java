package com.richardsenger.piratesnships.almanac;

/**
 * When a placed Captain's Almanac lies open (ALM1, docs/design.md §3.4), without world access. The human's rule: the
 * book opens only when someone actually reads it (right-click), never because a player comes near. It stays open while
 * a reader still has it open (the client sends {@code CloseAlmanacPayload} when the screen closes; a reader who leaves,
 * logs out or changes dimension counts as closed) and at least {@code almanac.open_ticks} after the last right-click,
 * so the opening animation always plays out even when the screen is closed at once. With {@code almanac.enabled} off it
 * never opens.
 */
public final class AlmanacOpenRule {

    /** No right-click yet. */
    public static final long NEVER = Long.MIN_VALUE;

    private AlmanacOpenRule() {
    }

    /**
     * Whether the tome lies open at game time {@code now}: {@code openedAt} is the last right-click's game time
     * ({@link #NEVER} for none), {@code openTicks} the minimum time open after it, {@code readers} whether a reader
     * still has it open.
     */
    public static boolean open(boolean enabled, long now, long openedAt, int openTicks, boolean readers) {
        if (!enabled) return false;
        if (readers) return true;
        return openedAt != NEVER && now >= openedAt && now - openedAt < openTicks;
    }
}
