package com.richardsenger.piratesnships.world.outpost;

import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.WorldConfig;
import com.richardsenger.piratesnships.world.structure.PortStructureData;
import com.richardsenger.piratesnships.world.structure.ShoreAnchor;

import java.util.Map;

import static com.richardsenger.piratesnships.world.structure.PortStructureData.weights;

/**
 * Datagen of the navy outpost's worldgen files (WG3, design.md §10.1): the structure (type
 * {@code pirates_n_ships:port_village}, port kind {@code navy_outpost}, the fort gate's shore anchor (7, 0)), its
 * structure set (random spread 48/20, the rarest port), the pools and the biome tag (beaches).
 *
 * <p><b>STR4 (natural layout, art/README.md "Natural layout of the outpost (STR4)").</b> The curtain wall is a trace of
 * many pieces in three states, each its own pool: along the shore ({@link #WALLS}), down the flank after a salient
 * angle ({@link #WALLS_FLANK}) and back toward the other run after a second ({@link #WALLS_BACK}); every run ends in a
 * tower ({@link #TERMINATORS}, the walls' fallback). The wall connectors carry placement priority 2, so both runs are
 * laid out before the court: lanes of gravel and cobbles (terrain matching, {@link #LANES}) grow inland from the land
 * gate ({@link #LANEHEADS}: never an end or a bend, so the court always grows inland), with buildings
 * ({@link #BUILDINGS}) and decorations ({@link #DECORATIONS}) on their sides and dead ends against the wall
 * ({@link #LANE_ENDS}). Terrain adaptation {@code beard_thin}: the buildings and decorations hanging from the lanes sit
 * on the slope; the rigid chain from the gate (walls, towers, quay) is kept out of the beard
 * ({@code PortStructure.unbearded}), so the walls stay level and the quay's water stays water.
 * {@code land_pieces_on_land}: a lane over open water is dropped with what hangs from it. No spawn overrides: the navy
 * is {@code misc} and never spawns naturally; the garrison is placed with the structure ({@link Garrison}).
 */
public final class OutpostData {

    /** Fixed salt of the structure set's random spread (never change it: it moves every outpost of existing worlds). */
    public static final int SALT = 902_417_385;
    /** STR4: six deep, so a run has room for its angles and the court for a few lanes. */
    public static final int SIZE = 6;
    /** STR4: room for a wall run of six pieces and a tower each way, and for the court inland. */
    public static final int MAX_DISTANCE_FROM_CENTER = 80;
    public static final int START_HEIGHT = 1;
    /** The fort gate (15×10×15): its sea gate and quay connector in the middle of its north edge. */
    public static final ShoreAnchor FORT_GATE = new ShoreAnchor(7, 0);
    public static final String TERRAIN_ADAPTATION = "beard_thin";

    /**
     * The shore state (STR4): straights of two lengths, a bastion, a sally port, a wall with a stair to the court, and
     * the salient angles that turn a run inland. Per run (an angle serves one run) the angle is 4 of 12, the bastion 2.
     */
    public static final Map<String, Integer> WALLS = weights("wall", 2, "wall_long", 2, "wall_bastion", 2,
            "wall_sally_port", 1, "wall_stair", 1, "wall_angle_east", 4, "wall_angle_west", 4);
    /** The flank (after one angle): mostly straights, a second angle (2 of 12 per run) or a step back out (1 of 12). */
    public static final Map<String, Integer> WALLS_FLANK = weights("flank_wall", 2, "flank_long", 3, "flank_bastion", 1,
            "flank_sally_port", 1, "flank_stair", 1, "flank_angle_east", 2, "flank_angle_west", 2,
            "flank_step_east", 1, "flank_step_west", 1);
    /** The back (after two angles): straights only, it never turns again. */
    public static final Map<String, Integer> WALLS_BACK = weights("back_wall", 2, "back_long", 2, "back_stair", 1,
            "back_sally_port", 1);
    /** The run's end (the walls' fallback): the ST3 corner tower, a round tower, a squat battery. */
    public static final Map<String, Integer> TERMINATORS = weights("wall_tower", 2, "round_tower", 2, "battery", 1);

