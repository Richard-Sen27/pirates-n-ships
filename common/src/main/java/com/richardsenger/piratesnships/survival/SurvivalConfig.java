package com.richardsenger.piratesnships.survival;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Server config section {@code survival} (docs/design.md §17, group "Survival"; §14). Declared ahead of the feature
 * by {@code core.settings.SettingsModule}; nothing reads these values yet.
 */
public final class SurvivalConfig {

    private static final ConfigSection S = ModConfigs.server("survival", "Cold water and swimming hunger");

    public static final ConfigValue<Boolean> COLD_WATER_ENABLED = S.bool("cold_water_enabled", true,
            "Being in the water of cold and frozen ocean biomes fills a freezing meter that ends in freezing damage");
    public static final ConfigValue<Integer> TIME_TO_FREEZE_SECONDS = S.intRange("time_to_freeze_seconds", 30, 1, 3600,
            "Seconds in cold water until the freezing meter is full and freezing damage starts");
    public static final ConfigValue<Double> SWIMMING_HUNGER_MULTIPLIER = S.doubleRange("swimming_hunger_multiplier", 2.0, 0.0, 20.0,
            "Multiplier on the exhaustion (hunger) caused by swimming (1 = vanilla)");

    private SurvivalConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }
}
