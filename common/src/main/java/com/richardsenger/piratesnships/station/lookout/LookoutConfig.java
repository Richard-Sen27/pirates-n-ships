package com.richardsenger.piratesnships.station.lookout;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/** Server config of the crow's nest and its lookout (CN1, docs/design.md §6, §7, §17), section {@code lookout}. */
public final class LookoutConfig {

    private static final ConfigSection S = ModConfigs.server("lookout",
            "The crow's nest: a crew member or player in it keeps watch and calls out ships, land and sea monsters");

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "A crow's nest with a lookout (a crew member at the station or a player standing in it) scans the sea. Off: no scans, no calls");
    public static final ConfigValue<Boolean> ANNOUNCE = S.bool("announce", true,
            "The lookout's calls go to the ship's owner and the players aboard as chat lines. Off: the lookout keeps watch silently");
    public static final ConfigValue<Integer> RANGE = S.intRange("range", 160, 8, 1024,
            "Blocks (horizontal) at which the lookout in a crow's nest sees ships, land and sea monsters");
    public static final ConfigValue<Integer> SCAN_INTERVAL_TICKS = S.intRange("scan_interval_ticks", 60, 1, 12000,
            "Ticks between two scans of every manned crow's nest");
    public static final ConfigValue<Integer> MEMORY_TICKS = S.intRange("memory_ticks", 6000, 0, 1728000,
            "Ticks the lookout remembers a sighting after it was last seen; within them the same ship, monster or coast is not called again");
    public static final ConfigValue<Integer> LAND_DIRECTIONS = S.intRange("land_directions", 16, 4, 64,
            "Directions (rays round the compass) the lookout samples for land");
    public static final ConfigValue<Integer> LAND_STEP = S.intRange("land_step", 16, 4, 128,
            "Blocks between two land samples along a ray");
    public static final ConfigValue<Integer> LAND_MIN_DISTANCE = S.intRange("land_min_distance", 24, 0, 512,
            "Land closer than this many blocks is not called (the quay the ship lies at)");
    public static final ConfigValue<Integer> LAND_REGION = S.intRange("land_region", 128, 16, 2048,
            "Size in blocks of the squares a coast is remembered by: land in a square called already is not called again");

    private LookoutConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }
}
