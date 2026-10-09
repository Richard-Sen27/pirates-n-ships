package com.richardsenger.piratesnships.apparel;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/** Server config section {@code apparel} (docs/design.md §9, §17): the wearable hats. */
public final class ApparelConfig {

    private static final ConfigSection S = ModConfigs.server("apparel", "Wearable apparel: pirate hat, bandana, navy tricorn, officer's bicorne, captain's hat, officer's coat, the captain's coat, breeches and boots");

    public static final ConfigValue<Integer> HAT_ARMOR = S.intRange("hat_armor", 1, 0, 3,
            "Armour points a hat gives while worn on the head (0 = no armour modifier)");

    public static final ConfigValue<Integer> OFFICERS_COAT_ARMOR = S.intRange("officers_coat_armor", 3, 0, 8,
            "Armour points the officer's coat gives while worn on the chest (0 = no armour modifier)");

    public static final ConfigValue<Integer> CAPTAINS_COAT_ARMOR = S.intRange("captains_coat_armor", 3, 0, 8,
            "Armour points the captain's coat gives while worn on the chest (0 = no armour modifier)");

    public static final ConfigValue<Integer> CAPTAINS_BREECHES_ARMOR = S.intRange("captains_breeches_armor", 2, 0, 6,
            "Armour points the captain's breeches give while worn on the legs (0 = no armour modifier)");

    public static final ConfigValue<Integer> CAPTAINS_BOOTS_ARMOR = S.intRange("captains_boots_armor", 1, 0, 3,
            "Armour points the captain's boots give while worn on the feet (0 = no armour modifier)");

    private ApparelConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }
}
