package com.richardsenger.piratesnships.hazards.waves;

import com.richardsenger.piratesnships.sailing.wind.WindNoise;
import org.jetbrains.annotations.Nullable;

/**
 * How the sea state follows the weather (docs/design.md §5.4, WV1). Pure: no world access.
 *
 * <ul>
 *   <li><b>Target state</b>: thunder (vanilla thunder level ≥ 0.5) → storm; rain (rain level ≥ 0.5) → rough; clear →
 *       calm or moderate, chosen by a slow value noise of game time ({@link #clearNoise}: moderate while it is above
 *       {@link #MODERATE_ABOVE}), so a clear day has calm and choppy stretches of a few in-game hours.</li>
 *   <li><b>Easing</b>: the sea's amplitude moves linearly toward the target state's, at a rate that takes
 *       {@code state_change_seconds} from calm to storm ({@link #ease}).</li>
 *   <li><b>Direction</b>: the waves run with the wind, offset by a slow noise of up to ±{@link #DIRECTION_NOISE_DEG}
 *       ({@link #directionDegrees}).</li>
 * </ul>
 */
public final class SeaStateModel {

    /** Rain or thunder level from which the weather counts. */
    public static final double WEATHER_LEVEL = 0.5;
    /** Clear weather is moderate while {@link #clearNoise} is above this. */
    public static final double MODERATE_ABOVE = 0.15;
    /** Time scale of the clear-weather calm / moderate noise [ticks]: about a third of a day. */
    public static final double CLEAR_PERIOD = 8000.0;
    /** Largest offset of the wave direction from the wind direction [degrees]. */
    public static final double DIRECTION_NOISE_DEG = 25.0;
    /** Time scale of the direction offset [ticks]. */
    public static final double DIRECTION_PERIOD = 6000.0;

    private static final long CH_CLEAR = 41;
    private static final long CH_DIRECTION = 43;

    private SeaStateModel() {
    }

    /** The sea state the weather asks for. */
    public static SeaState target(double rainLevel, double thunderLevel, double clearNoise) {
        if (thunderLevel >= WEATHER_LEVEL) {
            return SeaState.STORM;
        }
        if (rainLevel >= WEATHER_LEVEL) {
            return SeaState.ROUGH;
        }
        return clearNoise > MODERATE_ABOVE ? SeaState.MODERATE : SeaState.CALM;
    }

    /**
     * The state the sea eases toward when nothing holds one (GR6): {@link #target} from the weather, except on a
     * GameTest server, where it is always {@link SeaState#CALM}. A GameTest world has clear weather, so the sea would
     * otherwise be calm or moderate depending on {@link #clearNoise} of the game time the runner happens to be at, i.e.
     * on the test order. An override (a wave test's hold, {@code /pirates waves set}) still wins
     * ({@link Tracker#tick}).
     */
    public static SeaState weatherTarget(boolean gameTestServer, double rainLevel, double thunderLevel, double clearNoise) {
        return gameTestServer ? SeaState.CALM : target(rainLevel, thunderLevel, clearNoise);
    }

    /** The slow clear-weather noise in [-1, 1] at {@code gameTime}. */
    public static double clearNoise(long seed, double gameTime) {
        return WindNoise.fbm1(seed, CH_CLEAR, gameTime / CLEAR_PERIOD);
    }

    /** Wave direction (compass bearing the waves run toward) for a wind blowing toward {@code windTowardDeg}. */
    public static double directionDegrees(double windTowardDeg, long seed, double gameTime) {
        return windTowardDeg + DIRECTION_NOISE_DEG * WindNoise.fbm1(seed, CH_DIRECTION, gameTime / DIRECTION_PERIOD);
    }

    /** Amplitude change per tick that takes {@code changeSeconds} from calm to storm. */
    public static double ratePerTick(double changeSeconds) {
        return (SeaState.STORM.amplitude() - SeaState.CALM.amplitude()) / Math.max(1.0, changeSeconds * 20.0);
    }

    /** One tick of easing: {@code current} moves toward {@code target} by at most {@code ratePerTick}. */
    public static double ease(double current, double target, double ratePerTick) {
        if (current < target) {
            return Math.min(target, current + ratePerTick);
        }
        return Math.max(target, current - ratePerTick);
    }

    /**
     * The sea of one dimension: the eased amplitude (of the state, before the config multiplier) and an optional
     * override from {@code /pirates waves set} or a test, which sets the amplitude at once and holds it.
     */
    public static final class Tracker {

        /** No expiry for an override. */
        public static final long FOREVER = Long.MAX_VALUE;

        private double amplitude = Double.NaN;
        private SeaState target = SeaState.CALM;
        private @Nullable SeaState override;
        private @Nullable Double overrideDirection;
        private long overrideUntil = FOREVER;
        private WaveField.Origin origin = WaveField.Origin.NONE;

        /**
         * One tick at {@code gameTime}: eases toward the weather's {@code target}, or holds the override (dropped once
         * its expiry has passed). The first tick starts at the target.
         */
        public void tick(SeaState weatherTarget, double ratePerTick, long gameTime) {
            if (override != null && gameTime >= overrideUntil) {
                clearOverride();
            }
            target = override != null ? override : weatherTarget;
            if (Double.isNaN(amplitude)) {
                amplitude = target.amplitude();
            } else {
                amplitude = ease(amplitude, target.amplitude(), ratePerTick);
            }
        }

        /**
         * Sets an override: the amplitude jumps to the state's and stays until {@link #clearOverride} or until game time
         * {@code untilGameTime} (exclusive; {@link #FOREVER} for none).
         */
        public void setOverride(SeaState state, @Nullable Double directionDegrees, long untilGameTime) {
            setOverride(state, directionDegrees, untilGameTime, WaveField.Origin.NONE);
        }

        /**
         * {@link #setOverride(SeaState, Double, long)} with the field's time and space pinned to {@code origin} while the
         * override holds (tests: a phase that does not depend on the game time or the structure's place).
         */
        public void setOverride(SeaState state, @Nullable Double directionDegrees, long untilGameTime, WaveField.Origin origin) {
            this.origin = origin;
            override = state;
            overrideDirection = directionDegrees;
            overrideUntil = untilGameTime;
            target = state;
            amplitude = state.amplitude();
        }

        /** Drops the override; the sea eases back to the weather's state from where it is. */
        public void clearOverride() {
            override = null;
            overrideDirection = null;
            overrideUntil = FOREVER;
            origin = WaveField.Origin.NONE;
        }

        /** Origin of the field's phases: {@link WaveField.Origin#NONE} unless an override pinned one. */
        public WaveField.Origin origin() {
            return origin;
        }

        public @Nullable SeaState override() {
            return override;
        }

        /** Fixed wave direction of the override (compass bearing toward), or null to follow the wind. */
        public @Nullable Double overrideDirection() {
            return overrideDirection;
        }

        /** The eased amplitude of the state [blocks] (0 before the first tick). */
        public double amplitude() {
            return Double.isNaN(amplitude) ? 0.0 : amplitude;
        }

        public SeaState target() {
            return target;
        }

        /** The state the sea is in now (the nearest to the eased amplitude). */
        public SeaState current() {
            return SeaState.nearest(amplitude());
        }
    }
}
