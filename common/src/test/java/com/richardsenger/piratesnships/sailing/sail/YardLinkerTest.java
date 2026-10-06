package com.richardsenger.piratesnships.sailing.sail;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.sail.YardLookup.Cell;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Rule F5a (docs/design.md §5.2): yard rows, middles, pairing, area and centroids. */
class YardLinkerTest {

    private static final YardRules R = YardRules.DEFAULTS;
    private static final double EPS = 1e-9;

    /** A sparse world: unknown cells are air. */
    private static final class World implements YardLookup {
        final Map<List<Integer>, Cell> cells = new HashMap<>();

        @Override
        public Cell at(int x, int y, int z) {
            return cells.getOrDefault(List.of(x, y, z), Cell.AIR);
        }

        World set(int x, int y, int z, Cell c) {
            cells.put(List.of(x, y, z), c);
            return this;
        }

        /** A yard of {@code length} blocks along x at height y, z = 0, starting at x0. */
        World yardX(int x0, int y, int length) {
            for (int i = 0; i < length; i++) set(x0 + i, y, 0, Cell.YARD_X);
            return this;
        }

        World yardZ(int x, int y, int z0, int length) {
            for (int i = 0; i < length; i++) set(x, y, z0 + i, Cell.YARD_Z);
            return this;
        }

        World mast(int x, int y0, int y1, int z) {
            for (int y = y0; y <= y1; y++) set(x, y, z, Cell.MAST);
            return this;
        }

        List<int[]> yardBlocks() {
            List<int[]> out = new ArrayList<>();
            cells.forEach((k, v) -> {
                if (v.isYard()) out.add(new int[] {k.get(0), k.get(1), k.get(2)});
            });
            return out;
        }

        YardLinker.Linked link() {
            return YardLinker.link(this, yardBlocks(), R);
        }
    }

    // ------------------------------------------------------------------ rows and middles

    @Test
    void oddRowHasItsTrueMiddle() {
        World w = new World().yardX(-2, 10, 5);
        YardRow row = YardLinker.row(w, 1, 10, 0, R);
        assertNotNull(row);
        assertEquals(5, row.length());
        assertEquals(0, row.middle());
        assertEquals(2.5, row.negativeExtent(), EPS);
        assertEquals(2.5, row.positiveExtent(), EPS);
    }

    @Test
    void evenRowMiddleIsTheLowerOfTheTwoMiddleBlocks() {
        World w = new World().yardX(0, 10, 4);
        YardRow row = YardLinker.row(w, 3, 10, 0, R);
        assertNotNull(row);
        assertEquals(1, row.middle(), "blocks 0..3: middles 1 and 2, the lower one counts");
        assertEquals(1.5, row.negativeExtent(), EPS);
        assertEquals(2.5, row.positiveExtent(), EPS);
        YardRow z = YardLinker.row(new World().yardZ(5, 3, -4, 2), 5, 3, -3, R);
        assertNotNull(z);
        assertEquals(-4, z.middle());
        assertEquals(5, z.middleX());
        assertEquals(-4, z.middleZ());
    }

    @Test
    void rowStopsAtABlockOfTheOtherAxis() {
        World w = new World().yardX(0, 10, 3).set(3, 10, 0, Cell.YARD_Z).yardX(4, 10, 2);
        YardRow row = YardLinker.row(w, 1, 10, 0, R);
        assertNotNull(row);
        assertEquals(0, row.min());
        assertEquals(2, row.max());
    }

    @Test
    void tooLongRowIsNoYard() {
        assertNotNull(YardLinker.row(new World().yardX(0, 10, 15), 7, 10, 0, R));
        assertNull(YardLinker.row(new World().yardX(0, 10, 16), 7, 10, 0, R));
        assertNull(YardLinker.row(new World(), 0, 10, 0, R), "air is no yard");
    }

    // ------------------------------------------------------------------ pairing

    @Test
    void twoYardsOnOneMastAreOneSail() {
        World w = new World().yardX(-1, 13, 3).mast(0, 11, 12, 0).yardX(-1, 10, 3).mast(0, 5, 9, 0);
        YardLinker.Linked l = w.link();
        assertEquals(2, l.rows().size());
        assertEquals(1, l.sails().size());
        SquareSail s = l.sails().get(0);
        assertEquals(13, s.upper().y());
        assertEquals(10, s.lower().y());
        assertEquals(3, s.drop());
        assertEquals(9.0, s.area(), EPS);
        assertNotNull(YardLinker.sailHeadedAt(w, 0, 13, 0, R), "the upper middle block is the head");
        assertNull(YardLinker.sailHeadedAt(w, 1, 13, 0, R), "another block of the upper yard is not the head");
        assertNull(YardLinker.sailHeadedAt(w, 0, 10, 0, R), "the lower yard heads nothing");
    }

