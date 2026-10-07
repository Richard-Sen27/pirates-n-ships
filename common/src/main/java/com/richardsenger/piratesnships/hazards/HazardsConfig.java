package com.richardsenger.piratesnships.hazards;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Server config section {@code hazards} and client section {@code hazard_visuals} (docs/design.md §12, §17 "Hazards:
 * waterspouts / whirlpools / kraken: enabled + frequency each"). Takes over the {@code hazards} placeholder that
 * {@code core.settings} declared in {@code hazard.HazardConfig}; the kraken values are still placeholders that nothing
 * reads yet.
 *
 * <p>Units: field strengths ({@code pull}, {@code lift}, {@code spin}, {@code drag_down}) are accelerations in
 * blocks/tick² at the centre, falling off linearly to 0 at {@code radius}. Ships get the same field as an acceleration of
 * {@code 100 × strength} m/s² (a quarter of an entity's) times {@code ship_force_scale} times the mass effect
 * {@code min(1, max_ship_mass_effect / mass)} (see {@link HazardField#shipForce}).
 */
public final class HazardsConfig {

    private static final ConfigSection S = ModConfigs.server("hazards", "Waterspouts, whirlpools and the kraken");

    public static final ConfigValue<Integer> SPAWN_CHECK_INTERVAL_TICKS = S.intRange("spawn_check_interval_ticks", 200, 20, 24000,
            "Ticks between the spawn checks for waterspouts and whirlpools (each check rolls once per player at sea)");
    public static final ConfigValue<Integer> SPAWN_MIN_DISTANCE = S.intRange("spawn_min_distance", 48, 8, 256,
            "Nearest distance in blocks from the player at which a hazard forms");
    public static final ConfigValue<Integer> SPAWN_MAX_DISTANCE = S.intRange("spawn_max_distance", 96, 8, 256,
            "Farthest distance in blocks from the player at which a hazard forms (the chunk must be loaded)");
    public static final ConfigValue<Double> SHIP_FORCE_SCALE = S.doubleRange("ship_force_scale", 1.0, 0.0, 20.0,
            "Multiplier on the pull, lift and spin of hazards on ships (1 = a light ship is accelerated at 100 m/s² per "
                    + "blocks/tick² of field, a quarter of what entities get; 0 = ships are not affected)");
    public static final ConfigValue<Double> MAX_SHIP_MASS_EFFECT = S.doubleRange("max_ship_mass_effect", 400.0, 1.0, 1_000_000.0,
            "Ship mass (Sable mass units, about 1 per plank) up to which a ship feels the full hazard force; heavier ships "
                    + "feel it scaled by this mass divided by theirs, so big ships barely notice");

    private static final ConfigSection SPOUTS = S.section("waterspouts", "Waterspouts over the ocean during thunderstorms");
    public static final ConfigValue<Boolean> WATERSPOUTS_ENABLED = SPOUTS.bool("enabled", true,
            "Waterspouts form over the ocean during thunderstorms, lift entities and small ships and tear at sails. "
                    + "Off also removes existing waterspouts");
    public static final ConfigValue<Double> WATERSPOUT_CHANCE = SPOUTS.doubleRange("chance_per_check", 0.02, 0.0, 1.0,
            "Chance per spawn check, for each player at sea while the world is thundering, that a waterspout forms nearby");
    public static final ConfigValue<Integer> WATERSPOUT_MAX_PER_PLAYER = SPOUTS.intRange("max_per_player", 1, 0, 16,
            "Most waterspouts alive near one player at once (no new ones form while that many are near)");
    public static final ConfigValue<Integer> WATERSPOUT_DURATION = SPOUTS.intRange("duration_ticks", 1200, 20, 72000,
            "How long a waterspout lasts in ticks");
    public static final ConfigValue<Double> WATERSPOUT_RADIUS = SPOUTS.doubleRange("radius", 8.0, 1.0, 48.0,
            "Radius in blocks within which a waterspout pulls and lifts");
    public static final ConfigValue<Double> WATERSPOUT_FUNNEL_HEIGHT = SPOUTS.doubleRange("funnel_height", 24.0, 4.0, 128.0,
            "Height of the funnel in blocks; the lift fades to nothing at the top");
    public static final ConfigValue<Double> WATERSPOUT_PULL = SPOUTS.doubleRange("pull", 0.08, 0.0, 1.0,
            "Pull toward the funnel's axis at the centre, blocks/tick²");
    public static final ConfigValue<Double> WATERSPOUT_LIFT = SPOUTS.doubleRange("lift", 0.12, 0.0, 1.0,
            "Upward acceleration at the centre and the water line, blocks/tick² (gravity is about 0.08)");
    public static final ConfigValue<Double> WATERSPOUT_MAX_LIFT_SPEED = SPOUTS.doubleRange("max_lift_speed", 0.6, 0.0, 4.0,
            "The lift stops speeding entities up above this upward speed, blocks/tick");
    public static final ConfigValue<Double> SAIL_DAMAGE_CHANCE = SPOUTS.doubleRange("sail_damage_chance", 0.15, 0.0, 1.0,
            "Chance per second for each set sail inside a waterspout that the wind tears it in one trim step (full to half, half to furled)");

    private static final ConfigSection POOLS = S.section("whirlpools", "Whirlpools in the deep ocean");
    public static final ConfigValue<Boolean> WHIRLPOOLS_ENABLED = POOLS.bool("enabled", true,
            "Whirlpools appear in the deep ocean, pull ships toward their centre, turn them and can drag small boats under. "
                    + "Off also removes existing whirlpools");
    // 0.002 × 120 checks per in-game day (24000 / 200 ticks) = 0.24 expected per day in the deep ocean, a 21 % chance
    // of at least one: rarer than one a day, between C7's 0.05 per day and daily
    public static final ConfigValue<Double> WHIRLPOOL_CHANCE = POOLS.doubleRange("chance_per_check", 0.002, 0.0, 1.0,
            "Chance per spawn check, for each player in the deep ocean, that a whirlpool appears nearby "
                    + "(0.002 at the default interval is about one whirlpool every four in-game days spent in the deep ocean)");
    public static final ConfigValue<Integer> WHIRLPOOL_MAX_PER_PLAYER = POOLS.intRange("max_per_player", 1, 0, 16,
            "Most whirlpools alive near one player at once");
    public static final ConfigValue<Integer> WHIRLPOOL_DURATION = POOLS.intRange("duration_ticks", 2400, 20, 72000,
            "How long a whirlpool lasts in ticks");
    public static final ConfigValue<Double> WHIRLPOOL_RADIUS = POOLS.doubleRange("radius", 12.0, 1.0, 64.0,
            "Radius in blocks within which a whirlpool pulls and spins");
    public static final ConfigValue<Double> WHIRLPOOL_PULL = POOLS.doubleRange("pull", 0.05, 0.0, 1.0,
            "Pull toward the centre at the centre, blocks/tick²");
    public static final ConfigValue<Double> WHIRLPOOL_SPIN = POOLS.doubleRange("spin", 0.04, 0.0, 1.0,
            "Sideways (counter-clockwise seen from above) push at the centre, blocks/tick²; turns a ship lying at the centre");
    public static final ConfigValue<Double> WHIRLPOOL_DRAG_DOWN = POOLS.doubleRange("drag_down", 0.08, 0.0, 1.0,
            "Downward pull on boats and swimmers in the inner third, blocks/tick² (enough to drag a boat under)");
    public static final ConfigValue<Double> WHIRLPOOL_DRIFT_SPEED = POOLS.doubleRange("drift_speed", 0.02, 0.0, 0.5,
            "How fast a whirlpool drifts on its random heading, blocks/tick (0 = stationary)");

    private static final ConfigSection KRAKEN = S.section("kraken", "The kraken boss of the deep ocean");
    public static final ConfigValue<Boolean> KRAKEN_ENABLED = KRAKEN.bool("enabled", true,
            "The kraken can appear in the deep ocean and attack ships");
    public static final ConfigValue<Double> KRAKEN_CHANCE_PER_DAY = KRAKEN.doubleRange("chance_per_day", 0.02, 0.0, 1.0,
            "Base chance per in-game day, for each player in the deep ocean, that the kraken appears (higher at night and in storms)");

    private static final ConfigSection VISUALS = ModConfigs.client("hazard_visuals", "Client-side look and sound of waterspouts and whirlpools");
    public static final ConfigValue<Double> PARTICLE_DENSITY = VISUALS.doubleRange("particle_density", 1.0, 0.0, 4.0,
            "Multiplier on the number of particles of waterspouts and whirlpools (0 = none)");
    public static final ConfigValue<Boolean> HAZARD_SOUNDS = VISUALS.bool("sounds", true,
            "Waterspouts roar and whirlpools gurgle");

    private HazardsConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }
}
