package com.richardsenger.piratesnships.sailing.effects;

import java.util.function.DoubleSupplier;

/**
 * How each wind streak looks and moves (WD2, docs/design.md §5.4 "Wind streaks"): the variety that makes the streaks
 * read as moving air instead of a conveyor belt. Each streak gets its own speed around the wind's, a small heading
 * jitter, a gentle wobble over its flight, its own life and one of the stroke sprites; most come in loose puffs
 * ({@link WindPuffs}); they gather near deck height ({@link WindStreakRules#y}); their opacity grows with the wind and
 * they fade in fast and out slowly. Pure: the random numbers come from the caller. Client visuals only.
 *
 * @param speedSpread          a streak flies at 1 ± this times the wind speed ({@code wind_effects.speed_spread})
 * @param headingJitterDegrees a streak's heading is the wind's ± this [degrees] ({@code wind_effects.heading_jitter_degrees})
 * @param wobble               largest wobble amplitude [blocks] ({@code wind_effects.wobble}); each streak wobbles by
 *                             {@link #WOBBLE_MIN_FACTOR} to 1 times it
 * @param heightPeak           most streaks fly this high above the sea [blocks] ({@code wind_effects.height_peak})
 * @param puffShare            share of the streaks that come in puffs in steady wind; a gust raises it toward 1
 *                             ({@code wind_effects.puff_share})
 * @param puffSize             largest puff [streaks] ({@code wind_effects.puff_size}); puffs have
 *                             {@link #puffMinSize()} to this many
 * @param puffSpread           a puff's streaks start within this distance of its centre [blocks]
 *                             ({@code wind_effects.puff_spread})
 * @param puffTicks            a puff's streaks are born over this many ticks ({@code wind_effects.puff_ticks})
 * @param opacity              opacity of a streak in a full wind ({@code wind_effects.opacity})
 */