    /** What the land gate draws: a lane, a long lane, a fork or the parade ground; never an end, a bend or a T. */
    public static final Map<String, Integer> LANEHEADS = weights("lane", 2, "lane_long", 2, "parade_ground", 3,
            "lane_fork_left", 2, "lane_fork_right", 2);
    /** The court's lanes (straights 5 of 15, the rest turn, fork or open into the parade ground). */
    public static final Map<String, Integer> LANES = weights("lane", 3, "lane_long", 2, "lane_bend_left", 2,
            "lane_bend_right", 2, "lane_fork_left", 2, "lane_fork_right", 2, "lane_junction", 1, "parade_ground", 1);
    /** The lanes' dead ends against the wall (their fallback): a store shed, the privy, a woodpile. */
    public static final Map<String, Integer> LANE_ENDS = weights("lane_end_store", 1, "lane_end_privy", 1,
            "lane_end_woodpile", 1);

    /**
     * The buildings (WG3's barracks 2 / brig 1 / watchtower 1, STR4's variants and new buildings): barracks, the
     * officers' quarters, the armoury and the store house are the common ones, a court of five or six buildings
     * usually mixes four or more kinds.
     */
    public static final Map<String, Integer> BUILDINGS = weights("barracks", 2, "brig", 1, "watchtower", 1,
            "barracks_long", 2, "barracks_mess", 1, "brig_large", 1, "signal_tower", 1, "officers_quarters", 2,
            "powder_magazine", 1, "armoury", 2, "gun_battery", 1, "store_house", 2, "chapel", 1);

    /** The decorations of the lanes: shot and stores are the most common filler. */
    public static final Map<String, Integer> DECORATIONS = weights("deco_cannon_balls", 3, "deco_crates", 3,
            "deco_drill_target", 2, "deco_lantern_post", 2, "deco_bell_post", 1, "deco_notice_board", 1,
            "deco_water_trough", 2, "deco_cart", 1, "deco_tree", 2, "deco_benches", 2);

    private static final String GROUP = "navy_outpost";

    private OutpostData() {
    }

    public static PortStructureData.Spec spec() {
        return new PortStructureData.Spec(OutpostKeys.HAS_NAVY_OUTPOST, OutpostKeys.START, SIZE, START_HEIGHT,
                MAX_DISTANCE_FROM_CENTER, FORT_GATE, PortKind.NAVY_OUTPOST, new JsonObject(), TERRAIN_ADAPTATION, true);
    }

    public static void gather(DataContributions data) {
        PortStructureData.writeStructure(data, OutpostKeys.NAVY_OUTPOST, spec());
        PortStructureData.writeStructureSet(data, OutpostKeys.NAVY_OUTPOSTS, OutpostKeys.NAVY_OUTPOST, SALT,
                WorldConfig.NAVY_OUTPOST.spacing().defaultValue(), WorldConfig.NAVY_OUTPOST_SEPARATION.defaultValue());
        String terminators = OutpostKeys.TERMINATORS.location().toString();
        String laneEnds = OutpostKeys.LANE_ENDS.location().toString();
        PortStructureData.pool(data, OutpostKeys.START, GROUP, "minecraft:empty", "rigid", weights("fort_gate", 1));
        PortStructureData.pool(data, OutpostKeys.WALLS, GROUP, terminators, "rigid", WALLS);
        PortStructureData.pool(data, OutpostKeys.WALLS_FLANK, GROUP, terminators, "rigid", WALLS_FLANK);
        PortStructureData.pool(data, OutpostKeys.WALLS_BACK, GROUP, terminators, "rigid", WALLS_BACK);
        // the walls' fallback once the depth or the room runs out; never empty (vanilla skips a connector with an empty fallback)
        PortStructureData.pool(data, OutpostKeys.TERMINATORS, GROUP, "minecraft:empty", "rigid", TERMINATORS);
        PortStructureData.pool(data, OutpostKeys.QUAY, GROUP, "minecraft:empty", "rigid", weights("quay", 1));
        PortStructureData.pool(data, OutpostKeys.LANEHEADS, GROUP, laneEnds, "terrain_matching", LANEHEADS);
        PortStructureData.pool(data, OutpostKeys.LANES, GROUP, laneEnds, "terrain_matching", LANES);
        PortStructureData.pool(data, OutpostKeys.LANE_ENDS, GROUP, "minecraft:empty", "terrain_matching", LANE_ENDS);
        PortStructureData.pool(data, OutpostKeys.BUILDINGS, GROUP, "minecraft:empty", "rigid", BUILDINGS);
        PortStructureData.pool(data, OutpostKeys.DECORATIONS, GROUP, "minecraft:empty", "rigid", DECORATIONS);
        PortStructureData.biomeTag(data, OutpostKeys.HAS_NAVY_OUTPOST, "#minecraft:is_beach");
    }
}
