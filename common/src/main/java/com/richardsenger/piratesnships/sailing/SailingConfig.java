package com.richardsenger.piratesnships.sailing;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;
import com.richardsenger.piratesnships.sailing.force.SailingParams;
import com.richardsenger.piratesnships.sailing.wind.WindParams;

/**
 * Server config sections {@code wind} and {@code sailing} (docs/design.md §17). Defaults come from
 * {@link WindParams#DEFAULTS} and {@link SailingParams#DEFAULTS}, so the pure model and the config can't disagree.
 * {@link #windParams()} and {@link #sailingParams()} are the only adapters from config to the pure logic.
 */
public final class SailingConfig {

    private static final WindParams W = WindParams.DEFAULTS;
    private static final SailingParams S = SailingParams.DEFAULTS;
    private static final SailingParams.AnchorParams A = SailingParams.AnchorParams.DEFAULTS;

    private static final ConfigSection WIND = ModConfigs.server("wind", "Global wind field: direction, strength, weather and gusts");

    public static final ConfigValue<Double> VARIABILITY = WIND.doubleRange("variability", W.variability(), 0.0, 10.0,
            "How fast wind direction and strength drift. 1 = direction changes over about one in-game day, 0 = frozen");
    public static final ConfigValue<Double> MIN_STRENGTH = WIND.doubleRange("min_strength", W.minStrength(), 0.0, 50.0,
            "Lowest clear-weather wind speed in blocks per second");
    public static final ConfigValue<Double> MAX_STRENGTH = WIND.doubleRange("max_strength", W.maxStrength(), 0.0, 50.0,
            "Highest clear-weather wind speed in blocks per second (raised to min_strength if lower)");
    public static final ConfigValue<Boolean> WEATHER_AFFECTS_WIND = WIND.bool("weather_affects_wind", W.weatherAffectsWind(),
            "Rain and thunderstorms strengthen the wind and storms bring gusts");
    public static final ConfigValue<Double> RAIN_MULTIPLIER = WIND.doubleRange("rain_multiplier", W.rainMultiplier(), 0.0, 10.0,
            "Wind strength multiplier in rain");
    public static final ConfigValue<Double> THUNDER_MULTIPLIER = WIND.doubleRange("thunder_multiplier", W.thunderMultiplier(), 0.0, 10.0,
            "Wind strength multiplier in thunderstorms");
    public static final ConfigValue<Boolean> GUSTS_ENABLED = WIND.bool("gusts_enabled", W.gustsEnabled(),
            "Thunderstorms produce short gusts of extra wind");
    public static final ConfigValue<Double> GUSTS_PER_MINUTE = WIND.doubleRange("gusts_per_minute", W.gustsPerMinute(), 0.1, 30.0,
            "Average number of gusts per minute during a full thunderstorm");
    public static final ConfigValue<Double> GUST_STRENGTH = WIND.doubleRange("gust_strength", W.gustStrength(), 0.0, 5.0,
            "Extra wind strength at the peak of the strongest gust, as a fraction (0.4 = +40%)");
    public static final ConfigValue<Double> GUST_DIRECTION_SHIFT = WIND.doubleRange("gust_direction_shift", W.gustDirectionShiftDeg(), 0.0, 90.0,
            "Largest change of wind direction during a gust, in degrees");
    public static final ConfigValue<Boolean> REGIONAL_VARIATION = WIND.bool("regional_variation", W.regionalVariation(),
            "Wind direction and strength differ by region instead of being the same everywhere in a dimension");
    public static final ConfigValue<Double> REGIONAL_SCALE = WIND.doubleRange("regional_scale", W.regionalScale(), 64.0, 100000.0,
            "Distance in blocks over which the regional wind pattern changes");
    public static final ConfigValue<Double> REGIONAL_DIRECTION_VARIATION = WIND.doubleRange("regional_direction_variation", W.regionalDirectionDeg(), 0.0, 180.0,
            "Largest regional deviation of the wind direction, in degrees");
    public static final ConfigValue<Double> REGIONAL_STRENGTH_VARIATION = WIND.doubleRange("regional_strength_variation", W.regionalStrengthFraction(), 0.0, 1.0,
            "Largest regional deviation of the wind strength, as a fraction (0.3 = plus or minus 30%)");
    public static final ConfigValue<Integer> SYNC_INTERVAL = WIND.intRange("sync_interval_ticks", 20, 1, 1200,
            "How often the wind is sent to each player, in ticks");

    private static final ConfigSection SAILING = ModConfigs.server("sailing", "Sail, rudder, keel and anchor forces");

