package com.richardsenger.piratesnships.mob.kraken;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.hazards.HazardsConfig;
import com.richardsenger.piratesnships.mob.MobConfig;

/**
 * Server config section {@code mobs.kraken} (work package K1a, docs/design.md §12, §17): the kraken's fight. Whether it
 * exists and how often it appears are {@code hazards.kraken.enabled} and {@code hazards.kraken.chance_per_day}
 * ({@link HazardsConfig#KRAKEN_ENABLED}, {@link HazardsConfig#KRAKEN_CHANCE_PER_DAY}); the spawn checks run at
 * {@code hazards.spawn_check_interval_ticks}, and the grip's mass cap is {@code hazards.max_ship_mass_effect}.
 */
public final class KrakenConfig {

    private static final ConfigSection S = MobConfig.KRAKEN;

    public static final ConfigValue<Boolean> PEACEFUL = S.bool("peaceful", false,
            "The kraken never attacks: it stays in the deep and ignores ships and swimmers");
    public static final ConfigValue<Double> DETECTION_RANGE = S.doubleRange("detection_range", 32.0, 4.0, 128.0,
            "Blocks within which the kraken notices a ship or a swimmer and drifts toward it");
    public static final ConfigValue<Double> TENTACLE_REACH = S.doubleRange("tentacle_reach", 10.0, 3.0, 32.0,
            "Blocks from the kraken's body that a tentacle reaches");
    public static final ConfigValue<Double> GRIP_FORCE = S.doubleRange("grip_force", 3.0, 0.0, 100.0,
            "Pull of one gripping tentacle on a ship, as an acceleration in m/s² of a ship up to hazards.max_ship_mass_effect "
                    + "(heavier ships feel the force of a ship at that mass; gravity is about 11). Downward, plus grip_side_fraction "
                    + "of it toward the kraken");
    public static final ConfigValue<Integer> MAX_GRIPS = S.intRange("max_grips", 4, 0, 8,
            "Most tentacles gripping a ship's hull at once (the others beat the masts, sweep the deck and grab swimmers)");
    public static final ConfigValue<Double> GRIP_SIDE_FRACTION = S.doubleRange("grip_side_fraction", 0.35, 0.0, 2.0,
            "Sideways part of a grip's pull (toward the kraken) as a fraction of the downward part; makes a big ship list");
    public static final ConfigValue<Double> TENTACLE_HEALTH = S.doubleRange("tentacle_health", 40.0, 1.0, 1000.0,
            "Damage a tentacle takes before it is cut (it lets go and grows back)");
    public static final ConfigValue<Integer> TENTACLE_REGROW_TICKS = S.intRange("tentacle_regrow_ticks", 600, 1, 72000,
            "Ticks a cut tentacle needs to grow back; it can't be hit or used meanwhile");
    public static final ConfigValue<Double> TENTACLE_BODY_SHARE = S.doubleRange("tentacle_body_share", 0.5, 0.0, 1.0,
            "Fraction of the damage dealt to a tentacle that the kraken's body takes too");
    public static final ConfigValue<Double> EYE_DAMAGE_MULTIPLIER = S.doubleRange("eye_damage_multiplier", 3.0, 1.0, 20.0,
            "Damage multiplier of hits on the kraken's eyes (its weak spots)");
    public static final ConfigValue<Integer> MAST_STRIKE_INTERVAL_TICKS = S.intRange("mast_strike_interval_ticks", 100, 1, 72000,
            "Ticks between two mast blocks broken by a tentacle (respects the mobGriefing game rule)");
    public static final ConfigValue<Integer> SWIPE_INTERVAL_TICKS = S.intRange("swipe_interval_ticks", 60, 1, 72000,
            "Ticks between two swipes of a tentacle across a ship's deck");
    public static final ConfigValue<Double> SWIPE_KNOCKBACK = S.doubleRange("swipe_knockback", 1.5, 0.0, 10.0,
            "Sideways speed (blocks/tick) a swipe gives to everyone on deck within 3 blocks of the tentacle");
    public static final ConfigValue<Double> SWIPE_DAMAGE = S.doubleRange("swipe_damage", 4.0, 0.0, 100.0,
            "Damage of a swipe");
    public static final ConfigValue<Double> GRAB_DAMAGE = S.doubleRange("grab_damage", 2.0, 0.0, 100.0,
            "Damage per second to a swimmer held under water by a tentacle");
    public static final ConfigValue<Integer> GRAB_HOLD_TICKS = S.intRange("grab_hold_ticks", 60, 1, 1200,
            "Ticks a tentacle holds a swimmer under before letting go");
    public static final ConfigValue<Integer> ATTACK_DURATION_TICKS = S.intRange("attack_duration_ticks", 1200, 20, 72000,
            "Ticks of attacking after which the kraken gives up and sinks away");
    public static final ConfigValue<Double> RETREAT_HEALTH_FRACTION = S.doubleRange("retreat_health_fraction", 0.3, 0.0, 1.0,
            "Below this fraction of its health the kraken lets go of everything and sinks away (0 = fights to the death)");
    public static final ConfigValue<Double> NIGHT_MULTIPLIER = S.doubleRange("night_multiplier", 3.0, 0.0, 100.0,
            "Multiplier on hazards.kraken.chance_per_day at night");
    public static final ConfigValue<Double> THUNDER_MULTIPLIER = S.doubleRange("thunder_multiplier", 3.0, 0.0, 100.0,
            "Multiplier on hazards.kraken.chance_per_day during a thunderstorm (stacks with the night)");
    public static final ConfigValue<Integer> MIN_SEPARATION = S.intRange("min_separation", 200, 0, 10000,
            "No kraken appears within this many blocks of another one");
    public static final ConfigValue<Integer> MIN_WATER_DEPTH = S.intRange("min_water_depth", 20, 4, 200,
            "Blocks of water a kraken needs under the surface where it appears");

    private KrakenConfig() {
    }

    /** The brain's parameters from the current config. */
    public static KrakenBrain.Params brain() {
        return new KrakenBrain.Params(PEACEFUL.get(), RETREAT_HEALTH_FRACTION.get(), ATTACK_DURATION_TICKS.get());
    }

    /** Loads the class so the values above are declared in time. Called from {@code MobConfig.init()}. */
    public static void init() {
    }
}
