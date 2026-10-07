package com.richardsenger.piratesnships.sailing.helm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.richardsenger.piratesnships.sailing.helm.SessionRules.End;
import org.junit.jupiter.api.Test;

/** Pure logic of HELM1: wheel integration and clamping, lock to lock, wheel → rudder, input accumulation, session ends. */
class WheelLogicTest {

    private static final double EPS = 1e-9;

    // ------------------------------------------------------------------ locks and rudder mapping

    @Test
    void lockAngleIsHalfTheTurnsLockToLock() {
        assertEquals(270.0, WheelMath.lockAngle(1.5), EPS);
        assertEquals(180.0, WheelMath.lockAngle(1.0), EPS);
        assertEquals(0.0, WheelMath.lockAngle(-2.0), EPS);
    }

    @Test
    void wheelIsClampedAtTheLocks() {
        assertEquals(270.0, WheelMath.clampWheel(400.0, 270.0), EPS);
        assertEquals(-270.0, WheelMath.clampWheel(-1e9, 270.0), EPS);
        assertEquals(12.5, WheelMath.clampWheel(12.5, 270.0), EPS);
        assertEquals(0.0, WheelMath.clampWheel(Double.NaN, 270.0), EPS);
    }

    @Test
    void rudderFollowsTheWheelLinearly() {
        assertEquals(0.0, WheelMath.rudderAngle(0.0, 270.0, 35.0), EPS);
        assertEquals(35.0, WheelMath.rudderAngle(270.0, 270.0, 35.0), EPS);
        assertEquals(-35.0, WheelMath.rudderAngle(-270.0, 270.0, 35.0), EPS);
        assertEquals(17.5, WheelMath.rudderAngle(135.0, 270.0, 35.0), EPS);
        assertEquals(-35.0 / 3, WheelMath.rudderAngle(-90.0, 270.0, 35.0), EPS);
        // past the lock the rudder stays at its maximum; no lock means no rudder
        assertEquals(35.0, WheelMath.rudderAngle(999.0, 270.0, 35.0), EPS);
        assertEquals(0.0, WheelMath.rudderAngle(90.0, 0.0, 35.0), EPS);
    }

    @Test
    void wheelForFractionShowsClickSteps() {
        assertEquals(90.0, WheelMath.wheelForFraction(1.0 / 3, 270.0), EPS);
        assertEquals(-270.0, WheelMath.wheelForFraction(-1.0, 270.0), EPS);
        assertEquals(270.0, WheelMath.wheelForFraction(2.0, 270.0), EPS);
    }

    @Test
    void sideOfTheRudder() {
        assertEquals(WheelMath.Side.MIDSHIPS, WheelMath.side(0.0));
        assertEquals(WheelMath.Side.MIDSHIPS, WheelMath.side(0.4));
        assertEquals(WheelMath.Side.MIDSHIPS, WheelMath.side(-0.4));
        assertEquals(WheelMath.Side.STARBOARD, WheelMath.side(12.0));
        assertEquals(WheelMath.Side.PORT, WheelMath.side(-0.6));
    }

    // ------------------------------------------------------------------ integration and the per-tick budget

    @Test
    void deltasIntegrateWithinTheTickBudget() {
        WheelMath.Step s = WheelMath.turn(0.0, 0.0, 20.0, 30.0, 270.0);
        assertEquals(20.0, s.wheel(), EPS);
        s = WheelMath.turn(s.wheel(), s.usedThisTick(), 20.0, 30.0, 270.0); // only 10 left this tick
        assertEquals(30.0, s.wheel(), EPS);
        assertEquals(30.0, s.usedThisTick(), EPS);
        s = WheelMath.turn(s.wheel(), s.usedThisTick(), 5.0, 30.0, 270.0); // budget used up
        assertEquals(30.0, s.wheel(), EPS);
        s = WheelMath.turn(s.wheel(), s.usedThisTick(), -50.0, 30.0, 270.0); // back the other way: the net change of
        assertEquals(-20.0, s.wheel(), EPS);                                   // the tick may be -30 at most, so all of
        assertEquals(-20.0, s.usedThisTick(), EPS);                            // it applies
        s = WheelMath.turn(s.wheel(), s.usedThisTick(), -50.0, 30.0, 270.0);
        assertEquals(-30.0, s.wheel(), EPS);
        s = WheelMath.turn(s.wheel(), 0.0, 1000.0, 30.0, 270.0); // a new tick
        assertEquals(0.0, s.wheel(), EPS);
    }

