package com.richardsenger.piratesnships.world.outpost;

import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.trade.market.PortKind;
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
 * many pieces in three states, each its own pool: along the shore ({@link #WALLS}; the gate's own pair draws
 * {@link #WALLS_GATE}, the same without the angles), down the flank after a salient
 * angle ({@link #WALLS_FLANK_HEAD} for its first piece, then {@link #WALLS_FLANK}); a second angle turns it back toward
 * the other run and draws the corner tower that closes it (a run that ran on toward the other one ran into the court's
 * first lane, laid before the walls grow). Every run ends in a tower ({@link #TERMINATORS}, the walls' fallback). The wall connectors carry placement priority 2, so both runs are
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
    /**
     * STR6 (the human, 2026-10-12: "navy_post directly next to mansion, should not happen"): no outpost starts within
     * this many chunks of a seafarer village's placement chunk (vanilla's {@code exclusion_zone}). Both reach about 64
     * blocks (4 chunks) from their start chunk in the layout dumps, so 8 chunks keeps the starts at least 9 chunks
     * (144 blocks) apart, more than the two can reach toward each other; it costs the outposts about a third of their
     * possible sites (17 by 17 chunks of every 30 by 30 village grid cell).
     */
    public static final int VILLAGE_EXCLUSION_CHUNKS = 8;
    /** STR4: seven deep, so a run has room along the shore, round its angles and down the flank. */
    public static final int SIZE = 7;
    /**
     * STR4: room for a run of seven of the longest pieces and its tower along the shore (7 + 7 × 11 + 7 = 91), so a run
     * that never turns still ends in a tower, and for the court inland.
     */
    public static final int MAX_DISTANCE_FROM_CENTER = 96;
    public static final int START_HEIGHT = 1;
    /** The fort gate (15×10×15): its sea gate and quay connector in the middle of its north edge. */
    public static final ShoreAnchor FORT_GATE = new ShoreAnchor(7, 0);
    public static final String TERRAIN_ADAPTATION = "beard_thin";

    /**
     * The shore state (STR4): straights of two lengths, a bastion, a sally port, a wall with a stair to the court, and
     * the salient angles that turn a run inland. Per run (an angle serves one run) the angle is 5 of 13, the bastion 2:
     * a run turns inland after two to four pieces along the shore as a rule.
     */
    public static final Map<String, Integer> WALLS = weights("wall", 2, "wall_long", 2, "wall_bastion", 2,
            "wall_sally_port", 1, "wall_stair", 1, "wall_angle_east", 5, "wall_angle_west", 5);
    /**
     * What the gate's own pair draws: the shore state without the angles, so a run stands at least one piece along the
     * shore before it turns inland and the court has room between the flanks.
     */
    public static final Map<String, Integer> WALLS_GATE = weights("wall", 2, "wall_long", 3, "wall_bastion", 2,
            "wall_sally_port", 1, "wall_stair", 1);
    /** The flank's first piece after the shore's angle: straights only, so the flank runs inland before it turns. */
    public static final Map<String, Integer> WALLS_FLANK_HEAD = weights("flank_wall", 2, "flank_long", 3, "flank_bastion", 1,
            "flank_sally_port", 1, "flank_stair", 1);
    /**
     * The flank (after one angle): mostly straights, so the flank runs a good way inland and the court has room; now
     * and then a second angle that closes the run with a corner tower (1 of 11 per run) or a step back out to the shore
     * state (1 of 11).
     */
    public static final Map<String, Integer> WALLS_FLANK = weights("flank_wall", 2, "flank_long", 3, "flank_bastion", 1,
            "flank_sally_port", 1, "flank_stair", 1, "flank_angle_east", 1, "flank_angle_west", 1,
            "flank_step_east", 1, "flank_step_west", 1);
    /** The run's end (the walls' fallback): the ST3 corner tower, a round tower, a squat battery. */
    public static final Map<String, Integer> TERMINATORS = weights("wall_tower", 2, "round_tower", 2, "battery", 1);

    /** What the land gate draws: a lane, a long lane, a fork or the parade ground; never an end, a bend or a T. */
    public static final Map<String, Integer> LANEHEADS = weights("lane", 2, "lane_long", 2, "parade_ground", 3,
            "lane_fork_left", 1, "lane_fork_right", 1);
    /**
     * The court's lanes (straights 7 of 15, bends 4, forks and the T 3, the parade ground 1); the dead ends are only
     * their fallback, where the walls, the depth or the room stop a lane (drawn here as well, they cut courts short).
     * Forks are rare: their branches run parallel to the trunk a few blocks off and leave room only for the smallest
     * buildings between them.
     */
    public static final Map<String, Integer> LANES = weights("lane", 5, "lane_long", 4, "lane_bend_left", 1,
            "lane_bend_right", 1, "lane_fork_left", 1, "lane_fork_right", 1, "lane_junction", 1, "parade_ground", 1);
    /**
     * STR8 (the human: the paths are "too curly"): what a bend's, a fork's and the junction's ways on draw, the
     * straights only, so two turns never follow each other. With it the lanes pool moved toward the straights (9 of 15
     * draws, were 7 of 15; the bends 2 of 15, were 4 of 15).
     */
    public static final Map<String, Integer> LANES_STRAIGHT = weights("lane", 3, "lane_long", 2);
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
        // STR8: the outpost and the cold shores' outpost share the placement and the zones (the villages' and vanilla's)
        java.util.List<PortStructureData.Exclusion> exclusions = new java.util.ArrayList<>(java.util.List.of(new PortStructureData.Exclusion(
                com.richardsenger.piratesnships.world.village.VillageKeys.SEAFARER_VILLAGES, VILLAGE_EXCLUSION_CHUNKS)));
        exclusions.addAll(PortStructureData.vanillaExclusions());
        PortStructureData.writePort(data, OutpostKeys.NAVY_OUTPOST, OutpostKeys.NAVY_OUTPOST_COLD,
                () -> PortStructureData.withExtension(PortStructureData.structure(spec()), OutpostKeys.QUAY_EXTENSION),
                OutpostKeys.NAVY_OUTPOSTS, OutpostKeys.NAVY_OUTPOSTS_COLD, "navy_outpost", SALT, exclusions);
        String terminators = OutpostKeys.TERMINATORS.location().toString();
        String laneEnds = OutpostKeys.LANE_ENDS.location().toString();
        PortStructureData.pool(data, OutpostKeys.START, GROUP, "minecraft:empty", "rigid", weights("fort_gate", 1));
        PortStructureData.pool(data, OutpostKeys.WALLS_GATE, GROUP, terminators, "rigid", WALLS_GATE);
        PortStructureData.pool(data, OutpostKeys.WALLS, GROUP, terminators, "rigid", WALLS);
        PortStructureData.pool(data, OutpostKeys.WALLS_FLANK_HEAD, GROUP, terminators, "rigid", WALLS_FLANK_HEAD);
        PortStructureData.pool(data, OutpostKeys.WALLS_FLANK, GROUP, terminators, "rigid", WALLS_FLANK);
        // the walls' fallback once the depth or the room runs out; never empty (vanilla skips a connector with an empty
        // fallback). Its own fallback is itself: the flank's angle draws the towers directly (end_*_out), and at the
        // depth limit vanilla tries only a pool's fallback, so an angle there still ends its run in a tower
        PortStructureData.pool(data, OutpostKeys.TERMINATORS, GROUP, terminators, "rigid", TERMINATORS);
        PortStructureData.pool(data, OutpostKeys.QUAY, GROUP, "minecraft:empty", "rigid", weights("quay", 1));
        PortStructureData.pool(data, OutpostKeys.QUAY_EXTENSION, GROUP, "minecraft:empty", "rigid", weights("quay_extension", 1));
        PortStructureData.pool(data, OutpostKeys.LANEHEADS, GROUP, laneEnds, "terrain_matching", OutpostKeys.LANE_PROCESSORS, LANEHEADS);
        PortStructureData.pool(data, OutpostKeys.LANES, GROUP, laneEnds, "terrain_matching", OutpostKeys.LANE_PROCESSORS, LANES);
        PortStructureData.pool(data, OutpostKeys.LANES_STRAIGHT, GROUP, laneEnds, "terrain_matching", OutpostKeys.LANE_PROCESSORS,
                LANES_STRAIGHT);
        PortStructureData.pool(data, OutpostKeys.LANE_ENDS, GROUP, "minecraft:empty", "terrain_matching", OutpostKeys.LANE_PROCESSORS,
                LANE_ENDS);
        PortStructureData.pool(data, OutpostKeys.BUILDINGS, GROUP, "minecraft:empty", "rigid", BUILDINGS);
        PortStructureData.pool(data, OutpostKeys.DECORATIONS, GROUP, "minecraft:empty", "rigid", DECORATIONS);
        // STR8: the lanes' own list (the court's lanes named the connections list before)
        PortStructureData.processorList(data, OutpostKeys.LANE_PROCESSORS, "connections", "loot_chests", "trail_bed");
        PortStructureData.biomeTag(data, OutpostKeys.HAS_NAVY_OUTPOST, "#" + PortStructureData.PORT_SHORES.location());
    }
}
