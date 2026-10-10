package com.richardsenger.piratesnships.world.outpost;

import net.minecraft.core.BlockPos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The navy outpost's piece vocabulary (ST3, STR4; art/README.md "Natural layout of the outpost (STR4)"): which pieces
 * are curtain wall, which end a run, which are buildings, and where on each wall piece the garrison and the squads
 * stand. Pure tables in the pieces' local coordinates (y 0 the foundation row, the outside to the north), shared by
 * {@link GarrisonPosts} and {@code mob/squad/SquadRoutes}.
 *
 * <p><b>Walls.</b> A run grows from the fort gate in two states (along the shore, down the flank), each a pool of the
 * same kinds of piece; the kinds share their geometry, so a {@link Wall} entry holds for both states of its kind.
 * Symmetric pieces serve both runs; the angles and steps belong to one run (a jigsaw never mirrors).
 */
public final class OutpostPieces {

    /**
     * A wall piece: the post of its guard (facing outward, local north), and the spot at the far end of its walkway
     * for each run (where a patrol walking out along the east or west run turns to the next piece).
     */
    public record Wall(BlockPos post, BlockPos walkEast, BlockPos walkWest) {
    }

    /** Every wall piece by name. */
    public static final Map<String, Wall> WALLS;
    /** The pieces that end a run (the terminators pool). */
    public static final List<String> TOWERS = List.of("wall_tower", "round_tower", "battery");
    /** The buildings of the court (the buildings pool), the ST3 three first. */
    public static final List<String> BUILDINGS = List.of("barracks", "brig", "watchtower", "barracks_long", "barracks_mess",
            "brig_large", "signal_tower", "officers_quarters", "powder_magazine", "armoury", "gun_battery", "store_house", "chapel");
    /** The brig variants (cells of brig bars with brig doors). */
    public static final Set<String> BRIGS = Set.of("brig", "brig_large");

    static {
        Map<String, Wall> walls = new LinkedHashMap<>();
        Wall straight = new Wall(new BlockPos(1, 5, 2), new BlockPos(5, 5, 3), new BlockPos(1, 5, 3));
        Wall longWall = new Wall(new BlockPos(1, 5, 2), new BlockPos(9, 5, 3), new BlockPos(1, 5, 3));
        Wall nine = new Wall(new BlockPos(1, 5, 2), new BlockPos(7, 5, 3), new BlockPos(1, 5, 3));
        // the bastion stands on three rows of footings and its line runs behind the projection (z 5..9)
        Wall bastion = new Wall(new BlockPos(5, 8, 2), new BlockPos(9, 8, 8), new BlockPos(1, 8, 8));
        for (String state : List.of("wall", "flank")) {
            walls.put(state.equals("wall") ? "wall" : state + "_wall", straight);
            walls.put(state + "_long", longWall);
            walls.put(state + "_sally_port", nine);
            walls.put(state + "_stair", nine);
            walls.put(state + "_bastion", bastion);
        }
        for (String state : List.of("wall", "flank")) {
            Wall east = new Wall(new BlockPos(5, 5, 1), new BlockPos(4, 5, 5), new BlockPos(4, 5, 5));
            Wall west = new Wall(new BlockPos(1, 5, 1), new BlockPos(2, 5, 5), new BlockPos(2, 5, 5));
            walls.put(state + "_angle_east", east);
            walls.put(state + "_angle_west", west);
        }
        walls.put("flank_step_east", new Wall(new BlockPos(1, 5, 2), new BlockPos(4, 5, 1), new BlockPos(4, 5, 1)));
        walls.put("flank_step_west", new Wall(new BlockPos(7, 5, 2), new BlockPos(3, 5, 1), new BlockPos(3, 5, 1)));
        WALLS = Map.copyOf(walls);
    }

    private OutpostPieces() {
    }

    public static boolean isWall(String name) {
        return WALLS.containsKey(name);
    }

    public static boolean isTower(String name) {
        return TOWERS.contains(name);
    }
}
