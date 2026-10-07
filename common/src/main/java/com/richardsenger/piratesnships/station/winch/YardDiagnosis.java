package com.richardsenger.piratesnships.station.winch;

import com.richardsenger.piratesnships.sailing.sail.SquareSail;
import com.richardsenger.piratesnships.sailing.sail.YardLinker;
import com.richardsenger.piratesnships.sailing.sail.YardLookup;
import com.richardsenger.piratesnships.sailing.sail.YardRow;
import com.richardsenger.piratesnships.sailing.sail.YardRules;
import org.jetbrains.annotations.Nullable;

/**
 * Why a yard heads a square sail or not (Q5 diagnostics for {@code /pirates ship rigging} and the crew's "no sails"
 * answer). Pure: it walks the same cells as {@link YardLinker#sailHeadedBy} (rule F5a, docs/design.md §5.2) and names the
 * first thing that stops the pair, so the captain learns what to change.
 */
public final class YardDiagnosis {

    private YardDiagnosis() {
    }

    /** What the walk down from a yard's middle block found. */
    public enum Verdict {
        /** The yard heads a sail (the rule's own answer). */
        HEADS_SAIL,
        /** Nothing but air and mast within the largest gap: no lower yard. Fine for the foot of a sail. */
        NOTHING_BELOW,
        /** A block that is neither air, a mast block nor a yard sits in the mast column {@code distance} blocks down. */
        BLOCKED,
        /** The first yard below is only {@code distance} blocks down, closer than the smallest gap. */
        TOO_CLOSE,
        /** The yard below runs along the other axis. */
        OTHER_AXIS,
        /** The yard below is not centered on this yard's mast column (its middle block is elsewhere). */
        OFF_MIDDLE,
        /** The yard below is longer than the longest yard, so it is no yard. */
        LOWER_TOO_LONG
    }

    /**
     * The finding for one yard.
     *
     * @param verdict  see {@link Verdict}
     * @param distance blocks from the yard's middle down to the cell that decided it (0 for {@link Verdict#NOTHING_BELOW})
     * @param x        that cell's x (the middle column)
     * @param y        that cell's y
     * @param z        that cell's z
     */
    public record Finding(Verdict verdict, int distance, int x, int y, int z) {
        public boolean headsSail() {
            return verdict == Verdict.HEADS_SAIL;
        }
    }

    /** Walks down from {@code upper}'s middle block as rule F5a does and tells what decided the pairing. */
    public static Finding explain(YardLookup lookup, YardRow upper, YardRules rules) {
        int mx = upper.middleX();
        int mz = upper.middleZ();
        for (int d = 1; d <= rules.maxGap(); d++) {
            int y = upper.y() - d;
            YardLookup.Cell cell = lookup.at(mx, y, mz);
            if (cell.isOpen()) {
                continue;
            }
            if (!cell.isYard()) {
                return new Finding(Verdict.BLOCKED, d, mx, y, mz);
            }
            if (d < rules.minGap()) {
                return new Finding(Verdict.TOO_CLOSE, d, mx, y, mz);
            }
            YardRow lower = YardLinker.row(lookup, mx, y, mz, rules);
            if (lower == null) {
                return new Finding(Verdict.LOWER_TOO_LONG, d, mx, y, mz);
            }
            if (lower.alongX() != upper.alongX()) {
                return new Finding(Verdict.OTHER_AXIS, d, mx, y, mz);
            }
            if (!lower.isMiddle(mx, y, mz)) {
                return new Finding(Verdict.OFF_MIDDLE, d, mx, y, mz);
            }
            return new Finding(Verdict.HEADS_SAIL, d, mx, y, mz);
        }
        return new Finding(Verdict.NOTHING_BELOW, 0, mx, upper.y(), mz);
    }

    /**
     * Length of the straight run of yard blocks of one axis through {@code (x, y, z)}, counted up to {@code cap}; 0 when
     * there is no yard block. Tells how long a row is that {@link YardLinker#row} rejects as too long.
     */
    public static int runLength(YardLookup lookup, int x, int y, int z, int cap) {
        YardLookup.Cell cell = lookup.at(x, y, z);
        if (!cell.isYard()) {
            return 0;
        }
        boolean alongX = cell == YardLookup.Cell.YARD_X;
        int n = 1;
        for (int s = -1; s <= 1; s += 2) {
            for (int i = 1; n < cap; i++) {
                int cx = alongX ? x + s * i : x;
                int cz = alongX ? z : z + s * i;
                if (lookup.at(cx, y, cz) != cell) break;
                n++;
            }
        }
        return n;
    }

    /** Whether {@code row} is the lower yard (foot) of {@code sail}. */
    public static boolean isFootOf(YardRow row, @Nullable SquareSail sail) {
        return sail != null && sail.lower().equals(row);
    }
}