    public static final ConfigValue<Double> SAIL_FORCE_SCALE = SAILING.doubleRange("sail_force_scale", S.sailForceScale(), 0.0, 100.0,
            "Sail force per block of sail area and per block/s of apparent wind");
    public static final ConfigValue<Double> HALF_TRIM_FACTOR = SAILING.doubleRange("half_trim_factor", S.halfTrimFactor(), 0.0, 1.0,
            "Fraction of the full sail force a half-set sail produces");
    public static final ConfigValue<Double> RUDDER_STRENGTH = SAILING.doubleRange("rudder_strength", S.rudderStrength(), 0.0, 20.0,
            "Rudder side force per unit of ship mass, forward speed and rudder deflection");
    public static final ConfigValue<Double> MAX_RUDDER_ANGLE = SAILING.doubleRange("max_rudder_angle", S.maxRudderAngleDeg(), 0.0, 90.0,
            "Largest rudder deflection in degrees");
    public static final ConfigValue<Boolean> KEEL_ENABLED = SAILING.bool("keel_enabled", S.keelEnabled(),
            "Hulls resist sideways motion in water (needed to sail across or against the wind)");
    public static final ConfigValue<Double> KEEL_LONGITUDINAL_DRAG = SAILING.doubleRange("keel_longitudinal_drag", S.keelLongitudinalDrag(), 0.0, 10.0,
            "Extra water drag along the hull, per second (fraction of speed lost per second)");
    public static final ConfigValue<Double> KEEL_LATERAL_DRAG = SAILING.doubleRange("keel_lateral_drag", S.keelLateralDrag(), 0.0, 50.0,
            "Extra water drag across the hull, per second");
    public static final ConfigValue<Double> KEEL_YAW_DRAG = SAILING.doubleRange("keel_yaw_drag", S.keelYawDragFactor(), 0.0, 10.0,
            "Multiplier on the turning resistance the keel causes");
    public static final ConfigValue<Double> ANCHOR_STIFFNESS = SAILING.doubleRange("anchor_stiffness", A.stiffness(), 0.0, 10.0,
            "How hard a holding anchor pulls the ship back per block beyond the slack");
    public static final ConfigValue<Double> ANCHOR_DAMPING = SAILING.doubleRange("anchor_damping", A.damping(), 0.0, 20.0,
            "How strongly a holding anchor brakes the ship's horizontal motion");
    public static final ConfigValue<Double> ANCHOR_MAX_ACCELERATION = SAILING.doubleRange("anchor_max_acceleration", A.maxAcceleration(), 0.0, 100.0,
            "Cap of the anchor force divided by ship mass, in blocks per second squared");
    public static final ConfigValue<Double> ANCHOR_SLACK = SAILING.doubleRange("anchor_slack", A.slack(), 0.0, 64.0,
            "Distance in blocks an anchored ship may drift before the rode pulls it back");
    public static final ConfigValue<Integer> ANCHOR_DROP_TICKS = SAILING.intRange("anchor_drop_ticks", A.dropTicks(), 1, 1200,
            "Ticks from dropping the anchor until it holds fully");
    public static final ConfigValue<Integer> ANCHOR_RAISE_TICKS = SAILING.intRange("anchor_raise_ticks", A.raiseTicks(), 1, 2400,
            "Ticks from raising the anchor until it is stowed");

    private static final ConfigSection SHIPS = ModConfigs.server("sailing_runtime", "How sails and the keel act on assembled ships");
    public static final ConfigValue<Boolean> FORCES_ENABLED = SHIPS.bool("forces_enabled", true,
            "Sails and the keel push assembled ships. Off: ships only float and drift");
    public static final ConfigValue<Boolean> SAILS_NEED_WATER = SHIPS.bool("sails_need_water", true,
            "Sails only push a ship that is afloat. Off: wind also pushes ships on land or in the air");
    public static final ConfigValue<Double> FULL_DRAFT = SHIPS.doubleRange("full_draft", 1.0, 0.1, 16.0,
            "Depth of the hull bottom below the sea, in blocks, at which the keel acts at full strength (less depth acts proportionally)");
    public static final ConfigValue<Double> SAIL_HEEL_FACTOR = SHIPS.doubleRange("sail_heel_factor", 0.25, 0.0, 1.0,
            "Fraction of the heeling and pitching moment of sails and keel that is applied (1 = physical; hollow block hulls have no ballast and capsize easily)");
    public static final ConfigValue<Integer> SCAN_INTERVAL = SHIPS.intRange("scan_interval_ticks", 5, 1, 200,
            "How often loaded ships without a sailing state are looked for, in ticks");
    public static final ConfigValue<Boolean> SAIL_BLOCK_TRIM = SHIPS.bool("sail_block_trim", true,
            "Using a sail block cycles its own trim (furled, half, full) without the winch");

    private SailingConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }

    /** Current wind tuning from the server config. */
    public static WindParams windParams() {
        return new WindParams(VARIABILITY.get(), MIN_STRENGTH.get(), MAX_STRENGTH.get(),
                WEATHER_AFFECTS_WIND.get(), RAIN_MULTIPLIER.get(), THUNDER_MULTIPLIER.get(),
                GUSTS_ENABLED.get(), GUSTS_PER_MINUTE.get(), GUST_STRENGTH.get(), GUST_DIRECTION_SHIFT.get(),
                REGIONAL_VARIATION.get(), REGIONAL_SCALE.get(), REGIONAL_DIRECTION_VARIATION.get(),
                REGIONAL_STRENGTH_VARIATION.get());
    }

    /** Current sailing tuning from the server config. */
    public static SailingParams sailingParams() {
        return new SailingParams(SAIL_FORCE_SCALE.get(), HALF_TRIM_FACTOR.get(), RUDDER_STRENGTH.get(),
                MAX_RUDDER_ANGLE.get(), KEEL_ENABLED.get(), KEEL_LONGITUDINAL_DRAG.get(), KEEL_LATERAL_DRAG.get(),
                KEEL_YAW_DRAG.get(), new SailingParams.AnchorParams(ANCHOR_STIFFNESS.get(), ANCHOR_DAMPING.get(),
                ANCHOR_MAX_ACCELERATION.get(), ANCHOR_SLACK.get(), ANCHOR_DROP_TICKS.get(), ANCHOR_RAISE_TICKS.get()));
    }
}
