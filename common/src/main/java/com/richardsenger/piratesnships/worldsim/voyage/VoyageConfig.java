package com.richardsenger.piratesnships.worldsim.voyage;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.worldsim.WorldSimConfig;
import com.richardsenger.piratesnships.worldsim.lane.LanePathfinder;

import java.util.List;

/**
 * Server config sections {@code world_simulation.lanes} and {@code world_simulation.voyages} (WS2, design.md §10.4,
 * §17). The master toggle, the convoy frequency and the voyage cap stay in {@link WorldSimConfig}.
 */
public final class VoyageConfig {

    private static final ConfigSection LANES = WorldSimConfig.sub("lanes",
            "Sea lanes between ports, found on a grid read from the biome map");

    public static final ConfigValue<Integer> CELL_BLOCKS = LANES.intRange("cell_blocks", 32, 8, 256,
            "Side of one sea grid cell in blocks (smaller = finer lanes, slower to find)");
    public static final ConfigValue<Double> LAND_MARGIN_COST = LANES.doubleRange("land_margin_cost", 4.0, 1.0, 100.0,
            "Cost multiplier for sea cells next to land, so lanes keep off the coasts (1 = hug the coast)");
    public static final ConfigValue<Integer> MAX_CELLS = LANES.intRange("max_cells", 20_000, 100, 1_000_000,
            "Most grid cells one lane search may expand before it gives up");
    public static final ConfigValue<Integer> MILLIS_PER_CHECK = LANES.intRange("millis_per_check", 5, 1, 1000,
            "Server time in milliseconds a background lane search may use per voyage check; a longer search continues in the next check");
    public static final ConfigValue<Integer> ENDPOINT_RADIUS_CELLS = LANES.intRange("endpoint_radius_cells", 8, 0, 64,
            "How far (in cells) from a port's berth the lane may start when the berth's own cell is not sea");
    public static final ConfigValue<Integer> RETRY_FAILED_TICKS = LANES.intRange("retry_failed_ticks", 24_000, 0, 10_000_000,
            "Ticks before a port pair without a lane is searched again (24000 = one day)");
    public static final ConfigValue<Integer> VERSION = LANES.intRange("version", 1, 0, 1_000_000,
            "Raise to throw away every cached lane (e.g. after changing the grid settings)");
    public static final ConfigValue<Integer> RISK_RADIUS = LANES.intRange("risk_radius", 300, 0, 10_000,
            "Pirate islands within this many blocks of a lane make delivery contracts on it riskier (and pay more)");
    public static final ConfigValue<Double> RISK_PER_PIRATE_ISLAND = LANES.doubleRange("risk_per_pirate_island", 0.4, 0.0, 1.0,
            "Contract risk added by each pirate island near the lane (combined as 1 - (1 - r)^n)");
    public static final ConfigValue<Double> BASE_RISK = LANES.doubleRange("base_risk", 0.1, 0.0, 1.0,
            "Contract risk of a lane with no pirate island near it");

    private static final ConfigSection VOYAGES = WorldSimConfig.sub("voyages",
            "Abstract NPC voyages that move along the lanes and trade on arrival");

    public static final ConfigValue<Integer> TICK_INTERVAL = VOYAGES.intRange("tick_interval_ticks", 20, 1, 1200,
            "Ticks between two voyage updates (movement, arrivals, spawn rolls)");
    public static final ConfigValue<Double> SPEED = VOYAGES.doubleRange("speed_blocks_per_second", 4.0, 0.1, 50.0,
            "Speed of an abstract voyage in blocks per second, before the wind factor");
    public static final ConfigValue<Boolean> WIND_AFFECTS_SPEED = VOYAGES.bool("wind_affects_speed", true,
            "Voyages sail faster downwind and slower into the wind");
    public static final ConfigValue<Double> DOWNWIND_FACTOR = VOYAGES.doubleRange("downwind_factor", 1.2, 0.1, 5.0,
            "Speed factor with the wind straight astern");
    public static final ConfigValue<Double> HEADWIND_FACTOR = VOYAGES.doubleRange("headwind_factor", 0.6, 0.1, 5.0,
            "Speed factor with the wind straight ahead (beam reach is halfway between)");
    public static final ConfigValue<Integer> CONVOY_CARGO_UNITS = VOYAGES.intRange("convoy_cargo_units", 64, 1, 10_000,
            "Units of each good a convoy buys at its origin and sells at its destination");
    public static final ConfigValue<Integer> CONVOY_MIN_DISTANCE = VOYAGES.intRange("convoy_min_distance", 200, 0, 100_000,
            "Ports closer than this (blocks between centres) never send convoys to each other");
    public static final ConfigValue<Integer> CONVOY_MAX_DISTANCE = VOYAGES.intRange("convoy_max_distance", 3000, 0, 100_000,
            "Ports farther apart than this (blocks between centres) never send convoys to each other");
    public static final ConfigValue<List<String>> MERCHANT_TEMPLATES = VOYAGES.stringList("merchant_templates",
            List.of("pirates_n_ships:starter_sloop", "pirates_n_ships:starter_sloop_basic"),
            "Ship templates merchant convoys sail (one is picked at random)");
    public static final ConfigValue<List<String>> NAVY_TEMPLATES = VOYAGES.stringList("navy_templates",
            List.of("pirates_n_ships:starter_sloop", "pirates_n_ships:starter_sloop_basic"),
            "Ship templates navy patrols sail (one is picked at random)");
    public static final ConfigValue<List<String>> PIRATE_TEMPLATES = VOYAGES.stringList("pirate_templates",
            List.of("pirates_n_ships:starter_sloop", "pirates_n_ships:starter_sloop_basic"),
            "Ship templates pirate raiders sail (one is picked at random)");

    private VoyageConfig() {
    }

    public static void init() {
    }

    public static LanePathfinder.Params laneParams() {
        return new LanePathfinder.Params(LAND_MARGIN_COST.get(), MAX_CELLS.get(), ENDPOINT_RADIUS_CELLS.get());
    }

    public static VoyageRules.Speed speed() {
        return new VoyageRules.Speed(SPEED.get(), WIND_AFFECTS_SPEED.get(), DOWNWIND_FACTOR.get(), HEADWIND_FACTOR.get());
    }
}
