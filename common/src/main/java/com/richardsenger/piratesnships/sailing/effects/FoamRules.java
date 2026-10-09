package com.richardsenger.piratesnships.sailing.effects;

/**
 * Pure rules of the foam streaks (WD1, docs/design.md §5.4 "Seeing wind and waves"): the simulated waves have no
 * visible surface, so faint flat foam streaks lie on the water, stretched along the direction the waves run, more of
 * them the higher the sea, most of them on the crests of the wave field. None in a calm sea. Client-only visuals, no
 * gameplay effect; fed by the synced sea ({@code hazards.waves.ClientWaves}) and the client config
 * ({@code wave_effects.foam*}).
 *
 * <p><b>WAV4:</b> the sea states got higher (calm 0.15, moderate 0.6, rough 1.2, storm 2.0 blocks), and a storm
 * already carpeted the water in streaks (the human: "a bit too many of those wind markers in the water"). So the rate
 * grows with the square root of the height above {@link #minAmplitude} ({@link #rate}: a storm has about twice a
 * moderate sea's foam, not seven times), the default density is half of WD1's, and the foam keeps to the upper half of
 * the waves ({@link #crestKeep}), so the streaks mark the crests instead of covering the sea.
 *
 * @param density      candidate foam streaks per tick at one block of wave height above {@code minAmplitude}
 *                     ({@code wave_effects.foam_density}); the rate grows with the square root of that height
 * @param minAmplitude no foam below this wave height [blocks] ({@code wave_effects.foam_min_amplitude}); the calm sea
 *                     is 0.15 and the moderate 0.6 (times {@code waves.amplitude})
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
    /**
     * Share of the candidate positions kept at or below the still level (WAV4: the troughs and the lower half of the
     * waves); the crest band above it keeps up to all of them.
     */
    public static final double TROUGH_KEEP = 0.1;
    /**
     * Top of the crest band as a share of the sea's amplitude (WAV4): from the still level up to this height the share
     * kept rises smoothly from {@link #TROUGH_KEEP} to 1. The summed trains rarely stand above half the amplitude (their
     * root mean square is about 0.3 of it), so this band is where the drawn crests are.
     */
    public static final double CREST_BAND = 0.5;

    /** WD1's density was 2.5 per block of wave height; WAV4 halved it and made the rate grow as a square root. */
    public static final FoamRules DEFAULTS = new FoamRules(1.25, 0.2, 24.0, 80);

    public FoamRules {
        density = Math.max(0.0, density);
        minAmplitude = Math.max(0.0, minAmplitude);
        radius = Math.max(INNER_RADIUS, radius);
        lifeTicks = Math.max(1, lifeTicks);
    }

    /**
     * Mean candidate positions per tick for a sea with crests {@code amplitude} blocks high: {@code density ×
     * √(amplitude − minAmplitude)}, nothing below {@link #minAmplitude} (a calm sea). The square root saturates: with
     * the defaults a moderate sea (0.6) gets 0.79, a rough one (1.2) 1.25 and a storm (2.0) 1.68, so a storm carries
     * about twice a moderate sea's foam (WD1's linear rate gave 3.3×, and with WAV4's heights it would have been 5×
     * WD1's moderate sea). Each candidate is then kept with the probability {@link #crestKeep}, on average about 0.3.
     */
    public double rate(double amplitude) {
        if (!(amplitude > 0.0) || amplitude < minAmplitude) {
            return 0.0;
        }
        return density * Math.sqrt(amplitude - minAmplitude);
    }

    /**
     * Mean foam streaks alive around the camera in a steady sea of {@code amplitude}, before the video setting and the
     * candidates that fall on land or in a dry hull: {@link #rate} × the mean share kept × {@link #lifeTicks}, with
     * {@code meanKeep} the average of {@link #crestKeep} over the sea (about 0.31 for the spectrum of WAV2).
     */
    public double streaksAlive(double amplitude, double meanKeep) {
        return rate(amplitude) * meanKeep * lifeTicks;
    }

    /**
     * Probability of keeping a candidate where the wave field stands {@code height} above still water in a sea of crest
     * height {@code amplitude}: {@link #TROUGH_KEEP} at or below the still level, rising smoothly (a smoothstep) to 1 at
     * {@link #CREST_BAND} × the amplitude and held above. So the foam gathers in bands along the crests, across the
     * direction the waves run, and the troughs stay nearly clear.
     */
    public static double crestKeep(double height, double amplitude) {
        if (!(amplitude > 0.0)) {
            return 0.0;
        }
        double t = Math.max(0.0, Math.min(1.0, height / (CREST_BAND * amplitude)));
        return TROUGH_KEEP + (1.0 - TROUGH_KEEP) * t * t * (3.0 - 2.0 * t);
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
