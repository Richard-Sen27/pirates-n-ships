package com.richardsenger.piratesnships.world.village;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.WorldConfig;
import com.richardsenger.piratesnships.world.structure.PortStructureData;
import com.richardsenger.piratesnships.world.structure.ShoreAnchor;
import net.minecraft.data.PackOutput;

import java.util.Map;

import static com.richardsenger.piratesnships.world.structure.PortStructureData.weights;

/**
 * Datagen of the seafarer village's worldgen files (vanilla formats, raw JSON through {@link PortStructureData}): the
 * structure, its structure set, the template pools, the streets' processor list and the biome tag.
 *
 * <p><b>STR3 (natural layout, design.md §10.1).</b> A street network ({@link #STREETS}: straights, gentle bends, forks,
 * a crossroads, a well square and a market square, ending in one of three turning places), fourteen buildings
 * ({@link #BUILDINGS}: four houses, two taverns, two shipwrights and the warehouse, net loft, fish market, beacon tower,
 * chapel and well) and a decoration pool ({@link #DECORATIONS}). The dock head's inland street draws from
 * {@link #STREETHEADS} (no ends, no bends), so every village climbs inland from the shore anchor (WG1).
 *
 * <p><b>Terrain.</b> Terrain adaptation {@code beard_thin}: the buildings and decorations (rigid, sunk one block onto
 * the surface row) get their ground levelled and the slope round them eased, the streets follow the terrain (terrain
 * matching). Vanilla's beardifier treats every rigid piece alike, so the dock head and the pier would have the sea
 * raised in front of the quay and under the berths: the structure sets {@code unbearded_start} and the port structure
 * takes the dock head (the start) and the pier (rigid, hanging rigidly from the start) out of the beard's reach
 * ({@code PortStructure#unbearded}), as STR2 did for the island's jetty. {@code land_pieces_on_land}: a street that
 * turns out to sea is dropped with what hangs from it ({@code LandFilter}); a street crossing an inlet is laid by the
 * boardwalk processor ({@link VillageKeys#STREET_PROCESSORS}): its cobbled crown and kerbs stay a causeway, its dirt
 * verges become planks.
 */
public final class VillageData {

    /** Fixed salt of the structure set's random spread (never change it: it moves every village of existing worlds). */
    public static final int SALT = 584_103_926;
    public static final int SIZE = 7;
    /**
     * How far the jigsaw may place a piece from the dock head (STR3: 64, was 80): with the network's side streets the
     * village spread up to 153 blocks along the beach while it reached 70 inland (aspect 0.45); 64 keeps it within
     * about 130 either way.
     */
    public static final int MAX_DISTANCE_FROM_CENTER = 64;
    public static final int START_HEIGHT = 1;

    /** STR3: vanilla's terrain adaptation for the village; the buildings sit on the slope instead of floating. */
    public static final String TERRAIN_ADAPTATION = "beard_thin";

    /**
     * Weights of the buildings pool (STR3, art/README.md "Natural layout of the village (STR3)"): the four houses make
     * about half of a village, the two taverns, the two shipwrights and the warehouse, net loft, fish market and chapel
     * turn up once or twice. Big pieces only fit where the streets leave room (the jigsaw tries the next one), so a small
     * piece that fits anywhere is drawn more often than its weight says: the well and the beacon tower (7 by 7) have
     * half the weight of the rest, so a village has about one of each rather than three.
     */
    public static final Map<String, Integer> BUILDINGS = weights(
            "house_small", 6, "house_tall", 4, "house_garden", 4, "house_fisher", 4,
            "tavern", 2, "inn", 2, "shipwright", 2, "slipway", 2,
            "warehouse", 2, "net_loft", 2, "fish_market", 2, "lighthouse", 1, "chapel", 2, "well", 1);

    /**
     * Weights of the streets pool (STR3): straights are 5 of 16 draws, and a street piece leads on to 1.3 street
     * pieces on average (forks two, the crossroads three, the market square none), so a village of size 7 branches two
     * or three times and never runs in one line; the ends are only the fallback.
     */
    public static final Map<String, Integer> STREETS = weights(
            "street", 3, "street_long", 2, "street_bend_left", 2, "street_bend_right", 2,
            "street_fork_left", 2, "street_fork_right", 2, "street_crossroads", 1,
            "street_square_well", 1, "street_square_market", 1);

