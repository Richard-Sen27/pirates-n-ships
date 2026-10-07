package com.richardsenger.piratesnships.world;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;
import com.richardsenger.piratesnships.trade.market.PortKind;

import java.util.Optional;

/**
 * Server config section {@code world} (docs/design.md §17, group "World"; §9, §10.1): placement of each structure
 * type and natural spawn weights of each mob. Declared ahead of the features by {@code core.settings.SettingsModule}.
 * The seafarer village and pirate island values are read by {@code world.structure.PortStructure} (WG1, WG2) through
 * {@link #placement}; the pirate spawn weight and island cap by {@code world.island.PirateIslandSpawns}; the other
 * structures and spawn weights are not read yet.
 */
public final class WorldConfig {

    private static final ConfigSection S = ModConfigs.server("world", "Structure placement and mob spawn weights");

    private static final ConfigSection STRUCTURES = S.section("structures",
            "How far apart and how often each structure type generates");

    /** Placement settings of one structure type. */
    public record StructurePlacement(ConfigValue<Integer> spacing, ConfigValue<Double> frequency) {
    }

    /**
     * Everything {@code PortStructure} reads for one port kind. {@code spacing} and {@code separation} are datapack
     * values (the structure set's random spread): their defaults are what datagen writes.
     */
    public record PortPlacement(ConfigValue<Boolean> enabled, ConfigValue<Double> frequency, ConfigValue<Integer> spacing,
                                ConfigValue<Integer> separation, ConfigValue<Integer> shoreProbeBlocks,
                                ConfigValue<Integer> maxDistanceFromWater, ConfigValue<Integer> maxShoreHeight) {
    }

    private static final ConfigSection ISLAND = STRUCTURES.section("pirate_island", "Placement of pirate islands");

    /**
     * Pirate islands (WG2). {@code spacing} and {@code separation} are datapack values (structure set
     * {@code pirates_n_ships:pirate_islands}); {@code frequency} is read at placement time.
     */
    public static final StructurePlacement PIRATE_ISLAND = new StructurePlacement(
            ISLAND.intRange("spacing", 64, 2, 4096,
                    "Average distance in chunks between two pirate islands. Datapack value: this default is written into "
                            + "the structure set pirates_n_ships:pirate_islands; change it with a datapack, not here"),
            ISLAND.doubleRange("frequency", 0.8, 0.0, 1.0,
                    "Chance that pirate islands generate at a possible location (0 = never, 1 = always); read at placement"));
    public static final ConfigValue<Boolean> PIRATE_ISLAND_ENABLED = ISLAND.bool("enabled", true,
            "Pirate island camps generate in new chunks (off = no new camps; existing ones stay)");
    public static final ConfigValue<Integer> PIRATE_ISLAND_SEPARATION = ISLAND.intRange("separation", 24, 1, 4095,
            "Minimum distance in chunks between two pirate islands. Datapack value: this default is written into the "
                    + "structure set pirates_n_ships:pirate_islands; change it with a datapack, not here");
    public static final ConfigValue<Integer> PIRATE_ISLAND_SHORE_PROBE = ISLAND.intRange("shore_probe_blocks", 24, 4, 64,
            "How far (blocks) a camp site looks in each direction for the sea; the direction with the most water gets the jetty");
    public static final ConfigValue<Integer> PIRATE_ISLAND_MAX_DISTANCE_FROM_WATER = ISLAND.intRange("max_distance_from_water", 12, 1, 64,
            "A camp site farther than this (blocks) from sea water is skipped");
    public static final ConfigValue<Integer> PIRATE_ISLAND_MAX_SHORE_HEIGHT = ISLAND.intRange("max_shore_height", 4, 0, 32,
            "A camp site whose ground at the camp is more than this many blocks above sea level is skipped "
                    + "(the camp's sand is laid one block above the sea)");
    public static final ConfigValue<Boolean> PIRATE_ISLAND_BURIED_TREASURE = ISLAND.bool("buried_treasure", true,
            "The treasure spot of a new camp gets a buried chest (loot table pirates_n_ships:chests/buried_treasure), "
                    + "recorded on the port as a treasure site; off = the marker stays plain sand");
    public static final ConfigValue<Integer> PIRATE_ISLAND_MAX_PIRATES = ISLAND.intRange("max_pirates", 8, 0, 64,
            "Pirates stop spawning naturally in a camp once this many pirates are within 32 blocks of the spawn spot");

    public static final PortPlacement PIRATE_ISLAND_PLACEMENT = new PortPlacement(PIRATE_ISLAND_ENABLED, PIRATE_ISLAND.frequency(),
            PIRATE_ISLAND.spacing(), PIRATE_ISLAND_SEPARATION, PIRATE_ISLAND_SHORE_PROBE, PIRATE_ISLAND_MAX_DISTANCE_FROM_WATER,
            PIRATE_ISLAND_MAX_SHORE_HEIGHT);

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
    public static final PortPlacement SEAFARER_VILLAGE_PLACEMENT = new PortPlacement(SEAFARER_VILLAGE_ENABLED,
            SEAFARER_VILLAGE.frequency(), SEAFARER_VILLAGE.spacing(), SEAFARER_VILLAGE_SEPARATION, SEAFARER_VILLAGE_SHORE_PROBE,
            SEAFARER_VILLAGE_MAX_DISTANCE_FROM_WATER, SEAFARER_VILLAGE_MAX_SHORE_HEIGHT);

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

    /** The placement values of a port kind; empty for kinds without a placed structure yet (the navy outpost). */
    public static Optional<PortPlacement> placement(PortKind kind) {
        return switch (kind) {
            case SEAFARER_VILLAGE -> Optional.of(SEAFARER_VILLAGE_PLACEMENT);
            case PIRATE_ISLAND -> Optional.of(PIRATE_ISLAND_PLACEMENT);
            case NAVY_OUTPOST -> Optional.empty();
        };
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
