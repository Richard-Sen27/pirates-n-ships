package com.richardsenger.piratesnships.hazard;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Server config section {@code waves} and client section {@code wave_effects} (docs/design.md §17, group "Waves";
 * §5.4). The client section can't be called {@code waves} because config translation keys don't include the config
 * type, so it would clash with the server section. Declared ahead of the feature by {@code core.settings.SettingsModule};
 * nothing reads these values yet. The {@code hazards} section belongs to {@code hazards.HazardsConfig} (H1).
 */
public final class HazardConfig {

    private static final ConfigSection WAVES = ModConfigs.server("waves", "Simulated sea state: wave roll and pitch on ships");

    public static final ConfigValue<Boolean> WAVES_ENABLED = WAVES.bool("enabled", true,
            "The sea state follows the weather and rocks ships with roll and pitch forces. Off = always a flat sea");
    public static final ConfigValue<Double> WAVE_AMPLITUDE = WAVES.doubleRange("amplitude", 1.0, 0.0, 5.0,
            "Multiplier on the roll and pitch forces of waves on ships (1 = normal, 0 = no wave forces)");

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
