package com.richardsenger.piratesnships.sailing.ballast;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** TRM1: the shipwright's ballast. Bow is +z, so a torque about +x pushes the bow down (the stern up). */
class BallastRuleTest {

    private static final double G = 11.0;
    private static final BallastRule.Params ON = BallastRule.Params.DEFAULTS;

    /** A solid plank box {@code sx × sy × sz} from the origin: 0.5 kpg and volume 1 per block. */
    private static BallastRule.Hull box(int sx, int sy, int sz) {
        BallastRule.Hull h = new BallastRule.Hull();
        for (int x = 0; x < sx; x++)
            for (int y = 0; y < sy; y++)
                for (int z = 0; z < sz; z++)
                    h.block(x + 0.5, y + 0.5, z + 0.5, 1.0, 0.5);
        return h;
    }

    @Test
    void symmetricBoxFloatsWithZeroMoment() {
        BallastRule.Result r = BallastRule.solve(box(5, 2, 9), G, ON);
        assertEquals(45.0, r.mass(), 1e-12);
        assertEquals(r.mass() * G, r.buoyancy(), 1e-6);
        assertEquals(0.0, r.torqueX(), 1e-9);
        assertEquals(0.0, r.torqueZ(), 1e-9);
        assertEquals(2.5, r.cob()[0], 1e-9);
        assertEquals(4.5, r.cob()[2], 1e-9);
        assertFalse(r.capped());
    }

    @Test
    void draftSolve() {
        // W = 45 · 11 = 495; one full layer (45 blocks) floats 472.5, so the waterline is 22.5 / 472.5 into the second
        assertEquals(1.0 + 22.5 / 472.5, BallastRule.waterline(box(5, 2, 9), G), 1e-9);
        // a light box floats in its bottom layer: 0.5 · 11 / 10.5 deep
        assertEquals(0.5 * G / 10.5, BallastRule.waterline(box(3, 1, 3), G), 1e-9);
        // a box too heavy to float sits with its top at the waterline (fully submerged, B at its maximum)
        BallastRule.Hull lead = new BallastRule.Hull().block(0.5, 0.5, 0.5, 1.0, 4.0);
        BallastRule.Result r = BallastRule.solve(lead, G, ON);
        assertEquals(1.0, r.waterline(), 1e-9);
        assertEquals(10.5, r.buoyancy(), 1e-9);
    }

    @Test
    void extraMassAftGivesBowUpMomentAndBowDownBallast() {
        BallastRule.Hull h = box(5, 2, 9);
        h.block(2.5, 2.5, 0.5, 0.0, 10.0); // 10 kpg on deck at the stern end (z = 0.5), no buoyancy of its own
        BallastRule.Result r = BallastRule.solve(h, G, ON);
        double mass = 55.0;
        double comZ = (45.0 * 4.5 + 10.0 * 0.5) / mass;
        assertEquals(comZ, r.com()[2], 1e-12);
        assertEquals(4.5, r.cob()[2], 1e-9); // the box floats evenly at the level pose
        double b = mass * G;
        assertEquals(b, r.buoyancy(), 1e-6);
        // static moment −r_z · B about x: negative = bow up (the stern-heavy ship sits stern down)
        assertTrue(r.staticMomentX() < 0.0);
        assertEquals(-(4.5 - comZ) * b, r.staticMomentX(), 1e-6);
        // the ballast is its negative: bow down, magnitude B × the 0.727-block offset
        assertEquals((4.5 - comZ) * b, r.torqueX(), 1e-6);
        assertEquals(0.0, r.torqueZ(), 1e-9);
        assertEquals(4.5 - comZ, r.offset(), 1e-9);
    }

