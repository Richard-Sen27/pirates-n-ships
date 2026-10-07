package com.richardsenger.piratesnships.crew.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The animation controller's state choice: work beats sitting beats walking beats idle. */
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
    void animationNamesAreTheRigContract() {
        assertEquals("idle", CrewPose.IDLE.animation());
        assertEquals("walk", CrewPose.WALK.animation());
        assertEquals("work", CrewPose.WORK.animation());
        assertEquals("sit", CrewPose.SIT.animation());
    }
}
