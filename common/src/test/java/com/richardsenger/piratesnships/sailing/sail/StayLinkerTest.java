package com.richardsenger.piratesnships.sailing.sail;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.sail.CleatLookup.Cell;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Rule F5b (docs/design.md §5.2): stays, clew search, area, drawn cloth, centroids and the cleat frame. */
class StayLinkerTest {

    private static final StayRules R = StayRules.DEFAULTS;
    private static final double EPS = 1e-9;

    /** A sparse world: unknown cells are air. */
    private static final class World implements CleatLookup {
        final Map<List<Integer>, Cell> cells = new HashMap<>();

        @Override
        public Cell at(int x, int y, int z) {
            return cells.getOrDefault(List.of(x, y, z), Cell.AIR);
        }

        World set(BlockPoint p, Cell c) {
            cells.put(List.of(p.x(), p.y(), p.z()), c);
            return this;
        }

        World cleat(BlockPoint p) {
            return set(p, Cell.CLEAT);
        }
    }

    private static final BlockPoint A = new BlockPoint(0, 10, 0);
    private static final BlockPoint B = new BlockPoint(0, 6, 5);
    private static final BlockPoint C = new BlockPoint(0, 6, 0);

    @Test
    void stayChecksLengthDropAndSameCleat() {
        assertEquals(StayLinker.Check.OK, StayLinker.check(A, B, R));
        assertEquals(StayLinker.Check.OK, StayLinker.check(B, A, R));
        assertEquals(StayLinker.Check.SAME, StayLinker.check(A, A, R));
        // exactly 16 apart is allowed, a little more is not
        assertEquals(StayLinker.Check.OK, StayLinker.check(new BlockPoint(0, 16, 0), new BlockPoint(0, 0, 0), R));
        assertEquals(StayLinker.Check.TOO_LONG, StayLinker.check(new BlockPoint(0, 16, 0), new BlockPoint(0, 0, 1), R));
        // exactly the minimum drop is allowed, one less is not
        assertEquals(StayLinker.Check.OK, StayLinker.check(new BlockPoint(0, 2, 0), new BlockPoint(0, 0, 5), R));
        assertEquals(StayLinker.Check.TOO_FLAT, StayLinker.check(new BlockPoint(0, 1, 0), new BlockPoint(0, 0, 5), R));
        assertEquals(StayLinker.Check.TOO_FLAT, StayLinker.check(new BlockPoint(0, 0, 0), new BlockPoint(0, 0, 5), R));
        assertEquals(Math.sqrt(41), StayLinker.length(A, B), EPS);
    }

    @Test
    void sailIsTheTriangleOfHeadTackAndClew() {
        World w = new World().cleat(A).cleat(B).cleat(C);
        TriangularSail s = StayLinker.sail(w, A, B, R);
        assertNotNull(s);
        assertEquals(A, s.head());
        assertEquals(B, s.tack());
        assertEquals(C, s.clew());
        assertEquals(4, s.drop());
        assertEquals(10.0, s.area(), EPS); // 0.5 * 4 * 5
        assertEquals(s, StayLinker.sail(w, B, A, R), "either order of the stay's ends");
        assertEquals(new TriangleCloth(0, -4, 5, 4), s.geometry());
    }

    @Test
    void areaIsHalfTheCrossProductForAnySlantedStay() {
        BlockPoint head = new BlockPoint(3, 12, -2);
        BlockPoint tack = new BlockPoint(-1, 7, 4); // 4 across x, 6 along z: 7.21 horizontally
        BlockPoint clew = new BlockPoint(3, 8, -2);
        TriangularSail s = new TriangularSail(head, tack, clew);
        assertEquals(0.5 * 4 * Math.hypot(4, 6), s.area(), EPS);
    }

