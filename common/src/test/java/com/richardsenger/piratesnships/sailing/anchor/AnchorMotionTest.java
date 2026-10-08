package com.richardsenger.piratesnships.sailing.anchor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** The anchor's body (AN2a): fall, sinking, landing offset, the chain's end, dragging and heaving in. */
class AnchorMotionTest {

    private static final double DT = 0.05;
    private static final double SINK = 4.0;
    private static final double DRAG = 1.5;

    /** A flat seabed: solid at y ≤ {@code floor − 1}, water from {@code floor} up to {@code surface − 1}. */
    private static AnchorMotion.Terrain sea(int floor, int surface) {
        return new AnchorMotion.Terrain() {
            @Override
            public boolean solid(int x, int y, int z) {
                return y < floor;
            }

            @Override
            public boolean water(int x, int y, int z) {
                return y >= floor && y < surface;
            }
        };
    }

    /** Falls until it rests (or {@code maxTicks}); returns {ticks, final body}. */
    private static Object[] fallUntilRest(AnchorMotion.Body b, Vec3 hawse, double chain, AnchorMotion.Terrain t, int maxTicks) {
        int n = 0;
        while (!b.resting() && n < maxTicks) {
            b = AnchorMotion.fall(b, hawse, DT, SINK, DRAG, chain, t);
            n++;
        }
        return new Object[] {n, b};
    }

    @Test
    void sinksAtTheSinkSpeedAndLandsOnTheSeabed() {
        AnchorMotion.Terrain t = sea(2, 8);
        Vec3 hawse = new Vec3(0.5, 9.0, 0.5);
        AnchorMotion.Body b = new AnchorMotion.Body(AnchorMotion.crown(hawse), Vec3.ZERO, 0.0, false, false);
        double minVy = 0.0;
        int ticks = 0;
        while (!b.resting() && ticks < 200) {
            b = AnchorMotion.fall(b, hawse, DT, SINK, DRAG, 32, t);
            if (!b.resting()) {
                minVy = Math.min(minVy, b.vel().y);
            }
            ticks++;
        }
        assertTrue(b.resting(), "never landed");
        assertEquals(2.0, b.pos().y, 1e-9, "not on top of the seabed");
        assertTrue(minVy >= -SINK - 1e-9, "sank faster than the sink speed: " + minVy);
        // 5 blocks of water at up to 4 blocks/s with the ramp-up: between 1.25 and 2 s
        assertTrue(ticks >= 25 && ticks <= 40, "landed after " + ticks + " ticks");
        assertEquals(Vec3.ZERO, b.vel());
        assertEquals(b.ring().distanceTo(hawse), b.paidOut(), 1e-9, "the chain paid out to the distance");
    }

    @Test
    void fallsFasterThroughAirThanWater() {
        AnchorMotion.Terrain air = sea(2, 2);
        Vec3 hawse = new Vec3(0.5, 9.0, 0.5);
        AnchorMotion.Body start = new AnchorMotion.Body(AnchorMotion.crown(hawse), Vec3.ZERO, 0.0, false, false);
        int inAir = (int) fallUntilRest(start, hawse, 32, air, 200)[0];
        int inWater = (int) fallUntilRest(start, hawse, 32, sea(2, 8), 200)[0];
        assertTrue(inAir < inWater, "air " + inAir + " ticks, water " + inWater);
    }

    @Test
    void keepsTheShipsSpeedAndLandsAheadOfTheDropButAsternOfTheMovingHawse() {
        AnchorMotion.Terrain t = sea(2, 8);
        Vec3 hawse0 = new Vec3(0.5, 9.0, 0.5);
        double shipSpeed = 4.0;
        AnchorMotion.Body b = new AnchorMotion.Body(AnchorMotion.crown(hawse0), new Vec3(0, 0, shipSpeed), 0.0, false, false);
        int n = 0;
        Vec3 hawse = hawse0;
        while (!b.resting() && n < 200) {
            n++;
            hawse = hawse0.add(0, 0, shipSpeed * DT * n); // the ship sails on at its speed
            b = AnchorMotion.fall(b, hawse, DT, SINK, DRAG, 32, t);
        }
        double ahead = b.pos().z - hawse0.z;
        double astern = hawse.z - b.pos().z;
        assertTrue(ahead > 0.5, "the anchor lost the ship's speed at once: " + ahead);
        assertTrue(ahead < shipSpeed / DRAG, "it kept more than the water allows: " + ahead);
        assertTrue(astern >= 2.0, "it should land astern of the moving hawse: " + astern);
        assertTrue(b.paidOut() > 5.0, "the chain did not pay out with the distance: " + b.paidOut());
    }

    @Test
    void theChainsEndHoldsItAboveTheGround() {
        AnchorMotion.Terrain t = sea(2, 8);
        Vec3 hawse = new Vec3(0.5, 9.0, 0.5);
        AnchorMotion.Body b = new AnchorMotion.Body(AnchorMotion.crown(hawse), Vec3.ZERO, 0.0, false, false);
        for (int i = 0; i < 100; i++) {
            b = AnchorMotion.fall(b, hawse, DT, SINK, DRAG, 3.0, t);
        }
        assertFalse(b.resting(), "a 3-block chain cannot reach a seabed 7 blocks down");
        assertTrue(b.atChainEnd());
        assertEquals(3.0, b.ring().distanceTo(hawse), 1e-9);
        assertEquals(3.0, b.paidOut(), 1e-9);
        assertTrue(b.vel().y >= -1e-9, "still falling at the chain's end: " + b.vel());
    }

