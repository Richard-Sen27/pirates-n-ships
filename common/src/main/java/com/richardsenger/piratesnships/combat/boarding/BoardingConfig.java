package com.richardsenger.piratesnships.combat.boarding;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Server config section {@code boarding} (docs/design.md §8.3 "Boarding", §17 "Combat"). Its {@code plank} sub-section
 * holds the boarding plank (BRD1): a run of plank blocks laid from one ship's gunwale onto a ship lying alongside.
 */
public final class BoardingConfig {

    private static final ConfigSection S = ModConfigs.server("boarding", "Boarding another ship");
    private static final ConfigSection PLANK = S.section("plank",
            "Boarding plank: a walkway laid from one ship's gunwale onto the deck of a ship lying alongside");

    public static final ConfigValue<Boolean> ENABLED = PLANK.bool("enabled", true,
            "Boarding planks can be laid. Off = the item refuses; planks already laid still break when the ships part");
    public static final ConfigValue<Integer> MAX_LENGTH = PLANK.intRange("max_length", 4, 1, PlankRun.MAX_SEGMENTS,
            "Longest plank run in blocks, from the cell beside the clicked gunwale to the cell over the other ship's deck");
    public static final ConfigValue<Double> BREAK_DISTANCE = PLANK.doubleRange("break_distance", 1.5, 0.1, 8.0,
            "A plank breaks (dropping one plank) when its far end has moved this many blocks away from the spot on the "
                    + "other ship it was laid onto, for example when the hulls part or roll apart");
    public static final ConfigValue<Integer> CHECK_INTERVAL_TICKS = PLANK.intRange("check_interval_ticks", 10, 1, 200,
            "Ticks between two checks of a plank's far end");

    private BoardingConfig() {
    }

    public static void init() {
    }
}
