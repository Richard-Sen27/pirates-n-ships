package com.richardsenger.piratesnships.crew.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The animation controller's state choice: a station pose (ART7) beats work beats sleeping beats sitting beats walking
 * beats idle.
 */
class CrewPoseTest {

    @Test
    void standingStillIsIdle() {
        assertEquals(CrewPose.IDLE, CrewPose.choose(false, false, false));
    }

    @Test
    void movingLegsWalk() {
        assertEquals(CrewPose.WALK, CrewPose.choose(true, false, false));
    }

    @Test
    void anOrderInProgressIsWorkWhateverElse() {
        assertEquals(CrewPose.WORK, CrewPose.choose(false, true, false));
        assertEquals(CrewPose.WORK, CrewPose.choose(true, true, false));
        assertEquals(CrewPose.WORK, CrewPose.choose(false, true, true));
        assertEquals(CrewPose.WORK, CrewPose.choose(true, true, true));
    }

    @Test
    void seatedSitsEvenWhenTheLegsSwing() {
        assertEquals(CrewPose.SIT, CrewPose.choose(false, false, true));
        assertEquals(CrewPose.SIT, CrewPose.choose(true, false, true));
    }

    @Test
    void restingInAHammockSleepsUnlessWorking() {
        // the hammock seat is a vehicle, so a sleeper is also seated
        assertEquals(CrewPose.SLEEP, CrewPose.choose(false, false, true, true));
        assertEquals(CrewPose.SLEEP, CrewPose.choose(true, false, true, true));
        assertEquals(CrewPose.SLEEP, CrewPose.choose(false, false, false, true));
        assertEquals(CrewPose.WORK, CrewPose.choose(false, true, true, true));
        assertEquals(CrewPose.SIT, CrewPose.choose(false, false, true, false));
        assertEquals(CrewPose.choose(true, false, true), CrewPose.choose(true, false, true, false));
    }

    @Test
    void animationNamesAreTheRigContract() {
        assertEquals("idle", CrewPose.IDLE.animation());
        assertEquals("walk", CrewPose.WALK.animation());
        assertEquals("work", CrewPose.WORK.animation());
        assertEquals("sit", CrewPose.SIT.animation());
        assertEquals("sleep", CrewPose.SLEEP.animation());
    }

    @Test
    void aStationPoseWinsOverEverythingElse() {
        for (CrewPose station : new CrewPose[]{CrewPose.HELM, CrewPose.HELM_TURN_LEFT, CrewPose.HELM_TURN_RIGHT,
                CrewPose.CANNON_AIM, CrewPose.CANNON_LOAD, CrewPose.CANNON_FIRE, CrewPose.CAPSTAN_PUSH}) {
            assertEquals(station, CrewPose.choose(false, false, false, false, station));
            assertEquals(station, CrewPose.choose(true, true, false, false, station), "working at the station");
        }
        assertEquals(CrewPose.WORK, CrewPose.choose(false, true, false, false, null));
        assertEquals(CrewPose.WORK, CrewPose.choose(false, true, false, false, CrewPose.IDLE), "not a station pose: ignored");
    }

    @Test
    void stationPosesRoundTripThroughTheirSyncedId() {
        int n = 0;
        for (CrewPose p : CrewPose.values()) {
            if (!p.isStationPose()) {
                assertEquals(-1, p.stationId());
                continue;
            }
            n++;
            assertEquals(p, CrewPose.byStationId(p.stationId()));
        }
        assertEquals(7, n);
        assertNull(CrewPose.byStationId(-1));
        assertNull(CrewPose.byStationId(99));
        assertTrue(CrewPose.values().length < Byte.MAX_VALUE, "the id is synced as a byte");
    }

    @Test
    void stationAnimationNamesAndLoopModes() {
        assertEquals("helm_hold", CrewPose.HELM.animation());
        assertEquals("helm_turn_left", CrewPose.HELM_TURN_LEFT.animation());
        assertEquals("helm_turn_right", CrewPose.HELM_TURN_RIGHT.animation());
        assertEquals("cannon_aim", CrewPose.CANNON_AIM.animation());
        assertEquals("cannon_load", CrewPose.CANNON_LOAD.animation());
        assertEquals("cannon_fire", CrewPose.CANNON_FIRE.animation());
        assertEquals("capstan_push", CrewPose.CAPSTAN_PUSH.animation());
        assertTrue(CrewPose.CAPSTAN_PUSH.loops());
        assertEquals(6, CrewPose.CAPSTAN_PUSH.stationId(), "appended after the cannon poses: synced ids never move");
        assertFalse(CrewPose.CANNON_FIRE.loops(), "the lunge plays once");
        assertTrue(CrewPose.CANNON_LOAD.loops());
        assertTrue(CrewPose.HELM.loops());
    }
}
