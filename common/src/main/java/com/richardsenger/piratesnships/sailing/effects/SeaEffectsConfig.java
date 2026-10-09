package com.richardsenger.piratesnships.sailing.effects;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;
import com.richardsenger.piratesnships.hazard.HazardConfig;

/**
 * Client config section {@code wind_effects} (docs/design.md §17, §5.4 "Seeing wind and waves", WD1): the wind streaks.
 * The foam's values live in the existing client section {@code wave_effects} ({@link HazardConfig}). Defaults come from
 * {@link WindStreakRules#DEFAULTS} and {@link FoamRules#DEFAULTS}; {@link #windStreaks()} and {@link #foam()} are the
 * only adapters from config to the pure rules. Client visuals only, no gameplay effect.
 */
public final class SeaEffectsConfig {

    private static final WindStreakRules W = WindStreakRules.DEFAULTS;

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
            "Streaks fly from 1 block up to this many blocks above the sea (deck height and the rigging)");
    public static final ConfigValue<Integer> LIFE_TICKS = WIND_EFFECTS.intRange("life_ticks", W.lifeTicks(), 10, 200,
            "Life of one streak in ticks, including fading in and out");

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

    /** The foam rules from the current client config ({@code wave_effects.foam_*}). */
    public static FoamRules foam() {
        return new FoamRules(HazardConfig.FOAM_DENSITY.get(), HazardConfig.FOAM_MIN_AMPLITUDE.get(),
                HazardConfig.FOAM_RADIUS.get(), HazardConfig.FOAM_LIFE_TICKS.get());
    }
}
