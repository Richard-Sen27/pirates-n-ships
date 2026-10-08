package com.richardsenger.piratesnships.crew.galley;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.crew.CrewConfig;
import java.util.List;

/**
 * Server config sub-section {@code crew.meals} (CRW2, docs/design.md §7.4, §17): the crew's visible meals at the pantry.
 * The meals are a sight only: the rations are still taken once a day at dawn (CR2).
 */
public final class MealConfig {

    private static final ConfigSection S = CrewConfig.sub("meals",
            "Crew sit down at the pantry or water barrel at meal times and eat there for a while (CRW2). Only a sight: the rations are taken at dawn");

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "At every meal time the free crew (not at a station, not asleep) sit down beside the nearest pantry or water barrel aboard. Off = no meals");
    public static final ConfigValue<List<String>> MEAL_TIMES = S.stringList("meal_times", MealRules.DEFAULT_TIMES,
            "Times of day (0 to 23999 ticks; 6000 = noon, 12000 = sunset) at which a meal starts. The crew turns in at nightfall (12542), so a later meal reaches only crew without a hammock. Entries that are not whole numbers are ignored");
    public static final ConfigValue<Integer> MEAL_TICKS = S.intRange("meal_ticks", 100, 1, 6000,
            "Ticks a meal lasts before the crew gets up again");

    private MealConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code ProvisionsModule.registerConfig()}. */
    public static void init() {
    }

    /** The configured meal times, parsed ({@link MealRules#parseTimes}). */
    public static List<Long> times() {
        return MealRules.parseTimes(MEAL_TIMES.get());
    }
}
