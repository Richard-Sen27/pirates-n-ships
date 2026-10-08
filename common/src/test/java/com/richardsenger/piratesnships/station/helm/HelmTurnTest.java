package com.richardsenger.piratesnships.station.helm;

import com.richardsenger.piratesnships.crew.npc.CrewPose;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** ART7: the helmsman turns the wheel the way it moved, for one loop after each step, and holds it otherwise. */
class HelmTurnTest {

    @Test
    void aSteadyWheelIsHeld() {
        HelmTurn t = new HelmTurn();
        assertEquals(0, t.update(30, 0), "the first reading is no turn");
        assertEquals(0, t.update(30, 1));
        assertEquals(0, t.update(30.2, 2), "below the threshold");
    }

    @Test
    void aStepToStarboardTurnsRightForOneLoop() {
        HelmTurn t = new HelmTurn();
        t.update(0, 0);
        assertEquals(1, t.update(45, 10));
        assertEquals(1, t.update(45, 10 + HelmTurn.HOLD_TICKS - 1));
        assertEquals(0, t.update(45, 10 + HelmTurn.HOLD_TICKS));
    }

    @Test
    void aStepToPortTurnsLeftAndEveryStepRenewsTheTurn() {
        HelmTurn t = new HelmTurn();
        t.update(90, 0);
        assertEquals(-1, t.update(60, 5));
        assertEquals(-1, t.update(30, 20));
        assertEquals(-1, t.update(30, 20 + HelmTurn.HOLD_TICKS - 1), "renewed by the second step");
        assertEquals(1, t.update(40, 50), "a step back to starboard turns the other way at once");
    }

    @Test
    void turnsMapToTheHelmPoses() {
        assertEquals(CrewPose.HELM, HelmPoses.pose(0));
        assertEquals(CrewPose.HELM_TURN_RIGHT, HelmPoses.pose(1));
        assertEquals(CrewPose.HELM_TURN_LEFT, HelmPoses.pose(-1));
    }
}
