package com.richardsenger.piratesnships.mob.captain;

import com.richardsenger.piratesnships.world.outpost.GarrisonPosts;
import com.richardsenger.piratesnships.world.outpost.GarrisonPosts.PlacedPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where a pirate island's captain stands (BOS1), pure like {@link GarrisonPosts}: a post in the ST2 pieces' local
 * coordinates (y 0 the foundation row, art/structures/pirate_island/*.py), turned with its piece.
 *
 * <ul>
 *   <li><b>Captain's hut</b> ({@code captains_hut.py}): the middle of the room, (4, 3, 5) on the floor boards (y 2),
 *       between the rug and the stool, one block in from the door (4, 3, 3), facing it (north).</li>
 *   <li><b>No hut:</b> the huts pool draws the captain's hut one time in six, so an island without one puts its captain
 *       in the camp ({@code camp_start.py}): on the inland trail (6, 1, 10) south of the east-west trail, facing the
 *       campfire (north).</li>
 * </ul>
 * The island's first captain's hut in the jigsaw's piece order wins, so the same post comes out in every chunk.
 */
public final class CaptainPosts {

    public static final String CAPTAINS_HUT = "captains_hut";
    public static final String CAMP_START = "camp_start";

    /** A post: the block the captain stands in and the way he faces, both local to the piece. */
    public record Post(BlockPos local, Direction facing) {
    }

    /** The post in the world: block and facing. */
    public record Placement(BlockPos pos, Direction facing, String piece) {
    }

    public static final Post HUT_POST = new Post(new BlockPos(4, 3, 5), Direction.NORTH);
    public static final Post CAMP_POST = new Post(new BlockPos(6, 1, 10), Direction.NORTH);

    private static final Pattern PIECE_NAME = Pattern.compile("pirate_island/([a-z0-9_]+)");

    private CaptainPosts() {
    }

    /** The ST2 piece name in a pool element's description (see {@link GarrisonPosts#pieceName}), or empty. */
    public static String pieceName(String element) {
        Matcher m = PIECE_NAME.matcher(element);
        return m.find() ? m.group(1) : "";
    }

    /** The captain's post of an island made of {@code pieces} (jigsaw order), or empty without a hut or a camp. */
    public static Optional<Placement> post(List<PlacedPiece> pieces) {
        for (String name : List.of(CAPTAINS_HUT, CAMP_START)) {
            Post post = CAPTAINS_HUT.equals(name) ? HUT_POST : CAMP_POST;
            for (PlacedPiece piece : pieces) {
                if (name.equals(piece.name())) {
                    return Optional.of(new Placement(GarrisonPosts.toWorld(piece, post.local()),
                            piece.rotation().rotate(post.facing()), name));
                }
            }
        }
        return Optional.empty();
    }
}