    @Test
    void gapLimits() {
        for (int gap = 1; gap <= 10; gap++) {
            World w = new World().yardX(-1, 20, 3).yardX(-1, 20 - gap, 3);
            boolean expected = gap >= R.minGap() && gap <= R.maxGap();
            assertEquals(expected ? 1 : 0, w.link().sails().size(), "gap " + gap);
        }
        World close = new World().yardX(-1, 20, 3).yardX(-1, 19, 3);
        assertEquals(1, YardLinker.link(close, close.yardBlocks(), new YardRules(1, 12, 15)).sails().size(), "configured min gap 1");
        World far = new World().yardX(-1, 20, 3).yardX(-1, 8, 3);
        assertEquals(1, YardLinker.link(far, far.yardBlocks(), new YardRules(1, 12, 15)).sails().size(), "configured max gap 12");
    }

    @Test
    void onlyAirAndMastsMayBeInTheGap() {
        World mast = new World().yardX(-1, 15, 3).mast(0, 11, 14, 0).yardX(-1, 10, 3);
        assertEquals(1, mast.link().sails().size());
        World blocked = new World().yardX(-1, 15, 3).mast(0, 11, 14, 0).set(0, 12, 0, Cell.OTHER).yardX(-1, 10, 3);
        assertEquals(0, blocked.link().sails().size());
        World besideTheColumn = new World().yardX(-1, 15, 3).set(1, 12, 0, Cell.OTHER).yardX(-1, 10, 3);
        assertEquals(1, besideTheColumn.link().sails().size(), "only the mast column counts");
    }

    @Test
    void aThirdYardInBetweenSplitsThePair() {
        World w = new World().yardX(-1, 16, 3).yardX(-2, 13, 5).yardX(-3, 10, 7);
        YardLinker.Linked l = w.link();
        assertEquals(3, l.rows().size());
        assertEquals(2, l.sails().size());
        for (SquareSail s : l.sails()) {
            assertEquals(3, s.drop(), "each sail spans to the nearest yard below: " + s);
        }
        SquareSail top = l.sails().stream().filter(s -> s.upper().y() == 16).findFirst().orElseThrow();
        assertEquals(13, top.lower().y());
        assertEquals((3 + 5) / 2.0 * 3, top.area(), EPS);
        SquareSail course = l.sails().stream().filter(s -> s.upper().y() == 13).findFirst().orElseThrow();
        assertEquals(10, course.lower().y());
        assertEquals((5 + 7) / 2.0 * 3, course.area(), EPS);
    }

    @Test
    void aThirdYardTooCloseLeavesTheUpperYardWithoutSail() {
        World w = new World().yardX(-1, 16, 3).yardX(-1, 15, 3).yardX(-1, 10, 3);
        YardLinker.Linked l = w.link();
        assertEquals(1, l.sails().size(), "the yard 1 below blocks the top yard; the middle one pairs with the bottom");
        assertEquals(15, l.sails().get(0).upper().y());
    }

    @Test
    void yardsWithDifferentAxesAreNoSail() {
        World w = new World().yardX(-1, 13, 3).yardZ(0, 10, -1, 3);
        assertEquals(0, w.link().sails().size());
        World under = new World().yardX(-1, 16, 3).yardZ(0, 13, -1, 3).yardX(-1, 10, 3);
        assertEquals(0, under.link().sails().size(), "a crossing yard in the gap blocks the pair");
    }

    @Test
    void lowerYardMustBeCenteredOnTheMastColumn() {
        World offset = new World().yardX(-1, 13, 3).yardX(0, 10, 3); // lower middle at x=1, upper at x=0
        assertEquals(0, offset.link().sails().size());
        World even = new World().yardX(-1, 13, 3).yardX(-1, 10, 4); // middles 0 (odd) and 0 (even: blocks -1..2)
        assertEquals(1, even.link().sails().size());
    }

    @Test
    void twoSailsOnOneMast() {
        // topsail 20 → 17, course 8 → 5; 17 → 8 is 9 blocks, beyond the largest gap, so the topsail's foot heads nothing
        World w = new World().yardX(-2, 20, 5).mast(0, 18, 19, 0).yardX(-2, 17, 5)
                .mast(0, 9, 16, 0).yardX(-3, 8, 7).mast(0, 6, 7, 0).yardX(-3, 5, 7).mast(0, 0, 4, 0);
        YardLinker.Linked l = w.link();
        assertEquals(4, l.rows().size());
        assertEquals(2, l.sails().size());
        assertTrue(l.sails().stream().anyMatch(s -> s.upper().y() == 20 && s.lower().y() == 17 && s.area() == 15.0));
        assertTrue(l.sails().stream().anyMatch(s -> s.upper().y() == 8 && s.lower().y() == 5 && s.area() == 21.0));
    }

