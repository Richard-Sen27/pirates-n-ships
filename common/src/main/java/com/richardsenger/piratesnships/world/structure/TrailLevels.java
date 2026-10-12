package com.richardsenger.piratesnships.world.structure;

import java.util.List;

/**
 * Level streets, trails and lanes (STR8, the human: "the paths are often very curly because of the original terrain";
 * his screenshots showed the village's cobbled streets riding every bump, the kerb stepping up and down every few
 * blocks). Vanilla lays a terrain-matching piece block by block on the surface (its gravity processor), so a street on
 * uneven ground is a wavy ramp. Since STR8 each trail piece is laid level ({@link TrailBedProcessor}) on its own bed
 * row, and this class picks the rows (pure, no world access; {@code PortStructure.plan} feeds it):
 *
 * <ul>
 *     <li>a trail piece's natural row is the surface row under the centre of its box;</li>
 *     <li>its row is the natural row clamped to its parent's row ± {@value #MAX_STEP} when it hangs from the start or
 *     from another trail piece, so neighbouring pieces differ by at most one block (a trail hanging from anything else
 *     keeps its natural row);</li>
 *     <li>a trail piece whose row would lie more than {@value #MAX_CUT} blocks off its natural row (a steep hill, a
 *     gully) is dropped with everything hanging from it: the bed would sit in a cutting or on an embankment;</li>
 *     <li>a building or decoration hanging from a trail piece moves so that its connector row (the surface row at the
 *     joint, after the ST1 sink) lies on the trail's row; anything hanging from it moves with it; the start and the
 *     pieces hanging from rigid pieces do not move.</li>
 * </ul>
 */
public final class TrailLevels {

    /** The largest step between a trail piece's row and its parent's. */
    public static final int MAX_STEP = 1;
    /** The largest distance between a trail piece's row and its natural row; beyond it the piece is dropped. */
    public static final int MAX_CUT = 4;

    /** What a piece is for the levelling. */
    public enum Kind {
        /** The start piece: its {@link Node#row} is its foundation row. */
        START,
        /** A terrain-matching piece (street, trail, lane, their ends): {@link Node#row} is its bed row as planned. */
        TRAIL,
        /** Anything else (a building, a decoration, the pier, a wall): {@link Node#row} is its connector row as planned. */
        OTHER
    }

    /**
     * One piece: its kind, the index of the piece it hangs from (-1 for the start), its planned row (see {@link Kind})
     * and, for a trail, the surface row under its centre ({@code natural}; ignored for the others).
     */
    public record Node(Kind kind, int parent, int row, int natural) {
    }

    /** Per piece: the vertical move and whether it is dropped. */
    public record Result(int[] delta, boolean[] dropped) {

        public int levelled(Node node, int i) {
            return node.row() + delta[i];
        }
    }

    private TrailLevels() {
    }

    /** The moves and drops for {@code nodes} (a parent always comes before its children, jigsaw order). */
    public static Result solve(List<Node> nodes) {
        int n = nodes.size();
        int[] delta = new int[n];
        int[] row = new int[n];
        boolean[] dropped = new boolean[n];
        for (int i = 0; i < n; i++) {
            Node node = nodes.get(i);
            int p = node.parent();
            boolean hasParent = p >= 0 && p < i;
            if (hasParent && dropped[p]) {
                dropped[i] = true;
                continue;
            }
            switch (node.kind()) {
                case START -> row[i] = node.row();
                case TRAIL -> {
                    Kind parentKind = hasParent ? nodes.get(p).kind() : Kind.OTHER;
                    int level = node.natural();
                    if (parentKind == Kind.START || parentKind == Kind.TRAIL) {
                        level = Math.max(row[p] - MAX_STEP, Math.min(row[p] + MAX_STEP, level));
                    }
                    if (Math.abs(level - node.natural()) > MAX_CUT) {
                        dropped[i] = true;
                        continue;
                    }
                    row[i] = level;
                    delta[i] = level - node.row();
                }
                case OTHER -> {
                    if (hasParent && nodes.get(p).kind() == Kind.TRAIL) {
                        delta[i] = row[p] - node.row();
                    } else if (hasParent && nodes.get(p).kind() == Kind.OTHER) {
                        delta[i] = delta[p];
                    }
                    row[i] = node.row() + delta[i];
                }
            }
        }
        return new Result(delta, dropped);
    }
}
