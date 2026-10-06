package com.richardsenger.piratesnships.world;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Server config section {@code world} (docs/design.md §17, group "World"; §9, §10.1): placement of each structure
 * type and natural spawn weights of each mob. Declared ahead of the features by {@code core.settings.SettingsModule};
 * nothing reads these values yet.
 */
public final class WorldConfig {

    private static final ConfigSection S = ModConfigs.server("world", "Structure placement and mob spawn weights");

    private static final ConfigSection STRUCTURES = S.section("structures",
            "How far apart and how often each structure type generates");

    /** Placement settings of one structure type. */
    public record StructurePlacement(ConfigValue<Integer> spacing, ConfigValue<Double> frequency) {
    }

    public static final StructurePlacement PIRATE_ISLAND = structure("pirate_island", "pirate islands", 40, 0.8);
    public static final StructurePlacement SEAFARER_VILLAGE = structure("seafarer_village", "seafarer villages", 36, 1.0);
    public static final StructurePlacement NAVY_OUTPOST = structure("navy_outpost", "navy outposts", 48, 0.8);
    public static final StructurePlacement WRECK = structure("wreck", "wrecks", 24, 1.0);

    private static final ConfigSection SPAWNS = S.section("spawn_weights",
            "Natural spawn weight of each mob (higher = more common, 0 = never spawns naturally)");

    public static final ConfigValue<Integer> SPAWN_WEIGHT_PIRATE = SPAWNS.intRange("pirate", 10, 0, 1000,
            "Spawn weight of pirates on pirate islands (0 = never spawn naturally)");
    public static final ConfigValue<Integer> SPAWN_WEIGHT_SAILOR = SPAWNS.intRange("sailor", 10, 0, 1000,
            "Spawn weight of sailors in seafarer villages (0 = never spawn naturally)");
    public static final ConfigValue<Integer> SPAWN_WEIGHT_NAVY_SOLDIER = SPAWNS.intRange("navy_soldier", 10, 0, 1000,
            "Spawn weight of navy soldiers at navy outposts (0 = never spawn naturally)");
    public static final ConfigValue<Integer> SPAWN_WEIGHT_NAVY_OFFICER = SPAWNS.intRange("navy_officer", 2, 0, 1000,
            "Spawn weight of navy officers at navy outposts (0 = never spawn naturally)");
    public static final ConfigValue<Integer> SPAWN_WEIGHT_SHARK = SPAWNS.intRange("shark", 4, 0, 1000,
            "Spawn weight of sharks in deep ocean biomes, compared to vanilla ocean mobs such as cod (15) and dolphins (2)");

    private WorldConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }

    private static StructurePlacement structure(String name, String plural, int spacing, double frequency) {
        ConfigSection s = STRUCTURES.section(name, "Placement of " + plural);
        return new StructurePlacement(
                s.intRange("spacing", spacing, 2, 4096,
                        "Average distance in chunks between two " + plural),
                s.doubleRange("frequency", frequency, 0.0, 1.0,
                        "Chance that " + plural + " generate at a possible location (0 = never, 1 = always)"));
    }
}
