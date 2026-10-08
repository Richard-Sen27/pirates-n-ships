package com.richardsenger.piratesnships.sailing.anchor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

/** The chain's force (AN2a): taut test, spring-damper, horizontal only, the holding cap and dragging. */
class AnchorChainTest {

    private static final AnchorChain.Params P = new AnchorChain.Params(4.0, 3.0, 2.0, 0.2);
    private static final double FLAT = 1.0;
    private static final double MASS = 100.0;
    private static final Vector3d HAWSE = new Vector3d(0, 10, 0);
    private static final Vector3d STILL = new Vector3d();

    @Test
    void tautOnlyWhenTheDistanceReachesThePaidOutLength() {
        assertFalse(AnchorChain.taut(9.0, 10.0));
        assertTrue(AnchorChain.taut(9.96, 10.0));
        assertTrue(AnchorChain.taut(10.5, 10.0));
    }

    @Test
    void slackChainPullsNothingButTheSettleDrag() {
        Vector3d ring = new Vector3d(0, 2, -5);
        AnchorChain.Result r = AnchorChain.force(HAWSE, ring, 12.0, STILL, MASS, P);
        assertFalse(r.taut());
        assertEquals(0.0, r.force().length(), 1e-12);
        AnchorChain.Result moving = AnchorChain.force(HAWSE, ring, 12.0, new Vector3d(0, 0, 1), MASS, P);
        assertEquals(-0.2 * MASS, moving.force().z, 1e-9, "the chain on the seabed brakes mildly");
    }

    @Test
    void tautChainPullsHorizontallyTowardTheAnchor() {
        // anchor 8 astern (−z) and 6 below: distance 10, paid out 9.9 → stretch 0.1
        Vector3d ring = new Vector3d(0, 4, -8);
        AnchorChain.Result r = AnchorChain.force(HAWSE, ring, 9.9, STILL, MASS, P);
        assertTrue(r.taut());
        assertEquals(0.1, r.stretch(), 1e-9);
        double pull = 4.0 * 0.1 * MASS;
        assertEquals(0.0, r.force().y, 1e-12, "the chain never pulls the ship down");
        assertEquals(-pull * 0.8, r.force().z, 1e-9, "the horizontal part of the pull along the chain");
        assertEquals(0.0, r.force().x, 1e-12);
        assertFalse(r.capped());
    }

    @Test
    void dampingAddsWhileTheShipMovesAwayAndNeverPushes() {
        Vector3d ring = new Vector3d(0, 4, -8);
        Vector3d away = new Vector3d(0, 0, 0.5); // moving +z, away from the anchor
        AnchorChain.Result r = AnchorChain.force(HAWSE, ring, 9.9, away, MASS, P);
        double w = 0.5 * 0.8;
        double expected = (4.0 * 0.1 + 3.0 * w) * MASS * 0.8 + 0.2 * 0.5 * MASS;
        assertEquals(-expected, r.force().z, 1e-9);
        AnchorChain.Result toward = AnchorChain.force(HAWSE, ring, 9.9, new Vector3d(0, 0, -2.0), MASS, P);
        assertTrue(toward.force().z > 0.0, "only the settle drag acts while the ship closes on its anchor fast: " + toward.force());
    }

    @Test
    void holdingPowerCapsThePull() {
        Vector3d ring = new Vector3d(0, 10, -20);
        AnchorChain.Result r = AnchorChain.force(HAWSE, ring, 10.0, new Vector3d(0, 0, 4), MASS, P);
        assertTrue(r.capped());
        assertEquals(2.0 * MASS, r.force().length(), 1e-9, "capped at holding × displacement");
        AnchorChain.Result none = AnchorChain.force(HAWSE, ring, 10.0, new Vector3d(0, 0, 4), MASS,
                new AnchorChain.Params(4.0, 3.0, 0.0, 0.2));
        assertEquals(0.0, none.force().length(), 1e-12, "no holding power, no pull");
    }

    @Test
    void forceScalesWithDisplacement() {
        Vector3d ring = new Vector3d(0, 4, -8);
        double light = AnchorChain.force(HAWSE, ring, 9.9, STILL, 50, P).force().length();
        double heavy = AnchorChain.force(HAWSE, ring, 9.9, STILL, 500, P).force().length();
        assertEquals(10.0, heavy / light, 1e-9);
    }

    @Test
    void dragsOnlyBeyondTheHoldingStretchAtTheScrapeRateWithinTheSlack() {
        // holding 2 / stiffness 4: a flat chain's spring reaches the cap at 0.5 blocks of stretch
        assertEquals(0.0, AnchorChain.dragExcess(0.4, FLAT, P), 1e-12);
        assertEquals(0.3, AnchorChain.dragExcess(0.8, FLAT, P), 1e-12);
        assertEquals(0.0, AnchorChain.dragStep(-1.0, FLAT, P, 1.0, 0.05), 1e-12);
        assertEquals(0.05, AnchorChain.dragStep(0.8, FLAT, P, 1.0, 0.05), 1e-12, "rate limited");
        assertEquals(0.02, AnchorChain.dragStep(0.52, FLAT, P, 1.0, 0.05), 1e-12, "the excess only");
        assertEquals(0.5, AnchorChain.dragStep(2.0, FLAT, P, 1.0, 0.05), 1e-12, "past the slack it keeps the excess at DRAG_SLACK");
        AnchorChain.Params zero = new AnchorChain.Params(4.0, 3.0, 0.0, 0.2);
        assertEquals(0.3, AnchorChain.dragExcess(0.3, FLAT, zero), 1e-12, "no holding power: any stretch drags");
    }

    @Test
    void aSteepChainDragsLaterAndAVerticalOneNever() {
        // 0.5 horizontal (60° down): the horizontal pull reaches the cap only at 1 block of stretch
        assertEquals(0.0, AnchorChain.dragExcess(0.8, 0.5, P), 1e-12);
        assertEquals(0.2, AnchorChain.dragExcess(1.2, 0.5, P), 1e-12);
        assertEquals(0.0, AnchorChain.dragExcess(5.0, 0.0, P), 1e-12, "straight up and down it only lifts");
    }
}
