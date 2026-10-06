package com.richardsenger.piratesnships.audio.creak;

import java.util.random.RandomGenerator;
import org.jetbrains.annotations.Nullable;

/**
 * Decides, tick by tick, when one ship's planks creak (docs/design.md §16): now and then while the ship rolls or
 * pitches, more often when it moves harder, never more often than the minimum interval and never while it lies still.
 * Pure and per ship: feed it the ship's rocking rate once per game tick.
 *
 * <h2>Rule</h2>
 * <ul>
 *   <li>The <b>rocking rate</b> {@code r} is the size of the roll and pitch part of the angular velocity [rad/s].
 *       Below {@code rollRateThreshold} nothing happens.</li>
 *   <li>From the threshold on, a creak plays in a tick with probability
 *       {@code min(1, creaksPerSecond / 20 · r / threshold)}: at the threshold the mean rate is
 *       {@code creaksPerSecond}, twice the rate gives twice the chance.</li>
 *   <li>After a creak, no creak for {@code minIntervalTicks}.</li>
 *   <li>Volume is uniform in {@code [minVolume, maxVolume]} scaled toward the top by the intensity (a gentle roll is
 *       quieter), pitch is uniform in {@code [minPitch, maxPitch]}, and the position is a uniform random point of the
 *       hull box, in its lower half (where the planks work), as fractions 0..1 of the box.</li>
 * </ul>
 */
public final class CreakTrigger {

    /**
     * Creak tuning.
     *
     * @param enabled           whether ships creak at all
     * @param creaksPerSecond   mean creaks per second at the threshold rate (before the minimum interval)
     * @param minIntervalTicks  shortest time between two creaks of one ship [ticks]
     * @param minVolume         quietest creak
     * @param maxVolume         loudest creak
     * @param minPitch          lowest pitch
     * @param maxPitch          highest pitch
     * @param rollRateThreshold rocking rate from which a ship counts as rolling [rad/s]
     */
    public record Params(boolean enabled, double creaksPerSecond, int minIntervalTicks, double minVolume,
                         double maxVolume, double minPitch, double maxPitch, double rollRateThreshold) {

        public static final Params DEFAULTS = new Params(true, 0.2, 40, 0.25, 0.6, 0.5, 0.8, 0.05);

        public Params {
            creaksPerSecond = Math.max(0.0, creaksPerSecond);
            minIntervalTicks = Math.max(1, minIntervalTicks);
            minVolume = Math.max(0.0, minVolume);
            maxVolume = Math.max(minVolume, maxVolume);
            minPitch = Math.max(0.01, minPitch);
            maxPitch = Math.max(minPitch, maxPitch);
            rollRateThreshold = Math.max(1.0e-4, rollRateThreshold);
        }
    }

    /** One creak to play: volume, pitch and the position as fractions 0..1 of the hull box. */
    public record Creak(float volume, float pitch, double fx, double fy, double fz) {
    }

    private int ticksSinceLast = Integer.MAX_VALUE / 2;

    /**
     * Advances one tick.
     *
     * @param rockingRate size of the roll and pitch angular velocity [rad/s]
     * @return the creak to play this tick, or null
     */
    public @Nullable Creak tick(double rockingRate, Params p, RandomGenerator random) {
        if (ticksSinceLast < Integer.MAX_VALUE / 2) {
            ticksSinceLast++;
        }
        double rate = Math.abs(rockingRate);
        if (!p.enabled() || !(rate >= p.rollRateThreshold()) || ticksSinceLast < p.minIntervalTicks()) {
            return null;
        }
        double intensity = rate / p.rollRateThreshold();
        double chance = Math.min(1.0, p.creaksPerSecond() / 20.0 * intensity);
        if (!(random.nextDouble() < chance)) {
            return null;
        }
        ticksSinceLast = 0;
        // intensity 1 → up to 60% of the volume span, from intensity 3 on the whole span
        double span = Math.min(1.0, 0.4 + 0.2 * intensity);
        float volume = (float) (p.minVolume() + (p.maxVolume() - p.minVolume()) * span * random.nextDouble());
        float pitch = (float) (p.minPitch() + (p.maxPitch() - p.minPitch()) * random.nextDouble());
        return new Creak(volume, pitch, random.nextDouble(), 0.5 * random.nextDouble(), random.nextDouble());
    }

    /** Ticks since the last creak (saturates; large before the first). */
    public int ticksSinceLast() {
        return ticksSinceLast;
    }
}
