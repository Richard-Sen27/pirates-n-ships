package com.richardsenger.piratesnships.sailing.wind;

/**
 * The global wind field (docs/design.md §5.1): a pure, stateless function of world seed, dimension, game time,
 * weather and (optionally) position. Every server and every test computes the same wind for the same inputs, so
 * nothing needs to be saved.
 *
 * <h2>Model</h2>
 * <ul>
 *   <li><b>Direction</b>: {@code base(seed) + TURNS · 360° · fbm(t / DIRECTION_PERIOD · variability)}. Two octaves of
 *       value noise with {@link #DIRECTION_TURNS} = 1.5 full turns of amplitude, so over long times the bearing covers
 *       the whole compass while changing at most {@link #maxDirectionChangePerTick} per tick.</li>
 *   <li><b>Base strength</b>: {@code min + (max − min) · clamp(0.5 + 0.75 · fbm(t / STRENGTH_PERIOD · variability))}
 *       [blocks/s], always inside [min, max].</li>
 *   <li><b>Weather</b>: {@code 1 + (rainMul − 1) · rain + (thunderMul − rainMul) · thunder}, with the vanilla rain and
 *       thunder levels (0..1). Vanilla ramps those by 0.01 per tick and its thunder level never exceeds its rain level,
 *       so the factor moves smoothly from 1 (clear) through {@code rainMul} (rain) to {@code thunderMul} (thunder).</li>
 *   <li><b>Gusts</b>: time is cut into slots of {@code 1200 / gustsPerMinute} ticks. Each slot holds at most one gust
 *       (75% chance) of 40..100 ticks with a {@code sin²} envelope, a hashed peak strength and a hashed direction shift.
 *       Gust intensity is scaled by the thunder level, so there are none in clear weather or plain rain.</li>
 *   <li><b>Regional variation</b> (toggle): 2D noise over {@code regionalScale} blocks offsets the direction by up to
 *       {@code regionalDirectionDeg} and scales the strength by up to ±{@code regionalStrengthFraction}. Off = the same
 *       wind everywhere in the dimension.</li>
 * </ul>
 */
public final class WindField {

    /** Time scale of direction changes at variability 1: one in-game day [ticks]. */
    public static final double DIRECTION_PERIOD = 24000.0;
    /** Time scale of strength changes at variability 1: a quarter day [ticks]. */
    public static final double STRENGTH_PERIOD = 6000.0;
    /** Amplitude of the direction noise in full turns. */
    public static final double DIRECTION_TURNS = 1.5;
    /** Spread factor of the strength noise around the middle of [min, max] (clamped). */
    public static final double STRENGTH_SPREAD = 0.75;
    /** Chance that a gust slot actually holds a gust. */
    public static final double GUST_CHANCE = 0.75;
    public static final double GUST_MIN_TICKS = 40.0;
    public static final double GUST_MAX_TICKS = 100.0;

    private static final long CH_DIRECTION = 1;
    private static final long CH_STRENGTH = 2;
    private static final long CH_GUST = 3;
    private static final long CH_REGION_DIR = 4;
    private static final long CH_REGION_STRENGTH = 5;
    private static final long CH_BASE = 6;

    private WindField() {
    }

    /** Folds the world seed and the dimension id (e.g. {@code "minecraft:overworld"}) into one noise seed. */
    public static long fieldSeed(long worldSeed, String dimension) {
        return WindNoise.mix(worldSeed ^ WindNoise.mix(dimension.hashCode() * 0x632BE59BD9B4E019L));
    }