public record WindStreakStyle(double speedSpread, double headingJitterDegrees, double wobble, double heightPeak,
                             double puffShare, int puffSize, double puffSpread, int puffTicks, double opacity) {

    /** A streak lives from this to {@link #LIFE_MAX_FACTOR} times {@code wind_effects.life_ticks} (25 to 60 at 40). */
    public static final double LIFE_MIN_FACTOR = 0.625, LIFE_MAX_FACTOR = 1.5;
    /** The smallest wobble as a share of {@link #wobble}. */
    public static final double WOBBLE_MIN_FACTOR = 0.2;
    /** The sideways part of the wobble as a share of the vertical part ("mostly vertical"). */
    public static final double WOBBLE_SIDEWAYS = 0.3;
    /** Wobble periods over a streak's life. */
    public static final double WOBBLE_CYCLES = 1.0;
    /** Share of the life spent fading in (fast) and fading out (slow). */
    public static final double FADE_IN = 0.15, FADE_OUT = 0.6;
    /** Wind speeds [blocks/s] at which a streak is faint and at which it is fully opaque. */
    public static final double FAINT_STRENGTH = 4.0, FULL_STRENGTH = 12.0;
    /** Opacity at {@link #FAINT_STRENGTH} as a share of {@link #opacity}. */
    public static final double FAINT_SHARE = 0.3;
    /**
     * Stroke sprites ({@code textures/particle/wind_streak_<n>.png}): 0 straight taper, 1 a gentle arc, 2 a soft S,
     * 3 the "whoosh" with a curl at the head; picked with these weights, so the curl stays an accent.
     */
    public static final double[] VARIANT_WEIGHTS = {0.3, 0.3, 0.25, 0.15};
    public static final int VARIANTS = VARIANT_WEIGHTS.length;
    /** How far a puff streak's speed and heading may stray from its puff's, as a share of the full spread. */
    public static final double PUFF_COHESION = 0.25;

    public static final WindStreakStyle DEFAULTS = new WindStreakStyle(0.3, 8.0, 0.25, 3.0, 0.65, 6, 2.0, 5, 0.36);

    public WindStreakStyle {
        speedSpread = clamp(speedSpread, 0.0, 0.9);
        headingJitterDegrees = clamp(headingJitterDegrees, 0.0, 90.0);
        wobble = Math.max(0.0, wobble);
        heightPeak = Math.max(0.0, heightPeak);
        puffShare = clamp(puffShare, 0.0, 1.0);
        puffSize = Math.max(1, puffSize);
        puffSpread = Math.max(0.0, puffSpread);
        puffTicks = Math.max(1, puffTicks);
        opacity = clamp(opacity, 0.0, 1.0);
    }

    /** One streak's own values. */
    public record Streak(double speedFactor, double headingOffsetDegrees, int lifeTicks, double wobbleAmplitude,
                         double wobblePhase, int variant) {
    }

    /**
     * The values of one streak.
     *
     * @param meanLife {@code wind_effects.life_ticks}
     * @param speedU   uniform in [0, 1): the speed within the spread (a puff passes its members ones close together)
     * @param headingU uniform in [0, 1): the heading within the jitter
     * @param random   four more uniforms in [0, 1): life, wobble amplitude, wobble phase, sprite
     */
    public Streak streak(int meanLife, double speedU, double headingU, DoubleSupplier random) {
        double speed = 1.0 + speedSpread * (2.0 * unit(speedU) - 1.0);
        double heading = headingJitterDegrees * (2.0 * unit(headingU) - 1.0);
        double lifeMin = LIFE_MIN_FACTOR * meanLife, lifeMax = LIFE_MAX_FACTOR * meanLife;
        int life = Math.max(1, (int) Math.round(lifeMin + unit(random.getAsDouble()) * (lifeMax - lifeMin)));
        double amplitude = wobble * (WOBBLE_MIN_FACTOR + (1.0 - WOBBLE_MIN_FACTOR) * unit(random.getAsDouble()));
        double phase = unit(random.getAsDouble()) * 2.0 * Math.PI;
        return new Streak(speed, heading, life, amplitude, phase, variant(random.getAsDouble()));
    }

    /** The sprite for a uniform {@code u} in [0, 1), by {@link #VARIANT_WEIGHTS}. */
    public static int variant(double u) {
        double x = unit(u);
        for (int i = 0; i < VARIANTS - 1; i++) {
            x -= VARIANT_WEIGHTS[i];
            if (x < 0.0) {
                return i;
            }
        }
        return VARIANTS - 1;
    }

    /**
     * A puff member's uniform: its puff's {@code group} value, strayed by up to ± {@link #PUFF_COHESION} / 2 with
     * {@code own}, kept in [0, 1), so a puff's streaks fly roughly together and drift apart only a little.
     */
    public static double cohere(double group, double own) {
        return clamp(group + (unit(own) - 0.5) * PUFF_COHESION, 0.0, Math.nextDown(1.0));
    }

    /**
     * The wobble at {@code f} (age / life, 0 to 1) of a streak with this amplitude and phase: one sine over the life,
     * mostly up and down with a smaller sideways part a quarter period behind.
     *
     * @return {@code {sideways, vertical}} [blocks]
     */
    public static double[] wobbleOffset(double amplitude, double phase, double f) {
        double a = 2.0 * Math.PI * WOBBLE_CYCLES * f + phase;
        return new double[] {WOBBLE_SIDEWAYS * amplitude * Math.cos(a), amplitude * Math.sin(a)};
    }

    /**
     * Opacity factor in [0, 1] at {@code age} of {@code lifetime} ticks: a fast smoothstep in over the first
     * {@link #FADE_IN} of the life, then a long, slow smoothstep out over the last {@link #FADE_OUT}, so a streak
     * appears briskly and dissolves.
     */
    public static double fade(double age, double lifetime) {
        if (lifetime <= 0.0 || age < 0.0 || age > lifetime) {
            return 0.0;
        }
        double f = age / lifetime;
        return Math.min(smooth(f / FADE_IN), smooth((1.0 - f) / FADE_OUT));
    }

    /** Opacity of a fully faded-in streak in a wind of {@code strength} blocks/s: faint at 4, full from 12. */
    public double opacity(double strength) {
        double t = smooth((strength - FAINT_STRENGTH) / (FULL_STRENGTH - FAINT_STRENGTH));
        return opacity * (FAINT_SHARE + (1.0 - FAINT_SHARE) * t);
    }

    /** Share of the streaks that come in puffs at gust intensity {@code gust} in [0, 1]: all of them at a gust's peak. */
    public double puffShare(double gust) {
        double g = clamp(gust, 0.0, 1.0);
        return puffShare + (1.0 - puffShare) * g;
    }

    /** The smallest puff: half the largest, rounded up. */
    public int puffMinSize() {
        return (puffSize + 1) / 2;
    }

    /** Mean streaks in one puff. */
    public double meanPuffSize() {
        return (puffMinSize() + puffSize) / 2.0;
    }

    /** A puff's size for a uniform {@code u} in [0, 1): evenly from {@link #puffMinSize()} to {@link #puffSize}. */
    public int puffSize(double u) {
        int min = puffMinSize();
        return Math.min(puffSize, min + (int) Math.floor(unit(u) * (puffSize - min + 1)));
    }

    private static double unit(double u) {
        return clamp(u, 0.0, Math.nextDown(1.0));
    }

    private static double clamp(double v, double lo, double hi) {
        return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }

    private static double smooth(double t) {
        double c = clamp(t, 0.0, 1.0);
        return c * c * (3.0 - 2.0 * c);
    }
}