    @Test
    void integrationStopsAtTheLock() {
        double w = 0.0;
        for (int t = 0; t < 20; t++) {
            w = WheelMath.turn(w, 0.0, 30.0, 30.0, 270.0).wheel();
        }
        assertEquals(270.0, w, EPS);
        w = WheelMath.turn(w, 0.0, -10.0, 30.0, 270.0).wheel();
        assertEquals(260.0, w, EPS); // turning back from the lock works at once
    }

    @Test
    void badDeltasAreIgnored() {
        assertEquals(0.0, WheelMath.allowedDelta(0.0, Double.NaN, 30.0), EPS);
        assertEquals(0.0, WheelMath.allowedDelta(0.0, Double.POSITIVE_INFINITY, 30.0), EPS);
        assertEquals(0.0, WheelMath.allowedDelta(0.0, 10.0, 0.0), EPS);
    }

    // ------------------------------------------------------------------ client input

    @Test
    void mouseAndKeysAccumulateIntoOneDeltaPerTick() {
        WheelInput in = new WheelInput();
        in.addMouse(3.0);
        in.addMouse(-1.0);
        in.addMouse(2.5); // three frames
        assertEquals(4.5, in.pendingMouse(), EPS);
        assertEquals(4.5 * 0.5 + 6.0, in.drain(0.5, false, true, 6.0, 30.0), EPS); // mouse right and D
        assertEquals(0.0, in.pendingMouse(), EPS);
        assertEquals(-6.0, in.drain(0.5, true, false, 6.0, 30.0), EPS); // A alone
        assertEquals(0.0, in.drain(0.5, true, true, 6.0, 30.0), EPS); // both keys cancel out
        in.addMouse(-200.0);
        assertEquals(-30.0, in.drain(1.0, false, false, 6.0, 30.0), EPS); // limited like the server
        in.addMouse(Double.NaN);
        assertEquals(0.0, in.drain(1.0, false, false, 6.0, 30.0), EPS);
        in.addMouse(5.0);
        in.reset();
        assertEquals(0.0, in.drain(1.0, false, false, 6.0, 30.0), EPS);
    }

    @Test
    void viewDegreesFollowTheGameSensitivity() {
        // MouseHandler at the default sensitivity 0.5: (0.5·0.6 + 0.2)³·8 = 1, then 0.15° per unit
        assertEquals(0.15, WheelInput.viewDegrees(1.0, 0.5), EPS);
        assertEquals(-15.0, WheelInput.viewDegrees(-100.0, 0.5), EPS);
        assertEquals(0.6144, WheelInput.viewDegrees(1.0, 1.0), 1e-9); // (0.8)³·8 = 4.096, ·0.15
    }

    // ------------------------------------------------------------------ sessions

    @Test
    void distanceToTheHelmBlock() {
        assertEquals(0.0, SessionRules.distanceToBlock(10.5, 5.5, 3.5, 10, 5, 3), EPS); // inside
        assertEquals(0.5, SessionRules.distanceToBlock(10.5, 5.0, 2.5, 10, 5, 3), EPS); // north of it, on deck
        assertEquals(2.0, SessionRules.distanceToBlock(13.0, 5.0, 3.5, 10, 5, 3), EPS); // east
        assertEquals(Math.sqrt(2.0), SessionRules.distanceToBlock(9.0, 5.0, 2.0, 10, 5, 3), EPS); // diagonal corner
    }

    @Test
    void sessionEndRules() {
        assertEquals(End.NONE, SessionRules.check(true, true, true, 0.5, 2.0));
        assertEquals(End.NONE, SessionRules.check(true, true, true, 2.0, 2.0));
        assertEquals(End.TOO_FAR, SessionRules.check(true, true, true, 2.01, 2.0));
        assertEquals(End.TOO_FAR, SessionRules.check(true, true, true, Double.NaN, 2.0));
        assertEquals(End.NOT_ON_SHIP, SessionRules.check(true, true, false, 0.5, 2.0));
        assertEquals(End.GONE, SessionRules.check(true, false, true, 0.5, 2.0));
        assertEquals(End.DISABLED, SessionRules.check(false, true, true, 0.5, 2.0));
    }
}