    @Test
    void clewIsFirstCleatBelowHeadThroughAirAndMast() {
        // the nearest cleat counts
        World w = new World().cleat(A).cleat(B).cleat(C).cleat(A.below(2));
        assertEquals(A.below(2), StayLinker.findClew(w, A, B.y()));
        // mast blocks are passed
        w = new World().cleat(A).cleat(B).cleat(C).set(A.below(1), Cell.MAST).set(A.below(2), Cell.MAST);
        assertEquals(C, StayLinker.findClew(w, A, B.y()));
        // anything else ends the search
        w = new World().cleat(A).cleat(B).cleat(C).set(A.below(2), Cell.OTHER);
        assertNull(StayLinker.findClew(w, A, B.y()));
        assertNull(StayLinker.sail(w, A, B, R));
        // no cleat at all
        assertNull(StayLinker.sail(new World().cleat(A).cleat(B), A, B, R));
    }

    @Test
    void clewMustNotBeBelowTheTack() {
        World w = new World().cleat(A).cleat(B).cleat(A.below(5)); // y=5, one below the tack's height
        assertNull(StayLinker.sail(w, A, B, R));
        w = new World().cleat(A).cleat(B).cleat(A.below(4)); // at the tack's height: allowed
        assertNotNull(StayLinker.sail(w, A, B, R));
    }

    @Test
    void stayStraightDownHasNoSail() {
        BlockPoint tack = A.below(4);
        World w = new World().cleat(A).cleat(tack);
        // the tack itself is the first cleat below the head: a triangle without area
        assertNull(StayLinker.sail(w, A, tack, R));
    }

    @Test
    void invalidStaysHaveNoSail() {
        BlockPoint far = new BlockPoint(0, 6, 17);
        assertNull(StayLinker.sail(new World().cleat(A).cleat(far).cleat(C), A, far, R));
        BlockPoint flat = new BlockPoint(0, 9, 5);
        assertNull(StayLinker.sail(new World().cleat(A).cleat(flat).cleat(A.below(1)), A, flat, R));
        assertThrows(IllegalArgumentException.class, () -> new TriangularSail(A, B, new BlockPoint(1, 6, 0)));
    }

    @Test
    void drawnClothAndCentroidPerTrim() {
        TriangularSail s = new TriangularSail(A, B, C);
        assertEquals(0.0, s.drawnArea(SailTrim.FURLED), EPS);
        assertEquals(5.0, s.drawnArea(SailTrim.HALF), EPS);
        assertEquals(10.0, s.drawnArea(SailTrim.FULL), EPS);
        // furled: the middle of the stay
        assertArrayEquals(new double[] {0, -2, 2.5}, s.centroid(SailTrim.FURLED), EPS);
        // half: triangle (0,0,0), (0,-4,5), (0,-2,0)
        assertArrayEquals(new double[] {0, -2, 5 / 3.0}, s.centroid(SailTrim.HALF), EPS);
        // full: triangle (0,0,0), (0,-4,5), (0,-4,0)
        assertArrayEquals(new double[] {0, -8 / 3.0, 5 / 3.0}, s.centroid(SailTrim.FULL), EPS);
    }

    @Test
    void cleatFrameRoundTripsAndTurnsWithTheFacing() {
        for (int q = 0; q < 4; q++) {
            int[] local = CleatFrame.toLocal(q, 3, -2, 7);
            assertArrayEquals(new int[] {3, -2, 7}, CleatFrame.toWorld(q, local[0], local[1], local[2]));
        }
        // stored facing south (0), then the block is turned a quarter clockwise (now west, 1): the offset turns with it
        int[] local = CleatFrame.toLocal(0, 0, -4, 5); // 5 south
        assertArrayEquals(new int[] {-5, -4, 0}, CleatFrame.toWorld(1, local[0], local[1], local[2])); // 5 west
    }

    @Test
    void rulesClampToSaneValues() {
        StayRules r = new StayRules(0, 0);
        assertEquals(1, r.maxLength());
        assertEquals(1, r.minDrop());
    }
}
