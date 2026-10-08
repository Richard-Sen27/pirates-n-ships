package com.richardsenger.piratesnships.sailing.effects;

/**
 * Pure helpers shared by the wind streaks and the foam (WD1, docs/design.md §5.4 "Seeing wind and waves"): how many
 * particles a fractional rate gives this tick, where in the ring around the camera a particle starts, and how a particle
 * fades in and out over its life. No world access; the random numbers come from the caller.
 */
public final class SpawnRules {

    /** Share of the life spent fading in, and the same share fading out. */
    public static final double FADE_FRACTION = 0.25;

    private SpawnRules() {
    }

    /**
     * Particles to spawn this tick for an average of {@code rate} per tick: the whole part always, plus one more with the
     * probability of the fractional part, so the long-run mean is exactly {@code rate}.
     *
     * @param random01 a uniform random number in [0, 1)
     */
    public static int count(double rate, double random01) {
        if (!(rate > 0.0)) {
            return 0;
        }
        int whole = (int) Math.floor(rate);
        return whole + (random01 < rate - whole ? 1 : 0);
    }

    /**
     * A point spread evenly over the area of the ring between {@code innerRadius} and {@code outerRadius} around
     * {@code (cx, cz)}.
     *
     * @param u1 uniform in [0, 1): the angle
     * @param u2 uniform in [0, 1): the radius (area-uniform)
     * @return {@code {x, z}}
     */
    public static double[] ringPoint(double cx, double cz, double innerRadius, double outerRadius, double u1, double u2) {
        double r0 = Math.max(0.0, Math.min(innerRadius, outerRadius));
        double r1 = Math.max(r0, outerRadius);
        double r = Math.sqrt(r0 * r0 + u2 * (r1 * r1 - r0 * r0));
        double a = u1 * 2.0 * Math.PI;
        return new double[] {cx + r * Math.cos(a), cz + r * Math.sin(a)};
    }

    /**
     * Opacity factor in [0, 1] at {@code age} of {@code lifetime} ticks: rises over the first {@link #FADE_FRACTION} of
     * the life, holds, falls over the last; a smoothstep at both ends so nothing pops.
     */
    public static double fade(double age, double lifetime) {
        if (lifetime <= 0.0 || age < 0.0 || age > lifetime) {
            return 0.0;
        }
        double f = age / lifetime;
        double in = smooth(f / FADE_FRACTION);
        double out = smooth((1.0 - f) / FADE_FRACTION);
        return Math.min(in, out);
    }

    /** The unit vector {@code {x, z}} of a compass bearing (0 = north = −Z, 90 = east = +X), as {@code WindSample}. */
    public static double[] bearing(double degrees) {
        double rad = Math.toRadians(degrees);
        return new double[] {Math.sin(rad), -Math.cos(rad)};
    }

    /** Multiplier on the spawn rates for the video setting "Particles": all 1, decreased 0.5, minimal 0. */
    public static double particleSetting(int ordinal) {
        return switch (ordinal) {
            case 0 -> 1.0;
            case 1 -> 0.5;
            default -> 0.0;
        };
    }

    private static double smooth(double t) {
        double c = Math.max(0.0, Math.min(1.0, t));
        return c * c * (3.0 - 2.0 * c);
    }
}
