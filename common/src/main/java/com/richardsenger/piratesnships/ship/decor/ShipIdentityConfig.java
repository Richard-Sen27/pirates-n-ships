package com.richardsenger.piratesnships.ship.decor;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/** Server config section {@code ship_identity} (docs/design.md §4.8, §17): how a ship shows its name. */
public final class ShipIdentityConfig {

    private static final ConfigSection SECTION = ModConfigs.server("ship_identity", "Ship identity: the ship's name on nameplates");

    public static final ConfigValue<Boolean> NAMEPLATE_SHOWS_NAME = SECTION.bool("nameplate_shows_name", true,
            "A nameplate on an assembled, named ship shows the ship's name on its board");
    public static final ConfigValue<Integer> NAMEPLATE_REFRESH_TICKS = SECTION.intRange("nameplate_refresh_ticks", 20, 1, 1200,
            "How often a nameplate checks the name of the ship it is on, in ticks (20 ticks = 1 second)");

    private ShipIdentityConfig() {
    }

    public static void init() {
    }
}
