package com.richardsenger.piratesnships.ship.hull.client;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Client config section {@code ship_hud} (HUD1, docs/design.md §4.6): the ship status overlay. Declared on both sides
 * from {@code HullModule.registerConfig()} so datagen and the config screen see it (no client classes here); only the
 * client reads it. The server's side is {@code ships.ship_status_hud} and {@code ships.ship_status_sync_interval_ticks}.
 */
public final class ShipHudConfig {

    /** Speed shown on the HUD: knots (1 block = 1 m) or blocks per second. */
    public enum SpeedUnit { KNOTS, BLOCKS }

    private static final ConfigSection S = ModConfigs.client("ship_hud", "Ship status overlay while you are aboard a ship");

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "Show the ship HUD while aboard: compass with heading and wind, speed and rudder, and the water in each compartment");
    public static final ConfigValue<ShipHudLayout.Corner> CORNER = S.enumValue("corner", ShipHudLayout.Corner.TOP_RIGHT,
            "Screen corner of the ship HUD (TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT); at the top right it moves "
                    + "down below the status effect icons");
    public static final ConfigValue<Double> SCALE = S.doubleRange("scale", 1.0, 0.5, 3.0,
            "Size of the ship HUD (1 = 112 x 104 GUI pixels)");
    public static final ConfigValue<SpeedUnit> SPEED_UNIT = S.enumValue("speed_unit", SpeedUnit.KNOTS,
            "Unit of the ship's speed on the HUD: KNOTS (1 block = 1 metre) or BLOCKS (blocks per second)");

    private ShipHudConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }
}
