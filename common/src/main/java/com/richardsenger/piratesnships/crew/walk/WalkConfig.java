package com.richardsenger.piratesnships.crew.walk;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.crew.CrewConfig;

/**
 * Server config sub-section {@code crew.walk} (WALK1, docs/design.md §6, §17): crew walk across the deck to their
 * station, meal spot and hammock instead of being seated there at once.
 */
public final class WalkConfig {

    private static final ConfigSection S = CrewConfig.sub("walk",
            "Crew walk across the deck to their station, meal spot and hammock (WALK1); an unreachable spot seats them there after a timeout");

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "Crew members walk to their station, meal spot and hammock over the deck. Off = they are seated there at once (the old behaviour)");
    public static final ConfigValue<Double> SPEED = S.doubleRange("speed", 1.0, 0.1, 3.0,
            "Walking speed as a multiple of the crew member's movement speed");
    public static final ConfigValue<Double> ARRIVE_DISTANCE = S.doubleRange("arrive_distance", 1.5, 0.5, 4.0,
            "Distance in blocks (measured on the ship) from the spot at which a walking crew member counts as arrived and takes its place");
    public static final ConfigValue<Integer> TIMEOUT_TICKS = S.intRange("timeout_ticks", 200, 20, 2400,
            "Ticks a crew member walks before it is seated at its spot anyway (an unreachable spot never stalls it)");
    public static final ConfigValue<Integer> REPATH_TICKS = S.intRange("repath_ticks", 20, 5, 200,
            "Ticks between two path searches while the ship moves");
    public static final ConfigValue<Boolean> AVOIDS_CREW = S.bool("avoids_crew", true,
            "Walking crew steer round crew members, villagers and players standing or seated in their way: a sidestep where there is room, "
                    + "else they wait; two walkers meeting head-on in a narrow gangway let the one give way (WALK2). Off = they walk into them (WALK1)");
    public static final ConfigValue<Integer> WAIT_TICKS = S.intRange("wait_ticks", 20, 1, 400,
            "Ticks a walking crew member waits for someone in its way when there is no room to step round, before it looks for a way again; "
                    + "the wait counts towards timeout_ticks");

    private WalkConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code CrewContentModule.registerConfig()}. */
    public static void init() {
    }
}
