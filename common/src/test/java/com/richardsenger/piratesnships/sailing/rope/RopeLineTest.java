package com.richardsenger.piratesnships.sailing.rope;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.sailing.sail.BlockPoint;
import org.junit.jupiter.api.Test;

/** The decorative rope line's shape (RP1): catenary points, sag, the same-height and vertical cases, the span limit. */
class RopeLineTest {

    private static final double EPS = 1e-9;

    @Test
    void endsAreExactAndTheMiddleSagsByTheFractionOfTheSpan() {
        double[][] pts = RopeLine.points(0, 5, 0, 8, 5, 0, 0.08);
        assertArrayEquals(new double[] {0, 5, 0}, pts[0], EPS);
        assertArrayEquals(new double[] {8, 5, 0}, pts[pts.length - 1], EPS);
        assertEquals(RopeLine.segments(8) + 1, pts.length);
        double[] mid = pts[pts.length / 2];
        assertEquals(4.0, mid[0], EPS);
        assertEquals(5 - 0.08 * 8, mid[1], 1e-9, "sag 0.64 at the middle of an 8-block rope");
        for (double[] p : pts) {
            assertTrue(p[1] <= 5 + EPS, "a rope never rises above its level ends");
            assertEquals(0, p[2], EPS);
        }
    }

    @Test
    void sameHeightRopeIsSymmetric() {
        double[][] pts = RopeLine.points(-3, 2, 1, 3, 2, 9, 0.2); // 10 blocks: 20 pieces, a point at the middle
        int n = pts.length - 1;
        for (int i = 0; i <= n; i++) {
            assertEquals(pts[i][1], pts[n - i][1], 1e-9, "point " + i);
        }
        assertEquals(2 - 0.2 * 10, pts[n / 2][1], 1e-9);
    }

    @Test
    void sagGrowsWithTheSpan() {
        assertEquals(0.32, RopeLine.sagDepth(4, 0, 0.08), EPS);
        assertEquals(0.64, RopeLine.sagDepth(0, 8, 0.08), EPS);
        assertEquals(1.28, RopeLine.sagDepth(16, 0, 0.08), EPS);
        assertEquals(0.0, RopeLine.sagDepth(16, 0, 0.0), EPS, "0 = taut");
        assertEquals(0.5 * 10, RopeLine.sagDepth(10, 0, 3.0), EPS, "the fraction is capped at 0.5");
        assertEquals(0.0, RopeLine.sagDepth(10, 0, -1.0), EPS);
    }

    @Test
    void slopedRopeSagsBelowItsChordAndAVerticalOneHangsStraight() {
        double[][] pts = RopeLine.points(0, 10, 0, 6, 2, 0, 0.1);
        int n = pts.length - 1;
        double[] mid = pts[n / 2];
        assertEquals(6 - 0.1 * 6, mid[1], 1e-9, "sag measured from the chord, scaled by the horizontal span");
        double[][] vertical = RopeLine.points(0, 10, 0, 0, 0, 0, 0.3);
        for (double[] p : vertical) {
            assertEquals(0, p[0], EPS);
            assertEquals(0, p[2], EPS);
        }
        assertEquals(5.0, vertical[(vertical.length - 1) / 2][1], EPS);
    }

    @Test
    void shapeIsALevelCatenary() {
        double ratio = 0.3;
        double k = RopeLine.catenaryK(ratio);
        assertEquals(ratio, (Math.cosh(k) - 1) / (2 * k), 1e-9, "k solves the sag ratio");
        assertEquals(0.0, RopeLine.shape(0, k), EPS);
        assertEquals(0.0, RopeLine.shape(1, k), EPS);
        assertEquals(1.0, RopeLine.shape(0.5, k), EPS);
        // y = a cosh(x / a) over a span of L = 1 with a = 1 / (2k), lowest point at x = 0.5
        double a = 1 / (2 * k);
        double sag = ratio;
        for (double t : new double[] {0.1, 0.25, 0.4, 0.7, 0.9}) {
            double heightAboveLowest = a * (Math.cosh((t - 0.5) / a) - 1);
            double belowChord = sag - heightAboveLowest;
            assertEquals(belowChord, sag * RopeLine.shape(t, k), 1e-9, "t=" + t);
        }
        // a catenary hangs fuller near the ends than the parabola of the same sag
        assertTrue(RopeLine.shape(0.1, k) > 4 * 0.1 * 0.9);
        assertEquals(4 * 0.25 * 0.75, RopeLine.shape(0.25, 0), EPS, "no sag: the parabola limit");
        assertEquals(0.0, RopeLine.catenaryK(0), EPS);
    }

    @Test
    void segmentsAndSpanLimit() {
        assertEquals(RopeLine.MIN_SEGMENTS, RopeLine.segments(0.5));
        assertEquals(8, RopeLine.segments(4));
        assertEquals(32, RopeLine.segments(16));
        assertEquals(RopeLine.MAX_SEGMENTS, RopeLine.segments(100));
        BlockPoint a = new BlockPoint(0, 0, 0);
        assertTrue(RopeLine.fits(a, new BlockPoint(16, 0, 0), 16));
        assertFalse(RopeLine.fits(a, new BlockPoint(16, 1, 0), 16));
        assertTrue(RopeLine.fits(a, new BlockPoint(9, 9, 9), 16), "15.6 blocks");
        assertFalse(RopeLine.fits(a, new BlockPoint(10, 10, 10), 16), "17.3 blocks");
    }
}