    @Test
    void aWallStopsTheSidewaysDrift() {
        AnchorMotion.Terrain t = new AnchorMotion.Terrain() {
            @Override
            public boolean solid(int x, int y, int z) {
                return y < 2 || z >= 2;
            }

            @Override
            public boolean water(int x, int y, int z) {
                return y >= 2 && y < 8;
            }
        };
        Vec3 hawse = new Vec3(0.5, 9.0, 0.5);
        AnchorMotion.Body b = new AnchorMotion.Body(AnchorMotion.crown(hawse), new Vec3(0, 0, 8), 0.0, false, false);
        b = (AnchorMotion.Body) fallUntilRest(b, hawse, 32, t, 200)[1];
        assertTrue(b.pos().z < 2.0, "went through the wall: " + b.pos());
    }

    @Test
    void dragsAlongTheSeabedTowardTheHawseAndClimbsASingleStep() {
        AnchorMotion.Terrain t = new AnchorMotion.Terrain() {
            @Override
            public boolean solid(int x, int y, int z) {
                return y < 2 || y == 2 && z >= 3; // a one-block step at z = 3
            }

            @Override
            public boolean water(int x, int y, int z) {
                return y < 8;
            }
        };
        Vec3 hawse = new Vec3(0.5, 9.0, 10.5);
        AnchorMotion.Body b = new AnchorMotion.Body(new Vec3(0.5, 2.0, 2.5), Vec3.ZERO, 10.0, true, false);
        AnchorMotion.Body d = AnchorMotion.drag(b, hawse, 0.4, t);
        assertEquals(2.0, d.pos().y, 1e-9);
        assertEquals(2.9, d.pos().z, 1e-9);
        assertTrue(d.resting());
        d = AnchorMotion.drag(d, hawse, 0.4, t);
        assertEquals(3.0, d.pos().y, 1e-9, "did not climb the step");
        assertEquals(3.3, d.pos().z, 1e-9);
        assertTrue(d.resting());
        // straight below the hawse it stays
        AnchorMotion.Body below = new AnchorMotion.Body(new Vec3(0.5, 2.0, 10.5), Vec3.ZERO, 7.0, true, false);
        assertEquals(below, AnchorMotion.drag(below, hawse, 1.0, t));
    }

    @Test
    void aHighWallCatchesTheDraggedAnchor() {
        AnchorMotion.Terrain t = new AnchorMotion.Terrain() {
            @Override
            public boolean solid(int x, int y, int z) {
                return y < 2 || z == 3;
            }

            @Override
            public boolean water(int x, int y, int z) {
                return y < 8;
            }
        };
        AnchorMotion.Body b = new AnchorMotion.Body(new Vec3(0.5, 2.0, 2.9), Vec3.ZERO, 10.0, true, false);
        assertEquals(b.pos(), AnchorMotion.drag(b, new Vec3(0.5, 9.0, 10.5), 0.5, t).pos());
    }

    @Test
    void heavingSlidesItOverTheSeabedThenLiftsItToTheHawse() {
        AnchorMotion.Terrain t = sea(2, 8);
        Vec3 hawse = new Vec3(0.5, 9.0, 10.5);
        // resting 10 blocks astern on the seabed: the ring is 5 below the hawse
        AnchorMotion.Body b = new AnchorMotion.Body(new Vec3(0.5, 2.0, 0.5), Vec3.ZERO, Math.hypot(10, 5), true, false);
        double l = b.paidOut();
        int n = 0;
        boolean slid = false;
        while (l > 0 && n < 400) {
            l -= 2.5 * DT;
            AnchorMotion.Body next = AnchorMotion.heave(b, hawse, l, DT, SINK, DRAG, t);
            if (next.resting() && next.pos().z > b.pos().z) {
                slid = true;
                assertEquals(2.0, next.pos().y, 1e-9, "left the seabed while sliding");
            }
            assertTrue(next.ring().distanceTo(hawse) <= Math.max(l, 0) + 1e-6, "the chain is longer than wound: " + next);
            b = next;
            n++;
        }
        assertTrue(slid, "never slid over the seabed");
        assertFalse(b.resting());
        assertTrue(b.ring().distanceTo(hawse) < 0.2, "not at the hawse: " + b.ring());
        // about (chain length) / (raise speed) seconds
        assertEquals(Math.hypot(10, 5) / 2.5 / DT, n, 2.0);
    }

    @Test
    void anAnchorLosingItsGroundIsNoLongerSupported() {
        AnchorMotion.Terrain t = sea(2, 8);
        assertTrue(AnchorMotion.supported(new Vec3(0.5, 2.0, 0.5), t));
        assertFalse(AnchorMotion.supported(new Vec3(0.5, 3.0, 0.5), t));
        assertFalse(AnchorMotion.supported(new Vec3(0.5, 2.5, 0.5), t));
    }
}
