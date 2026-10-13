package com.richardsenger.piratesnships.world.structure;

import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;

/**
 * Keeps a port's land pieces on land (STR2, {@code land_pieces_on_land}; STR8 made it stricter): the jigsaw assembler
 * knows nothing of the sea, so a trail that leaves the camp along the beach and bends seaward would run on over the open
 * water, with its huts after it. This drops every land piece (a terrain-matching trail, or a building or decoration
 * hanging from one) that does not stand on land, and every piece that hangs from a dropped one; the start piece and the
 * pieces hanging rigidly from it (the jetty) stay.
 *
 * <p><b>On land</b> (STR8, the human: "a structure completely on water, without proper land below it"): the footprint's
 * centre is land and at least {@value #LAND_SAMPLES_TO_KEEP} of its {@value #SAMPLES} samples (the centre and the four
 * corners one block in) are land; a building (a rigid land piece: a hut, a house, a decoration) needs its four outer
 * corners on land as well. STR2 dropped a piece only with three of five samples over water, so a hut whose seaward half
 * stood over the sea stayed. A trail that crosses a narrow inlet off its centre keeps its boards (the boardwalk
 * processor); a branch out to sea ends at the waterline. Pure: the water test is passed in.
 */
public final class LandFilter {

    /** Footprint samples per piece: the centre and the four corners (one block in). */
    public static final int SAMPLES = 5;
    /** A land piece needs at least this many of its samples on land, the centre among them (STR8; STR2 dropped at 3 wet). */
    public static final int LAND_SAMPLES_TO_KEEP = 4;

    /** Whether the column at (x, z) is open water (its ground under the sea surface). */
    @FunctionalInterface
    public interface Water {
        boolean at(int x, int z);
    }

    /**
     * One placed piece: its box, the index of the piece it hangs from (-1 for the start), whether it is a land piece and
     * whether it is a building (a rigid land piece, whose four corners must stand on land too).
     */
    public record Node(BoundingBox box, int parent, boolean land, boolean building) {

        /** A node that is not a building (a trail, the start, the pier). */
        public Node(BoundingBox box, int parent, boolean land) {
            this(box, parent, land, false);
        }
    }

    private LandFilter() {
    }

    /** Which pieces stay, by index. A parent always comes before its children (jigsaw order). */
    public static boolean[] keep(List<Node> nodes, Water water) {
        boolean[] keep = new boolean[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            boolean parentKept = node.parent() < 0 || (node.parent() < i && keep[node.parent()]);
            keep[i] = parentKept && !(node.land() && !onLand(node, water));
        }
        return keep;
    }

    /** Whether a land piece stands on land: the centre and {@value #LAND_SAMPLES_TO_KEEP} samples, a building's corners. */
    public static boolean onLand(Node node, Water water) {
        BoundingBox box = node.box();
        if (water.at((box.minX() + box.maxX()) / 2, (box.minZ() + box.maxZ()) / 2)) return false;
        if (SAMPLES - wetSamples(box, water) < LAND_SAMPLES_TO_KEEP) return false;
        if (!node.building()) return true;
        return !water.at(box.minX(), box.minZ()) && !water.at(box.maxX(), box.minZ())
                && !water.at(box.minX(), box.maxZ()) && !water.at(box.maxX(), box.maxZ());
    }

    /** Whether the footprint stands mostly over water (fewer than {@value #LAND_SAMPLES_TO_KEEP} land samples, or a wet centre). */
    public static boolean overWater(BoundingBox box, Water water) {
        return !onLand(new Node(box, -1, true), water);
    }

    /** Whether the footprint's centre column is open water (STR9: a closing tower must not stand in the sea). */
    public static boolean overWaterCentre(BoundingBox box, Water water) {
        return water.at((box.minX() + box.maxX()) / 2, (box.minZ() + box.maxZ()) / 2);
    }

    /** How many of the box's {@value #SAMPLES} footprint samples (centre, corners one block in) are water. */
    public static int wetSamples(BoundingBox box, Water water) {
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
        return wet;
    }
}
