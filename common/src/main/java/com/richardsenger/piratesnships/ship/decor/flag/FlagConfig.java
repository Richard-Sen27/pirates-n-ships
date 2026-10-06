package com.richardsenger.piratesnships.ship.decor.flag;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Server config section {@code flags} (docs/design.md §4.7, §17). False-colors detection and NPC surrender live in
 * {@code law/LawConfig} (section {@code flags_brig}); they are not duplicated here.
 */
public final class FlagConfig {

    private static final ConfigSection SECTION = ModConfigs.server("flags", "Flagpoles: hoisting, striking and showing flags");

    public static final ConfigValue<Integer> HOIST_DELAY_TICKS = SECTION.intRange("hoist_delay_ticks", 60, 0, 200,
            "Ticks it takes to hoist, change, strike, raise or take down a flag at a flagpole (20 ticks = 1 second)");
    public static final ConfigValue<Boolean> FOLLOW_WIND = SECTION.bool("follow_wind", true,
            "Flags turn to point downwind (in 90 degree steps), doubling as a wind indicator");
    public static final ConfigValue<Integer> WIND_UPDATE_INTERVAL_TICKS = SECTION.intRange("wind_update_interval_ticks", 200, 20, 24000,
            "How often a flying flag checks the wind direction, in ticks");
    public static final ConfigValue<Boolean> CUSTOM_BANNER_FLAGS = SECTION.bool("custom_banner_flags", true,
            "Vanilla banners can be hoisted as custom flags (treated as neutral)");

    private FlagConfig() {
    }

    public static void init() {
    }
}
