package com.richardsenger.piratesnships.sailing.effects;

/**
 * Pure rules of the foam streaks (WD1, docs/design.md §5.4 "Seeing wind and waves"): the simulated waves have no
 * visible surface, so faint flat foam streaks lie on the water, stretched along the direction the waves run, more of
 * them the higher the sea, most of them on the crests of the wave field. None in a calm sea. Client-only visuals, no
 * gameplay effect; fed by the synced sea ({@code hazards.waves.ClientWaves}) and the client config
 * ({@code wave_effects.foam*}).
 *
 * @param density      candidate foam streaks per tick per block of wave height ({@code wave_effects.foam_density})
 * @param minAmplitude no foam below this wave height [blocks] ({@code wave_effects.foam_min_amplitude}); the calm sea
 *                     is 0.1 and the moderate 0.3 (times {@code waves.amplitude})
 * @param radius       horizontal distance from the camera [blocks] ({@code wave_effects.foam_radius})
 * @param lifeTicks    life of one foam streak [ticks] ({@code wave_effects.foam_life_ticks})
 */
public record FoamRules(double density, double minAmplitude, double radius, int lifeTicks) {

    /** Foam starts at least this far from the camera [blocks]. */
    public static final double INNER_RADIUS = 2.0;
    /** Height above the water surface, against z-fighting [blocks]. */
    public static final double LIFT = 0.02;
    /** Drift along the wave direction per block of wave height [blocks/s]. */
    public static final double DRIFT_PER_AMPLITUDE = 0.5;
    /** Shortest and longest streak [blocks] (the length grows with the sea). */
    public static final double MIN_LENGTH = 1.5, MAX_LENGTH = 5.0;
    /** Share of the candidate positions kept in the deepest trough; the highest crest keeps all of its candidates. */
    public static final double TROUGH_KEEP = 0.15;

    public static final FoamRules DEFAULTS = new FoamRules(2.5, 0.2, 24.0, 80);

    public FoamRules {
        density = Math.max(0.0, density);
        minAmplitude = Math.max(0.0, minAmplitude);
        radius = Math.max(INNER_RADIUS, radius);
        lifeTicks = Math.max(1, lifeTicks);
    }

    /**
     * Mean candidate positions per tick for a sea with crests {@code amplitude} blocks high: {@code density ×
     * amplitude}, nothing below {@link #minAmplitude} (a calm sea). Each candidate is then kept with the probability
     * {@link #crestKeep}, so on average a bit more than half of them become foam.
     */
    public double rate(double amplitude) {
        if (!(amplitude > 0.0) || amplitude < minAmplitude) {
            return 0.0;
        }
        return density * amplitude;
    }

    /**
     * Probability of keeping a candidate where the wave field stands {@code height} above still water in a sea of crest
     * height {@code amplitude}: {@link #TROUGH_KEEP} in the deepest trough, 1 on the highest crest, linear between. So
     * the foam gathers in bands along the crests, across the direction the waves run.
     */
    public static double crestKeep(double height, double amplitude) {
        if (!(amplitude > 0.0)) {
            return 0.0;
        }
        double t = Math.max(0.0, Math.min(1.0, (height / amplitude + 1.0) * 0.5));
        return TROUGH_KEEP + (1.0 - TROUGH_KEEP) * t;
    }

    /** Length of a foam streak [blocks] in a sea of {@code amplitude}: longer in a higher sea. */
    public static double length(double amplitude) {
        return Math.max(MIN_LENGTH, Math.min(MAX_LENGTH, MIN_LENGTH + Math.max(0.0, amplitude) * 3.0));
    }

    /** Drift along the wave direction [blocks/tick]. */
    public static double driftPerTick(double amplitude) {
        return Math.max(0.0, amplitude) * DRIFT_PER_AMPLITUDE / 20.0;
    }

    /** Opacity in [0, 1] of a fully faded-in streak: faint in a moderate sea, up to 0.55 in a storm. */
    public static double opacity(double amplitude) {
        return Math.max(0.15, Math.min(0.55, 0.15 + Math.max(0.0, amplitude) * 0.35));
    }

    /** Whether a camera at {@code camY} is near enough to the water at {@code seaY} for foam to be worth spawning. */
    public boolean inRange(double camY, double seaY) {
        return camY > seaY - 2.0 && camY < seaY + 2.0 * radius;
    }
}
