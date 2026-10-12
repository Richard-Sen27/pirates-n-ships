package com.richardsenger.piratesnships.world.island;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.trade.market.PortKind;
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
 * <b>STR2 (natural layout):</b> a path network (straights, bends, forks, a T-junction, a clearing; weights
 * {@link #PATHS}), twenty buildings ({@link #HUTS}) and a decoration pool ({@link #DECORATIONS}, rigid); terrain
 * adaptation {@code beard_thin}, so the huts sit on the slope instead of floating or sinking. The beard works round the
 * rigid jetty as well: it lowers the shore beside the jetty to the jetty's foot and raises a deep seabed toward it
 * (about three blocks of water at the berths), which is accepted; the village and the outpost keep {@code none}.
 * {@code land_pieces_on_land}: a trail that bends out to sea is dropped with what hangs from it
 * ({@code LandFilter}), so the camp spreads along the beach and inland, never over the open water.
 *
 * <p><b>Spawn overrides.</b> Category {@code monster}, bounding box {@code piece} (only inside the pieces, not the
 * whole box over the water): pirates, weight {@link #PIRATE_SPAWN_WEIGHT}, groups of 1-3. Vanilla's natural spawner
 * never spawns a {@code misc} entity type, so the pirate's type is {@code monster}; {@link PirateIslandSpawns} lets it
 * spawn on any sturdy block in daylight and caps the pirates per camp.
 */
public final class IslandData {

    /** Fixed salt of the structure set's random spread (never change it: it moves every island of existing worlds). */
    public static final int SALT = 739_215_604;
    /**
     * STR6: no pirate island starts within this many chunks of a seafarer village's or a navy outpost's placement chunk
     * (vanilla's {@code exclusion_zone} for the villages, {@code ConfiguredSpreadPlacement}'s {@code exclusion_zones} for
     * the outposts). Smaller than the outposts' 8: the islands' spread is the sparsest (52), and 8 against both sets
     * would cost them about 45 % of their sites, 6 about 28 %.
     */
    public static final int PORT_EXCLUSION_CHUNKS = 6;
    public static final int SIZE = 6;
    public static final int MAX_DISTANCE_FROM_CENTER = 64;
    public static final int START_HEIGHT = 1;
    /** Datapack value; {@code world.spawn_weights.pirate} = 0 stops island spawns at the spawn check. */
    public static final int PIRATE_SPAWN_WEIGHT = 10;
    public static final int PIRATE_MIN_GROUP = 1;
    public static final int PIRATE_MAX_GROUP = 3;

    /** STR2: vanilla's terrain adaptation for the island; the ground pieces sit on the slope instead of floating. */
    public static final String TERRAIN_ADAPTATION = "beard_thin";

    /**
     * Weights of the huts pool (STR2, art/README.md "Natural layout (STR2)"): four tents and three huts make up most
     * of a camp, the three taverns, the three captain's huts and the special buildings (lookout tower, smokehouse,
     * powder store, longboat) turn up once or twice, the three treasure looks about one slot in ten.
     */
    public static final Map<String, Integer> HUTS = weights(
            "tent", 3, "tent_small", 2, "tent_large", 2, "tent_lean", 2,
            "hut_stilts", 2, "hut_porch", 2, "hut_thatch", 2,
            "tavern_hut", 1, "tavern_long", 1, "tavern_deck", 1,
            "captains_hut", 1, "captains_lookout", 1, "captains_cabin", 1,
            "lookout_tower", 1, "smokehouse", 2, "powder_store", 1, "longboat", 1,
            "treasure_spot", 1, "treasure_cairn", 1, "treasure_wreck", 1);

    /**
     * Weights of the paths pool (STR2): the network turns, forks or ends in a clearing more often than it runs on
     * straight (straights 5 of 16), so a camp of size 6 branches two or three times and never runs in one line. A
     * trail piece leads on to 1.2 trail pieces on average (forks and the junction two, the clearing none), so the
     * network keeps growing until the depth or the space runs out; the trail ends are only the fallback (with the
     * trail ends drawn here as well, a camp died out after two or three pieces in one seed of ten).
     */
    public static final Map<String, Integer> PATHS = weights(
            "path", 4, "path_long", 4, "path_bend_left", 1, "path_bend_right", 1,
            "path_fork_left", 2, "path_fork_right", 2, "path_junction", 1, "path_clearing", 2);

    /**
     * STR8 (the human: the trails are "too curly"): what a bend's, a fork's and the junction's ways on draw, the
     * straights only, so two turns never follow each other. With it the paths pool moved toward the straights (8 of 17
     * draws, were 5 of 16; the bends 2 of 17, were 4 of 16).
     */
    public static final Map<String, Integer> PATHS_STRAIGHT = weights("path", 3, "path_long", 2);

    /**
     * The trailheads pool (STR2): what the camp's inland trailhead draws, the trail pieces that lead straight on
     * inland or fork (no end, no clearing, no bend or junction, which would turn the trunk along the beach), with the
     * paths pool's weights. The camp's two trails along the beach draw from the paths pool and may end soon or turn
     * out to sea (the land filter drops those); the inland trunk always grows, so a camp is never a row along the
     * beach and never just its start.
     */
    public static final Map<String, Integer> TRAILHEADS = weights(
            "path", 4, "path_long", 4, "path_fork_left", 2, "path_fork_right", 2);

    /** The trails' ends (STR2): the skull warning post, rocks, a palm. */
    public static final Map<String, Integer> TERMINATORS = weights("path_end", 1, "path_end_rocks", 1, "path_end_palm", 1);

    /** The decoration pool (STR2): crates, rocks and palms are the most common filler. */
    public static final Map<String, Integer> DECORATIONS = weights(
            "deco_campfire", 2, "deco_crates", 3, "deco_hammock", 2, "deco_rocks", 3, "deco_fish_rack", 2,
            "deco_washing_line", 2, "deco_rum_cask", 2, "deco_skull_post", 1, "deco_jolly_roger", 1, "deco_palm", 3);

    private static final String GROUP = "pirate_island";

    private IslandData() {
    }

    public static PortStructureData.Spec spec() {
        return new PortStructureData.Spec(IslandKeys.HAS_PIRATE_ISLAND, IslandKeys.START, SIZE, START_HEIGHT,
                MAX_DISTANCE_FROM_CENTER, ShoreAnchor.PIRATE_CAMP, PortKind.PIRATE_ISLAND, spawnOverrides(), TERRAIN_ADAPTATION, true);
    }

    public static void gather(DataContributions data) {
        // STR8: the island and the cold shores' island share the placement and the zones (the ports' and vanilla's)
        java.util.List<PortStructureData.Exclusion> exclusions = new java.util.ArrayList<>(java.util.List.of(
                new PortStructureData.Exclusion(com.richardsenger.piratesnships.world.village.VillageKeys.SEAFARER_VILLAGES,
                        PORT_EXCLUSION_CHUNKS),
                new PortStructureData.Exclusion(com.richardsenger.piratesnships.world.outpost.OutpostKeys.NAVY_OUTPOSTS,
                        PORT_EXCLUSION_CHUNKS)));
        exclusions.addAll(PortStructureData.vanillaExclusions());
        PortStructureData.writePort(data, IslandKeys.PIRATE_ISLAND, IslandKeys.PIRATE_ISLAND_COLD,
                () -> PortStructureData.withExtension(PortStructureData.structure(spec()), IslandKeys.JETTY_EXTENSION),
                IslandKeys.PIRATE_ISLANDS, IslandKeys.PIRATE_ISLANDS_COLD, "pirate_island", SALT, exclusions);
        PortStructureData.pool(data, IslandKeys.START, GROUP, "minecraft:empty", "rigid", weights("camp_start", 1));
        PortStructureData.pool(data, IslandKeys.PATHS, GROUP, IslandKeys.TERMINATORS.location().toString(), "terrain_matching",
                IslandKeys.TRAIL_LEVEL_PROCESSORS, PATHS);
        PortStructureData.pool(data, IslandKeys.PATHS_STRAIGHT, GROUP, IslandKeys.TERMINATORS.location().toString(), "terrain_matching",
                IslandKeys.TRAIL_LEVEL_PROCESSORS, PATHS_STRAIGHT);
        PortStructureData.pool(data, IslandKeys.TRAILHEADS, GROUP, IslandKeys.TERMINATORS.location().toString(),
                "terrain_matching", IslandKeys.TRAIL_LEVEL_PROCESSORS, TRAILHEADS);
        PortStructureData.pool(data, IslandKeys.HUTS, GROUP, "minecraft:empty", "rigid", HUTS);
        PortStructureData.pool(data, IslandKeys.JETTY, GROUP, "minecraft:empty", "rigid", weights("jetty", 1));
        PortStructureData.pool(data, IslandKeys.JETTY_EXTENSION, GROUP, "minecraft:empty", "rigid", weights("jetty_extension", 1));
        // The paths' fallback once the depth runs out; never empty (vanilla skips a connector with an empty fallback)
        PortStructureData.pool(data, IslandKeys.TERMINATORS, GROUP, "minecraft:empty", "terrain_matching",
                IslandKeys.TRAIL_LEVEL_PROCESSORS, TERMINATORS);
        PortStructureData.pool(data, IslandKeys.DECORATIONS, GROUP, "minecraft:empty", "rigid", DECORATIONS);
        // the STR2 list stays: the trails of islands planned before STR8 name it and keep their look
        data.json(PackOutput.Target.DATA_PACK, "worldgen/processor_list", IslandKeys.TRAIL_PROCESSORS.location(),
                IslandData::trailProcessors);
        PortStructureData.processorList(data, IslandKeys.TRAIL_LEVEL_PROCESSORS, "connections", "trail_bed", "boardwalk");
        PortStructureData.biomeTag(data, IslandKeys.HAS_PIRATE_ISLAND, "#" + PortStructureData.PORT_SHORES.location());
        data.encoded(PackOutput.Target.DATA_PACK, "loot_table", TreasureChests.LOOT_TABLE.location(), LootTable.DIRECT_CODEC,
                TreasureLoot.buriedTreasure());
    }

    /**
     * The trails' processor list (STR2): the connections (WG4) and the boardwalk ({@code BoardwalkProcessor}: a trail
     * running out over water is laid as spruce boards).
     */
    static JsonObject trailProcessors() {
        JsonArray processors = new JsonArray();
        for (String type : new String[] {"connections", "boardwalk"}) {
            JsonObject processor = new JsonObject();
            processor.addProperty("processor_type", Constants.id(type).toString());
            processors.add(processor);
        }
        JsonObject json = new JsonObject();
        json.add("processors", processors);
        return json;
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