    /**
     * The streetheads pool (STR3): what the dock head's inland street draws, the pieces that branch at once and still
     * lead on inland (the forks and the crossroads; no end, no bend, no square), with the streets pool's weights. The
     * dock head has one way inland, so a first piece that did not branch left the village to the luck of one street:
     * with the straights and the well square here as well, one seed in sixteen ran inland in a line (aspect below
     * 0.5) in a scan of 80 seeds; every one of them had started without a branch.
     */
    public static final Map<String, Integer> STREETHEADS = weights(
            "street_fork_left", 2, "street_fork_right", 2, "street_crossroads", 1);

    /** The streets' ends (the fallback once the depth runs out): ST1's turning place, a green, a loop. */
    public static final Map<String, Integer> TERMINATORS = weights("street_end", 1, "street_end_green", 1, "street_end_loop", 1);

    /** The decoration pool (STR3): barrels and flowers are the most common filler (the small lantern post fits anywhere). */
    public static final Map<String, Integer> DECORATIONS = weights(
            "deco_cart", 2, "deco_barrels", 3, "deco_nets", 2, "deco_rowboat", 1, "deco_flowers", 3,
            "deco_bench_tree", 2, "deco_lantern", 1, "deco_fish_rack", 2, "deco_woodpile", 2, "deco_laundry", 2);

    private static final String GROUP = "village";

    private VillageData() {
    }

    public static PortStructureData.Spec spec() {
        return new PortStructureData.Spec(VillageKeys.HAS_SEAFARER_VILLAGE, VillageKeys.START, SIZE, START_HEIGHT,
                MAX_DISTANCE_FROM_CENTER, ShoreAnchor.VILLAGE, PortKind.SEAFARER_VILLAGE, new JsonObject(), TERRAIN_ADAPTATION, true);
    }

    /** The structure's JSON: {@link PortStructureData#structure} with the dock head unbearded (STR3). */
    static JsonObject structure() {
        JsonObject json = PortStructureData.structure(spec());
        json.addProperty("unbearded_start", true);
        return json;
    }

    public static void gather(DataContributions data) {
        data.json(PackOutput.Target.DATA_PACK, "worldgen/structure", VillageKeys.SEAFARER_VILLAGE.location(), VillageData::structure);
        PortStructureData.writeStructureSet(data, VillageKeys.SEAFARER_VILLAGES, VillageKeys.SEAFARER_VILLAGE, SALT,
                WorldConfig.SEAFARER_VILLAGE.spacing().defaultValue(), WorldConfig.SEAFARER_VILLAGE_SEPARATION.defaultValue());
        String terminators = VillageKeys.TERMINATORS.location().toString();
        PortStructureData.pool(data, VillageKeys.START, GROUP, "minecraft:empty", "rigid", weights("dock_head", 1));
        PortStructureData.pool(data, VillageKeys.STREETS, GROUP, terminators, "terrain_matching", VillageKeys.STREET_PROCESSORS, STREETS);
        PortStructureData.pool(data, VillageKeys.STREETHEADS, GROUP, terminators, "terrain_matching", VillageKeys.STREET_PROCESSORS,
                STREETHEADS);
        PortStructureData.pool(data, VillageKeys.BUILDINGS, GROUP, "minecraft:empty", "rigid", BUILDINGS);
        PortStructureData.pool(data, VillageKeys.PIER, GROUP, "minecraft:empty", "rigid", weights("pier", 1));
        // The streets' fallback once the depth runs out. It must not be empty: vanilla skips a connector whose fallback
        // pool is empty (and not minecraft:empty), so an empty terminators pool would stop every street.
        PortStructureData.pool(data, VillageKeys.TERMINATORS, GROUP, "minecraft:empty", "terrain_matching", VillageKeys.STREET_PROCESSORS,
                TERMINATORS);
        PortStructureData.pool(data, VillageKeys.DECORATIONS, GROUP, "minecraft:empty", "rigid", DECORATIONS);
        data.json(PackOutput.Target.DATA_PACK, "worldgen/processor_list", VillageKeys.STREET_PROCESSORS.location(),
                VillageData::streetProcessors);
        PortStructureData.biomeTag(data, VillageKeys.HAS_SEAFARER_VILLAGE, "#minecraft:is_beach");
    }

    /**
     * The streets' processor list (STR3): the connections (WG4) and the boardwalk ({@code BoardwalkProcessor}: the
     * dirt verges of a street over water become planks; the cobbled crown and the kerbs stay as a causeway).
     */
    static JsonObject streetProcessors() {
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
}
