package com.richardsenger.piratesnships.world.island;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.WorldConfig;
import com.richardsenger.piratesnships.world.structure.PortStructureData;
import com.richardsenger.piratesnships.world.structure.ShoreAnchor;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.Map;

import static com.richardsenger.piratesnships.world.structure.PortStructureData.weights;

/**
 * Datagen of the pirate island's worldgen files (WG2, design.md §10.1): the structure (type
 * {@code pirates_n_ships:port_village}, port kind {@code pirate_island}, the camp's shore anchor (6, 0)), its structure
 * set (random spread, wider than the village's), the five pools of the ST2 pieces, the biome tag (beaches), the
 * buried-treasure loot table and the spawn overrides that let pirates spawn inside the camp's pieces.
 *
 * <p>Projections as in the village: the camp, jetty and huts are rigid, the paths and their ends terrain matching.
 * Terrain adaptation {@code none} for the same reason as the village (the jetty would be bearded).
 *
 * <p><b>Spawn overrides.</b> Category {@code monster}, bounding box {@code piece} (only inside the pieces, not the
 * whole box over the water): pirates, weight {@link #PIRATE_SPAWN_WEIGHT}, groups of 1-3. Vanilla's natural spawner
 * never spawns a {@code misc} entity type, so the pirate's type is {@code monster}; {@link PirateIslandSpawns} lets it
 * spawn on any sturdy block in daylight and caps the pirates per camp.
 */
public final class IslandData {

    /** Fixed salt of the structure set's random spread (never change it: it moves every island of existing worlds). */
    public static final int SALT = 739_215_604;
    public static final int SIZE = 6;
    public static final int MAX_DISTANCE_FROM_CENTER = 64;
    public static final int START_HEIGHT = 1;
    /** Datapack value; {@code world.spawn_weights.pirate} = 0 stops island spawns at the spawn check. */
    public static final int PIRATE_SPAWN_WEIGHT = 10;
    public static final int PIRATE_MIN_GROUP = 1;
    public static final int PIRATE_MAX_GROUP = 3;

    /** Weights of the huts pool (WG2): tents are common, the treasure spot is one in six. */
    public static final Map<String, Integer> HUTS = weights("tent", 3, "tavern_hut", 1, "captains_hut", 1, "treasure_spot", 1);

    private static final String GROUP = "pirate_island";

    private IslandData() {
    }

    public static PortStructureData.Spec spec() {
        return new PortStructureData.Spec(IslandKeys.HAS_PIRATE_ISLAND, IslandKeys.START, SIZE, START_HEIGHT,
                MAX_DISTANCE_FROM_CENTER, ShoreAnchor.PIRATE_CAMP, PortKind.PIRATE_ISLAND, spawnOverrides());
    }

    public static void gather(DataContributions data) {
        PortStructureData.writeStructure(data, IslandKeys.PIRATE_ISLAND, spec());
        PortStructureData.writeStructureSet(data, IslandKeys.PIRATE_ISLANDS, IslandKeys.PIRATE_ISLAND, SALT,
                WorldConfig.PIRATE_ISLAND.spacing().defaultValue(), WorldConfig.PIRATE_ISLAND_SEPARATION.defaultValue());
        PortStructureData.pool(data, IslandKeys.START, GROUP, "minecraft:empty", "rigid", weights("camp_start", 1));
        PortStructureData.pool(data, IslandKeys.PATHS, GROUP, IslandKeys.TERMINATORS.location().toString(), "terrain_matching",
                weights("path", 1));
        PortStructureData.pool(data, IslandKeys.HUTS, GROUP, "minecraft:empty", "rigid", HUTS);
        PortStructureData.pool(data, IslandKeys.JETTY, GROUP, "minecraft:empty", "rigid", weights("jetty", 1));
        // The paths' fallback once the depth runs out; never empty (vanilla skips a connector with an empty fallback)
        PortStructureData.pool(data, IslandKeys.TERMINATORS, GROUP, "minecraft:empty", "terrain_matching", weights("path_end", 1));
        PortStructureData.biomeTag(data, IslandKeys.HAS_PIRATE_ISLAND, "#minecraft:is_beach");
        data.encoded(PackOutput.Target.DATA_PACK, "loot_table", TreasureChests.LOOT_TABLE.location(), LootTable.DIRECT_CODEC,
                TreasureLoot.buriedTreasure());
    }

    static JsonObject spawnOverrides() {
        JsonObject pirate = new JsonObject();
        pirate.addProperty("type", Constants.id("pirate").toString());
        pirate.addProperty("weight", PIRATE_SPAWN_WEIGHT);
        pirate.addProperty("minCount", PIRATE_MIN_GROUP);
        pirate.addProperty("maxCount", PIRATE_MAX_GROUP);
        JsonArray spawns = new JsonArray();
        spawns.add(pirate);
        JsonObject monster = new JsonObject();
        monster.addProperty("bounding_box", "piece");
        monster.add("spawns", spawns);
        JsonObject overrides = new JsonObject();
        overrides.add("monster", monster);
        return overrides;
    }
}