    @Test
    void tooLongYardCarriesNoSail() {
        World w = new World().yardX(-8, 13, 17).yardX(-1, 10, 3);
        assertEquals(0, w.link().sails().size());
    }

    // ------------------------------------------------------------------ area and centroids

    @Test
    void areaIsTheMeanLengthTimesTheDrop() {
        SquareSail s = new SquareSail(new YardRow(true, 20, 0, -1, 1), new YardRow(true, 14, 0, -3, 3));
        assertEquals(6, s.drop());
        assertEquals((3 + 7) / 2.0 * 6, s.area(), EPS);
        assertEquals(s.area(), s.drawnArea(SailTrim.FULL), EPS);
        assertEquals(0.0, s.drawnArea(SailTrim.FURLED), EPS);
        // upper half: widths 3 → 5 over 3 blocks
        assertEquals((3 + 5) / 2.0 * 3, s.drawnArea(SailTrim.HALF), EPS);
    }

    @Test
    void centroidOfARectangleIsItsMiddle() {
        SquareSail s = new SquareSail(new YardRow(true, 13, 0, -1, 1), new YardRow(true, 9, 0, -1, 1));
        assertArrayEquals(new double[] {0, 0}, s.centroid(SailTrim.FURLED), EPS);
        assertArrayEquals(new double[] {0, 1}, s.centroid(SailTrim.HALF), EPS);
        assertArrayEquals(new double[] {0, 2}, s.centroid(SailTrim.FULL), EPS);
    }

    @Test
    void centroidOfATrapezoid() {
        // widths 2 (top) and 6 (bottom), height 3: centroid depth h (a + 2b) / (3 (a + b)) = 3 * 14 / 24 = 1.75
        SquareSail s = new SquareSail(new YardRow(true, 10, 0, 0, 1), new YardRow(true, 7, 0, -2, 3));
        assertEquals(1.75, s.centroid(SailTrim.FULL)[1], EPS);
        // both even rows: middles 0, extents 0.5/1.5 and 2.5/3.5: centers +0.5 at both, so u = 0.5
        assertEquals(0.5, s.centroid(SailTrim.FULL)[0], EPS);
        // half: widths 2 → 4 over 1.5: depth 1.5 * (2 + 8) / (3 * 6) = 0.8333
        assertEquals(1.5 * 10.0 / 18.0, s.centroid(SailTrim.HALF)[1], EPS);
        assertTrue(s.centroid(SailTrim.HALF)[1] < s.centroid(SailTrim.FULL)[1]);
    }

    @Test
    void centroidMovesTowardTheWiderSide() {
        // upper yard centered (u 0), lower yard reaching further to +u: the centroid lies on the + side
        SquareSail s = new SquareSail(new YardRow(true, 10, 0, -1, 1), new YardRow(true, 6, 0, -1, 2));
        assertTrue(s.centroid(SailTrim.FULL)[0] > 0.0);
        assertEquals(0.0, s.centroid(SailTrim.FURLED)[0], EPS);
    }

    @Test
    void geometryMatchesTheYards() {
        SquareSail s = new SquareSail(new YardRow(false, 10, 4, 0, 3), new YardRow(false, 5, 4, -1, 3));
        ClothGeometry g = s.geometry();
        assertEquals(false, g.alongX());
        assertEquals(1.5f, g.upperNeg(), 1e-6);
        assertEquals(2.5f, g.upperPos(), 1e-6);
        assertEquals(2.5f, g.lowerNeg(), 1e-6); // lower blocks -1..3 (5 long), middle 1 like the upper's: 2.5 / 2.5
        assertEquals(2.5f, g.lowerPos(), 1e-6);
        assertEquals(5, g.drop());
        assertEquals(-1.5f, g.negativeEdge(0), 1e-6);
        assertEquals(-2.5f, g.negativeEdge(5), 1e-6);
        assertEquals(2.5f, g.positiveEdge(2.5f), 1e-6);
    }

    @Test
    void clothClearsTheMastAndMeetsTheYards() {
        ClothGeometry g = new SquareSail(new YardRow(true, 10, 0, -2, 2), new YardRow(true, 2, 0, -2, 2)).geometry();
        assertEquals(ClothGeometry.AT_YARD, g.standoff(0f, 0.5f, 8f), 1e-6);
        assertEquals(ClothGeometry.AT_YARD, g.standoff(8f, 0.5f, 8f), 1e-6);
        for (float v = 0.5f; v <= 7.5f; v += 0.25f) {
            assertTrue(g.standoff(v, 0.5f, 8f) > 0.5f, "cloth inside the mast at v=" + v);
        }
        assertTrue(g.standoff(4f, 0.5f, 8f) > g.standoff(4f, 0f, 8f), "the belly is in the middle");
    }
}
