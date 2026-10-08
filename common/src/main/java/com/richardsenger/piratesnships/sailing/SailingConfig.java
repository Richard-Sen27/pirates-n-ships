package com.richardsenger.piratesnships.sailing;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;
import com.richardsenger.piratesnships.sailing.force.HullDampingModel;
import com.richardsenger.piratesnships.sailing.force.SailingParams;
import com.richardsenger.piratesnships.sailing.sail.StayRules;
import com.richardsenger.piratesnships.sailing.sail.YardRules;
import com.richardsenger.piratesnships.sailing.wind.WindParams;

/**
 * Server config sections {@code wind} and {@code sailing} (docs/design.md §17). Defaults come from
 * {@link WindParams#DEFAULTS} and {@link SailingParams#DEFAULTS}, so the pure model and the config can't disagree.
 * {@link #windParams()} and {@link #sailingParams()} are the only adapters from config to the pure logic.
 */
public final class SailingConfig {

    private static final WindParams W = WindParams.DEFAULTS;
    private static final SailingParams S = SailingParams.DEFAULTS;
    private static final HullDampingModel.Params D = HullDampingModel.Params.DEFAULTS;

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

    private static final ConfigSection SAILING = ModConfigs.server("sailing", "Sail, rudder, keel and hull damping forces (the anchor's chain: anchor_chain)");

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
    public static final ConfigValue<Boolean> HULL_DAMPING_ENABLED = SAILING.bool("hull_damping_enabled", D.enabled(),
            "The water damps a floating ship's rolling and pitching, so it settles after a wave or a gust. Off: it rocks on without end");
    public static final ConfigValue<Double> ROLL_DAMPING = SAILING.doubleRange("roll_damping", D.roll(), 0.0, 10.0,
            "How fast the side-to-side rolling dies down, per second (higher = settles sooner, too high looks glued to the water)");
    public static final ConfigValue<Double> PITCH_DAMPING = SAILING.doubleRange("pitch_damping", D.pitch(), 0.0, 10.0,
            "How fast the bow-up, bow-down pitching dies down, per second");

    private static final ConfigSection SAILS = SAILING.section("sails", "Square sails (two yards on one mast), triangular sails (a rope stay between cleats) and rope lines");
    public static final ConfigValue<Integer> YARD_MIN_GAP = SAILS.intRange("yard_min_gap", YardRules.DEFAULTS.minGap(), 1, 32,
            "Smallest height difference, in blocks, between the upper and the lower yard of a square sail");
    public static final ConfigValue<Integer> YARD_MAX_GAP = SAILS.intRange("yard_max_gap", YardRules.DEFAULTS.maxGap(), 1, 32,
            "Largest height difference, in blocks, between the upper and the lower yard of a square sail (raised to yard_min_gap if lower)");
    public static final ConfigValue<Integer> YARD_MAX_LENGTH = SAILS.intRange("yard_max_length", YardRules.DEFAULTS.maxLength(), 1, 31,
            "Longest yard in blocks; a longer row of yard blocks carries no sail. Odd lengths center the yard on the mast");
    public static final ConfigValue<Integer> YARD_REFRESH_TICKS = SAILS.intRange("yard_refresh_ticks", 20, 1, 1200,
            "How often, in ticks, each yard and each cleat with a stay re-checks the sail it heads for the cloth display (placing or breaking one updates at once)");
    public static final ConfigValue<Integer> STAY_MAX_LENGTH = SAILS.intRange("stay_max_length", StayRules.DEFAULTS.maxLength(), 2, 64,
            "Longest rope stay of a triangular sail: largest distance in blocks between its two cleats");
    public static final ConfigValue<Integer> STAY_MIN_DROP = SAILS.intRange("stay_min_drop", StayRules.DEFAULTS.minDrop(), 1, 32,
            "Smallest height difference in blocks between the two cleats of a rope stay");
    public static final ConfigValue<Boolean> ROPE_LINES = SAILS.bool("rope_lines", true,
            "A rope between two cleats, a cleat and a mooring ring, or two rings on one ship (or both on land) that makes no sail "
                    + "stays as a decorative rope line, up to stay_max_length long. Off = the rope only rigs stays, and existing lines are not drawn");
    public static final ConfigValue<Double> ROPE_SAG = SAILS.doubleRange("rope_sag", 0.08, 0.0, 0.5,
            "How far a rope line hangs down in the middle, as a fraction of its horizontal span (0 = taut)");

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
            "Using a yard that heads a square sail, or the head cleat of a triangular sail, cycles that sail's trim (furled, half, full) without the winch");
    public static final ConfigValue<Boolean> STEERING_ENABLED = SHIPS.bool("steering_enabled", true,
            "Using the helm of an assembled ship turns its rudder, and the rudder turns the ship. Off: the rudder has no effect");
    public static final ConfigValue<Integer> RUDDER_STEPS = SHIPS.intRange("rudder_steps", 3, 1, 5,
            "Rudder steps on each side of midships; the last step is max_rudder_angle");
    public static final ConfigValue<Boolean> ANCHOR_ENABLED = SHIPS.bool("anchor_enabled", true,
            "The capstan drops and raises an anchor whose chain holds the ship. Off: capstans do nothing and dropped anchors stop holding");
    public static final ConfigValue<Integer> ANCHOR_CHAIN_LENGTH = SHIPS.intRange("anchor_chain_length", 32, 1, 256,
            "Length of the anchor chain in blocks: how far the falling anchor can run out from the hawse. No ground this far below the hawse: the anchor can't be dropped");

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

    /** Current square sail limits from the server config. */
    public static YardRules yardRules() {
        return new YardRules(YARD_MIN_GAP.get(), YARD_MAX_GAP.get(), YARD_MAX_LENGTH.get());
    }

    /** Current triangular sail limits from the server config. */
    public static StayRules stayRules() {
        return new StayRules(STAY_MAX_LENGTH.get(), STAY_MIN_DROP.get());
    }

    /** Current hull damping tuning from the server config. */
    public static HullDampingModel.Params hullDampingParams() {
        return new HullDampingModel.Params(HULL_DAMPING_ENABLED.get(), ROLL_DAMPING.get(), PITCH_DAMPING.get());
    }

    /** Current sailing tuning from the server config. */
    public static SailingParams sailingParams() {
        return new SailingParams(SAIL_FORCE_SCALE.get(), HALF_TRIM_FACTOR.get(), RUDDER_STRENGTH.get(),
                MAX_RUDDER_ANGLE.get(), KEEL_ENABLED.get(), KEEL_LONGITUDINAL_DRAG.get(), KEEL_LATERAL_DRAG.get(),
                KEEL_YAW_DRAG.get());
    }
}
