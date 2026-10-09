package com.richardsenger.piratesnships.almanac.data;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Server config of the Almanac's data side (ALM2), in the server section {@code almanac}.
 *
 * <p>Merge note: ALM1 owns the {@code almanac} section ({@code almanac.AlmanacConfig}); a section can be declared only
 * once. When both land, {@link #REMEMBER_PAGE} moves into ALM1's class (same name, default and comment) and the field
 * here becomes {@code AlmanacConfig.REMEMBER_PAGE}, so nothing else changes.
 */
public final class AlmanacDataConfig {

    private static final ConfigSection S = ModConfigs.server("almanac", "The Captain's Almanac, the in-game guide book");

    public static final ConfigValue<Boolean> REMEMBER_PAGE = S.bool("remember_page", true,
            "The Almanac opens at the page each player last read, also after death and relog (false: nothing is stored, it opens at the start)");

    private AlmanacDataConfig() {
    }

    /** Loads the class so the values above are declared in time. */
    public static void init() {
    }
}
