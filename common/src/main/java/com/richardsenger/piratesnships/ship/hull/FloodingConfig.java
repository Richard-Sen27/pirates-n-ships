package com.richardsenger.piratesnships.ship.hull;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;
import com.richardsenger.piratesnships.ship.hull.flooding.FloodParams;
import com.richardsenger.piratesnships.ship.hull.flooding.RecomputeDebouncer;

/** Server config section {@code flooding} (docs/design.md §17). Pure logic never reads this; use {@link #params()}. */
public final class FloodingConfig {

    private static final ConfigSection SECTION = ModConfigs.server("flooding", "Hull breaches, water inflow, pumps and hull analysis");

    public static final ConfigValue<Boolean> ENABLED = SECTION.bool("enabled", true,
            "Ships take on water through breaches, holes, low rims and open hatches below the waterline");
    public static final ConfigValue<Double> INFLOW_RATE = SECTION.doubleRange("inflow_rate", 1.0, 0.0, 20.0,
            "Multiplier for how fast water flows through breaches, holes and open doors or hatches");
    public static final ConfigValue<Double> PUMP_RATE = SECTION.doubleRange("pump_rate", 1.0, 0.0, 100.0,
            "Blocks of water one working pump removes per second");
    public static final ConfigValue<Boolean> PUMP_ENABLED = SECTION.bool("pump_enabled", true,
            "Bilge pumps work: a player using one, or a crew member ordered to it, removes flood water");
    public static final ConfigValue<Integer> PUMP_REACH = SECTION.intRange("pump_reach", 4, 0, 16,
            "Blocks below its own cell a bilge pump's intake reaches (through decks) to find a flooded compartment");
    public static final ConfigValue<Integer> PUMP_USE_TICKS = SECTION.intRange("pump_use_ticks", 8, 1, 100,
            "Ticks one use keeps a pump working. Holding the use key repeats the use every 4 ticks, so keep this above 4");
    public static final ConfigValue<Double> PUMP_EXHAUSTION = SECTION.doubleRange("pump_exhaustion", 0.025, 0.0, 4.0,
            "Food exhaustion per pump use (4 = one hunger point; holding the key uses the pump 5 times per second)");
    public static final ConfigValue<Boolean> PATCH_ENABLED = SECTION.bool("patch_enabled", true,
            "Hull patches can be placed into breaches to close them");
    public static final ConfigValue<Integer> DEBOUNCE_TICKS = SECTION.intRange("debounce_ticks", 10, 0, 200,
            "Ticks a hull must stay unchanged before it is re-analyzed after block changes");
    public static final ConfigValue<Integer> MAX_DEBOUNCE_TICKS = SECTION.intRange("max_debounce_ticks", 60, 0, 1200,
            "Latest re-analysis after the first unprocessed hull change, even while the hull keeps changing");
    public static final ConfigValue<Double> REANALYSIS_TILT_DEGREES = SECTION.doubleRange("reanalysis_tilt_degrees", 5.0, 0.5, 45.0,
            "Re-analyze a hull when the ship has tilted this many degrees since the last analysis");

    private FloodingConfig() {
    }

    public static void init() {
    }

    /** Current values as a plain parameter object for the simulation. */
    public static FloodParams params() {
        return new FloodParams(ENABLED.get(), INFLOW_RATE.get(), PUMP_RATE.get() / 20.0);
    }

    /** A debouncer configured from the current values. */
    public static RecomputeDebouncer newDebouncer() {
        int quiet = DEBOUNCE_TICKS.get();
        return new RecomputeDebouncer(quiet, Math.max(quiet, MAX_DEBOUNCE_TICKS.get()));
    }
}
