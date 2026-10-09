package com.richardsenger.piratesnships.crew.lean;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.crew.CrewConfig;

/**
 * Server config sub-section {@code crew.rail_lean} (RAIL5, docs/design.md §4.8 "Railing extras", §17): idle crew walk
 * to the ship's railing and lean on it for a while.
 */
public final class RailLeanConfig {

    private static final ConfigSection S = CrewConfig.sub("rail_lean",
            "Idle crew lean on the ship's railing for a while (RAIL5)");

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "An idle crew member (no order, no station, not eating or sleeping) walks to a free spot at the ship's railing, faces the sea and leans on the rail for a while. Off = idle crew only stroll about the deck");
    public static final ConfigValue<Integer> RADIUS = S.intRange("radius", 6, 1, 16,
            "Blocks around an idle crew member (measured on the ship) within which it looks for a spot at the rail");
    public static final ConfigValue<Integer> MIN_SECONDS = S.intRange("min_seconds", 30, 1, 600,
            "Shortest lean on the rail [seconds]; each lean lasts a random time between this and max_seconds");
    public static final ConfigValue<Integer> MAX_SECONDS = S.intRange("max_seconds", 90, 1, 600,
            "Longest lean on the rail [seconds]");
    public static final ConfigValue<Integer> LEANERS = S.intRange("leaners", 3, 0, 32,
            "Most crew members of one ship leaning on its rail at the same time (0 = nobody leans)");
    public static final ConfigValue<Integer> PAUSE_SECONDS = S.intRange("pause_seconds", 30, 0, 600,
            "Seconds a crew member strolls about after a lean (or a spot it could not reach) before it looks for the rail again");

    private RailLeanConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code CrewContentModule.registerConfig()}. */
    public static void init() {
    }
}
