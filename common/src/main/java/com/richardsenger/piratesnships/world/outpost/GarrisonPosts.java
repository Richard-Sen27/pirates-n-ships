package com.richardsenger.piratesnships.world.outpost;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where a navy outpost's garrison stands (WG3, design.md §10.1): a fixed table of posts in the ST3 pieces' local
 * coordinates (y 0 the foundation row, the sea to the north, art/README.md "Navy outpost (ST3)") and the rule that
 * fills them. Pure: no world access, so the same plan comes out in every chunk the outpost is placed in.
 *
 * <p><b>Posts.</b> Each post is the block a mob stands in (air, with a walkable block below and air above) and the
 * direction it faces, both turned with its piece. Officers: the fort gate's court beside the harbor master's office
 * door, then behind the desk, then across the path. Soldiers by tier:
 * <ol>
 *     <li>the two guards inside the land gate (facing landward);</li>
 *     <li>one post on every wall segment's walkway (beside the gun) and every wall tower's roof, facing the sea,
 *     nearest to the gate first (the two runs alternate);</li>
 *     <li>the building on the apron: inside the barracks, in the brig's guard room facing the cells, or on the
 *     watchtower's lookout;</li>
 *     <li>the gate's own walkway over the sea gate, then its landward wall.</li>
 * </ol>
 * Within a tier posts nearer the gate's centre come first, ties in the jigsaw's piece order. A plan takes the first
 * {@code soldiers} and {@code officers} posts and never puts two mobs on one post, so the counts are capped by the
 * posts the layout has: the fort gate alone has six soldier and three officer posts.
 */
public final class GarrisonPosts {

    public enum Role { SOLDIER, OFFICER }

    /** A post in its piece's local coordinates; {@code tier} orders the soldiers' posts (officers have their own list). */
    public record Post(Role role, BlockPos local, Direction facing, int tier) {
    }

    /** A placed piece of the outpost: its ST3 name ({@code fort_gate}, {@code wall}, ...), template origin and rotation. */
    public record PlacedPiece(String name, BlockPos origin, Rotation rotation) {
    }

    /** One mob of the garrison: where it stands (world block) and which way it looks. */
    public record Assignment(Role role, BlockPos pos, Direction facing) {
    }

    public static final String FORT_GATE = "fort_gate";
    /** The fort gate's court centre (local), the reference for "nearest to the gate". */
    static final BlockPos GATE_CENTRE = new BlockPos(7, 0, 7);

    static final int TIER_GATE = 0;
    static final int TIER_WALLS = 1;
    static final int TIER_BUILDING = 2;
    static final int TIER_GATE_WALKS = 3;

    /** The posts of each ST3 piece; the quay has none. */
    public static final Map<String, List<Post>> POSTS = Map.of(
            FORT_GATE, List.of(
                    officer(6, 1, 7, Direction.EAST),
                    officer(2, 1, 7, Direction.EAST),
                    officer(8, 1, 7, Direction.WEST),
                    soldier(6, 1, 10, Direction.SOUTH, TIER_GATE),
                    soldier(8, 1, 10, Direction.SOUTH, TIER_GATE),
                    soldier(3, 5, 2, Direction.NORTH, TIER_GATE_WALKS),
                    soldier(11, 5, 2, Direction.NORTH, TIER_GATE_WALKS),
                    soldier(3, 5, 13, Direction.SOUTH, TIER_GATE_WALKS),
                    soldier(11, 5, 13, Direction.SOUTH, TIER_GATE_WALKS)),
            "wall", List.of(soldier(1, 5, 2, Direction.NORTH, TIER_WALLS)),
            "wall_tower", List.of(soldier(5, 10, 1, Direction.NORTH, TIER_WALLS)),
            "barracks", List.of(soldier(7, 1, 3, Direction.NORTH, TIER_BUILDING)),
            "brig", List.of(soldier(5, 1, 2, Direction.SOUTH, TIER_BUILDING)),
            "watchtower", List.of(soldier(1, 11, 1, Direction.NORTH, TIER_BUILDING)));

    private static final Pattern PIECE_NAME = Pattern.compile("navy_outpost/([a-z0-9_]+)");

    private GarrisonPosts() {
    }

    /**
     * The ST3 piece name in a pool element's description (vanilla's {@code SinglePoolElement.toString()} is
     * {@code Single[Left[pirates_n_ships:navy_outpost/wall]]}; the element has no public template accessor), or empty.
     */
    public static String pieceName(String element) {
        Matcher m = PIECE_NAME.matcher(element);
        return m.find() ? m.group(1) : "";
    }

    /** The world block of a piece's local position: templates rotate about their origin (pivot 0), never mirrored. */
    public static BlockPos toWorld(PlacedPiece piece, BlockPos local) {
        return piece.origin().offset(local.rotate(piece.rotation()));
    }

    /**
     * The garrison of an outpost made of {@code pieces} (in the jigsaw's order): at most {@code soldiers} soldiers and
     * {@code officers} officers, officers first. No fort gate: no garrison.
     */
    public static List<Assignment> plan(List<PlacedPiece> pieces, int soldiers, int officers) {
        PlacedPiece gate = pieces.stream().filter(p -> FORT_GATE.equals(p.name())).findFirst().orElse(null);
        if (gate == null) return List.of();
        BlockPos centre = toWorld(gate, GATE_CENTRE);

        record Candidate(Assignment assignment, int tier, long distance, int order) {
        }
        List<Candidate> officerPosts = new ArrayList<>();
        List<Candidate> soldierPosts = new ArrayList<>();
        int order = 0;
        for (PlacedPiece piece : pieces) {
            for (Post post : POSTS.getOrDefault(piece.name(), List.of())) {
                BlockPos pos = toWorld(piece, post.local());
                long dx = pos.getX() - centre.getX();
                long dz = pos.getZ() - centre.getZ();
                Candidate c = new Candidate(new Assignment(post.role(), pos, piece.rotation().rotate(post.facing())),
                        post.tier(), dx * dx + dz * dz, order++);
                (post.role() == Role.OFFICER ? officerPosts : soldierPosts).add(c);
            }
        }
        soldierPosts.sort(Comparator.comparingInt(Candidate::tier).thenComparingLong(Candidate::distance)
                .thenComparingInt(Candidate::order));

        List<Assignment> out = new ArrayList<>();
        officerPosts.stream().limit(Math.max(0, officers)).forEach(c -> out.add(c.assignment()));
        soldierPosts.stream().limit(Math.max(0, soldiers)).forEach(c -> out.add(c.assignment()));
        return List.copyOf(out);
    }

    private static Post officer(int x, int y, int z, Direction facing) {
        return new Post(Role.OFFICER, new BlockPos(x, y, z), facing, 0);
    }

    private static Post soldier(int x, int y, int z, Direction facing, int tier) {
        return new Post(Role.SOLDIER, new BlockPos(x, y, z), facing, tier);
    }
}
