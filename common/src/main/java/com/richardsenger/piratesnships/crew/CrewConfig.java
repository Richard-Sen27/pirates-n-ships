package com.richardsenger.piratesnships.crew;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Server config section {@code crew} (docs/design.md §17, group "Crew"; §7.1, §7.3). Provisions have their own
 * section ({@code crew.provisions.ProvisionsConfig}). Declared ahead of the feature by
 * {@code core.settings.SettingsModule}. Read so far: {@link #MAX_CREW_MULTIPLIER} (the bunk limit,
 * {@code crew.hammock.ShipBunks}) and the {@code morale} sub-section (HM1, {@code crew.morale.CrewMorale},
 * {@code crew.hammock.CrewRest}); wages and mutiny come with CR2.
 */
public final class CrewConfig {

    private static final ConfigSection S = ModConfigs.server("crew", "Crew wages, morale and crew size");

    public static final ConfigValue<Boolean> WAGES_ENABLED = S.bool("wages_enabled", true,
            "Crew members are paid wages in doubloons from the ship's chest and lose morale when unpaid. Off = crew work for free");
    public static final ConfigValue<Boolean> MUTINY_ENABLED = S.bool("mutiny_enabled", true,
            "Crew with very low morale may mutiny. Off = they only desert");
    public static final ConfigValue<Double> MAX_CREW_MULTIPLIER = S.doubleRange("max_crew_multiplier", 1.0, 0.1, 10.0,
            "Multiplier on the crew limit a ship gets from its bunks and hammocks");

    private static final ConfigSection MORALE = S.section("morale", "Per-crew morale (0 to 100) and the hammock rule");

    public static final ConfigValue<Boolean> MORALE_ENABLED = MORALE.bool("enabled", true,
            "Crew members have morale, turn in to the ship's hammocks at night and gain or lose morale at dawn. Off = morale stays at 'start' and nobody is sent to a hammock");
    public static final ConfigValue<Integer> MORALE_START = MORALE.intRange("start", 70, 0, 100,
            "Morale of a new crew member");
    public static final ConfigValue<Integer> HAMMOCK_REST_PER_NIGHT = MORALE.intRange("hammock_rest_per_night", 5, 0, 100,
            "Morale a crew member gains at dawn after a night in a hammock (capped at 100)");
    public static final ConfigValue<Integer> NO_HAMMOCK_PER_NIGHT = MORALE.intRange("no_hammock_per_night", 10, 0, 100,
            "Morale a crew member loses at dawn after a night on a ship without a free hammock for it (floored at 0)");

    private CrewConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }
}
