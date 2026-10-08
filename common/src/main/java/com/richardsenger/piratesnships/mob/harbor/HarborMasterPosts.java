package com.richardsenger.piratesnships.mob.harbor;

import com.richardsenger.piratesnships.world.outpost.GarrisonPosts;
import com.richardsenger.piratesnships.world.outpost.GarrisonPosts.PlacedPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where a port's harbor master stands (PRT1a), pure like {@link GarrisonPosts}: one post on each port's start piece,
 * on the harbor master's side of the desk (the side away from the customer), in the piece's local coordinates (y 0
 * the foundation row) and turned with its piece.
 *
 * <ul>
 *   <li><b>Village dock head</b> ({@code village/dock_head.py}): the desk (7, 1, 6) faces north to the door; he stands
 *       behind it at (7, 1, 7), facing north.</li>
 *   <li><b>Navy fort gate</b> ({@code navy_outpost/fort_gate.py}): the desk (3, 1, 8) faces east to the office door; his
 *       stool fills (2, 1, 8) right behind it, so he stands beside the stool at (2, 1, 9), facing east.</li>
 *   <li><b>Pirate camp</b> ({@code pirate_island/camp_start.py}): the fence's counter (9, 1, 2) faces west to the camp;
 *       he stands behind it in the shack at (10, 1, 2), facing west.</li>
 * </ul>
 * A port has one start piece, so the first one in the jigsaw's order holds the post and every chunk agrees on it.
 */
public final class HarborMasterPosts {

    public static final String DOCK_HEAD = "dock_head";
    public static final String FORT_GATE = "fort_gate";
    public static final String CAMP_START = "camp_start";

    /** A post: the block he stands in and the way he faces, both local to the piece. */
    public record Post(BlockPos local, Direction facing) {
    }

    /** The post in the world: block, facing and the piece it belongs to. */
    public record Placement(BlockPos pos, Direction facing, String piece) {
    }

    public static final Post DOCK_HEAD_POST = new Post(new BlockPos(7, 1, 7), Direction.NORTH);
    public static final Post FORT_GATE_POST = new Post(new BlockPos(2, 1, 9), Direction.EAST);
    public static final Post CAMP_POST = new Post(new BlockPos(10, 1, 2), Direction.WEST);

    /** The post of each start piece, by piece name. */
    public static final Map<String, Post> POSTS = Map.of(DOCK_HEAD, DOCK_HEAD_POST, FORT_GATE, FORT_GATE_POST, CAMP_START, CAMP_POST);

    /** The structure group (template folder) of each start piece. */
    public static final Map<String, String> GROUPS = Map.of(DOCK_HEAD, "village", FORT_GATE, "navy_outpost", CAMP_START, "pirate_island");

    private static final Pattern PIECE_NAME = Pattern.compile("pirates_n_ships:(?:village|navy_outpost|pirate_island)/([a-z0-9_]+)");

    private HarborMasterPosts() {
    }

    /**
     * The piece name of a port piece's pool element description (see {@link GarrisonPosts#pieceName}), or empty for
     * anything else.
     */
    public static String pieceName(String element) {
        Matcher m = PIECE_NAME.matcher(element);
        return m.find() ? m.group(1) : "";
    }

    /** The harbor master's post of a port made of {@code pieces} (jigsaw order), or empty without a start piece. */
    public static Optional<Placement> post(List<PlacedPiece> pieces) {
        for (PlacedPiece piece : pieces) {
            Post post = POSTS.get(piece.name());
            if (post != null) {
                return Optional.of(new Placement(GarrisonPosts.toWorld(piece, post.local()),
                        piece.rotation().rotate(post.facing()), piece.name()));
            }
        }
        return Optional.empty();
    }
}
