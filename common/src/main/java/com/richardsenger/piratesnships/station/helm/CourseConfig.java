package com.richardsenger.piratesnships.station.helm;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.station.StationConfig;

/** Server config of the NPC helmsman (WS3a, docs/design.md §6, §17), section {@code crew_stations.course}. */
public final class CourseConfig {

    private static final ConfigSection S = StationConfig.section("course",
            "A crew member at the helm holds a course toward a point or along waypoints with the rudder");

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "Crew members at the helm hold courses. Off: courses end, the rudder is put midships and the helm takes no course orders");
    public static final ConfigValue<Double> GAIN = S.doubleRange("gain", CourseKeeper.Params.DEFAULTS.gain(), 0.0, 20.0,
            "Rudder degrees the helmsman gives per degree of heading error (higher = harder, quicker steering)");
    public static final ConfigValue<Double> DEADBAND_DEGREES = S.doubleRange("deadband_degrees", CourseKeeper.Params.DEFAULTS.deadbandDegrees(), 0.0, 45.0,
            "Heading errors up to this many degrees leave the rudder midships");
    public static final ConfigValue<Double> ANTICIPATION_SECONDS = S.doubleRange("anticipation_seconds", CourseKeeper.Params.DEFAULTS.anticipationSeconds(), 0.0, 20.0,
            "How many seconds ahead the helmsman judges the bow's swing, easing the rudder off before the bow points at the target (0 = no anticipation)");
    public static final ConfigValue<Double> ARRIVAL_RADIUS = S.doubleRange("arrival_radius", 8.0, 1.0, 128.0,
            "A waypoint counts as reached within this many blocks (horizontal distance from the ship's center)");
    public static final ConfigValue<Integer> STUCK_TICKS = S.intRange("stuck_ticks", 200, 20, 24000,
            "A ship with sails set that moves slower than stuck_speed for this many ticks is reported stuck");
    public static final ConfigValue<Double> STUCK_SPEED = S.doubleRange("stuck_speed", 0.3, 0.0, 10.0,
            "Speed in blocks per second under which a ship with sails set counts as not moving");
    public static final ConfigValue<Integer> UPDATE_INTERVAL_TICKS = S.intRange("update_interval_ticks", 5, 1, 100,
            "Ticks between two rudder corrections of the helmsman");
    public static final ConfigValue<Integer> MANUAL_OVERRIDE_TICKS = S.intRange("manual_override_ticks", 100, 0, 2400,
            "After a player moved the wheel (by a click step, or holding the wheel), the helmsman leaves the rudder alone for this many ticks");

    private CourseConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }

    /** The controller parameters at the current config. */
    public static CourseKeeper.Params params() {
        return new CourseKeeper.Params(GAIN.get(), DEADBAND_DEGREES.get(), ANTICIPATION_SECONDS.get());
    }
}
