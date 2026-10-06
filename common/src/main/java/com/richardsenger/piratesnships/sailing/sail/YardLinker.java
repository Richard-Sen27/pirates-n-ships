package com.richardsenger.piratesnships.sailing.sail;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * The square sail rule F5a (docs/design.md §5.2), pure:
 * <ul>
 *   <li>A <b>yard</b> is a straight row of yard blocks along one horizontal axis, at most {@code maxLength} long (a
 *       longer row is no yard). Its middle block marks the mast column ({@link YardRow#middle()}).</li>
 *   <li>A yard <b>heads a sail</b> when, going straight down from its middle block, the first cell that is neither
 *       air nor a mast block is the middle block of another yard with the same axis, {@code minGap..maxGap} blocks
 *       below. So a yard pairs with the nearest yard under it, a third yard in between splits the pair into two
 *       sails (the middle yard is the lower yard of one and the upper yard of the other), and anything else in the
 *       gap, a yard of the other axis, a yard that is off the column, or a yard too close all leave the upper yard
 *       without a sail.</li>
 * </ul>
 */
public final class YardLinker {

    private YardLinker() {
    }

    /** The result of linking a set of yard blocks: every valid yard, and every sail. */
    public record Linked(List<YardRow> rows, List<SquareSail> sails) { }

    /**
     * The yard containing the yard block at {@code (x, y, z)}, or null when there is no yard block or the row is longer
     * than {@code rules.maxLength()}. Reads at most {@code 2 * maxLength + 1} cells.
     */
    public static @Nullable YardRow row(YardLookup lookup, int x, int y, int z, YardRules rules) {
        YardLookup.Cell cell = lookup.at(x, y, z);
        if (!cell.isYard()) {
            return null;
        }
        boolean alongX = cell == YardLookup.Cell.YARD_X;
        int a = alongX ? x : z;
        int fixed = alongX ? z : x;
        int min = a;
        int max = a;
        int limit = rules.maxLength();
        while (max - min + 1 <= limit && cellAt(lookup, alongX, min - 1, y, fixed) == cell) {
            min--;
        }
        while (max - min + 1 <= limit && cellAt(lookup, alongX, max + 1, y, fixed) == cell) {
            max++;
        }
        if (max - min + 1 > limit) {
            return null;
        }
        return new YardRow(alongX, y, fixed, min, max);
    }

    /** The sail {@code upper} heads, or null (see the class comment). */
    public static @Nullable SquareSail sailHeadedBy(YardLookup lookup, YardRow upper, YardRules rules) {
        int mx = upper.middleX();
        int mz = upper.middleZ();
        for (int d = 1; d <= rules.maxGap(); d++) {
            YardLookup.Cell cell = lookup.at(mx, upper.y() - d, mz);
            if (cell.isOpen()) {
                continue;
            }
            if (!cell.isYard() || d < rules.minGap()) {
                return null;
            }
            YardRow lower = row(lookup, mx, upper.y() - d, mz, rules);
            if (lower == null || lower.alongX() != upper.alongX() || !lower.isMiddle(mx, upper.y() - d, mz)) {
                return null;
            }
            return new SquareSail(upper, lower);
        }
        return null;
    }

    /**
     * The sail headed by the yard that contains {@code (x, y, z)}, but only if that block is the yard's middle block
     * (the head); null otherwise.
     */
    public static @Nullable SquareSail sailHeadedAt(YardLookup lookup, int x, int y, int z, YardRules rules) {
        YardRow row = row(lookup, x, y, z, rules);
        return row == null || !row.isMiddle(x, y, z) ? null : sailHeadedBy(lookup, row, rules);
    }

    /** All yards through the given yard block positions ({@code {x, y, z}}), and the sails they head. */
    public static Linked link(YardLookup lookup, Iterable<int[]> yardBlocks, YardRules rules) {
        Set<YardRow> rows = new LinkedHashSet<>();
        for (int[] p : yardBlocks) {
            boolean known = false;
            for (YardRow r : rows) {
                if (r.contains(p[0], p[1], p[2])) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                YardRow r = row(lookup, p[0], p[1], p[2], rules);
                if (r != null) rows.add(r);
            }
        }
        List<SquareSail> sails = new ArrayList<>();
        for (YardRow r : rows) {
            SquareSail s = sailHeadedBy(lookup, r, rules);
            if (s != null) sails.add(s);
        }
        return new Linked(List.copyOf(rows), List.copyOf(sails));
    }

    private static YardLookup.Cell cellAt(YardLookup lookup, boolean alongX, int a, int y, int fixed) {
        return alongX ? lookup.at(a, y, fixed) : lookup.at(fixed, y, a);
    }
}
