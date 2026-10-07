package com.richardsenger.piratesnships.world;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Server config section {@code world} (docs/design.md §17, group "World"; §9, §10.1): placement of each structure
 * type and natural spawn weights of each mob. Declared ahead of the features by {@code core.settings.SettingsModule}.
 * The seafarer village values are read by {@code world.village.PortVillageStructure} (WG1), the wreck values by
 * {@code world.wreck.WreckStructure} (WK1); the other structures and the spawn weights are not read yet.
 */
public final class WorldConfig {

    private static final ConfigSection S = ModConfigs.server("world", "Structure placement and mob spawn weights");

    private static final ConfigSection STRUCTURES = S.section("structures",
            "How far apart and how often each structure type generates");

    /** Placement settings of one structure type. */
    public record StructurePlacement(ConfigValue<Integer> spacing, ConfigValue<Double> frequency) {
    }

    public static final StructurePlacement PIRATE_ISLAND = structure("pirate_island", "pirate islands", 40, 0.8);
    private static final ConfigSection VILLAGE = STRUCTURES.section("seafarer_village", "Placement of seafarer villages");

    /**
     * Seafarer villages. {@code spacing} and {@code separation} are datapack values (structure set
     * {@code pirates_n_ships:seafarer_villages}): the defaults here are what datagen writes, and changing them in the
     * config has no effect (a datapack overrides the structure set). {@code frequency} is read at placement time.
     */
    public static final StructurePlacement SEAFARER_VILLAGE = new StructurePlacement(
            VILLAGE.intRange("spacing", 36, 2, 4096,
                    "Average distance in chunks between two seafarer villages. Datapack value: this default is written into "
                            + "the structure set pirates_n_ships:seafarer_villages; change it with a datapack, not here"),
            VILLAGE.doubleRange("frequency", 1.0, 0.0, 1.0,
                    "Chance that seafarer villages generate at a possible location (0 = never, 1 = always); read at placement"));
    public static final ConfigValue<Boolean> SEAFARER_VILLAGE_ENABLED = VILLAGE.bool("enabled", true,
            "Seafarer villages generate in new chunks (off = no new villages; existing ones stay)");
    public static final ConfigValue<Integer> SEAFARER_VILLAGE_SEPARATION = VILLAGE.intRange("separation", 12, 1, 4095,
            "Minimum distance in chunks between two seafarer villages. Datapack value: this default is written into the "
                    + "structure set pirates_n_ships:seafarer_villages; change it with a datapack, not here");
    public static final ConfigValue<Integer> SEAFARER_VILLAGE_SHORE_PROBE = VILLAGE.intRange("shore_probe_blocks", 24, 4, 64,
            "How far (blocks) a village site looks in each direction for the sea; the direction with the most water gets the pier");
    public static final ConfigValue<Integer> SEAFARER_VILLAGE_MAX_DISTANCE_FROM_WATER = VILLAGE.intRange("max_distance_from_water", 12, 1, 64,
            "A village site farther than this (blocks) from sea water is skipped");
    public static final ConfigValue<Integer> SEAFARER_VILLAGE_MAX_SHORE_HEIGHT = VILLAGE.intRange("max_shore_height", 4, 0, 32,
            "A village site whose ground at the dock head is more than this many blocks above sea level is skipped "
                    + "(the quay is built one block above the sea)");
    public static final StructurePlacement NAVY_OUTPOST = structure("navy_outpost", "navy outposts", 48, 0.8);
    private static final ConfigSection WRECKS = STRUCTURES.section("wreck", "Placement of wrecks on the ocean floor");

    /**
     * Wrecks (WK1). {@code spacing} and {@code separation} are datapack values (structure set
     * {@code pirates_n_ships:wrecks}): the defaults here are what datagen writes, and changing them in the config has
     * no effect (a datapack overrides the structure set). {@code frequency} and {@code enabled} are read by
     * {@code world.wreck.WreckStructure} at placement time.
     */
    public static final StructurePlacement WRECK = new StructurePlacement(
            WRECKS.intRange("spacing", 24, 2, 4096,
                    "Average distance in chunks between two wrecks. Datapack value: this default is written into the "
                            + "structure set pirates_n_ships:wrecks; change it with a datapack, not here"),
            WRECKS.doubleRange("frequency", 1.0, 0.0, 1.0,
                    "Chance that wrecks generate at a possible location (0 = never, 1 = always); read at placement"));
    public static final ConfigValue<Boolean> WRECK_ENABLED = WRECKS.bool("enabled", true,
            "Wrecks generate on the ocean floor of new chunks (off = no new wrecks; existing ones stay)");
    public static final ConfigValue<Integer> WRECK_SEPARATION = WRECKS.intRange("separation", 8, 1, 4095,
            "Minimum distance in chunks between two wrecks. Datapack value: this default is written into the structure "
                    + "set pirates_n_ships:wrecks; change it with a datapack, not here");

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
