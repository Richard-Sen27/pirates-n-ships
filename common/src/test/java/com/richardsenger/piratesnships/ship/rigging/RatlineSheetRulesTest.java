package com.richardsenger.piratesnships.ship.rigging;

import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.Anchor;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.Cell;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.Point;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.SheetCell;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RL5's sheets (docs/design.md §4.8 "Net sheets and railing latches"): the anchors' capacity that keeps a net from being
 * wider at the top, the join rule of strands in a row (rectangle, trapezoid, RL4's triangle, a single strand, strands
 * not in a row), the cells a sheet fills and where its fill cells climb to.
 */
class RatlineSheetRulesTest {

    private static final double SPREAD = 6;

    private static Point p(double x, double y, double z) {
        return new Point(x, y, z);
    }

    /** A latch ring on the west edge of a railing at x 2, y 1, z {@code z} (RailingLatchBlock#ring). */
    private static Point latch(int z) {
        return RailingLatchBlock.ring(new net.minecraft.core.BlockPos(2, 2, z), net.minecraft.core.Direction.WEST);
    }

    /** A hoop ring on the west face of a wall at x 6, y {@code y}, z {@code z} (MastHoopBlock facing west). */
    private static Point hoop(int y, int z) {
        return p(5.7, y + 0.5, z + 0.5);
    }

    private static boolean[] all(int n) {
        boolean[] b = new boolean[n];
        java.util.Arrays.fill(b, true);
        return b;
    }

    private static int pairs(int[] partner) {
        int n = 0;
        for (int p : partner) {
            if (p >= 0) n++;
        }
        return n;
    }

    /** Every strand's own cells (walk between the rings), as the game hands them in. */
    private static List<List<Cell>> strandCells(List<Point> feet, List<Point> hubs) {
        List<List<Cell>> out = new ArrayList<>();
        for (int k = 0; k < feet.size(); k++) {
            out.add(RatlineNetRules.netCells(feet.get(k), hubs.get(k)));
        }
        return out;
    }

    private static Set<Cell> fittings(List<Point> feet, List<Point> hubs) {
        Set<Cell> out = new HashSet<>();
        for (Point f : feet) out.add(f.cell());
        for (Point h : hubs) out.add(h.cell());
        return out;
    }

    // ------------------------------------------------------------------ capacity

    @Test
    void aChainplateOrALatchHoldsOneStrandAndAHoopUpToTheMaximum() {
        assertTrue(RatlineNetRules.takesAnother(Anchor.CHAINPLATE, 0, 5));
        assertFalse(RatlineNetRules.takesAnother(Anchor.CHAINPLATE, 1, 5), "a second strand on a chainplate");
        assertTrue(RatlineNetRules.takesAnother(Anchor.LATCH, 0, 5));
        assertFalse(RatlineNetRules.takesAnother(Anchor.LATCH, 1, 5), "a second strand on a latch");
        assertTrue(RatlineNetRules.takesAnother(Anchor.HOOP, 4, 5));
        assertFalse(RatlineNetRules.takesAnother(Anchor.HOOP, 5, 5));
        assertEquals(1, RatlineNetRules.capacity(Anchor.LATCH, 8));
        // so the top of a net never has more ends than its bottom: every strand has its own bottom anchor
    }

    // ------------------------------------------------------------------ joins

