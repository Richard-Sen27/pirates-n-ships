package com.richardsenger.piratesnships.rpg.career.client;

import com.richardsenger.piratesnships.rpg.career.NavyRank;

import java.util.Optional;

/**
 * When the rank box shows and how strongly (HUD5, docs/design.md §4.6, pure, tested with JUnit). In
 * {@link Mode#ON_CHANGE} the box shows for {@code career_hud.show_seconds} after the client learns its career on join,
 * after its title changes and after the career screen closes, fading in over the first and out over the last
 * {@link #FADE_TICKS} of that window; a title change names itself ("Promoted to Lieutenant", "Now Buccaneer") in the
 * title row for the first half of the window, then the box settles on the rank. {@link Mode#ALWAYS} keeps the box on
 * screen (the HON1 behaviour), {@link Mode#OFF} never draws it.
 */
public final class RankHudVisibility {

    /** Client config {@code career_hud.mode}. */
    public enum Mode { OFF, ON_CHANGE, ALWAYS }

    /** Ticks of the fade in and of the fade out. */
    public static final int FADE_TICKS = 10;
    public static final int DEFAULT_SHOW_SECONDS = 5;
    public static final int MIN_SHOW_SECONDS = 1;
    public static final int MAX_SHOW_SECONDS = 30;

    /** A changed title: a promotion in the navy, or any other new title ("Now ..."). */
    public record Change(boolean promotion, String titleKey) {
    }

    private RankHudVisibility() {
    }

    /**
     * What a career sync means for the box: empty when the title stays; a {@link Change} when it differs from the one
     * shown before. A promotion is a higher navy rank while already in service; enlisting, leaving the service, a
     * letter of marque and infamy are "Now ...".
     */
    public static Optional<Change> change(RankHudLayout.View before, RankHudLayout.View after) {
        String was = RankHudLayout.titleKey(before), now = RankHudLayout.titleKey(after);
        if (was.equals(now)) return Optional.empty();
        boolean promotion = inService(before) && inService(after) && after.navy().ordinal() > before.navy().ordinal();
        return Optional.of(new Change(promotion, now));
    }

    private static boolean inService(RankHudLayout.View v) {
        return v.enlisted() && v.navy() != NavyRank.NONE;
    }

    /** {@code show_seconds} as ticks, clamped to the config's range. */
    public static int showTicks(int seconds) {
        return Math.max(MIN_SHOW_SECONDS, Math.min(MAX_SHOW_SECONDS, seconds)) * 20;
    }

    /**
     * The box's opacity, 0 to 1.
     *
     * @param age       ticks since the box was last shown (with the partial tick), NaN when it never was
     * @param showTicks the whole window, fades included ({@link #showTicks})
     */
    public static float alpha(Mode mode, double age, int showTicks) {
        return switch (mode) {
            case OFF -> 0f;
            case ALWAYS -> 1f;
            case ON_CHANGE -> windowAlpha(age, showTicks);
        };
    }

    static float windowAlpha(double age, int showTicks) {
        if (!(age >= 0) || showTicks <= 0 || age >= showTicks) return 0f;
        // a window shorter than both fades shares itself between them
        double fade = Math.min(FADE_TICKS, showTicks / 2.0);
        double in = age / fade, out = (showTicks - age) / fade;
        return (float) Math.max(0, Math.min(1, Math.min(in, out)));
    }

    /** Whether the title row names the change rather than the rank: the first half of the window. */
    public static boolean headline(double age, int showTicks) {
        return age >= 0 && age < showTicks / 2.0;
    }

    /**
     * The client's box state: when it was last shown and the change it names. Driven by {@link RankHud} from the
     * career sync, the career screen and the client tick.
     */
    public static final class State {

        private long shownAt = Long.MIN_VALUE;
        private Change change;

        /** Shows the box from tick {@code now}; {@code change} names a title change, or null (join, screen closed). */
        public synchronized void show(long now, Change change) {
            this.shownAt = now;
            this.change = change;
        }

        /** Ticks since the box was shown, NaN when it never was. */
        public synchronized double age(long now, float partial) {
            return shownAt == Long.MIN_VALUE ? Double.NaN : now - shownAt + partial;
        }

        /** The change to name right now: one is named only during {@link #headline}. */
        public synchronized Optional<Change> headlineChange(long now, int showTicks) {
            if (change == null || !headline(age(now, 0f), showTicks)) return Optional.empty();
            return Optional.of(change);
        }

        /** The change shown in the current window (headline or not), so the box can keep one width through it. */
        public synchronized Optional<Change> windowChange(long now, int showTicks) {
            double age = age(now, 0f);
            if (change == null || !(age >= 0) || age >= showTicks) return Optional.empty();
            return Optional.of(change);
        }

        public synchronized void reset() {
            shownAt = Long.MIN_VALUE;
            change = null;
        }
    }
}
