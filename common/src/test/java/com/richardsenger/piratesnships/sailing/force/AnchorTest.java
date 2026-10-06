package com.richardsenger.piratesnships.sailing.force;

import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnchorTest {

    private static final SailingParams.AnchorParams A = SailingParams.AnchorParams.DEFAULTS;
    private static final Vector3dc HAWSE = new Vector3d(0, 0, 8);
    private static final AnchorState HOLDING = new AnchorState(AnchorState.Phase.HOLDING, 1.0);

    private static AnchorState ticks(AnchorState s, int n) {
        for (int i = 0; i < n; i++) {
            s = s.tick(A);
        }
        return s;
    }

    @Test
    void stateMachineRunsThroughAllPhasesWithConfiguredDurations() {
        AnchorState s = AnchorState.RAISED.drop();
        assertEquals(AnchorState.Phase.DROPPING, s.phase());
        assertEquals(AnchorState.Phase.DROPPING, ticks(s, A.dropTicks() - 1).phase());
        s = ticks(s, A.dropTicks());
        assertEquals(HOLDING, s);
        s = s.raise();
        assertEquals(AnchorState.Phase.RAISING, s.phase());
        assertEquals(AnchorState.Phase.RAISING, ticks(s, A.raiseTicks() - 1).phase());
        assertEquals(AnchorState.RAISED, ticks(s, A.raiseTicks()));
    }

    @Test
    void commandsAreIdempotentAndReversalsKeepTheHold() {
        assertEquals(AnchorState.RAISED, AnchorState.RAISED.raise());
        assertEquals(HOLDING, HOLDING.drop());
        assertEquals(AnchorState.RAISED, ticks(AnchorState.RAISED, 50));
        assertEquals(HOLDING, ticks(HOLDING, 50));
        AnchorState half = ticks(AnchorState.RAISED.drop(), A.dropTicks() / 2);
        AnchorState reversed = half.raise();
        assertEquals(half.hold(), reversed.hold(), 1e-12);
        assertEquals(AnchorState.Phase.RAISING, reversed.phase());
        AnchorState again = reversed.tick(A).drop();
        assertEquals(AnchorState.Phase.DROPPING, again.phase());
        assertTrue(again.isOut());
        assertFalse(AnchorState.RAISED.isOut());
    }

    @Test
    void holdRampsSmoothly() {
        AnchorState s = AnchorState.RAISED.drop();
        double prev = s.hold();
        for (int i = 0; i < A.dropTicks() + 5; i++) {
            s = s.tick(A);
            assertTrue(s.hold() - prev <= 1.0 / A.dropTicks() + 1e-9);
            prev = s.hold();
        }
    }

    @Test
    void noForceWhenRaised() {
        ShipState ship = ShipState.atRest(100, 20).withPosition(new Vector3d(50, 0, 0));
        assertEquals(0.0, AnchorModel.compute(AnchorState.RAISED, new Vector3d(), HAWSE, ship, A).force().length());
    }

    @Test
    void pullsTowardTheAnchorBeyondTheSlackOnlyHorizontally() {
        ShipState ship = ShipState.atRest(100, 20).withPosition(new Vector3d(10, 0, -8));
        Vector3d anchor = new Vector3d(0, -20, 0);
        ForceContribution c = AnchorModel.compute(HOLDING, anchor, HAWSE, ship, A);
        Vector3d world = ship.toWorld(c.force(), new Vector3d());
        assertEquals(0.0, world.y, 1e-12);
        assertTrue(world.x < 0, "should pull toward the anchor (−x)");
        assertEquals(0.0, world.z, 1e-9);
        assertEquals(100 * A.stiffness() * (10 - A.slack()), world.length(), 1e-9);
        // Inside the slack: no pull at rest.
        ShipState near = ShipState.atRest(100, 20).withPosition(new Vector3d(1, 0, -8));
        assertEquals(0.0, AnchorModel.compute(HOLDING, anchor, HAWSE, near, A).force().length(), 1e-12);
    }

    @Test
    void dampsMotionScalesWithHoldAndIsCapped() {
        ShipState drifting = ShipState.atRest(100, 20).withPosition(new Vector3d(0, 0, -8)).withLinearVelocity(new Vector3d(2, 0, 0));
        Vector3d world = drifting.toWorld(AnchorModel.compute(HOLDING, new Vector3d(), HAWSE, drifting, A).force(), new Vector3d());
        assertEquals(-100 * A.damping() * 2, world.x, 1e-9);
        AnchorState half = new AnchorState(AnchorState.Phase.DROPPING, 0.5);
        assertEquals(0.5, AnchorModel.compute(half, new Vector3d(), HAWSE, drifting, A).force().length() / world.length(), 1e-9);
        ShipState far = ShipState.atRest(100, 20).withPosition(new Vector3d(1000, 0, 0)).withLinearVelocity(new Vector3d(30, 0, 0));
        assertEquals(100 * A.maxAcceleration(), AnchorModel.compute(HOLDING, new Vector3d(), HAWSE, far, A).force().length(), 1e-6);
    }

    @Test
    void holdsAShipAgainstASteadyPush() {
        // 1D simulation: constant push of 3 blocks/s² (below the cap) against the anchor.
        double m = 100;
        double x = 0;
        double v = 0;
        double dt = 0.05;
        double maxX = 0;
        for (int i = 0; i < 20 * 120; i++) {
            ShipState s = ShipState.atRest(m, 20).withPosition(new Vector3d(x, 0, -8)).withLinearVelocity(new Vector3d(v, 0, 0));
            double f = s.toWorld(AnchorModel.compute(HOLDING, new Vector3d(), HAWSE, s, A).force(), new Vector3d()).x + 3 * m;
            v += f / m * dt;
            x += v * dt;
            maxX = Math.max(maxX, x);
        }
        double expected = A.slack() + 3 / A.stiffness();
        assertEquals(expected, x, 0.05, "should settle where the rode balances the push");
        assertTrue(maxX < expected * 1.1, "strong damping: little overshoot, max " + maxX);
        assertTrue(Math.abs(v) < 1e-3);
    }
}
