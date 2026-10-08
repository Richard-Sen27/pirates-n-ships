package com.richardsenger.piratesnships.combat.cannon.npc;

import com.richardsenger.piratesnships.combat.cannon.CannonStation.CannonOrder;
import com.richardsenger.piratesnships.crew.npc.CrewPose;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** ART7: the gun crew rams while loading, lunges while firing, sights while it has a target, else stands. */
class GunCrewPosesTest {

    @Test
    void theOrderBeingCarriedOutChoosesThePose() {
        assertEquals(CrewPose.CANNON_LOAD, GunCrewPoses.pose(CannonOrder.LOAD, false));
        assertEquals(CrewPose.CANNON_LOAD, GunCrewPoses.pose(CannonOrder.LOAD, true), "loading beats aiming");
        assertEquals(CrewPose.CANNON_FIRE, GunCrewPoses.pose(CannonOrder.FIRE, true));
        assertEquals(CrewPose.CANNON_AIM, GunCrewPoses.pose(CannonOrder.FIRE_AT_WILL, false));
    }

    @Test
    void withoutAnOrderTheCrewAimsOnlyWithATarget() {
        assertEquals(CrewPose.CANNON_AIM, GunCrewPoses.pose(null, true));
        assertNull(GunCrewPoses.pose(null, false));
        assertNull(GunCrewPoses.pose("another station's order", false));
    }
}
