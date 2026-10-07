package com.richardsenger.piratesnships.sailing.sail;

import org.jetbrains.annotations.Nullable;

/**
 * The triangular sail rule F5b (docs/design.md §5.2), pure:
 * <ul>
 *   <li>A <b>stay</b> joins two cleats at most {@link StayRules#maxLength()} apart (between block centers) and at
 *       least {@link StayRules#minDrop()} apart in height. The higher cleat is the <b>head</b> (A), the lower the
 *       <b>tack</b> (B).</li>
 *   <li>The <b>clew</b> (C) is the first cleat straight below the head, searched from the block under the head down
 *       to the tack's height, through air and mast blocks only: anything else in between ends the search without a
 *       clew. The sail is the triangle A-B-C; a triangle without area (the tack straight below the head) is no
 *       sail.</li>
 *   <li><b>Rope lines</b> (RP1, {@link #decide}): any other rope between two anchors on one body within the longest
 *       stay is a decorative line. A line between two cleats becomes a stay as soon as it passes the stay rule with a
 *       clew, and turns back into a line when the clew goes: whether a rope is a stay is never stored, only derived.</li>
 * </ul>
 */
public final class StayLinker {

    /** Why a stay between two cleats is or is not allowed. */
    public enum Check {
        OK,
        /** Both ends are the same cleat. */
        SAME,
        /** Farther apart than {@link StayRules#maxLength()}. */
        TOO_LONG,
        /** Less than {@link StayRules#minDrop()} apart in height. */
        TOO_FLAT
    }

    /**
     * What a rope run between two anchors becomes (RP1, docs/design.md §5.2 "Ropes on cleats"), or why it is refused.
     * The first three are rigged; the rest are refusals.
     */
    public enum Rig {
        /** A stay between two cleats with a clew below the head: a triangular sail. */
        SAIL,
        /** A stay between two cleats without a clew yet (a third cleat below the head makes the sail). */
        STAY,
        /** A decorative rope line: no sail, no force, drawn with sag. */
        LINE,
        /** Both ends are the same anchor. */
        SAME,
        /** The ends are on different bodies (one on land and one on a ship, or two ships). */
        OTHER_BODY,
        /** Farther apart than {@link StayRules#maxLength()}. */
        TOO_LONG,
        /** Rope lines are off and the two cleats are less than {@link StayRules#minDrop()} apart in height. */
        TOO_FLAT,
        /** Rope lines are off and an end is not a cleat (only cleats take stays). */
        NO_LINES;

        public boolean rigged() {
            return this == SAIL || this == STAY || this == LINE;
        }
    }

    private StayLinker() {
    }

    /**
     * Decides what a rope from anchor {@code a} to anchor {@code b} becomes (RP1). Checked in this order: the same
     * anchor, different bodies, the length ({@link StayRules#maxLength()}, for every rope), then: two cleats that pass
     * the stay rule ({@link #check}) make a stay ({@link Rig#SAIL} when {@link #sail} finds the clew, else
     * {@link Rig#STAY}); anything else is a {@link Rig#LINE} when {@code linesEnabled}, else refused
     * ({@link Rig#TOO_FLAT} for two cleats, {@link Rig#NO_LINES} with a ring). With rope lines off this is the F5b
     * rule unchanged.
     *
     * @param aCleat   {@code a} is a cleat (else another anchor, the mooring ring)
     * @param sameBody both ends are in the world, or both in the same ship's plot
     */
    public static Rig decide(CleatLookup lookup, BlockPoint a, boolean aCleat, BlockPoint b, boolean bCleat, boolean sameBody,
                             boolean linesEnabled, StayRules rules) {
        if (a.equals(b)) {
            return Rig.SAME;
        }
        if (!sameBody) {
            return Rig.OTHER_BODY;
        }
        if (a.distanceSquared(b) > (long) rules.maxLength() * rules.maxLength()) {
            return Rig.TOO_LONG;
        }
        if (aCleat && bCleat && check(a, b, rules) == Check.OK) {
            return sail(lookup, a, b, rules) != null ? Rig.SAIL : Rig.STAY;
        }
        if (linesEnabled) {
            return Rig.LINE;
        }
        return aCleat && bCleat ? Rig.TOO_FLAT : Rig.NO_LINES;
    }

    public static Check check(BlockPoint a, BlockPoint b, StayRules rules) {
        if (a.equals(b)) {
            return Check.SAME;
        }
        if (a.distanceSquared(b) > (long) rules.maxLength() * rules.maxLength()) {
            return Check.TOO_LONG;
        }
        if (Math.abs(a.y() - b.y()) < rules.minDrop()) {
            return Check.TOO_FLAT;
        }
        return Check.OK;
    }

    /** Length of a stay between the two cleats' centers [blocks]. */
    public static double length(BlockPoint a, BlockPoint b) {
        return Math.sqrt((double) a.distanceSquared(b));
    }

    /**
     * The clew below {@code head}: the first cleat from the block under the head down to {@code tackY} (inclusive),
     * through air and mast blocks only; null when there is none or anything else comes first.
     */
    public static @Nullable BlockPoint findClew(CleatLookup lookup, BlockPoint head, int tackY) {
        for (int y = head.y() - 1; y >= tackY; y--) {
            CleatLookup.Cell c = lookup.at(head.x(), y, head.z());
            if (c == CleatLookup.Cell.CLEAT) {
                return new BlockPoint(head.x(), y, head.z());
            }
            if (!c.isOpen()) {
                return null;
            }
        }
        return null;
    }

    /** The sail of the stay between {@code a} and {@code b} (either order), or null (see the class comment). */
    public static @Nullable TriangularSail sail(CleatLookup lookup, BlockPoint a, BlockPoint b, StayRules rules) {
        if (check(a, b, rules) != Check.OK) {
            return null;
        }
        BlockPoint head = a.y() > b.y() ? a : b;
        BlockPoint tack = head == a ? b : a;
        BlockPoint clew = findClew(lookup, head, tack.y());
        if (clew == null) {
            return null;
        }
        TriangularSail s = new TriangularSail(head, tack, clew);
        return s.area() > 1.0e-9 ? s : null;
    }
}
