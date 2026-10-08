package com.richardsenger.piratesnships.ship.hull.client;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Client config section {@code ship_hud} (HUD1, HUD2, docs/design.md §4.6): the ship status overlay. Declared on both
 * sides from {@code HullModule.registerConfig()} so datagen and the config screen see it (no client classes here);
 * only the client reads it. HUD2 replaced {@code corner} with {@code compass_corner} and {@code hull_corner}; the
 * loader drops the old key from an existing file and writes the new ones with their defaults. The server's side is
 * {@code ships.ship_status_hud} and {@code ships.ship_status_sync_interval_ticks}.
 */
public final class ShipHudConfig {

    /** Speed shown on the HUD: knots (1 block = 1 m) or blocks per second. */
    public enum SpeedUnit { KNOTS, BLOCKS }

    private static final ConfigSection S = ModConfigs.client("ship_hud", "Ship status overlay while you are aboard a ship");

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "Show the ship HUD while aboard: compass with heading and wind, speed and rudder, and the water in each compartment");
    public static final ConfigValue<ShipHudLayout.Corner> COMPASS_CORNER = S.enumValue("compass_corner",
            ShipHudLayout.Corner.BOTTOM_LEFT,
            "Screen corner of the compass panel (rose, wind, speed, rudder, name and load): TOP_LEFT, TOP_RIGHT, "
                    + "BOTTOM_LEFT, BOTTOM_RIGHT. It keeps clear of the chat, the hotbar, the stamina bar and the effect "
                    + "icons; set both corners alike to stack the two panels as one");
    public static final ConfigValue<ShipHudLayout.Corner> HULL_CORNER = S.enumValue("hull_corner",
            ShipHudLayout.Corner.BOTTOM_RIGHT,
            "Screen corner of the hull panel (the strip of compartments with water, breaches and pumps): TOP_LEFT, "
                    + "TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT; in the compass panel's corner it stacks below the compass");
    public static final ConfigValue<Double> SCALE = S.doubleRange("scale", 1.0, 0.5, 3.0,
            "Size of the ship HUD panels (1 = 112 x 88 GUI pixels for the compass, 112 x 17 for the hull strip)");
    public static final ConfigValue<SpeedUnit> SPEED_UNIT = S.enumValue("speed_unit", SpeedUnit.KNOTS,
            "Unit of the ship's speed on the HUD: KNOTS (1 block = 1 metre) or BLOCKS (blocks per second)");

    private ShipHudConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }
}
