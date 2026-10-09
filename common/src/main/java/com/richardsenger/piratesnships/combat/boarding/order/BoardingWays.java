package com.richardsenger.piratesnships.combat.boarding.order;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * The ways across from one ship to another for the "Board!" order (BRD3, docs/design.md §8.4). Pure: the decks come in
 * as cell predicates in each ship's own plot frame and a map from the leaving ship's plot to the boarded ship's plot
 * ({@link BoardingWorld} reads them from the level).
 *
 * <ul>
 *   <li><b>A plank</b> (a gangway, BRD2/BRD4) laid between the two ships, whichever end it was laid from: the boarder
 *       walks to the deck cell before the board's near end, along the board and onto the cell past its far end
 *       ({@link #plank}).</li>
 *   <li><b>A gap</b> of at most {@code boarding.board_gap} blocks ({@link #gaps}): a <em>stand cell</em> of the leaving
 *       deck (free feet and head, a floor under the feet) at its edge (the neighbour that way is no stand cell of the
 *       same deck) whose outboard line, {@code k} = 1 to gap + 1 blocks out, reaches a stand cell of the other deck at
 *       the same height or one block up or down; the cells on the way must be free of the own ship (a railing, a
 *       bulwark stops the line, a gate or port is an opening). {@code k} = 1 is two hulls touching.</li>
 * </ul>
 * A boarder takes the crossing whose start is nearest ({@link #nearest}). No crossing: the order is refused with
 * "No way across to board".
 */
public final class BoardingWays {

    /** Largest height difference in blocks between the two decks of a gap crossing. */
    public static final int MAX_STEP = 1;

    public enum Kind { PLANK, GAP }

    /** Which ship's plot a point of a crossing is given in: the one left ({@code FROM}) or the one boarded ({@code TO}). */
    public enum Frame { FROM, TO }

    /** A point of a crossing, in the plot frame of {@link #frame}. */
    public record Point(Frame frame, Vec3 at) {
    }

    /**
     * A way across. {@code start} is the stand cell on the leaving ship the boarder walks to, {@code landing} the stand cell
     * on the boarded ship it ends on, {@code via} the points it walks over in between (the board's ends for a plank,
     * none for a gap). {@code gap} is the open water in blocks (0 for touching hulls and for a plank), {@code rise} the
     * height of the landing over the start in whole blocks.
     */
    public record Crossing(Kind kind, BlockPos start, BlockPos landing, List<Point> via, int gap, int rise) {
        public Crossing {
            start = start.immutable();
            landing = landing.immutable();
            via = List.copyOf(via);
        }

        /** Whether the boarder jumps: a gap of open water, or a deck a block higher. */
        public boolean hop() {
            return kind == Kind.GAP && (gap > 0 || rise > 0);
        }
    }

    /** One ship's deck in its plot frame. */
    public interface Deck {
        /** A boarder can stand in this cell: feet and head free, a floor below. */
        boolean stand(BlockPos cell);

        /** Something of this ship fills this cell (a block with collision). */
        boolean blocked(BlockPos cell);
    }

    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final int[] STEPS = {0, 1, -1};

    private BoardingWays() {
    }

    /**
     * Every gap crossing from the stand cells {@code fromStands} of deck {@code from} to deck {@code to}, at most
     * {@code maxGap} blocks of open water wide. {@code fromToTo} maps a point of the leaving ship's plot into the boarded
     * ship's plot (through the world). Sorted by start cell, then side.
     */
    public static List<Crossing> gaps(Collection<BlockPos> fromStands, Deck from, Deck to, UnaryOperator<Vec3> fromToTo, int maxGap) {
        List<BlockPos> cells = new ArrayList<>(fromStands);
        cells.sort(Comparator.naturalOrder());
        List<Crossing> out = new ArrayList<>();
        for (BlockPos c : cells) {
            for (Direction d : SIDES) {
                if (from.stand(c.relative(d))) continue; // not the deck's edge that way
                Crossing found = across(c, d, from, to, fromToTo, maxGap);
                if (found != null) out.add(found);
            }
        }
        return out;
    }

    /** The first stand cell of {@code to} on the line out of {@code edge} toward {@code d}, or null. */
    static @Nullable Crossing across(BlockPos edge, Direction d, Deck from, Deck to, UnaryOperator<Vec3> fromToTo, int maxGap) {
        for (int k = 1; k <= maxGap + 1; k++) {
            BlockPos out = edge.relative(d, k);
            if (from.blocked(out) || from.blocked(out.above())) return null; // the own rail or hull in the way
            BlockPos there = BlockPos.containing(fromToTo.apply(Vec3.atBottomCenterOf(out).add(0, 0.25, 0)));
            for (int dy : STEPS) {
                BlockPos land = there.above(dy);
                if (to.stand(land)) {
                    // the landing's height over the start, measured through the map (the decks may be offset)
                    BlockPos back = there.subtract(BlockPos.containing(fromToTo.apply(Vec3.atBottomCenterOf(edge).add(0, 0.25, 0))));
                    int rise = back.getY() + dy;
                    if (Math.abs(rise) > MAX_STEP) continue;
                    return new Crossing(Kind.GAP, edge, land, List.of(), k - 1, rise);
                }
            }
            if (to.blocked(there) || to.blocked(there.above())) return null; // the other hull's side: no deck to reach
        }
        return null;
    }

    /**
     * The crossing over a plank of {@code length} cells whose segment 0 is {@code base}, laid toward {@code facing}.
     * {@code laidFromLeaving}: the plank lies in the leaving ship's plot (laid from it); else in the boarded ship's
     * plot. The board is walked from the end on the leaving ship to the other: {@code start} and {@code landing} are the
     * stand cells beside its two ends, {@code boardY} the height of the walking surface over each cell's floor.
     */
    public static Crossing plank(boolean laidFromLeaving, BlockPos base, Direction facing, int length, double baseBoardY,
                                 double tipBoardY, BlockPos start, BlockPos landing) {
        BlockPos tip = base.relative(facing, Math.max(0, length - 1));
        Frame frame = laidFromLeaving ? Frame.FROM : Frame.TO;
        Point near = new Point(frame, Vec3.atBottomCenterOf(base).add(0, baseBoardY, 0));
        Point far = new Point(frame, Vec3.atBottomCenterOf(tip).add(0, tipBoardY, 0));
        List<Point> via = laidFromLeaving ? List.of(near, far) : List.of(far, near);
        return new Crossing(Kind.PLANK, start, landing, via, 0, landing.getY() - start.getY());
    }

    /**
     * The crossing whose start is nearest {@code at} (a point in the leaving ship's plot); planks win a tie. Null for
     * none.
     */
    public static @Nullable Crossing nearest(List<Crossing> crossings, Vec3 at) {
        Crossing best = null;
        double bestD = Double.MAX_VALUE;
        for (Crossing c : crossings) {
            double d = Vec3.atBottomCenterOf(c.start()).distanceToSqr(at);
            if (best == null || d < bestD - 1e-9 || Math.abs(d - bestD) <= 1e-9 && c.kind() == Kind.PLANK && best.kind() != Kind.PLANK) {
                best = c;
                bestD = d;
            }
        }
        return best;
    }
}
