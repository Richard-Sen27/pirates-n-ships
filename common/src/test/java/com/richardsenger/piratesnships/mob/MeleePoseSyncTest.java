package com.richardsenger.piratesnships.mob;

import com.richardsenger.piratesnships.combat.melee.rules.AttackKind;
import com.richardsenger.piratesnships.combat.melee.rules.CombatState;
import com.richardsenger.piratesnships.combat.melee.rules.Phase;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.mob.client.DuelistArmPose;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MEL3: the one mapping from a duelist's melee state to the synced pose ({@link MeleePoseSync#poseOf}), which the M3 mobs
 * and the crew both use, for every phase, attack, riposte and feint; out of the engine it is idle; every pose survives
 * the synced int; and every phase but idle has a telegraph on the client.
 */
class MeleePoseSyncTest {

    private static CombatState state(Phase phase, AttackKind attack, int duration, boolean riposte, boolean feint) {
        return new CombatState(phase, attack, 2, duration, 50f, 0, 0, 0, phase == Phase.GUARDING, riposte, Set.of(), feint);
    }

    @Test
    void bothDuelistKindsShowTheirPoseThroughTheSharedSync() {
        for (Class<?> kind : new Class<?>[]{SeafarerMob.class, CrewMember.class}) {
            assertTrue(MeleePosed.class.isAssignableFrom(kind), kind.getSimpleName() + " does not carry a synced melee pose");
        }
    }

    @Test
    void everyDuelistStateMapsToItsPose() {
        for (Phase phase : Phase.values()) {
            for (AttackKind attack : new AttackKind[]{null, AttackKind.SLASH, AttackKind.THRUST}) {
                for (boolean riposte : new boolean[]{false, true}) {
                    for (boolean feint : new boolean[]{false, true}) {
                        CombatState s = state(phase, attack, 9, riposte, feint);
                        MeleePose pose = MeleePoseSync.poseOf(true, s);
                        String what = phase + " " + attack + " riposte " + riposte + " feint " + feint;
                        assertEquals(phase, pose.phase(), what);
                        assertEquals(9, pose.duration(), what);
                        assertEquals(phase.attacking() ? attack : null, pose.attack(), what + ": the attack shows only while attacking");
                        assertEquals(phase.attacking() && riposte, pose.riposte(), what);
                        assertEquals(phase == Phase.RECOVERY && feint, pose.feint(), what + ": a feint is a recovery");
                        assertEquals(pose, MeleePose.unpack(pose.pack()), what + ": survives the synced int");
                        if (phase == Phase.IDLE) {
                            assertNull(DuelistArmPose.of(pose, 0.5f), what + ": idle leaves the animations alone");
                        } else if (!phase.attacking() || attack != null) {
                            assertNotNull(DuelistArmPose.of(pose, 0.5f), what + ": no telegraph");
                        }
                    }
                }
            }
        }
    }

    @Test
    void outOfTheEngineItIsIdle() {
        assertEquals(MeleePose.IDLE, MeleePoseSync.poseOf(false, state(Phase.WINDUP, AttackKind.SLASH, 8, false, false)));
        assertEquals(MeleePose.IDLE, MeleePoseSync.poseOf(true, null));
        assertEquals(Phase.IDLE, MeleePoseSync.poseOf(true, CombatState.fresh(100f)).phase(), "a fresh state is idle");
    }

    @Test
    void progressRunsThroughThePhase() {
        MeleePose windup = new MeleePose(Phase.WINDUP, AttackKind.SLASH, false, 8);
        assertEquals(0f, MeleePoseSync.progress(windup, 0, 0f), 1e-6f);
        assertEquals(0.5f, MeleePoseSync.progress(windup, 3, 1f), 1e-6f);
        assertEquals(1f, MeleePoseSync.progress(windup, 20, 0f), 1e-6f, "clamped at the end");
        assertEquals(1f, MeleePoseSync.progress(new MeleePose(Phase.GUARDING, null, false, 0), 0, 0f), 1e-6f, "open-ended");
        assertFalse(MeleePoseSync.progress(windup, -5, 0f) < 0f, "never before the start");
    }
}