    @Test
    void aRectangleOfThreeStrandsToThreeHoopsJoinsIntoOneSheet() {
        List<Point> feet = List.of(latch(2), latch(4), latch(6));
        List<Point> hubs = List.of(hoop(6, 2), hoop(6, 4), hoop(6, 6));
        int[] partner = RatlineNetRules.sheetPartners(feet, hubs, all(3), SPREAD);
        assertEquals(2, pairs(partner), "two pairs: " + java.util.Arrays.toString(partner));
        // every strand is in a pair and the middle one in two
        assertTrue(partner[1] >= 0 || partner[0] == 1 || partner[2] == 1);
        List<SheetCell> cells = RatlineNetRules.sheetCells(feet, hubs, strandCells(feet, hubs), partner, fittings(feet, hubs));
        // the columns between the strands (z 3 and 5) are filled from the rail to the hoops' height
        for (int z : new int[] {3, 5}) {
            final int zz = z;
            assertTrue(cells.stream().anyMatch(c -> c.fill() && c.cell().z() == zz && c.cell().y() == 2), "no fill at the rail, z " + z);
            assertTrue(cells.stream().anyMatch(c -> c.fill() && c.cell().z() == zz && c.cell().y() == 6), "no fill at the top, z " + z);
            // between two hubs: the fill names the other one
            assertTrue(cells.stream().filter(c -> c.fill() && c.cell().z() == zz).allMatch(c -> c.otherHub() >= 0),
                    "a fill cell at z " + z + " misses its second hub");
        }
        // a fill cell of a rectangle climbs straight up its column
        Point top = RatlineNetRules.topPoint(new Cell(3, 3, 3), hubs.get(0), hubs.get(1));
        assertEquals(3.5, top.z(), 1e-9);
        assertEquals(5.7, top.x(), 1e-9);
    }

    @Test
    void aTrapezoidOfThreeStrandsIntoTwoHoops() {
        List<Point> feet = List.of(latch(2), latch(4), latch(6));
        List<Point> hubs = List.of(hoop(6, 3), hoop(6, 3), hoop(6, 5));
        int[] partner = RatlineNetRules.sheetPartners(feet, hubs, all(3), SPREAD);
        assertEquals(2, pairs(partner));
        List<SheetCell> cells = RatlineNetRules.sheetCells(feet, hubs, strandCells(feet, hubs), partner, fittings(feet, hubs));
        // the pair sharing hoop z 3 is RL4's triangle (no second hub), the other a quadrilateral to both hoops
        assertTrue(cells.stream().anyMatch(c -> c.fill() && c.otherHub() < 0), "no triangle fill");
        assertTrue(cells.stream().anyMatch(c -> c.fill() && c.otherHub() >= 0), "no quadrilateral fill");
        assertTrue(cells.stream().anyMatch(c -> c.fill() && c.cell().z() == 5 && c.cell().y() == 2), "the rail between z 4 and 6");
    }

