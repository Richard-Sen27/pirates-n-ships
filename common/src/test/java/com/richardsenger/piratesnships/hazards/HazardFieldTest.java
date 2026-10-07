package com.richardsenger.piratesnships.hazards;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HazardFieldTest {

    private static final double EPS = 1e-9;
    private static final HazardField.Spout SPOUT = new HazardField.Spout(8, 24, 0.08, 0.12);
    private static final HazardField.Pool POOL = new HazardField.Pool(12, 0.05, 0.04, 0.08);

    @Test
    void falloffIsLinearToTheEdge() {
        assertEquals(1.0, HazardField.falloff(0, 8), EPS);
        assertEquals(0.5, HazardField.falloff(4, 8), EPS);
        assertEquals(0.25, HazardField.falloff(6, 8), EPS);
        assertEquals(0.0, HazardField.falloff(8, 8), EPS);
        assertEquals(0.0, HazardField.falloff(20, 8), EPS);
        assertEquals(0.0, HazardField.falloff(1, 0), EPS, "no radius, no field");
    }

    @Test
    void liftFadesWithHeight() {
        assertEquals(1.0, HazardField.heightFade(-1, 24), EPS);
        assertEquals(1.0, HazardField.heightFade(0, 24), EPS);
        assertEquals(0.5, HazardField.heightFade(12, 24), EPS);
        assertEquals(0.0, HazardField.heightFade(24, 24), EPS);
        assertEquals(0.0, HazardField.heightFade(30, 24), EPS);
    }

    @Test
    void waterspoutPullsTowardTheAxisAndLifts() {
        HazardField.Vec a = HazardField.waterspout(4, 0, 0, SPOUT);
        assertEquals(-0.04, a.x(), EPS, "half the pull, toward -x");
        assertEquals(0.0, a.z(), EPS);
        assertEquals(0.06, a.y(), EPS, "half the lift at the water line");

        HazardField.Vec diagonal = HazardField.waterspout(-3, 12, -4, SPOUT); // r = 5
        double f = 1 - 5.0 / 8;
        assertEquals(0.08 * f * 3 / 5, diagonal.x(), EPS);
        assertEquals(0.08 * f * 4 / 5, diagonal.z(), EPS);
        assertEquals(0.12 * f * 0.5, diagonal.y(), EPS, "lift halved halfway up the funnel");
    }

    @Test
    void waterspoutOnTheAxisOnlyLiftsAndOutsideDoesNothing() {
        HazardField.Vec centre = HazardField.waterspout(0, 0, 0, SPOUT);
        assertEquals(0.0, centre.horizontalLength(), EPS);
        assertEquals(0.12, centre.y(), EPS);
        assertEquals(HazardField.Vec.ZERO, HazardField.waterspout(8, 0, 0, SPOUT));
        assertEquals(HazardField.Vec.ZERO, HazardField.waterspout(0, 0, 9, SPOUT));
        assertEquals(0.0, HazardField.waterspout(2, 24, 0, SPOUT).y(), EPS, "no lift at the funnel top");
    }

    @Test
    void whirlpoolPullsAndSpinsCounterClockwise() {
        // east of the centre: pull west (-x), counter-clockwise seen from above (x east, z south) is north (-z)
        HazardField.Vec a = HazardField.whirlpool(6, 0, false, POOL);
        assertEquals(-0.025, a.x(), EPS);
        assertEquals(-0.02, a.z(), EPS);
        assertEquals(0.0, a.y(), EPS);
        // north of the centre (-z): pull south (+z), spin west (-x)
        HazardField.Vec b = HazardField.whirlpool(0, -6, false, POOL);
        assertEquals(-0.02, b.x(), EPS);
        assertEquals(0.025, b.z(), EPS);
        // the spin is perpendicular to the pull
        HazardField.Vec t = HazardField.tangent(3, 4);
        HazardField.Vec in = HazardField.inward(3, 4);
        assertEquals(0.0, t.x() * in.x() + t.z() * in.z(), EPS);
        assertEquals(1.0, t.horizontalLength(), EPS);
    }

    @Test
    void whirlpoolDragsBoatsAndSwimmersDownOnlyInTheInnerThird() {
        assertEquals(-0.08, HazardField.whirlpool(3.9, 0, true, POOL).y(), EPS, "inner third (r < 4)");
        assertEquals(0.0, HazardField.whirlpool(4.0, 0, true, POOL).y(), EPS, "at a third of the radius");
        assertEquals(0.0, HazardField.whirlpool(2, 0, false, POOL).y(), EPS, "not a boat or swimmer");
        assertEquals(-0.08, HazardField.whirlpool(0, 0, true, POOL).y(), EPS, "at the centre");
        assertEquals(HazardField.Vec.ZERO, HazardField.whirlpool(12, 0, true, POOL));
    }

    @Test
    void liftIsCappedAtTheMaximumUpwardSpeed() {
        assertEquals(0.3, HazardField.applyLift(0.2, 0.1, 0.6), EPS);
        assertEquals(0.6, HazardField.applyLift(0.55, 0.1, 0.6), EPS, "capped");
        assertEquals(0.8, HazardField.applyLift(0.8, 0.1, 0.6), EPS, "a faster entity is not slowed");
        assertEquals(0.7, HazardField.applyLift(0.8, -0.1, 0.6), EPS, "downward pushes always apply");
        assertEquals(-0.5, HazardField.applyLift(-0.6, 0.1, 0.6), EPS, "a falling entity is braked");
    }

    @Test
    void massCapLimitsTheEffectOnHeavyShips() {
        assertEquals(1.0, HazardField.massEffect(50, 400), EPS);
        assertEquals(1.0, HazardField.massEffect(400, 400), EPS);
        assertEquals(0.25, HazardField.massEffect(1600, 400), EPS);
        assertEquals(0.0, HazardField.massEffect(0, 400), EPS);

        HazardField.Vec a = new HazardField.Vec(0.05, 0.1, 0);
        HazardField.Vec light = HazardField.shipImpulse(a, 50, 400, 1.0);
        assertEquals(2.5, light.x(), EPS, "impulse = a × mass below the cap");
        HazardField.Vec heavy = HazardField.shipImpulse(a, 1600, 400, 1.0);
        assertEquals(20.0, heavy.x(), EPS, "impulse = a × cap above it");
        assertEquals(heavy.x() / 1600, a.x() * 0.25, EPS, "so the heavy ship's velocity changes by a quarter");
        assertEquals(40.0, HazardField.shipImpulse(a, 1600, 400, 2.0).x(), EPS, "scaled");
        assertEquals(0.0, HazardField.shipImpulse(a, 1600, 400, 0.0).x(), EPS);
    }

    @Test
    void particleCountFollowsTheDensity() {
        assertEquals(14, HazardVisuals.count(14, 1.0, 0.99));
        assertEquals(0, HazardVisuals.count(14, 0.0, 0.0));
        assertEquals(7, HazardVisuals.count(14, 0.5, 0.5));
        assertEquals(2, HazardVisuals.count(1.5, 1.0, 0.4), "the fraction rounds up when the roll is below it");
        assertEquals(1, HazardVisuals.count(1.5, 1.0, 0.6));
        assertTrue(HazardVisuals.count(14, 4.0, 0.0) == 56);
    }
}
