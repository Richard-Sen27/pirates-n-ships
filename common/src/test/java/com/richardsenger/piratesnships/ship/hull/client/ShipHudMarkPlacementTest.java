package com.richardsenger.piratesnships.ship.hull.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** LKO2: a sighting's mark sits on the compass rose's ring at its bearing, north up, inside the compass panel. */
class ShipHudMarkPlacementTest {

    /** The mark's centre in panel pixels (sprite pixel centres). */
    private static double[] centre(double bearing) {
        ShipHudLayout.Point o = ShipHudLayout.markOrigin(bearing);
        return new double[] {o.x() + ShipHudSheet.MARK.w() / 2.0, o.y() + ShipHudSheet.MARK.h() / 2.0};
    }

    @Test
    void cardinalBearingsSitOnTheRingNorthUp() {
        double rx = ShipHudLayout.ROSE_CX - 0.5, ry = ShipHudLayout.ROSE_CY - 0.5;
        double[] n = centre(0), e = centre(90), s = centre(180), w = centre(270);
        assertEquals(rx, n[0], 0.51);
        assertEquals(ry - ShipHudLayout.MARK_R, n[1], 0.51);
        assertEquals(rx + ShipHudLayout.MARK_R, e[0], 0.51);
        assertEquals(ry, e[1], 0.51);
        assertEquals(rx, s[0], 0.51);
        assertEquals(ry + ShipHudLayout.MARK_R, s[1], 0.51);
        assertEquals(rx - ShipHudLayout.MARK_R, w[0], 0.51);
        assertEquals(ry, w[1], 0.51);
    }

    @Test
    void everyBearingIsOnTheRingAndInsideThePanel() {
        double rx = ShipHudLayout.ROSE_CX - 0.5, ry = ShipHudLayout.ROSE_CY - 0.5;
        for (int b = 0; b < 360; b++) {
            double[] c = centre(b);
            double r = Math.hypot(c[0] - rx, c[1] - ry);
            assertEquals(ShipHudLayout.MARK_R, r, 0.75, "bearing " + b);
            double ang = Math.toDegrees(Math.atan2(c[0] - rx, -(c[1] - ry)));
            double diff = ((ang - b) % 360 + 540) % 360 - 180;
            assertTrue(Math.abs(diff) < 3, "bearing " + b + " drawn at " + ang);
            ShipHudLayout.Point o = ShipHudLayout.markOrigin(b);
            assertTrue(o.x() >= 0 && o.y() >= 0 && o.x() + ShipHudSheet.MARK.w() <= ShipHudLayout.W
                    && o.y() + ShipHudSheet.MARK.h() < ShipHudLayout.SPEED_Y - 1, "bearing " + b + " leaves the panel: " + o);
        }
    }

    @Test
    void marksFromEverySourceAreCollected() {
        java.util.UUID ship = java.util.UUID.randomUUID();
        ShipHudMarks.register(id -> id.equals(ship) ? java.util.List.of(new ShipHudMarks.Mark(45, 0xFFFFFFFF, 1f)) : java.util.List.of());
        assertEquals(1, ShipHudMarks.of(ship).stream().filter(m -> m.bearing() == 45).count());
        assertTrue(ShipHudMarks.of(java.util.UUID.randomUUID()).stream().noneMatch(m -> m.bearing() == 45));
    }
}
