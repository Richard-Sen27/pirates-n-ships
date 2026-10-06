package com.richardsenger.piratesnships.trade;

import java.nio.charset.StandardCharsets;

/**
 * Small deterministic hashing for seeded trade decisions (profiles, contract offers). Stable across JVMs and runs:
 * SplitMix64 over the seed and the UTF-8 bytes of a key, so results never depend on {@code String.hashCode} or
 * iteration order.
 */
public final class TradeRandom {

    private TradeRandom() {
    }

    public static long mix(long seed, String key) {
        long h = splitMix(seed ^ 0x5DEECE66DL);
        for (byte b : key.getBytes(StandardCharsets.UTF_8)) {
            h = splitMix(h ^ (b & 0xFF));
        }
        return h;
    }

    public static long mix(long seed, long value) {
        return splitMix(splitMix(seed) ^ value);
    }

    public static long splitMix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** A uniform double in [0, 1) from a hash. */
    public static double unit(long hash) {
        return (hash >>> 11) * 0x1.0p-53;
    }

    /** A sequence of uniform values from a seed. */
    public static final class Stream {
        private long state;

        public Stream(long seed) {
            this.state = seed;
        }

        public long nextLong() {
            state += 0x9E3779B97F4A7C15L;
            return splitMix(state);
        }

        public double nextDouble() {
            return unit(nextLong());
        }

        /** Uniform int in [0, bound). */
        public int nextInt(int bound) {
            return (int) Math.floor(nextDouble() * bound);
        }
    }
}
