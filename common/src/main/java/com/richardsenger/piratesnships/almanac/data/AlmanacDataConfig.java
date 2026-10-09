package com.richardsenger.piratesnships.almanac.data;

import com.richardsenger.piratesnships.almanac.AlmanacConfig;
import com.richardsenger.piratesnships.core.config.ConfigValue;

/**
 * Server config of the Almanac's data side (ALM2). The values live in the one {@code almanac} section of
 * {@link AlmanacConfig} (a section can be declared only once); this class keeps the data side's names.
 */
public final class AlmanacDataConfig {

    public static final ConfigValue<Boolean> REMEMBER_PAGE = AlmanacConfig.REMEMBER_PAGE;

    private AlmanacDataConfig() {
    }

    /** Loads the class so the values above are declared in time. */
    public static void init() {
        AlmanacConfig.init();
    }
}
