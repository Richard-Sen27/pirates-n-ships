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
    public static final ConfigValue<Integer> SHIP_UPDATE_INTERVAL_TICKS = SECTION.intRange("ship_update_interval_ticks", 20, 1, 24000,
            "How often a flying flag on a ship re-checks which way is downwind, in ticks (the ship turns under it)");
    public static final ConfigValue<Boolean> CUSTOM_BANNER_FLAGS = SECTION.bool("custom_banner_flags", true,
            "Vanilla banners can be hoisted as custom flags (treated as neutral)");
    public static final ConfigValue<Boolean> STACKED_POLES = SECTION.bool("stacked_poles", true,
            "Flagpoles stacked on each other form one tall pole: the top block flies the flag, the blocks below pass every use up to it. Off: every flagpole block is a pole of its own");
    public static final ConfigValue<Integer> MAX_POLE_HEIGHT = SECTION.intRange("max_pole_height", 6, 1, 16,
            "The most flagpole blocks one stacked pole may have; placing another on a pole this tall is refused");

    private FlagConfig() {
    }

    public static void init() {
    }
}