    @Test
    void extraMassToOneSideGivesARollBallast() {
        BallastRule.Hull h = box(5, 2, 9);
        h.block(4.5, 2.5, 4.5, 0.0, 5.0); // 5 kpg at +x
        BallastRule.Result r = BallastRule.solve(h, G, ON);
        double comX = (45.0 * 2.5 + 5.0 * 4.5) / 50.0;
        // τ_z = −(cob_x − com_x) · B: positive, rotating +x up again
        assertEquals((comX - 2.5) * 50.0 * G, r.torqueZ(), 1e-6);
        assertEquals(0.0, r.torqueX(), 1e-9);
    }

    @Test
    void capLimitsTheTorqueAndKeepsItsDirection() {
        BallastRule.Hull h = box(5, 2, 9);
        h.block(4.5, 2.5, 0.5, 0.0, 10.0);
        BallastRule.Result free = BallastRule.solve(h, G, ON);
        BallastRule.Result capped = BallastRule.solve(h, G, new BallastRule.Params(true, 0.1));
        assertTrue(capped.capped());
        assertFalse(free.capped());
        assertEquals(0.1 * 55.0 * G, capped.torqueMagnitude(), 1e-9);
        assertEquals(Math.atan2(free.torqueZ(), free.torqueX()), Math.atan2(capped.torqueZ(), capped.torqueX()), 1e-12);
    }

    @Test
    void offGivesZero() {
        BallastRule.Hull h = box(5, 2, 9);
        h.block(2.5, 2.5, 0.5, 0.0, 10.0);
        BallastRule.Result r = BallastRule.solve(h, G, new BallastRule.Params(false, 3.0));
        assertEquals(0.0, r.torqueX());
        assertEquals(0.0, r.torqueZ());
        assertEquals(BallastRule.Result.NONE, r);
        assertEquals(BallastRule.Result.NONE, BallastRule.solve(new BallastRule.Hull(), G, ON));
    }

    @Test
    void dryCellsLiftBelowTheirSurfaceAndMoveTheCentreOfBuoyancy() {
        // a 3-wide hollow hull: floor y 0..1, the dry hold cells at y 1..2 only at z < 2 (a hold aft)
        BallastRule.Hull h = new BallastRule.Hull();
        for (int x = 0; x < 3; x++)
            for (int z = 0; z < 4; z++)
                h.block(x + 0.5, 0.5, z + 0.5, 1.0, 1.0);
        double solidOnly = BallastRule.waterline(h, G);
        // 12 kpg → 132 N; the 12 floor blocks give at most 126 N: without the hold it cannot float
        assertEquals(1.0, solidOnly, 1e-9);
        for (int x = 0; x < 3; x++)
            for (int z = 0; z < 2; z++)
                h.dryCell(x + 0.5, 1.5, z + 0.5, 10.5);
        double yw = BallastRule.waterline(h, G);
        // the dry cells start lifting DRY_SURFACE_DROP higher: 6 cells must give 6 N at 1/9 + d
        assertEquals(1.0 + BallastRule.DRY_SURFACE_DROP + 6.0 / (6 * 10.5), yw, 1e-9);
        BallastRule.Result r = BallastRule.solve(h, G, ON);
        assertTrue(r.cob()[2] < 2.0, "the aft hold pulls the centre of buoyancy aft: " + r);
        // so the ballast lifts the bow: a torque about −x
        assertTrue(r.torqueX() < 0.0, r.toString());
    }

    @Test
    void cubeCentresNotMassCentresCarryTheBuoyancy() {
        // a slab-like block: buoyancy at the cube centre, mass at its lower half's centre; level by symmetry in x and z
        BallastRule.Hull h = new BallastRule.Hull().block(0.5, 0.5, 0.5, 0.5, 0.25, 0.5, 0.25, 0.5);
        BallastRule.Result r = BallastRule.solve(h, G, ON);
        assertEquals(0.25, r.com()[1], 1e-12);
        assertEquals(0.5, r.cob()[1], 1e-12);
        assertEquals(0.0, r.torqueMagnitude(), 1e-12);
    }
}