    /**
     * Samples the wind.
     *
     * @param p            tuning values
     * @param worldSeed    the world seed
     * @param dimension    dimension id, e.g. {@code "minecraft:overworld"}
     * @param gameTime     game time [ticks] (fractional values allowed for interpolation)
     * @param rainLevel    vanilla rain level, 0..1 (clamped)
     * @param thunderLevel vanilla thunder level, 0..1 (clamped)
     * @param x            world x [blocks]; only used with regional variation
     * @param z            world z [blocks]; only used with regional variation
     */
    public static WindSample sample(WindParams p, long worldSeed, String dimension, double gameTime,
                                    double rainLevel, double thunderLevel, double x, double z) {
        long seed = fieldSeed(worldSeed, dimension);
        double rain = clamp01(rainLevel);
        double thunder = clamp01(thunderLevel);

        double dirT = gameTime * p.variability() / DIRECTION_PERIOD;
        double strT = gameTime * p.variability() / STRENGTH_PERIOD;
        double base = WindNoise.unit(WindNoise.hash(seed, CH_BASE, 0, 0)) * 360.0;
        double direction = base + DIRECTION_TURNS * 360.0 * WindNoise.fbm1(seed, CH_DIRECTION, dirT);
        double s01 = clamp01(0.5 + STRENGTH_SPREAD * WindNoise.fbm1(seed, CH_STRENGTH, strT));
        double strength = p.minStrength() + (p.maxStrength() - p.minStrength()) * s01;

        if (p.regionalVariation()) {
            double rx = x / p.regionalScale();
            double rz = z / p.regionalScale();
            direction += p.regionalDirectionDeg() * WindNoise.fbm2(seed, CH_REGION_DIR, rx, rz);
            strength *= 1.0 + p.regionalStrengthFraction() * WindNoise.fbm2(seed, CH_REGION_STRENGTH, rx + 31.7, rz - 12.9);
        }

        double weather = 1.0;
        double gust = 0.0;
        if (p.weatherAffectsWind()) {
            weather = weatherMultiplier(p, rain, thunder);
            if (p.gustsEnabled() && thunder > 0.0) {
                double[] g = gust(p, seed, gameTime);
                gust = g[0] * thunder;
                direction += p.gustDirectionShiftDeg() * g[1] * gust;
            }
        }
        strength *= weather * (1.0 + p.gustStrength() * gust);
        return WindSample.of(direction, Math.max(0.0, strength), weather, gust);
    }

    /** The weather factor for vanilla rain and thunder levels (both 0..1). */
    public static double weatherMultiplier(WindParams p, double rainLevel, double thunderLevel) {
        double rain = clamp01(rainLevel);
        double thunder = clamp01(thunderLevel);
        return Math.max(0.0, 1.0 + (p.rainMultiplier() - 1.0) * rain + (p.thunderMultiplier() - p.rainMultiplier()) * thunder);
    }

    /**
     * Gust state at full storm intensity: {@code [intensity 0..1, direction sign -1..1]}. Deterministic in time; the
     * envelope is {@code sin²}, so it starts and ends at zero without jumps.
     */
    static double[] gust(WindParams p, long seed, double gameTime) {
        double slot = 1200.0 / p.gustsPerMinute();
        long k = (long) Math.floor(gameTime / slot);
        long h = WindNoise.hash(seed, CH_GUST, k, 0);
        if (WindNoise.unit(h) >= GUST_CHANCE) {
            return new double[]{0.0, 0.0};
        }
        double duration = Math.min(slot, GUST_MIN_TICKS + (GUST_MAX_TICKS - GUST_MIN_TICKS) * WindNoise.unit(WindNoise.mix(h + 1)));
        double start = (slot - duration) * WindNoise.unit(WindNoise.mix(h + 2));
        double local = gameTime - k * slot - start;
        if (local <= 0.0 || local >= duration) {
            return new double[]{0.0, 0.0};
        }
        double s = Math.sin(Math.PI * local / duration);
        double peak = 0.5 + 0.5 * WindNoise.unit(WindNoise.mix(h + 3));
        double sign = WindNoise.signed(WindNoise.mix(h + 4));
        return new double[]{s * s * peak, sign};
    }

    /** Upper bound of the base direction change per tick (no gusts, no movement) [degrees/tick]. */
    public static double maxDirectionChangePerTick(WindParams p) {
        return DIRECTION_TURNS * 360.0 * WindNoise.FBM_MAX_SLOPE * p.variability() / DIRECTION_PERIOD;
    }

    /** Upper bound of the clear-weather strength change per tick (no gusts, no movement) [blocks/s per tick]. */
    public static double maxStrengthChangePerTick(WindParams p) {
        double regional = p.regionalVariation() ? 1.0 + p.regionalStrengthFraction() : 1.0;
        return (p.maxStrength() - p.minStrength()) * STRENGTH_SPREAD * WindNoise.FBM_MAX_SLOPE * p.variability()
                / STRENGTH_PERIOD * regional;
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