    @Test
    void threeStrandsIntoOneHoopAreRl4sTriangle() {
        List<Point> feet = List.of(latch(2), latch(4), latch(6));
        List<Point> hubs = List.of(hoop(6, 4), hoop(6, 4), hoop(6, 4));
        int[] partner = RatlineNetRules.sheetPartners(feet, hubs, all(3), SPREAD);
        assertEquals(2, pairs(partner));
        // the owners are RL4's: the strand on the left by bearing (round the hub, from z 6 towards z 2)
        assertArrayEquals(new int[] {-1, 0, 1}, partner);
        List<SheetCell> cells = RatlineNetRules.sheetCells(feet, hubs, strandCells(feet, hubs), partner, fittings(feet, hubs));
        assertTrue(cells.stream().filter(SheetCell::fill).allMatch(c -> c.otherHub() < 0), "a triangle has one hub");
        // the same cells RL4's fan fills
        List<RatlineNetRules.FanCell> fan = RatlineNetRules.fanCells(hubs.get(0), List.of(feet.get(2), feet.get(1), feet.get(0)),
                List.of(RatlineNetRules.netCells(feet.get(2), hubs.get(0)), RatlineNetRules.netCells(feet.get(1), hubs.get(0)),
                        RatlineNetRules.netCells(feet.get(0), hubs.get(0))), new boolean[] {true, true});
        assertEquals(fan.stream().map(RatlineNetRules.FanCell::cell).collect(java.util.stream.Collectors.toSet()),
                cells.stream().map(SheetCell::cell).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void aSingleStrandIsItsOwnLine() {
        List<Point> feet = List.of(latch(4));
        List<Point> hubs = List.of(hoop(6, 4));
        int[] partner = RatlineNetRules.sheetPartners(feet, hubs, all(1), SPREAD);
        assertArrayEquals(new int[] {-1}, partner);
        List<SheetCell> cells = RatlineNetRules.sheetCells(feet, hubs, strandCells(feet, hubs), partner, fittings(feet, hubs));
        assertEquals(RatlineNetRules.netCells(feet.get(0), hubs.get(0)), cells.stream().map(SheetCell::cell).toList());
        assertTrue(cells.stream().noneMatch(SheetCell::fill));
    }

    @Test
    void strandsWhoseFeetAreNotInARowStaySeparate() {
        // one hoop; feet at two heights, and feet on a diagonal
        Point hub = hoop(8, 4);
        assertFalse(RatlineNetRules.joins(p(2.5, 1.3, 2.5), hub, p(2.5, 2.3, 4.5), hub, SPREAD), "two heights");
        assertFalse(RatlineNetRules.joins(p(2.5, 1.3, 2.5), hub, p(3.5, 1.3, 4.5), hub, SPREAD), "a diagonal");
        assertTrue(RatlineNetRules.joins(p(2.5, 1.3, 2.5), hub, p(2.5, 1.3, 4.5), hub, SPREAD), "a row");
        assertFalse(RatlineNetRules.joins(p(2.5, 1.3, 0.5), hub, p(2.5, 1.3, 6.6), hub, SPREAD), "beyond the spread");
        List<Point> feet = List.of(p(2.5, 1.3, 2.5), p(3.5, 1.3, 4.5), p(2.5, 2.3, 6.5));
        int[] partner = RatlineNetRules.sheetPartners(feet, List.of(hub, hub, hub), all(3), SPREAD);
        assertArrayEquals(new int[] {-1, -1, -1}, partner);
        // hubs that are not hoops never join (a boarding net's end, a chainplate): joinable false
        List<Point> row = List.of(latch(2), latch(4));
        assertArrayEquals(new int[] {-1, -1}, RatlineNetRules.sheetPartners(row, List.of(hoop(6, 2), hoop(6, 4)),
                new boolean[] {false, true}, SPREAD));
    }

    @Test
    void hubsMustBeInARowWithinTheSpreadAndNotCrossTheFeet() {
        Point f1 = latch(2), f2 = latch(4);
        assertTrue(RatlineNetRules.joins(f1, hoop(6, 2), f2, hoop(6, 4), SPREAD));
        assertFalse(RatlineNetRules.joins(f1, hoop(6, 2), f2, hoop(7, 4), SPREAD), "hubs at two heights");
        assertFalse(RatlineNetRules.joins(f1, hoop(6, 4), f2, hoop(6, 2), SPREAD), "crossed strands");
        assertFalse(RatlineNetRules.joins(f1, hoop(6, -5), f2, hoop(6, 4), SPREAD), "hubs beyond the spread");
    }

    @Test
    void theOwnerOfAPairStaysTheSameWhenTheShipTurns() {
        // RL4's fan on a ship, turned a quarter round: the same strand owns each pair (a bearing rule, not an axis rule)
        List<Point> feet = List.of(p(9.5, 9.3, 9.5), p(9.5, 9.3, 11.5), p(9.5, 9.3, 13.5));
        Point hub = p(12.7, 13.5, 11.5);
        int[] before = RatlineNetRules.sheetPartners(feet, List.of(hub, hub, hub), all(3), SPREAD);
        // turned 90 degrees about y round (0, 0): (x, z) -> (-z, x)
        List<Point> turned = feet.stream().map(f -> p(-f.z(), f.y(), f.x())).toList();
        Point hubTurned = p(-hub.z(), hub.y(), hub.x());
        int[] after = RatlineNetRules.sheetPartners(turned, List.of(hubTurned, hubTurned, hubTurned), all(3), SPREAD);
        assertArrayEquals(before, after);
    }
}
