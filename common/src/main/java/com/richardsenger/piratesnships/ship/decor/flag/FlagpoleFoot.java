package com.richardsenger.piratesnships.ship.decor.flag;

import java.util.function.IntFunction;

/**
 * The foot of a pole (FLG3, docs/design.md §4.7 "Flag pedestal and the pole on the crow's nest"), pure: what a
 * flagpole block stands on decides the part it shows and where the halyard's cleat sits.
 * <ul>
 *   <li>On a plain block (or nothing) the lowest flagpole block is the foot and carries the cleat in its top third
 *       (VIS1a-b): {@link FlagpolePart#BOTTOM}, or {@link FlagpolePart#SINGLE} for a one-block pole.</li>
 *   <li>On a <b>footing</b>, a flag pedestal or a crow's nest, the footing carries the cleat (its model with the pole
 *       stub), so the lowest flagpole block shows the part of a block standing on a pole: {@link FlagpolePart#MIDDLE},
 *       or {@link FlagpolePart#TOP} for a one-block pole.</li>
 *   <li>The pole itself, its head and its height are the flagpole blocks only ({@link FlagpoleRun}): a footing never
 *       counts towards {@code flags.max_pole_height} and never owns the flag.</li>
 *   <li>The lowest pole block on a crow's nest stands where the lookout's head is, so it has no collision
 *       ({@link #clearsHeadroom}).</li>
 * </ul>
 */
public final class FlagpoleFoot {

    /** What a block in a pole's column is, as far as the pole cares. */
    public enum Support {
        /** A flagpole block. */
        FLAGPOLE,
        /** A flag pedestal (a footing). */
        PEDESTAL,
        /** A crow's nest (a footing with a lookout in it). */
        CROWS_NEST,
        /** Anything else: a plain block, air. */
        OTHER;

        /** Whether this is a footing: it carries the cleat and the pole stub for the flagpole block on it. */
        public boolean footing() {
            return this == PEDESTAL || this == CROWS_NEST;
        }
    }

    /**
     * A pole read from its column: the offsets (from the block the walk started at) of its {@code foot} (lowest
     * flagpole block) and {@code head} (top flagpole block), its {@code height} in flagpole blocks, the offset of the
     * block that draws the cleat ({@code cleat}: the foot, or the footing below it) and what the foot stands on.
     */
    public record Pole(int foot, int head, int height, int cleat, Support base) {
    }

    private FlagpoleFoot() {
    }

    /** The part a flagpole block shows standing on {@code below} with ({@code poleAbove}) or without a flagpole on it. */
    public static FlagpolePart part(Support below, boolean poleAbove) {
        return FlagpolePart.of(below == Support.FLAGPOLE || below.footing(), poleAbove);
    }

    /** Whether the flagpole block standing on {@code below} has no collision (on a crow's nest: the lookout's head room). */
    public static boolean clearsHeadroom(Support below) {
        return below == Support.CROWS_NEST;
    }

    /**
     * The pole through offset 0 of {@code column} (offset to support; offset 0 must be a flagpole), each walk capped
     * at {@code limit} blocks.
     */
    public static Pole read(IntFunction<Support> column, int limit) {
        if (column.apply(0) != Support.FLAGPOLE) throw new IllegalArgumentException("no flagpole at offset 0");
        int head = 0;
        while (head < limit && column.apply(head + 1) == Support.FLAGPOLE) head++;
        int foot = 0;
        while (-foot < limit && column.apply(foot - 1) == Support.FLAGPOLE) foot--;
        Support base = column.apply(foot - 1);
        return new Pole(foot, head, head - foot + 1, base.footing() ? foot - 1 : foot, base);
    }
}
