package com.richardsenger.piratesnships.ship.hull.pump;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PMP1: the stroke of the bilge pump's drawn handle ({@link PumpHandleSwing}) and its geometry ({@link PumpHandlePose}). */
class PumpHandleSwingTest {

    private static final double PERIOD = 20;
    private static final double DEGREES = 28;

    @Test
    void anIdlePumpStaysAtRest() {
        PumpHandleSwing s = new PumpHandleSwing();
        for (double t = 0; t < 60; t += 0.5) {
            assertEquals(0.0, s.swing(false, 500 + t, PERIOD, DEGREES));
        }
    }

    @Test
    void pumpingRocksTheHandleDownAndUpOncePerStroke() {
        PumpHandleSwing s = new PumpHandleSwing();
        assertEquals(0.0, s.swing(true, 100, PERIOD, DEGREES), 1e-12, "starts at rest");
        assertEquals(DEGREES / 2, s.swing(true, 105, PERIOD, DEGREES), 1e-9, "half way down after a quarter stroke");
        assertEquals(DEGREES, s.swing(true, 110, PERIOD, DEGREES), 1e-9, "at the bottom after half a stroke");
        assertEquals(0.0, s.swing(true, 120, PERIOD, DEGREES), 1e-9, "back up after a full stroke");
        assertEquals(DEGREES, s.swing(true, 150, PERIOD, DEGREES), 1e-9, "and on: the bottom of the third stroke");
        for (double t = 100; t < 160; t += 0.25) {
            double a = s.swing(true, t, PERIOD, DEGREES);
            assertTrue(a >= -1e-9 && a <= DEGREES + 1e-9, t + ": " + a);
        }
    }

    @Test
    void stoppingEasesToRestWithinHalfAStrokeAndStaysThere() {
        PumpHandleSwing s = new PumpHandleSwing();
        s.swing(true, 0, PERIOD, DEGREES);
        double at = s.swing(true, 10, PERIOD, DEGREES);
        assertEquals(DEGREES, at, 1e-9);
        assertEquals(at, s.swing(false, 10, PERIOD, DEGREES), 1e-9, "stopping does not jump");
        assertTrue(s.swing(false, 12, PERIOD, DEGREES) < DEGREES, "it eases down");
        for (double t = 20; t < 80; t += 0.5) {
            assertEquals(0.0, s.swing(false, t, PERIOD, DEGREES), 1e-12, "at rest at " + t);
        }
    }

    @Test
    void theDrawnHandleNeverJumps() {
        PumpHandleSwing s = new PumpHandleSwing();
        double last = 0;
        double maxStep = 0;
        // pumping on and off at awkward moments, sampled every partial tick of 0.1
        for (int i = 0; i <= 2000; i++) {
            double t = i * 0.1;
            boolean pumping = (t >= 3 && t < 17.3) || (t >= 21.1 && t < 50) || (t >= 52 && t < 90) || t >= 150;
            double a = s.swing(pumping, t, PERIOD, DEGREES);
            maxStep = Math.max(maxStep, Math.abs(a - last));
            last = a;
        }
        // the steepest part of a stroke moves DEGREES * pi / PERIOD per tick
        assertTrue(maxStep <= 1.6 * DEGREES * Math.PI / PERIOD * 0.1, "largest step per 0.1 tick: " + maxStep);
    }

    @Test
    void pumpingAgainWhileSettlingKeepsTheStrokeRunning() {
        PumpHandleSwing s = new PumpHandleSwing();
        s.swing(true, 0, PERIOD, DEGREES);
        s.swing(false, 10, PERIOD, DEGREES);
        double before = s.swing(false, 13, PERIOD, DEGREES);
        assertEquals(before, s.swing(true, 13, PERIOD, DEGREES), 1e-9, "resumes from where it is");
        assertEquals(DEGREES, s.swing(true, 30, PERIOD, DEGREES), 1e-9, "the old stroke's phase runs on");
    }

    @Test
    void theRodFollowsTheHandleAndTheHandleClearsTheCylinder() {
        assertEquals(0.0, PumpHandlePose.rodLift(0), 1e-12, "the rod rests where it is built");
        assertEquals(16.6, PumpHandlePose.undersideAt(PumpHandlePose.ROD_ARM, PumpHandlePose.REST_DEGREES), 0.01,
                "at rest the handle's underside touches the rod's top (y 16.6)");
        double last = 0;
        for (double s = 1; s <= 32; s += 1) {
            double lift = PumpHandlePose.rodLift(s);
            assertTrue(lift < last, "the rod goes down as the handle does: " + s);
            last = lift;
        }
        // the rod's top stays above the cylinder lip, so it never vanishes into the cylinder
        assertTrue(16.6 + PumpHandlePose.rodLift(PumpHandlePose.MAX_SWING) > PumpHandlePose.LIP_TOP);
        assertTrue(PumpHandlePose.MAX_SWING > 32 && PumpHandlePose.MAX_SWING < 32.5, "clearance " + PumpHandlePose.MAX_SWING);
        assertTrue(PumpHandlePose.undersideAt(PumpHandlePose.LIP_ARM, PumpHandlePose.handleDegrees(28))
                > PumpHandlePose.LIP_TOP + 0.3, "the default stroke clears the lip with room to spare");
    }
}
