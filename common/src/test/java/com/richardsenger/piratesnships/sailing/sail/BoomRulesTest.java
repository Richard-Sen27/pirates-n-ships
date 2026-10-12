package com.richardsenger.piratesnships.sailing.sail;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * SAIL5: the boom's rules ({@link BoomRules}). Ship-frame vectors are {@code (port, forward)}; a flow vector is where
 * the wind blows toward. The test rig is a mainsail: the boom points aft, {@code (0, −1)}, so a positive sheet
 * (clockwise seen from above) swings its end to port: from aft {@code (0, −1)} clockwise is toward port {@code (1, 0)}...
 * checked in {@link #clockwiseFromAftIsToPort}.
 */
class BoomRulesTest {

    static final double MIN = BoomRules.DEFAULT_MIN_WIND;
    /** A mainsail's boom points aft. */
    static final double AFT_PORT = 0.0, AFT_FWD = -1.0;

    /** The flow of a wind coming from {@code beta} degrees off the bow on the {@code fromPort} side (or starboard). */
    static double[] wind(double beta, boolean fromPort) {
        double r = Math.toRadians(beta);
        // it comes from (sin beta toward that side, cos beta forward) and blows the other way
        double fromPortComponent = Math.sin(r) * (fromPort ? 1 : -1);
        return new double[] {-fromPortComponent, -Math.cos(r)};
    }

    @Test
    void stepsRunFromMinusSixtyToSixtyInFifteens() {
        assertEquals(9, BoomRules.STEPS);
        assertEquals(-60.0, BoomRules.angle(0));
        assertEquals(0.0, BoomRules.angle(BoomRules.CENTRE));
        assertEquals(60.0, BoomRules.angle(8));
        assertEquals(60.0, BoomRules.angle(12), "clamped");
        assertEquals(6, BoomRules.mirror(2));
        assertEquals(BoomRules.CENTRE, BoomRules.mirror(BoomRules.CENTRE));
        assertEquals(5, BoomRules.stepToward(4, 8));
        assertEquals(3, BoomRules.stepToward(4, 0));
        assertEquals(4, BoomRules.stepToward(4, 4));
    }

    @Test
    void turnIsClockwiseSeenFromAbove() {
        // the world: east (1, 0) turns to south (0, 1)
        double[] s = BoomRules.turn(1, 0, 90);
        assertEquals(0.0, s[0], 1e-12);
        assertEquals(1.0, s[1], 1e-12);
        // north (0, -1) turns to east (1, 0)
        double[] e = BoomRules.turn(0, -1, 90);
        assertEquals(1.0, e[0], 1e-12);
        assertEquals(0.0, e[1], 1e-12);
    }

    @Test
    void clockwiseFromAftIsToPort() {
        // ship frame: x port, z forward; seen from above with the bow up, port is left. Aft (0, -1) turned clockwise by
        // 90 degrees: (1, 0), port. So a positive sheet swings a mainsail's boom to port
        double[] p = BoomRules.turn(AFT_PORT, AFT_FWD, 45);
        assertTrue(p[0] > 0 && p[1] < 0, "aft and to port");
        assertEquals(1, BoomRules.side(p[0], p[1]));
        assertEquals(45.0, BoomRules.boomAngle(p[0], p[1]), 1e-9);
    }

    @Test
    void betaAndLeewardMatchTheForceModel() {
        double[] fromStarboardBeam = wind(90, false);
        assertEquals(90.0, BoomRules.beta(fromStarboardBeam[0], fromStarboardBeam[1]), 1e-9);
        assertEquals(1, BoomRules.leeward(fromStarboardBeam[0], fromStarboardBeam[1]), "from starboard it blows to port");
        double[] ahead = wind(0, false);
        assertEquals(0.0, BoomRules.beta(ahead[0], ahead[1]), 1e-9);
        assertEquals(0, BoomRules.leeward(ahead[0], ahead[1]));
        double[] astern = wind(180, true);
        assertEquals(180.0, BoomRules.beta(astern[0], astern[1]), 1e-9);
    }

    @Test
    void theBestAngleRunsFromCentredCloseHauledToAllTheWayOutOnARun() {
        assertEquals(0.0, BoomRules.bestAngle(45, MIN));
        assertEquals(15.0, BoomRules.bestAngle(60, MIN));
        assertEquals(45.0, BoomRules.bestAngle(90, MIN));
        assertEquals(60.0, BoomRules.bestAngle(120, MIN));
        assertEquals(60.0, BoomRules.bestAngle(180, MIN));
    }

    @Test
    void aBeamReachDrawsFullyWithTheBoomFortyFiveOutToLeewardOnEitherTack() {
        for (boolean fromPort : new boolean[] {false, true}) {
            double[] w = wind(90, fromPort);
            int best = BoomRules.bestStep(w[0], w[1], AFT_PORT, AFT_FWD, BoomRules.CENTRE, MIN);
            // to leeward: from starboard the boom goes to port (positive sheet on an aft boom), from port to starboard
            assertEquals(fromPort ? BoomRules.CENTRE - 3 : BoomRules.CENTRE + 3, best, "tack from port " + fromPort);
            assertEquals(1.0, BoomRules.factorAt(w[0], w[1], AFT_PORT, AFT_FWD, best, MIN), 1e-9);
        }
    }

    @Test
    void theBoomOnTheWindwardSideIsAbackAndDrawsNothing() {
        double[] w = wind(90, false); // from starboard, leeward port
        int windward = BoomRules.CENTRE - 3; // out to starboard
        double[] b = BoomRules.turn(AFT_PORT, AFT_FWD, BoomRules.angle(windward));
        assertEquals(-1, BoomRules.side(b[0], b[1]));
        assertTrue(BoomRules.aback(w[0], w[1], b[0], b[1]));
        assertEquals(0.0, BoomRules.factor(w[0], w[1], b[0], b[1], MIN));
        // centred is never aback
        assertFalse(BoomRules.aback(w[0], w[1], AFT_PORT, AFT_FWD));
    }

    @Test
    void tooCloseToTheWindItLuffs() {
        for (double beta : new double[] {0, 20, 44.9}) {
            double[] w = wind(beta, false);
            for (int step = 0; step < BoomRules.STEPS; step++) {
                assertEquals(0.0, BoomRules.factorAt(w[0], w[1], AFT_PORT, AFT_FWD, step, MIN), "beta " + beta + " step " + step);
            }
            assertEquals(BoomRules.CENTRE, BoomRules.bestStep(w[0], w[1], AFT_PORT, AFT_FWD, 7, MIN), "hauled in head to wind");
        }
        double[] closeHauled = wind(45, false);
        assertEquals(1.0, BoomRules.factorAt(closeHauled[0], closeHauled[1], AFT_PORT, AFT_FWD, BoomRules.CENTRE, MIN), 1e-9);
        // the config moves the limit
        double[] fifty = wind(50, false);
        assertEquals(0.0, BoomRules.factorAt(fifty[0], fifty[1], AFT_PORT, AFT_FWD, BoomRules.CENTRE, 55.0));
    }

    @Test
    void sheetedFarOutOnACloseReachTheClothLuffs() {
        double[] w = wind(60, false); // from starboard, best 15 out to port
        // all the way out to port, 60: the wind runs along the cloth
        assertEquals(0.0, BoomRules.factorAt(w[0], w[1], AFT_PORT, AFT_FWD, 8, MIN));
        assertTrue(BoomRules.factorAt(w[0], w[1], AFT_PORT, AFT_FWD, 5, MIN) > 0.99);
    }

    @Test
    void theFactorFallsOffWithTheSheetsDistanceFromItsBest() {
        double[] w = wind(90, false);
        double best = BoomRules.factorAt(w[0], w[1], AFT_PORT, AFT_FWD, 7, MIN);
        double one = BoomRules.factorAt(w[0], w[1], AFT_PORT, AFT_FWD, 6, MIN);
        double two = BoomRules.factorAt(w[0], w[1], AFT_PORT, AFT_FWD, 5, MIN);
        double three = BoomRules.factorAt(w[0], w[1], AFT_PORT, AFT_FWD, 4, MIN);
        assertEquals(1.0, best, 1e-9);
        assertEquals(1.0 - 0.0625, one, 1e-9, "one step off: 1 - (15/60)^2");
        assertTrue(best > one && one > two && two > three && three > 0.0, best + " " + one + " " + two + " " + three);
    }

    @Test
    void deadDownwindEitherSideDrawsAndByTheLeeStillDraws() {
        double[] run = wind(180, true);
        assertEquals(1.0, BoomRules.factorAt(run[0], run[1], AFT_PORT, AFT_FWD, 8, MIN), 1e-9);
        assertEquals(1.0, BoomRules.factorAt(run[0], run[1], AFT_PORT, AFT_FWD, 0, MIN), 1e-9);
        // by the lee: the wind 5 degrees past dead astern on the boom's side (boom to port, wind from port at 175)
        double[] byTheLee = wind(175, true);
        assertTrue(BoomRules.factorAt(byTheLee[0], byTheLee[1], AFT_PORT, AFT_FWD, 8, MIN) > 0.9);
        assertEquals(BoomRules.Swing.NONE, BoomRules.swing(byTheLee[0], byTheLee[1], AFT_PORT, AFT_FWD, 8, MIN));
    }

    @Test
    void theWindCrossingTheSternGybesTheBoomAndCrossingTheBowTacksIt() {
        // the boom out to port (positive sheet), the wind from the port quarter beyond the by-the-lee margin: a gybe
        double[] quarter = wind(150, true);
        assertEquals(BoomRules.Swing.GYBE, BoomRules.swing(quarter[0], quarter[1], AFT_PORT, AFT_FWD, 8, MIN));
        assertEquals(0, BoomRules.mirror(8));
        // after the gybe the boom draws on the new side
        assertTrue(BoomRules.factorAt(quarter[0], quarter[1], AFT_PORT, AFT_FWD, 0, MIN) > 0.99);
        // the wind on the same side forward of the beam (she tacked): a quiet swing
        double[] reach = wind(70, true);
        assertEquals(BoomRules.Swing.TACK, BoomRules.swing(reach[0], reach[1], AFT_PORT, AFT_FWD, 6, MIN));
        // head to wind the boom only flaps; centred nothing happens; to leeward nothing happens
        double[] irons = wind(10, true);
        assertEquals(BoomRules.Swing.NONE, BoomRules.swing(irons[0], irons[1], AFT_PORT, AFT_FWD, 6, MIN));
        assertEquals(BoomRules.Swing.NONE, BoomRules.swing(quarter[0], quarter[1], AFT_PORT, AFT_FWD, BoomRules.CENTRE, MIN));
        assertEquals(BoomRules.Swing.NONE, BoomRules.swing(quarter[0], quarter[1], AFT_PORT, AFT_FWD, 0, MIN));
    }

    @Test
    void aForwardBoomFollowsTheSameRules() {
        // a club-footed jib: the boom points forward; from starboard its end must go to port (a negative sheet)
        double[] w = wind(90, false);
        int best = BoomRules.bestStep(w[0], w[1], 0, 1, BoomRules.CENTRE, MIN);
        double[] b = BoomRules.turn(0, 1, BoomRules.angle(best));
        assertEquals(1, BoomRules.side(b[0], b[1]), "to port");
        assertEquals(BoomRules.CENTRE - 3, best);
    }

    @Test
    void autoTrimFromAnyStepReachesTheBestStepOneAtATime() {
        double[] w = wind(100, true); // from port: boom to starboard (negative sheet), 55 out: step 0 or 1
        int best = BoomRules.bestStep(w[0], w[1], AFT_PORT, AFT_FWD, BoomRules.CENTRE, MIN);
        assertTrue(best < BoomRules.CENTRE, "to starboard: " + best);
        for (int start = BoomRules.CENTRE; start < BoomRules.STEPS; start++) {
            int s = start;
            int n = 0;
            while (s != best && n < 20) {
                s = BoomRules.stepToward(s, best);
                n++;
            }
            assertEquals(best, s);
            assertEquals(Math.abs(start - best), n);
        }
    }

    @Test
    void aClickPushesTheBoomTowardItsSide() {
        // a boom pointing east: a click on its south side (clockwise of it) sheets +1, north -1, on its line 0
        assertEquals(1, BoomRules.clickSide(1, 0, 0.3, 0.4));
        assertEquals(-1, BoomRules.clickSide(1, 0, 0.3, -0.4));
        assertEquals(0, BoomRules.clickSide(1, 0, 0.3, 0.0));
        // and the step +1 turns it toward the click: east turned clockwise moves its end south
        double[] e = BoomRules.turn(1, 0, BoomRules.STEP_DEGREES);
        assertTrue(e[1] > 0);
    }

    @Test
    void aBoomedSailKeepsItsAreaAndSwingsItsFootAndCentroid() {
        BlockPoint head = new BlockPoint(0, 10, 0), tack = new BlockPoint(0, 4, -5), boom = new BlockPoint(0, 5, 0);
        TriangularSail s = new TriangularSail(head, tack, boom, true);
        assertEquals(0.5 * 5 * 5, s.area(), 1e-9, "drop 5 times the tack's 5 out, halved");
        assertArrayEquals(new double[] {0, -5, -5}, s.boomEnd(0), 1e-9);
        double[] out = s.boomEnd(90); // north (0, -1) clockwise to east (1, 0)
        assertArrayEquals(new double[] {5, -5, 0}, out, 1e-9);
        double[] c = s.centroid(com.richardsenger.piratesnships.sailing.force.SailTrim.FULL, 90);
        assertArrayEquals(new double[] {5 / 3.0, -10 / 3.0, 0}, c, 1e-9);
        TriangleCloth g = s.geometry();
        assertTrue(g.boomed());
        float[] corner = g.corner(90);
        assertEquals(5f, corner[0], 1e-6f);
        assertEquals(-5f, corner[1], 1e-6f);
        assertEquals(0f, corner[2], 1e-6f);
        // without a boom the corner is the tack
        TriangleCloth jib = new TriangularSail(head, tack, new BlockPoint(0, 5, 0)).geometry();
        assertArrayEquals(new float[] {0, -6, -5}, jib.corner(90), 1e-6f);
    }
}
