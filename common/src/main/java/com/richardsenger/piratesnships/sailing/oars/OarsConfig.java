package com.richardsenger.piratesnships.sailing.oars;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/** Server config section {@code oars} (OAR1, docs/design.md §5.3b, §17); {@link #params()} is the adapter to {@link OarRules}. */
public final class OarsConfig {

    private static final OarRules.Params D = OarRules.Params.DEFAULTS;

    private static final ConfigSection S = ModConfigs.server("oars",
            "Rowing: the whistle's Row order sends the crew to the rail or to oar benches to row the ship");

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "The whistle has a Row order, the crew row and the oars push the ship. Off: no rowing, and rowing crews ship their oars");
    public static final ConfigValue<Double> THRUST_PER_ROWER = S.doubleRange("thrust_per_rower", D.thrustPerRower(), 0.0, 1000.0,
            "Force one rower adds along the bow at rest (Sable's units, kpg x blocks/s²; a full sail gives about its area x the wind)");
    public static final ConfigValue<Double> BENCH_BONUS = S.doubleRange("bench_bonus", D.benchBonus(), 1.0, 4.0,
            "A rower seated on an oar bench pulls this many times the thrust of one standing at the rail");
    public static final ConfigValue<Double> MAX_SPEED = S.doubleRange("max_speed", D.maxSpeed(), 0.1, 20.0,
            "Rowing speed limit in blocks per second: the oars' force fades out as the ship's speed along the bow approaches it");
    public static final ConfigValue<Double> SIDE_TORQUE = S.doubleRange("side_torque", D.sideTorque(), 0.0, 8.0,
            "Lever, in blocks from the centre line, of each rower's pull: more rowers on one side turn the ship a little (0 = never)");
    public static final ConfigValue<Double> SHIP_OARS_SPEED_FACTOR = S.doubleRange("ship_oars_speed_factor", D.shipOarsSpeedFactor(), 1.0, 3.0,
            "The crew ship their oars by themselves when the sails drive the ship faster than max_speed times this");
    public static final ConfigValue<Integer> SHIP_OARS_TICKS = S.intRange("ship_oars_ticks", D.shipOarsTicks(), 1, 1200,
            "Ticks the sails must keep the ship that fast before the crew ship their oars");

    private OarsConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }

    public static OarRules.Params params() {
        return new OarRules.Params(THRUST_PER_ROWER.get(), BENCH_BONUS.get(), MAX_SPEED.get(), SIDE_TORQUE.get(),
                SHIP_OARS_SPEED_FACTOR.get(), SHIP_OARS_TICKS.get());
    }
}
