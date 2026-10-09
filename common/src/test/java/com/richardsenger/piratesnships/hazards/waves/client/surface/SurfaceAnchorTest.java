package com.richardsenger.piratesnships.hazards.waves.client.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SurfaceAnchorTest {

    @Test
    void firstUpdateAndFarJumpsSnap() {
        SurfaceAnchor a = new SurfaceAnchor();
        a.update(100, -50, false, 1);
        assertEquals(100, a.x());
        assertEquals(-50, a.z());
        a.update(100 + SurfaceAnchor.SNAP_DISTANCE + 1, -50, false, 1);
        assertEquals(100 + SurfaceAnchor.SNAP_DISTANCE + 1, a.x(), "teleport: snap");
    }

    @Test
    void freeAnchorStaysInsideTheDeadZoneAndFollowsBeyondIt() {
        SurfaceAnchor a = new SurfaceAnchor();
        a.update(0, 0, false, 1);
        a.update(SurfaceAnchor.DEAD_ZONE - 1, 0, false, 1);
        assertEquals(0, a.x(), "inside the dead zone it does not move");
        // a camera moving 1 block per tick (20 blocks/s) for 30 s
        double cam = 0;
        double lastX = a.x();
        for (int i = 0; i < 600; i++) {
            cam += 1.0;
            a.update(cam, 0, false, 1);
            assertTrue(a.x() - lastX <= Math.max(SurfaceAnchor.FREE_MIN_SPEED, cam * SurfaceAnchor.FREE_RATE) + 1e-9, "bounded step");
            lastX = a.x();
        }
        assertTrue(cam - a.x() < SurfaceAnchor.DEAD_ZONE + 30, "keeps up near the dead zone's edge: " + (cam - a.x()));
        // the camera stops: the anchor settles at the edge of the dead zone and stays
        for (int i = 0; i < 2000; i++) {
            a.update(cam, 0, false, 1);
        }
        assertEquals(SurfaceAnchor.DEAD_ZONE, cam - a.x(), 1e-6);
    }

    @Test
    void shipAnchorGlidesOntoTheShipAndFollowsIt() {
        SurfaceAnchor a = new SurfaceAnchor();
        a.update(0, 0, false, 1);
        double ship = 40;
        int ticks = 0;
        while (Math.abs(a.x() - ship) > 1e-9 && ticks < 400) {
            double before = a.x();
            a.update(ship, 0, true, 1);
            assertTrue(a.x() - before <= Math.max(SurfaceAnchor.SHIP_MIN_SPEED, (ship - before) * SurfaceAnchor.SHIP_RATE) + 1e-9);
            ticks++;
        }
        assertTrue(ticks < 60, "reached the ship in " + ticks + " ticks");
        // a ship sailing at 0.3 blocks per tick: the anchor stays on it
        for (int i = 0; i < 200; i++) {
            ship += 0.3;
            a.update(ship, 0, true, 1);
        }
        assertEquals(ship, a.x(), 1e-9);
    }

    @Test
    void pausedTimeDoesNotMoveIt() {
        SurfaceAnchor a = new SurfaceAnchor();
        a.update(0, 0, true, 1);
        a.update(30, 0, true, 0);
        assertEquals(0, a.x());
        a.update(30, 0, true, -5);
        assertEquals(0, a.x());
    }
}
