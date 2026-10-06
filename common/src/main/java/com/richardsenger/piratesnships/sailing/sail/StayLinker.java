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

    private StayLinker() {
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
