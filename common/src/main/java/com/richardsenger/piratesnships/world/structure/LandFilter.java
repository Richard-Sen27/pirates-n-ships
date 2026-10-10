package com.richardsenger.piratesnships.world.structure;

import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;

/**
 * Keeps a port's land pieces on land (STR2, {@code land_pieces_on_land}): the jigsaw assembler knows nothing of the sea,
 * so a trail that leaves the camp along the beach and bends seaward would run on over the open water, with its huts
 * after it. This drops every land piece (a terrain-matching trail, or a building or decoration hanging from one) whose
 * footprint lies mostly over water, and every piece that hangs from a dropped one; the start piece and the pieces
 * hanging rigidly from it (the jetty) stay. A trail that crosses a narrow inlet keeps its boards (most of it is on
 * land); a branch out to sea ends at the waterline. Pure: the water test is passed in.
 */
public final class LandFilter {

    /** Footprint samples per piece: the centre and the four corners (one block in). */
    public static final int SAMPLES = 5;
    /** A piece with at least this many samples over water is dropped. */
    public static final int WATER_SAMPLES_TO_DROP = 3;

    /** Whether the column at (x, z) is open water (its ground under the sea surface). */
    @FunctionalInterface
    public interface Water {
        boolean at(int x, int z);
    }

    /** One placed piece: its box, the index of the piece it hangs from (-1 for the start) and whether it is a land piece. */
    public record Node(BoundingBox box, int parent, boolean land) {
    }

    private LandFilter() {
    }

    /** Which pieces stay, by index. A parent always comes before its children (jigsaw order). */
    public static boolean[] keep(List<Node> nodes, Water water) {
        boolean[] keep = new boolean[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            boolean parentKept = node.parent() < 0 || (node.parent() < i && keep[node.parent()]);
            keep[i] = parentKept && !(node.land() && overWater(node.box(), water));
        }
        return keep;
    }

    /** Whether at least {@link #WATER_SAMPLES_TO_DROP} of the box's {@link #SAMPLES} footprint samples are water. */
    public static boolean overWater(BoundingBox box, Water water) {
        int x0 = Math.min(box.minX() + 1, box.maxX());
        int x1 = Math.max(box.maxX() - 1, box.minX());
        int z0 = Math.min(box.minZ() + 1, box.maxZ());
        int z1 = Math.max(box.maxZ() - 1, box.minZ());
        int wet = 0;
        if (water.at((box.minX() + box.maxX()) / 2, (box.minZ() + box.maxZ()) / 2)) wet++;
        if (water.at(x0, z0)) wet++;
        if (water.at(x1, z0)) wet++;
        if (water.at(x0, z1)) wet++;
        if (water.at(x1, z1)) wet++;
        return wet >= WATER_SAMPLES_TO_DROP;
    }
}
