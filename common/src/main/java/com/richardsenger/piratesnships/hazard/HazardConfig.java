package com.richardsenger.piratesnships.hazard;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Server config sections {@code waves} and {@code hazards} and client section {@code wave_effects}
 * (docs/design.md §17, groups "Waves" and "Hazards"; §5.4, §12). The client section can't be called {@code waves}
 * because config translation keys don't include the config type, so it would clash with the server section.
 * Declared ahead of the features by {@code core.settings.SettingsModule}; nothing reads these values yet.
 */
public final class HazardConfig {

    private static final ConfigSection WAVES = ModConfigs.server("waves", "Simulated sea state: wave roll and pitch on ships");

    public static final ConfigValue<Boolean> WAVES_ENABLED = WAVES.bool("enabled", true,
            "The sea state follows the weather and rocks ships with roll and pitch forces. Off = always a flat sea");
    public static final ConfigValue<Double> WAVE_AMPLITUDE = WAVES.doubleRange("amplitude", 1.0, 0.0, 5.0,
            "Multiplier on the roll and pitch forces of waves on ships (1 = normal, 0 = no wave forces)");

    private static final ConfigSection HAZARDS = ModConfigs.server("hazards", "Waterspouts, whirlpools and the kraken");

    private static final ConfigSection WATERSPOUTS = HAZARDS.section("waterspouts",
            "Waterspouts over the ocean during thunderstorms");
    public static final ConfigValue<Boolean> WATERSPOUTS_ENABLED = WATERSPOUTS.bool("enabled", true,
            "Waterspouts form over the ocean during thunderstorms, lift entities and small ships and damage sails");
    public static final ConfigValue<Double> WATERSPOUT_CHANCE_PER_MINUTE = WATERSPOUTS.doubleRange("chance_per_minute", 0.1, 0.0, 1.0,
            "Chance per minute (1200 ticks), for each player at sea during a thunderstorm, that a waterspout forms nearby");

    private static final ConfigSection WHIRLPOOLS = HAZARDS.section("whirlpools", "Whirlpools in the ocean");
    public static final ConfigValue<Boolean> WHIRLPOOLS_ENABLED = WHIRLPOOLS.bool("enabled", true,
            "Whirlpools appear in the ocean, pull ships toward their center and can drag small boats under");
    public static final ConfigValue<Double> WHIRLPOOL_CHANCE_PER_DAY = WHIRLPOOLS.doubleRange("chance_per_day", 0.05, 0.0, 1.0,
            "Chance per in-game day, for each player at sea, that a whirlpool appears nearby");

    private static final ConfigSection KRAKEN = HAZARDS.section("kraken", "The kraken boss of the deep ocean");
    public static final ConfigValue<Boolean> KRAKEN_ENABLED = KRAKEN.bool("enabled", true,
            "The kraken can appear in the deep ocean and attack ships");
    public static final ConfigValue<Double> KRAKEN_CHANCE_PER_DAY = KRAKEN.doubleRange("chance_per_day", 0.02, 0.0, 1.0,
            "Base chance per in-game day, for each player in the deep ocean, that the kraken appears (higher at night and in storms)");

    private static final ConfigSection WAVE_EFFECTS = ModConfigs.client("wave_effects", "Client-side wave visuals");

    public static final ConfigValue<Boolean> CAMERA_SWAY = WAVE_EFFECTS.bool("camera_sway", true,
            "The camera sways with the waves while you are on a ship");
    public static final ConfigValue<Double> CAMERA_SWAY_STRENGTH = WAVE_EFFECTS.doubleRange("camera_sway_strength", 1.0, 0.0, 2.0,
            "Multiplier on how strongly the camera sways with the waves (1 = normal)");

    private HazardConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }
}
