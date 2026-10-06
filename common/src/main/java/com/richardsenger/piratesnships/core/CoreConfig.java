package com.richardsenger.piratesnships.core;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/** Server config section of the {@code core} module. The pattern every module's config class follows. */
public final class CoreConfig {

    private static final ConfigSection SECTION = ModConfigs.server("core", "General settings");

    /** Extra debug logging. */
    public static final ConfigValue<Boolean> DEBUG = SECTION.bool("debug", false,
            "Log extra debug information from Pirates 'n' Ships systems");

    private CoreConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }
}
