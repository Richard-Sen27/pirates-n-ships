package com.richardsenger.piratesnships.sailing.wind;

/**
 * Small deterministic value noise used by the wind field. Pure Java, no Minecraft classes and no state: the same
 * inputs always give the same output, on every server and in every test.
 *
 * <p>Lattice values come from a SplitMix64 hash of (seed, channel, lattice index) and are interpolated with the
 * quintic fade {@code 6t^5 - 15t^4 + 10t^3}, so the noise and its first derivative are continuous. The slope of one
 * octave is at most {@code 1.875 * 2} per lattice cell (fade slope 1.875 times the largest value difference 2).
 */
public final class WindNoise {

    /** Upper bound of |d fbm / dx| per lattice cell of the first octave, for {@link #fbm1}. */
    public static final double FBM_MAX_SLOPE = 5.0;

    private WindNoise() {
    }

    /** SplitMix64 finalizer: a well mixed 64-bit hash. */
    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Hash of a seed, a channel and up to two lattice coordinates. */
    public static long hash(long seed, long channel, long a, long b) {
        long h = mix(seed + 0x9E3779B97F4A7C15L * (channel + 1));
        h = mix(h ^ (a * 0xD1B54A32D192ED03L));
        h = mix(h ^ (b * 0xABC98388FB8FAC03L));
        return h;
    }

    /** Uniform value in [0, 1) from a hash. */
    public static double unit(long hash) {
        return (hash >>> 11) * 0x1.0p-53;
    }

    /** Uniform value in [-1, 1) from a hash. */
    public static double signed(long hash) {
        return unit(hash) * 2.0 - 1.0;
    }

    static double fade(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    /** One octave of 1D value noise in [-1, 1]. One lattice cell per unit of {@code x}. */
    public static double value1(long seed, long channel, double x) {
        double fx = Math.floor(x);
        long i = (long) fx;
        double t = fade(x - fx);
        double a = signed(hash(seed, channel, i, 0));
        double b = signed(hash(seed, channel, i + 1, 0));
        return a + (b - a) * t;
    }

    /** One octave of 2D value noise in [-1, 1]. One lattice cell per unit of {@code x} and {@code z}. */
    public static double value2(long seed, long channel, double x, double z) {
        double fx = Math.floor(x);
        double fz = Math.floor(z);
        long i = (long) fx;
        long j = (long) fz;
        double tx = fade(x - fx);
        double tz = fade(z - fz);
        double v00 = signed(hash(seed, channel, i, j));
        double v10 = signed(hash(seed, channel, i + 1, j));
        double v01 = signed(hash(seed, channel, i, j + 1));
        double v11 = signed(hash(seed, channel, i + 1, j + 1));
        double v0 = v00 + (v10 - v00) * tx;
        double v1 = v01 + (v11 - v01) * tx;
        return v0 + (v1 - v0) * tz;
    }

    /**
     * Two octaves of 1D value noise (weights 1 and 0.5, frequencies 1 and 2), normalized to [-1, 1]. Its slope is at
     * most {@link #FBM_MAX_SLOPE} per unit of {@code x}.
     */
    public static double fbm1(long seed, long channel, double x) {
        return (value1(seed, channel, x) + 0.5 * value1(seed, channel + 1000, x * 2.0 + 17.31)) / 1.5;
    }

    /** Two octaves of 2D value noise, normalized to [-1, 1]. */
    public static double fbm2(long seed, long channel, double x, double z) {
        return (value2(seed, channel, x, z) + 0.5 * value2(seed, channel + 1000, x * 2.0 + 17.31, z * 2.0 - 5.77)) / 1.5;
    }
}
