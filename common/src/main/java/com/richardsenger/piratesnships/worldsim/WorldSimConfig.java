package com.richardsenger.piratesnships.worldsim;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Server config section {@code world_simulation} (docs/design.md §17, group "World simulation"; §10.4). Declared
 * ahead of the feature by {@code core.settings.SettingsModule}; nothing reads these values yet.
 */
public final class WorldSimConfig {

    private static final ConfigSection S = ModConfigs.server("world_simulation",
            "NPC voyages, convoys, patrols and raids between the ports");

    public static final ConfigValue<Boolean> ENABLED = S.bool("enabled", true,
            "Simulate NPC ships travelling between ports, faction tension and pirate raids. Off = no NPC voyages or raids");
    public static final ConfigValue<Integer> MAX_SIMULTANEOUS_VOYAGES = S.intRange("max_simultaneous_voyages", 32, 0, 1000,
            "Most NPC voyages (convoys and patrols) that can exist at the same time in the whole world");
    public static final ConfigValue<Integer> MATERIALIZE_RADIUS = S.intRange("materialize_radius", 192, 32, 2048,
            "Distance in blocks from a player at which an NPC voyage becomes a real ship with crew");
    public static final ConfigValue<Double> CONVOYS_PER_DAY = S.doubleRange("convoys_per_day", 2.0, 0.0, 100.0,
            "Average number of trade convoys that set out per in-game day across the world");
    public static final ConfigValue<Double> PATROLS_PER_DAY = S.doubleRange("patrols_per_day", 2.0, 0.0, 100.0,
            "Average number of navy patrols that set out per in-game day across the world");
    public static final ConfigValue<Boolean> RETALIATION_ENABLED = S.bool("retaliation_enabled", true,
            "High tension between factions makes them raid each other's settlements and convoys more often");

    private static final ConfigSection RAIDS = S.section("raids",
            "Pirate raids on navy settlements while a player stays there");

    public static final ConfigValue<Double> RAID_CHANCE_GROWTH = RAIDS.doubleRange("chance_growth_per_minute", 0.0005, 0.0, 1.0,
            "How much the raid chance per minute rises for every minute (1200 ticks) a player spends at a navy settlement");
    public static final ConfigValue<Double> RAID_CHANCE_CAP = RAIDS.doubleRange("chance_cap", 0.02, 0.0, 1.0,
            "Highest raid chance per minute the growth can reach (0 = no raids)");
    public static final ConfigValue<Double> RAID_COOLDOWN_DAYS = RAIDS.doubleRange("cooldown_days", 5.0, 0.0, 1000.0,
            "In-game days after a raid before the same settlement can be raided again");

    private WorldSimConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }

    /** A sub-section {@code world_simulation.<name>} for a world-simulation package's own config class. */
    public static ConfigSection sub(String name, String comment) {
        return S.section(name, comment);
    }
}
