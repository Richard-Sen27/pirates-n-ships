package com.richardsenger.piratesnships.mob.squad;

import com.richardsenger.piratesnships.world.outpost.GarrisonPosts;
import com.richardsenger.piratesnships.world.outpost.GarrisonPosts.PlacedPiece;
import com.richardsenger.piratesnships.world.outpost.OutpostPieces;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The patrol route of a navy outpost's squad (MOB2, docs/design.md §9), built from the outpost's placed pieces like
 * {@link GarrisonPosts} builds the posts: open spots in the ST3 pieces' local coordinates (y 0 the foundation row,
 * the sea to the north; art/README.md "Navy outpost (ST3)"), turned with their piece. Pure: no world access.
 *
 * <p>The route, in order:
 * <ol>
 *     <li>the fort gate's parade court (a few steps from the officer's post, at the foot of the stairs), the land
 *     gate's passage, the sea gate's passage;</li>
 *     <li>out onto the quay's deck (every quay, nearest first);</li>
 *     <li>up the court's stairs to the gate's walkway on its east side, then out along the east run to the far end of
 *     every wall piece (STR4: any piece of the trace, {@link OutpostPieces#WALLS}; in walking order, each time the
 *     nearest piece not yet walked, so the file follows the trace round its angles, past every gun);</li>
 *     <li>back along the walls and the gate's sea-side walkway to its west side, then out along the west run the same
 *     way;</li>
 *     <li>back along the west run to the head of the court's stairs.</li>
 * </ol>
 * The squad then walks back to the garrison posts. Every spot stands on the pieces' own paving or walkway, reachable
 * by walking (stairs, no ladders): the walkway is reached by the stone stairs of the gate's court, and the wall
 * towers and the buildings on the apron are left out (ladders, doors). The world still decides: a spot that terrain
 * filled is skipped at patrol time ({@code Squad}).
 */
public final class SquadRoutes {

    /** The fort gate's court centre on the paving row: the outpost's anchor ({@link GarrisonPost#outpost()}). */
    static final BlockPos GATE_CENTRE = new BlockPos(7, 0, 7);
    static final BlockPos COURT = new BlockPos(9, 1, 7);
    static final BlockPos LAND_GATE = new BlockPos(7, 1, 12);
    static final BlockPos SEA_GATE = new BlockPos(7, 1, 3);
    static final BlockPos EAST_WALK = new BlockPos(11, 5, 3);
    static final BlockPos WEST_WALK = new BlockPos(3, 5, 3);
    /**
     * On the ST3 wall's walkway at its end away from the gate, past the gun deck: on the east run the wall's east end,
     * on the west run its west end (beside the wall's post at (1, 5, 2)). Since ST3b the walkway behind the gun is clear
     * two blocks wide (z 3..4, the powder barrel and shot locker stand against the parapet), so the file walks every
     * segment end to end and on into the next. STR4: every wall piece has its own spots ({@link OutpostPieces#WALLS}).
     */
    static final BlockPos WALL_WALK_EAST_RUN = OutpostPieces.WALLS.get("wall").walkEast();
    static final BlockPos WALL_WALK_WEST_RUN = OutpostPieces.WALLS.get("wall").walkWest();
    /** The gate's walkway at the head of the court's stairs: the route's last waypoint, back from the west run. */
    static final BlockPos STAIR_HEAD = new BlockPos(12, 5, 4);
    /** On the quay's deck (y 5 continues the gate's paving), between the bollards. */
    static final BlockPos QUAY_DECK = new BlockPos(3, 6, 7);

    static final String QUAY = "quay";

    private SquadRoutes() {
    }

    /** The world position of the outpost's anchor (its fort gate's court centre), or {@code null} without a fort gate. */
    public static BlockPos anchor(List<PlacedPiece> pieces) {
        PlacedPiece gate = gate(pieces);
        return gate == null ? null : GarrisonPosts.toWorld(gate, GATE_CENTRE);
    }

    /** The patrol route of an outpost made of {@code pieces}; empty without a fort gate. */
    public static List<BlockPos> route(List<PlacedPiece> pieces) {
        PlacedPiece gate = gate(pieces);
        if (gate == null) return List.of();
        BlockPos centre = GarrisonPosts.toWorld(gate, GATE_CENTRE);
        Direction east = gate.rotation().rotate(Direction.EAST);

        List<BlockPos> out = new ArrayList<>();
        out.add(GarrisonPosts.toWorld(gate, COURT));
        out.add(GarrisonPosts.toWorld(gate, LAND_GATE));
        out.add(GarrisonPosts.toWorld(gate, SEA_GATE));
        out.addAll(nearestFirst(spots(pieces, QUAY, QUAY_DECK), centre));

        List<BlockPos> eastWalls = new ArrayList<>();
        List<BlockPos> westWalls = new ArrayList<>();
        for (PlacedPiece wall : pieces) {
            OutpostPieces.Wall kind = OutpostPieces.WALLS.get(wall.name());
            if (kind == null) continue;
            BlockPos eastSpot = GarrisonPosts.toWorld(wall, kind.walkEast());
            BlockPos westSpot = GarrisonPosts.toWorld(wall, kind.walkWest());
            int along = (eastSpot.getX() + westSpot.getX() - 2 * centre.getX()) * east.getStepX()
                    + (eastSpot.getZ() + westSpot.getZ() - 2 * centre.getZ()) * east.getStepZ();
            if (along >= 0) eastWalls.add(eastSpot);
            else westWalls.add(westSpot);
        }
        BlockPos eastWalk = GarrisonPosts.toWorld(gate, EAST_WALK);
        BlockPos westWalk = GarrisonPosts.toWorld(gate, WEST_WALK);
        out.add(eastWalk);
        out.addAll(alongTheRun(eastWalls, eastWalk));
        out.add(westWalk);
        out.addAll(alongTheRun(westWalls, westWalk));
        out.add(GarrisonPosts.toWorld(gate, STAIR_HEAD));
        return List.copyOf(out);
    }

    private static PlacedPiece gate(List<PlacedPiece> pieces) {
        return pieces.stream().filter(p -> GarrisonPosts.FORT_GATE.equals(p.name())).findFirst().orElse(null);
    }

    private static List<BlockPos> spots(List<PlacedPiece> pieces, String name, BlockPos local) {
        return pieces.stream().filter(p -> name.equals(p.name())).map(p -> GarrisonPosts.toWorld(p, local)).toList();
    }

    /**
     * The run's spots in walking order (STR4): from the gate's walkway, each time the nearest spot not yet walked to, so
     * a run that bends round an angle is walked piece by piece along its trace (on a straight run this is the nearest
     * first). Ties keep the jigsaw's order.
     */
    private static List<BlockPos> alongTheRun(List<BlockPos> spots, BlockPos from) {
        List<BlockPos> left = new ArrayList<>(spots);
        List<BlockPos> out = new ArrayList<>(spots.size());
        BlockPos at = from;
        while (!left.isEmpty()) {
            BlockPos next = nearestFirst(left, at).get(0);
            left.remove(next);
            out.add(next);
            at = next;
        }
        return out;
    }

    /** Sorted by horizontal distance from {@code centre}, stable (ties keep the jigsaw's order). */
    private static List<BlockPos> nearestFirst(List<BlockPos> spots, BlockPos centre) {
        List<BlockPos> sorted = new ArrayList<>(spots);
        sorted.sort(Comparator.comparingLong(p -> {
            long dx = p.getX() - centre.getX();
            long dz = p.getZ() - centre.getZ();
            return dx * dx + dz * dz;
        }));
        return sorted;
    }
}
