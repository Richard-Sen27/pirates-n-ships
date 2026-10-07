package com.richardsenger.piratesnships.apparel;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/** Server config section {@code apparel} (docs/design.md §9, §17): the wearable hats. */
public final class ApparelConfig {

    private static final ConfigSection S = ModConfigs.server("apparel", "Wearable hats: pirate hat, bandana, navy tricorn, officer's bicorne");

    public static final ConfigValue<Integer> HAT_ARMOR = S.intRange("hat_armor", 1, 0, 3,
            "Armour points a hat gives while worn on the head (0 = no armour modifier)");

    private ApparelConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }
}
