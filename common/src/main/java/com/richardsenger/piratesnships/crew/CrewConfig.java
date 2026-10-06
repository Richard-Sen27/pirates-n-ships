package com.richardsenger.piratesnships.crew;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Server config section {@code crew} (docs/design.md §17, group "Crew"; §7.1, §7.3). Provisions have their own
 * section ({@code crew.provisions.ProvisionsConfig}). Declared ahead of the feature by
 * {@code core.settings.SettingsModule}; nothing reads these values yet.
 */
public final class CrewConfig {

    private static final ConfigSection S = ModConfigs.server("crew", "Crew wages, morale and crew size");

    public static final ConfigValue<Boolean> WAGES_ENABLED = S.bool("wages_enabled", true,
            "Crew members are paid wages in doubloons from the ship's chest and lose morale when unpaid. Off = crew work for free");
    public static final ConfigValue<Boolean> MUTINY_ENABLED = S.bool("mutiny_enabled", true,
            "Crew with very low morale may mutiny. Off = they only desert");
    public static final ConfigValue<Double> MAX_CREW_MULTIPLIER = S.doubleRange("max_crew_multiplier", 1.0, 0.1, 10.0,
            "Multiplier on the crew limit a ship gets from its bunks and hammocks");

    private CrewConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }
}
