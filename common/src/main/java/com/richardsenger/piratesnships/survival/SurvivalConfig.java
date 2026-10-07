package com.richardsenger.piratesnships.survival;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;
import com.richardsenger.piratesnships.survival.swim.SwimHungerRules;

/**
 * Server config section {@code survival} (docs/design.md §14, §17 group "Survival"): cold water and swimming hunger.
 * Owned by {@link SurvivalModule} (taken over from {@code core.settings}).
 */
public final class SurvivalConfig {

    private static final ConfigSection S = ModConfigs.server("survival", "Cold water and swimming hunger");

    public static final ConfigValue<Double> SWIM_EXHAUSTION_MULTIPLIER = S.doubleRange("swim_exhaustion_multiplier",
            SwimHungerRules.DEFAULT_MULTIPLIER, 1.0, 10.0,
            "Multiplier on vanilla's exhaustion (hunger) for moving in water: 1 = vanilla, 1.5 = half again as much");

    private static final ConfigSection COLD = S.section("cold_water",
            "Water of cold biomes (#pirates_n_ships:cold_water) fills vanilla's freezing meter, as powder snow does");
    public static final ConfigValue<Boolean> COLD_WATER_ENABLED = COLD.bool("enabled", true,
            "Being in the water of a cold biome fills the freezing meter (frost overlay, slowdown, then freezing damage). "
                    + "Boats, ships, leather armour and the warm effect protect");
    public static final ConfigValue<Integer> FREEZE_TICKS_PER_TICK = COLD.intRange("freeze_ticks_per_tick", 1, 1, 20,
            "Freezing meter gained per tick in cold water (powder snow: 1; the meter is full at 140, 7 seconds at 1)");
    public static final ConfigValue<Integer> WARM_EFFECT_TICKS = COLD.intRange("warm_effect_ticks", 2400, 0, 72000,
            "How long the warm effect of a warming drink (#pirates_n_ships:warming, e.g. rum) lasts, in ticks (0 = no effect)");

    private static final ConfigSection SWIM = S.section("swim_hunger", "Extra hunger from swimming");
    public static final ConfigValue<Boolean> SWIM_HUNGER_ENABLED = SWIM.bool("enabled", true,
            "Moving in water costs extra hunger, scaled by swim_exhaustion_multiplier");

    private SurvivalConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }
}
