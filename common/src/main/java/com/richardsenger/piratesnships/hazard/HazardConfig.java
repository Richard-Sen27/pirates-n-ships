package com.richardsenger.piratesnships.hazard;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;
import com.richardsenger.piratesnships.sailing.effects.FoamRules;

/**
 * Server config section {@code waves} and client section {@code wave_effects} (docs/design.md §17, group "Waves";
 * §5.4; read by {@code hazards.waves} and {@code sailing.waves}, WV1). The client section can't be called {@code waves}
 * because config translation keys don't include the config type, so it would clash with the server section. Loaded by
 * {@code core.settings.SettingsModule}; it stays in this package so the settings module and its tests keep their
 * imports. The {@code hazards} section belongs to {@code hazards.HazardsConfig} (H1).
 */
public final class HazardConfig {

    private static final ConfigSection WAVES = ModConfigs.server("waves", "Simulated sea state: wave roll and pitch on ships");

    public static final ConfigValue<Boolean> WAVES_ENABLED = WAVES.bool("enabled", true,
            "The sea state follows the weather, rocks ships with roll and pitch and spills water into low open hulls. Off = always a flat sea");
    public static final ConfigValue<Double> WAVE_AMPLITUDE = WAVES.doubleRange("amplitude", 1.0, 0.0, 5.0,
            "Multiplier on the wave height of every sea state (calm 0.1, moderate 0.3, rough 0.7, storm 1.2 blocks); 0 = flat sea");
    public static final ConfigValue<Double> SHIP_TORQUE = WAVES.doubleRange("ship_torque", 5.5, 0.0, 50.0,
            "Roll and pitch torque per unit of wave slope and per kpg of ship mass, scaled by 1 / sqrt(blocks / 200)");
    public static final ConfigValue<Double> MAX_TORQUE_PER_MASS = WAVES.doubleRange("max_torque_per_mass", 2.0, 0.0, 20.0,
            "Upper limit of the wave torque per kpg of ship mass, so that small boats are not flipped");
    public static final ConfigValue<Double> STATE_CHANGE_SECONDS = WAVES.doubleRange("state_change_seconds", 60.0, 1.0, 1200.0,
            "Seconds the sea takes to go from calm to storm (or back) when the weather changes");
    public static final ConfigValue<Boolean> SPILL = WAVES.bool("spill", true,
            "Wave crests spill water over low rims and through low open hatches into compartments");
    public static final ConfigValue<Integer> SYNC_INTERVAL_TICKS = WAVES.intRange("sync_interval_ticks", 60, 10, 1200,
            "Ticks between sea state updates sent to players (clients blend between them)");

    private static final ConfigSection WAVE_EFFECTS = ModConfigs.client("wave_effects", "Client-side wave visuals");

    public static final ConfigValue<Boolean> CAMERA_SWAY = WAVE_EFFECTS.bool("camera_sway", false,
            "The camera rolls with the ship while you are aboard");
    public static final ConfigValue<Double> CAMERA_SWAY_FRACTION = WAVE_EFFECTS.doubleRange("camera_sway_fraction", 0.5, 0.0, 1.0,
            "Fraction of the ship's roll the camera follows (1 = the full roll)");
    public static final ConfigValue<Boolean> SPRAY = WAVE_EFFECTS.bool("spray", true,
            "Spray and a splash at the bow when it digs into a wave in rough or stormy seas");
    // Foam streaks on the water (WD1, read through sailing.effects.SeaEffectsConfig#foam)
    public static final ConfigValue<Boolean> FOAM = WAVE_EFFECTS.bool("foam", true,
            "Faint foam streaks on the water, stretched along the direction the waves run, more in a higher sea; none in a calm sea");
    public static final ConfigValue<Double> FOAM_DENSITY = WAVE_EFFECTS.doubleRange("foam_density", FoamRules.DEFAULTS.density(), 0.0, 20.0,
            "Foam streaks tried per tick for every block of wave height (about half of them land, mostly on the crests)");
    public static final ConfigValue<Double> FOAM_MIN_AMPLITUDE = WAVE_EFFECTS.doubleRange("foam_min_amplitude", FoamRules.DEFAULTS.minAmplitude(), 0.0, 5.0,
            "No foam below this wave height in blocks (calm sea 0.1, moderate 0.3, rough 0.7, storm 1.2)");
    public static final ConfigValue<Integer> FOAM_RADIUS = WAVE_EFFECTS.intRange("foam_radius", (int) FoamRules.DEFAULTS.radius(), 4, 64,
            "Horizontal distance from the camera in blocks within which foam appears");
    public static final ConfigValue<Integer> FOAM_LIFE_TICKS = WAVE_EFFECTS.intRange("foam_life_ticks", FoamRules.DEFAULTS.lifeTicks(), 10, 400,
            "Life of one foam streak in ticks, including fading in and out");

    private HazardConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }
}
