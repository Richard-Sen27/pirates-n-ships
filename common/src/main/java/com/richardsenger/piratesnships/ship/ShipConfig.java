package com.richardsenger.piratesnships.ship;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Server config section {@code ships} (docs/design.md §17, group "Ships"; §4.1 shipwright orders, §4.5 sinking).
 * Max block count and assembly enabled belong to the assembly package and are not declared here. Declared ahead of
 * the features by the settings package ({@code core.settings.SettingsModule}); nothing reads these values yet. The
 * ship feature package takes this class over when it is written.
 */
public final class ShipConfig {

    private static final ConfigSection S = ModConfigs.server("ships", "Sinking, wrecks and shipwright orders");

    public static final ConfigValue<Boolean> SINKING_ENABLED = S.bool("sinking_enabled", true,
            "Ships that lose their buoyancy sink to the seabed. Off = ships stay afloat however much water they take on");
    public static final ConfigValue<Integer> WRECK_PERSISTENCE_DAYS = S.intRange("wreck_persistence_days", 0, 0, 10000,
            "In-game days a sunk ship stays a sub-level on the seabed before it turns back into world blocks (0 = forever)");
    public static final ConfigValue<Boolean> SHIPWRIGHT_ORDERS = S.bool("shipwright_orders", true,
            "Shipwrights in seafarer villages sell prebuilt ships that are picked up at the village dock");

    private static final ConfigSection BUILD_TIME = S.section("build_time_days",
            "In-game days a shipwright needs to build each ship type");

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

    private ShipConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }
}
