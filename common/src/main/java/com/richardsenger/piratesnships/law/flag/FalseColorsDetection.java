package com.richardsenger.piratesnships.law.flag;

import net.minecraft.util.RandomSource;

/**
 * Chance that an observer sees through false colors (docs/design.md §4.7).
 *
 * <p>The model is a detection <i>rate</i> per second (a constant hazard while nothing changes):
 * <pre>
 *   rate      = strength * baseRatePerSecond * proximity * nestRate * (1 + score / scoreScale)
 *   proximity = 1 inside closeRange, 0 beyond maxRange (x crowsNestRangeFactor with a manned crow's nest),
 *               linear in between
 *   chance(interval) = 1 - exp(-rate * interval)
 * </pre>
 * Because {@code chance} comes from a rate, two checks of 1 s each detect with exactly the same overall probability
 * as one check of 2 s, so observers can check at whatever interval suits them. The result is always in [0, 1], 0 for
 * a legitimate flag, and grows when the observer is closer, has a manned crow's nest, or the captain's score is
 * higher.
 */
public final class FalseColorsDetection {

    private FalseColorsDetection() {
    }

    /**
     * @param strength             global multiplier (config {@code flags_brig.false_flag_detection_strength}; 0 = never)
     * @param baseRatePerSecond    detections per second at close range, clean record, no crow's nest
     * @param closeRange           blocks within which proximity is 1
     * @param maxRange             blocks beyond which nobody can tell (without crow's nest)
     * @param crowsNestRangeFactor max range multiplier for an observer with a manned crow's nest (>= 1)
     * @param crowsNestRateFactor  rate multiplier for an observer with a manned crow's nest (>= 1)
     * @param scoreScale           criminal score that doubles the rate (> 0)
     */
    public record Params(double strength, double baseRatePerSecond, double closeRange, double maxRange,
                         double crowsNestRangeFactor, double crowsNestRateFactor, double scoreScale) {

        public Params {
            if (strength < 0 || baseRatePerSecond < 0 || closeRange < 0 || maxRange < 0) {
                throw new IllegalArgumentException("detection params must be >= 0");
            }
            if (crowsNestRangeFactor < 1 || crowsNestRateFactor < 1) {
                throw new IllegalArgumentException("crow's nest factors must be >= 1");
            }
            if (scoreScale <= 0) throw new IllegalArgumentException("scoreScale must be > 0");
        }

        public static Params defaults() {
            return new Params(1.0, 0.05, 16.0, 96.0, 1.5, 1.5, 100.0);
        }

        public Params withStrength(double value) {
            return new Params(value, baseRatePerSecond, closeRange, maxRange, crowsNestRangeFactor, crowsNestRateFactor, scoreScale);
        }
    }

    /** 0..1: how well the observer can make out the ship at {@code distance} blocks. */
    public static double proximity(Params p, double distance, boolean crowsNest) {
        double max = p.maxRange() * (crowsNest ? p.crowsNestRangeFactor() : 1.0);
        double close = Math.min(p.closeRange(), max);
        double d = Math.max(0.0, distance);
        if (d <= close) return max > 0 ? 1.0 : 0.0;
        if (d >= max) return 0.0;
        return (max - d) / (max - close);
    }

    /** Detection rate per second (>= 0) for a false flag. */
    public static double ratePerSecond(Params p, double distance, boolean crowsNest, double criminalScore) {
        double rate = p.strength() * p.baseRatePerSecond() * proximity(p, distance, crowsNest)
                * (crowsNest ? p.crowsNestRateFactor() : 1.0)
                * (1.0 + Math.max(0.0, criminalScore) / p.scoreScale());
        return Double.isFinite(rate) ? Math.max(0.0, rate) : Double.MAX_VALUE;
    }

    /**
     * Chance (0..1) that one check covering {@code intervalTicks} of observation detects the false flag.
     * {@code 0} when {@code falseFlag} is false or the interval is not positive.
     */
    public static double chance(Params p, boolean falseFlag, double distance, boolean crowsNest, double criminalScore,
                                long intervalTicks) {
        if (!falseFlag || intervalTicks <= 0) return 0.0;
        double rate = ratePerSecond(p, distance, crowsNest, criminalScore);
        double c = -Math.expm1(-rate * (intervalTicks / 20.0));
        return Math.max(0.0, Math.min(1.0, c));
    }

    /** One roll against {@link #chance}. Pass the level's random in game, a seeded one in tests. */
    public static boolean roll(double chance, RandomSource random) {
        if (chance <= 0) return false;
        if (chance >= 1) return true;
        return random.nextDouble() < chance;
    }
}
