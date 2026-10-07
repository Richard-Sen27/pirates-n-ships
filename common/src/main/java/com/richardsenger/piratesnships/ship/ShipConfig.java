package com.richardsenger.piratesnships.ship;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Server config section {@code ships} (docs/design.md §17, group "Ships"; §4.1 shipwright orders, §4.5 sinking).
 * Max block count and assembly enabled belong to the assembly package and are not declared here. Declared ahead of
 * the features by the settings package ({@code core.settings.SettingsModule}). The ship HUD values are read by
 * {@code ship.hull.net.ShipStatusSync} (HUD1). The shipwright orders (SW1, {@code ship.template.ShipOrders}) read
 * {@link #SHIPWRIGHT_ORDERS}, {@link #buildDays} and the order values below; {@code order_price_doubloons.*} stays
 * declared but unread (the price is the template's {@code price} times {@link #ORDER_PRICE_FACTOR}).
 */
public final class ShipConfig {

    private static final ConfigSection S = ModConfigs.server("ships", "Sinking, wrecks and shipwright orders");

    public static final ConfigValue<Boolean> SINKING_ENABLED = S.bool("sinking_enabled", true,
            "Ships that lose their buoyancy sink to the seabed. Off = ships stay afloat however much water they take on");
    public static final ConfigValue<Integer> WRECK_PERSISTENCE_DAYS = S.intRange("wreck_persistence_days", 0, 0, 10000,
            "In-game days a sunk ship stays a sub-level on the seabed before it turns back into world blocks (0 = forever)");
    public static final ConfigValue<Boolean> SHIPWRIGHT_ORDERS = S.bool("shipwright_orders", true,
            "Shipwrights in seafarer villages sell prebuilt ships that are picked up at the village dock");

    public static final ConfigValue<Double> ORDER_PRICE_FACTOR = S.doubleRange("order_price_factor", 1.0, 0.0, 100.0,
            "Multiplier on the doubloon price of each ship template a shipwright sells (0 = free)");
    public static final ConfigValue<Double> ORDER_MATERIALS_FACTOR = S.doubleRange("order_materials_factor", 1.0, 0.0, 100.0,
            "Multiplier on the logs and wool a shipwright wants for an ordered ship (0 = no materials)");
    public static final ConfigValue<Integer> MAX_ORDERS_PER_PORT = S.intRange("max_orders_per_port", 3, 0, 100,
            "Ship orders a seafarer village's shipwright takes at once (open orders of all players, picked up ones free a slot)");

    // Ship HUD (HUD1, ship.hull.net.ShipStatusSync): read by the hull module
    public static final ConfigValue<Boolean> SHIP_STATUS_HUD = S.bool("ship_status_hud", true,
            "Send players aboard a ship its status (heading, speed, rudder, water per compartment) for the ship HUD. "
                    + "Off = the HUD shows for nobody");
    public static final ConfigValue<Integer> SHIP_STATUS_SYNC_INTERVAL_TICKS = S.intRange("ship_status_sync_interval_ticks", 20, 1, 200,
            "How often (ticks) the server checks the ship status of players aboard and sends it when it changed "
                    + "(unchanged, it still goes out every fifth time)");

    private static final ConfigSection BUILD_TIME = S.section("build_time_days",
            "In-game days a shipwright needs to build each ship type, for a ship of 500 blocks (scaled by the template's block count)");

    public static final ConfigValue<Double> BUILD_DAYS_DEFAULT = BUILD_TIME.doubleRange("default", 1.0, 0.0, 100.0,
            "In-game days a shipwright needs per 500 blocks of a ship template without its own entry here (0 = ready at once)");

    public static final ConfigValue<Double> BUILD_DAYS_SLOOP = BUILD_TIME.doubleRange("sloop", 2.0, 0.0, 100.0,
            "In-game days a shipwright needs to build a sloop (0 = ready at once)");
    public static final ConfigValue<Double> BUILD_DAYS_MERCHANT_COG = BUILD_TIME.doubleRange("merchant_cog", 3.0, 0.0, 100.0,
            "In-game days a shipwright needs to build a merchant cog (0 = ready at once)");
    public static final ConfigValue<Double> BUILD_DAYS_BRIGANTINE = BUILD_TIME.doubleRange("brigantine", 4.0, 0.0, 100.0,
            "In-game days a shipwright needs to build a brigantine (0 = ready at once)");

    private static final ConfigSection PRICES = S.section("order_price_doubloons",
            "Doubloons a shipwright charges for each ship type, on top of the materials");

    public static final ConfigValue<Integer> PRICE_SLOOP = PRICES.intRange("sloop", 200, 0, 1000000,
            "Doubloons a shipwright charges for a sloop, on top of the materials");
    public static final ConfigValue<Integer> PRICE_MERCHANT_COG = PRICES.intRange("merchant_cog", 350, 0, 1000000,
            "Doubloons a shipwright charges for a merchant cog, on top of the materials");
    public static final ConfigValue<Integer> PRICE_BRIGANTINE = PRICES.intRange("brigantine", 500, 0, 1000000,
            "Doubloons a shipwright charges for a brigantine, on top of the materials");

    /** The build time entries by template id path (the {@code default} entry is not among them). */
    private static final java.util.Map<String, ConfigValue<Double>> BUILD_DAYS = java.util.Map.of(
            "sloop", BUILD_DAYS_SLOOP, "merchant_cog", BUILD_DAYS_MERCHANT_COG, "brigantine", BUILD_DAYS_BRIGANTINE);

    private ShipConfig() {
    }

    /**
     * Build days per 500 blocks of the template whose id path is {@code templatePath}: {@code build_time_days.<path>}
     * if declared, else {@code build_time_days.default}.
     */
    public static double buildDays(String templatePath) {
        ConfigValue<Double> v = BUILD_DAYS.get(templatePath);
        return (v != null ? v : BUILD_DAYS_DEFAULT).get();
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }
}
