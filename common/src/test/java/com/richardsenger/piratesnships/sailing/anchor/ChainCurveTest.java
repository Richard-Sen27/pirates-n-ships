package com.richardsenger.piratesnships.sailing.anchor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The chain's shape (AN2b): catenary sag with slack, straight when taut, exact ends, no NaN, the seabed floor. */
class ChainCurveTest {

    private static final double NO_FLOOR = Double.NEGATIVE_INFINITY;

    /** Lowest point of the curve below the straight line between the ends [blocks]. */
    private static double sag(ChainCurve c) {
        int n = c.segments();
        double max = 0.0;
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n;
            double line = c.y(0) + (c.y(n) - c.y(0)) * t;
            max = Math.max(max, line - c.y(i));
        }
        return max;
    }

    private static double arcLength(ChainCurve c) {
        double sum = 0.0;
        for (int i = 0; i < c.segments(); i++) {
            double dx = c.x(i + 1) - c.x(i), dy = c.y(i + 1) - c.y(i), dz = c.z(i + 1) - c.z(i);
            sum += Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        return sum;
    }

    private static void assertFinite(ChainCurve c) {
        for (int i = 0; i <= c.segments(); i++) {
            assertTrue(Double.isFinite(c.x(i)) && Double.isFinite(c.y(i)) && Double.isFinite(c.z(i)), "point " + i);
        }
    }

    @Test
    void sagGrowsWithSlack() {
        ChainCurve c = new ChainCurve();
        double last = -1.0;
        for (double length : new double[] {20.2, 21.0, 23.0, 26.0, 30.0}) {
            c.compute(0, 0, 0, 16, 12, 0, length, false, NO_FLOOR, 32); // distance 20
            assertTrue(c.slack());
            double sag = sag(c);
            assertTrue(sag > last + 0.05, "length " + length + ": sag " + sag + " after " + last);
            last = sag;
        }
    }

    @Test
    void slackCurveHasThePaidOutLength() {
        ChainCurve c = new ChainCurve();
        for (double[] k : new double[][] {{12, 3, 0, 15}, {5, -8, 4, 20}, {0.5, 10, 0.2, 14}, {20, 0, 0, 20.05}, {3, 1, 0, 60}}) {
            int n = c.compute(1, 2, 3, 1 + k[0], 2 + k[1], 3 + k[2], k[3], false, NO_FLOOR, 64);
            assertEquals(64, n);
            assertFinite(c);
            assertEquals(k[3], arcLength(c), 0.02 * k[3], "case " + java.util.Arrays.toString(k));
        }
    }

    @Test
    void endpointsAreExact() {
        ChainCurve c = new ChainCurve();
        for (boolean taut : new boolean[] {false, true}) {
            int n = c.compute(10.25, 40.5, -7.75, 22.5, 55.0, -1.0, 30.0, taut, NO_FLOOR, 17);
            assertEquals(17, n);
            assertEquals(10.25, c.x(0));
            assertEquals(40.5, c.y(0));
            assertEquals(-7.75, c.z(0));
            assertEquals(22.5, c.x(n));
            assertEquals(55.0, c.y(n));
            assertEquals(-1.0, c.z(n));
        }
    }

    @Test
    void tautChainIsStraightWithASlightBow() {
        ChainCurve c = new ChainCurve();
        c.compute(0, 0, 0, 30, 20, 0, 10.0, false, NO_FLOOR, 36); // shorter than the distance: drawn straight
        assertFalse(c.slack());
        assertTrue(sag(c) <= ChainCurve.MAX_TAUT_BOW + 1e-9);
        c.compute(0, 0, 0, 30, 20, 0, 50.0, true, NO_FLOOR, 36); // the taut flag wins over the length
        assertFalse(c.slack());
        double sag = sag(c);
        assertTrue(sag > 0.0 && sag <= ChainCurve.MAX_TAUT_BOW + 1e-9, "bow " + sag);
        for (int i = 0; i <= c.segments(); i++) {
            assertEquals(0.0, c.z(i), 1e-12);
            assertEquals(30.0 * i / c.segments(), c.x(i), 1e-9);
        }
    }

    @Test
    void noNanAtZeroOrVerticalDistance() {
        ChainCurve c = new ChainCurve();
        c.compute(5, 5, 5, 5, 5, 5, 10.0, false, NO_FLOOR, 10);
        assertFinite(c);
        c.compute(5, 5, 5, 5, 5, 5, 0.0, true, NO_FLOOR, 1);
        assertFinite(c);
        c.compute(5, 5, 5, 5, 15, 5, 30.0, false, 5.0, 30); // straight up: hangs vertically
        assertFinite(c);
        c.compute(5, 5, 5, 5 + 1e-3, 15, 5, 30.0, false, 5.0, 30); // nearly vertical, much slack
        assertFinite(c);
        c.compute(0, 0, 0, 0.01, 0, 0, 256.0, false, NO_FLOOR, 64); // extreme slack
        assertFinite(c);
    }

    @Test
    void floorKeepsTheChainOnTheSeabed() {
        ChainCurve c = new ChainCurve();
        c.compute(0, 2, 0, 10, 12, 0, 30.0, false, 0.1, 30); // a lot of slack: the free curve dips below the ring
        assertTrue(c.slack());
        int onFloor = 0;
        for (int i = 0; i <= c.segments(); i++) {
            assertTrue(c.y(i) >= 0.1 - 1e-12);
            if (c.y(i) == 0.1) {
                onFloor++;
            }
        }
        assertTrue(onFloor > 0, "part of the chain lies on the seabed");
    }

    @Test
    void segmentCountScalesWithLengthAndIsCapped() {
        assertEquals(1, ChainCurve.segmentsFor(0.0));
        assertEquals(1, ChainCurve.segmentsFor(Double.NaN));
        assertEquals(10, ChainCurve.segmentsFor(9.5));
        assertEquals(ChainCurve.MAX_SEGMENTS, ChainCurve.segmentsFor(256.0));
        assertEquals(ChainCurve.MAX_SEGMENTS, new ChainCurve().compute(0, 0, 0, 1, 0, 0, 1, true, NO_FLOOR, 1000));
    }
}
