package com.richardsenger.piratesnships.sailing.effects;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;
import com.richardsenger.piratesnships.hazard.HazardConfig;

/**
 * Client config section {@code wind_effects} (docs/design.md §17, §5.4 "Seeing wind and waves", WD1): the wind streaks.
 * The foam's values live in the existing client section {@code wave_effects} ({@link HazardConfig}). Defaults come from
 * {@link WindStreakRules#DEFAULTS}, {@link WindStreakStyle#DEFAULTS} (WD2) and {@link FoamRules#DEFAULTS};
 * {@link #windStreaks()}, {@link #windStyle()} and {@link #foam()} are the only adapters from config to the pure rules.
 * Client visuals only, no gameplay effect.
 */
public final class SeaEffectsConfig {

    private static final WindStreakRules W = WindStreakRules.DEFAULTS;
    private static final WindStreakStyle S = WindStreakStyle.DEFAULTS;

    private static final ConfigSection WIND_EFFECTS = ModConfigs.client("wind_effects", "Client-side wind visuals: white streaks drifting with the wind");

    public static final ConfigValue<Boolean> STREAKS = WIND_EFFECTS.bool("streaks", true,
            "White wind streaks drift with the wind through the air over the sea, so you can see where the wind blows");
    public static final ConfigValue<Double> DENSITY = WIND_EFFECTS.doubleRange("density", W.density(), 0.0, 2.0,
            "Wind streaks spawned per tick for every block per second of wind speed");
    public static final ConfigValue<Double> MIN_STRENGTH = WIND_EFFECTS.doubleRange("min_strength", W.minStrength(), 0.0, 50.0,
            "No streaks below this wind speed in blocks per second (calm air)");
    public static final ConfigValue<Double> GUST_BOOST = WIND_EFFECTS.doubleRange("gust_boost", W.gustBoost(), 0.0, 5.0,
            "Extra streaks at the peak of a gust, as a fraction (1 = twice as many)");
    public static final ConfigValue<Integer> RADIUS = WIND_EFFECTS.intRange("radius", (int) W.radius(), 4, 64,
            "Horizontal distance from the camera in blocks within which streaks appear");
    public static final ConfigValue<Integer> HEIGHT = WIND_EFFECTS.intRange("height", (int) W.height(), 2, 48,
            "Streaks fly from 1 block up to at most this many blocks above the sea (the top of the rigging)");
    public static final ConfigValue<Integer> LIFE_TICKS = WIND_EFFECTS.intRange("life_ticks", W.lifeTicks(), 10, 200,
            "Mean life of one streak in ticks, including fading in and out; each lives 0.625 to 1.5 times this");
    public static final ConfigValue<Double> SPEED_SPREAD = WIND_EFFECTS.doubleRange("speed_spread", S.speedSpread(), 0.0, 0.9,
            "Each streak flies at 1 plus or minus this times the wind speed (0.3: 0.7 to 1.3 times)");
    public static final ConfigValue<Double> HEADING_JITTER = WIND_EFFECTS.doubleRange("heading_jitter_degrees",
            S.headingJitterDegrees(), 0.0, 45.0, "Each streak's heading strays up to this many degrees from the wind's");
    public static final ConfigValue<Double> WOBBLE = WIND_EFFECTS.doubleRange("wobble", S.wobble(), 0.0, 2.0,
            "Largest gentle up-and-down wobble of a streak over its flight in blocks (each wobbles 0.2 to 1 times this)");
    public static final ConfigValue<Double> HEIGHT_PEAK = WIND_EFFECTS.doubleRange("height_peak", S.heightPeak(), 1.0, 48.0,
            "Most streaks fly this many blocks above the sea (deck height), fewer higher up toward 'height'");
    public static final ConfigValue<Double> PUFF_SHARE = WIND_EFFECTS.doubleRange("puff_share", S.puffShare(), 0.0, 1.0,
            "Share of the streaks that come in loose puffs in a steady wind (the rest trickle singly); a gust raises it to all");
    public static final ConfigValue<Integer> PUFF_SIZE = WIND_EFFECTS.intRange("puff_size", S.puffSize(), 1, 16,
            "Most streaks in one puff (a puff has half this, rounded up, to this many)");
    public static final ConfigValue<Double> PUFF_SPREAD = WIND_EFFECTS.doubleRange("puff_spread", S.puffSpread(), 0.0, 8.0,
            "A puff's streaks start within this many blocks of its centre");
    public static final ConfigValue<Integer> PUFF_TICKS = WIND_EFFECTS.intRange("puff_ticks", S.puffTicks(), 1, 40,
            "A puff's streaks are born over this many ticks");
    public static final ConfigValue<Double> OPACITY = WIND_EFFECTS.doubleRange("opacity", S.opacity(), 0.0, 1.0,
            "Opacity of a streak in a strong wind (12 blocks per second and more); at 4 blocks per second it is 0.3 times this");

    private SeaEffectsConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code SailingModule.registerConfig()}. */
    public static void init() {
    }

    /** The wind streak rules from the current client config. */
    public static WindStreakRules windStreaks() {
        return new WindStreakRules(DENSITY.get(), MIN_STRENGTH.get(), GUST_BOOST.get(), RADIUS.get(), HEIGHT.get(),
                LIFE_TICKS.get());
    }

    /** How the wind streaks look and move (WD2) from the current client config. */
    public static WindStreakStyle windStyle() {
        return new WindStreakStyle(SPEED_SPREAD.get(), HEADING_JITTER.get(), WOBBLE.get(), HEIGHT_PEAK.get(),
                PUFF_SHARE.get(), PUFF_SIZE.get(), PUFF_SPREAD.get(), PUFF_TICKS.get(), OPACITY.get());
    }

    /** The foam rules from the current client config ({@code wave_effects.foam_*}). */
    public static FoamRules foam() {
        return new FoamRules(HazardConfig.FOAM_DENSITY.get(), HazardConfig.FOAM_MIN_AMPLITUDE.get(),
                HazardConfig.FOAM_RADIUS.get(), HazardConfig.FOAM_LIFE_TICKS.get());
    }
}
