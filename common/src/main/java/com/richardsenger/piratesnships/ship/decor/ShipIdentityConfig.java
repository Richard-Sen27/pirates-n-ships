package com.richardsenger.piratesnships.ship.decor;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Server config section {@code ship_identity} (docs/design.md §4.8, §17): how a ship shows its name on name boards
 * (NAME2; the keys keep the G8 nameplate's names).
 */
public final class ShipIdentityConfig {

    private static final ConfigSection SECTION = ModConfigs.server("ship_identity", "Ship identity: the ship's name on name boards");

    public static final ConfigValue<Boolean> NAMEPLATE_SHOWS_NAME = SECTION.bool("nameplate_shows_name", true,
            "A name board on an assembled, named ship shows the ship's name across its row");
    public static final ConfigValue<Integer> NAMEPLATE_REFRESH_TICKS = SECTION.intRange("nameplate_refresh_ticks", 20, 1, 1200,
            "How often a name board checks the name of the ship it is on and its place in its row, in ticks (20 ticks = 1 second)");
    public static final ConfigValue<Integer> NAME_BOARD_DISTANCE = SECTION.intRange("name_board_distance", 64, 16, 256,
            "How far away, in blocks, the name on a name board is still drawn");

    private ShipIdentityConfig() {
    }

    public static void init() {
    }
}
