package com.richardsenger.piratesnships.crew;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Server config section {@code crew} (docs/design.md §17, group "Crew"; §7.1, §7.3). Provisions have their own
 * section ({@code crew.provisions.ProvisionsConfig}). Declared ahead of the feature by
 * {@code core.settings.SettingsModule}. Read so far: {@link #MAX_CREW_MULTIPLIER} (the bunk limit,
 * {@code crew.hammock.ShipBunks}) and the {@code morale} sub-section (HM1, {@code crew.morale.CrewMorale},
 * {@code crew.hammock.CrewRest}) and the upkeep sub-sections {@code wages}, {@code desertion} and {@code mutiny}
 * (CR2, {@code crew.upkeep}; CRW2 added {@code wages.from_wallet} and the deserters' port keys). CR2 moved the old top-level keys {@code wages_enabled} and {@code mutiny_enabled} to
 * {@code wages.enabled} and {@code mutiny.enabled}; old values in a config file are dropped and read as the defaults.
 */
public final class CrewConfig {

    private static final ConfigSection S = ModConfigs.server("crew", "Crew wages, morale and crew size");

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
    public static final ConfigValue<Integer> PRESS_GANG_START = MORALE.intRange("press_gang_start", 30, 0, 100,
            "Morale of a sailor press-ganged into the crew (LA2)");

    private static final ConfigSection WAGES = S.section("wages", "Daily wages in doubloons, paid at dawn from the coins in the ship's containers (CR2)");

    public static final ConfigValue<Boolean> WAGES_ENABLED = WAGES.bool("enabled", true,
            "Crew members are paid wages in doubloons from the coins in the ship's crates, barrels and chests at dawn and lose morale when unpaid. Off = crew work for free");
    public static final ConfigValue<Integer> WAGE_PER_DAY = WAGES.intRange("per_day", 2, 0, 1000,
            "Doubloons one crew member costs per day");
    public static final ConfigValue<Integer> UNPAID_PER_DAY = WAGES.intRange("unpaid_per_day", 8, 0, 100,
            "Morale an unpaid crew member loses at dawn");
    public static final ConfigValue<Integer> PAID_PER_DAY = WAGES.intRange("paid_per_day", 1, 0, 100,
            "Morale a paid crew member gains at dawn");
    public static final ConfigValue<Boolean> WAGES_FROM_WALLET = WAGES.bool("from_wallet", true,
            "When the coins aboard do not cover the wages, the rest comes from the doubloons the ship's owner carries, while the owner is online in the ship's dimension (CRW2). Chests and crates aboard always pay first. Off = only coins aboard pay");

    private static final ConfigSection DESERTION = S.section("desertion", "Crew with low morale leave the ship (CR2)");

    public static final ConfigValue<Boolean> DESERTION_ENABLED = DESERTION.bool("enabled", true,
            "A crew member whose morale stays low for several dawns deserts and becomes a neutral sailor. Off = crew never desert");
    public static final ConfigValue<Integer> DESERT_BELOW = DESERTION.intRange("desert_below", 20, 0, 101,
            "Morale below which a dawn counts toward desertion");
    public static final ConfigValue<Integer> DESERT_DAYS = DESERTION.intRange("desert_days", 2, 1, 100,
            "Consecutive dawns with low morale after which a crew member deserts");
    public static final ConfigValue<Boolean> DESERT_AT_PORT_ONLY = DESERTION.bool("at_port_only", true,
            "A deserter does not leave at once: it stays aboard, marked as deserting, and walks off onto the quay when the ship is at a port (CRW2), or anywhere after desert_anywhere_after_days. Off = it leaves the ship at the dawn it deserts (the old behaviour)");
    public static final ConfigValue<Integer> DESERT_PORT_RADIUS = DESERTION.intRange("desert_port_radius", 48, 0, 512,
            "Blocks around a port's area within which a ship counts as at that port for deserters");
    public static final ConfigValue<Integer> DESERT_ANYWHERE_AFTER_DAYS = DESERTION.intRange("desert_anywhere_after_days", 3, 0, 100,
            "Dawns a deserter waits for a port; after that it leaves wherever the ship is (0 = at once)");

    private static final ConfigSection MUTINY = S.section("mutiny", "A whole crew with very low morale takes the ship (CR2)");

    public static final ConfigValue<Boolean> MUTINY_ENABLED = MUTINY.bool("enabled", false,
            "Crew with very low average morale may mutiny: they turn into hostile pirates and the ship loses its owner. While the average is that low nobody deserts (they plot instead). Off = they only desert");
    public static final ConfigValue<Integer> MUTINY_BELOW = MUTINY.intRange("mutiny_below", 15, 0, 101,
            "Average crew morale below which a dawn counts toward mutiny");
    public static final ConfigValue<Integer> MUTINY_DAYS = MUTINY.intRange("mutiny_days", 3, 1, 100,
            "Consecutive dawns with low average morale after which the crew mutinies");

    private CrewConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }

    /** A sub-section {@code crew.<name>} for a crew package's own config class. */
    public static ConfigSection sub(String name, String comment) {
        return S.section(name, comment);
    }
}
