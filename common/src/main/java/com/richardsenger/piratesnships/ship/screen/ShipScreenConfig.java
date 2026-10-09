package com.richardsenger.piratesnships.ship.screen;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/** Server config section {@code ship_screen} (HGUI1, docs/design.md §7.2, §17): the ship screen at the helm. */
public final class ShipScreenConfig {

    private static final ConfigSection S = ModConfigs.server("ship_screen",
            "The ship screen: sneak-use the helm of your assembled ship to manage the ship and its crew");

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "Sneak-using the helm of an assembled ship opens the ship screen. Off = sneak-use disassembles the ship at once, as before");
    public static final ConfigValue<Double> REACH = S.doubleRange("reach", 8.0, 2.0, 32.0,
            "Blocks from the helm within which the screen stays open and its buttons work");
    public static final ConfigValue<Integer> REFRESH_TICKS = S.intRange("refresh_ticks", 20, 5, 200,
            "Ticks between two refreshes of an open screen (sent only when something it shows changed)");

    private ShipScreenConfig() {
    }

    public static void init() {
    }
}
