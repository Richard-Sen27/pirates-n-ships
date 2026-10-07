package com.richardsenger.piratesnships.combat.melee.client.anim;

import com.richardsenger.piratesnships.combat.melee.rules.AttackKind;
import com.richardsenger.piratesnships.combat.melee.rules.Phase;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static com.richardsenger.piratesnships.combat.melee.client.anim.MeleeAnimationMapping.*;
import static org.junit.jupiter.api.Assertions.*;

class MeleeAnimationMappingTest {

    @Test
    void attackPhasesMapPerKind() {
        assertEquals(SLASH_WINDUP, forPhase(Phase.WINDUP, AttackKind.SLASH, false));
        assertEquals(SLASH_ACTIVE, forPhase(Phase.ACTIVE, AttackKind.SLASH, false));
        assertEquals(SLASH_RECOVERY, forPhase(Phase.RECOVERY, AttackKind.SLASH, false));
        assertEquals(THRUST_WINDUP, forPhase(Phase.WINDUP, AttackKind.THRUST, false));
        assertEquals(THRUST_ACTIVE, forPhase(Phase.ACTIVE, AttackKind.THRUST, false));
        assertEquals(THRUST_RECOVERY, forPhase(Phase.RECOVERY, AttackKind.THRUST, false));
    }

    @Test
    void riposteChangesOnlyTheWindup() {
        assertEquals(RIPOSTE_WINDUP, forPhase(Phase.WINDUP, AttackKind.SLASH, true));
        assertEquals(RIPOSTE_WINDUP, forPhase(Phase.WINDUP, AttackKind.THRUST, true));
        assertEquals(SLASH_ACTIVE, forPhase(Phase.ACTIVE, AttackKind.SLASH, true));
        assertEquals(THRUST_RECOVERY, forPhase(Phase.RECOVERY, AttackKind.THRUST, true));
    }

    @Test
    void defensivePhasesAndIdle() {
        assertNull(forPhase(Phase.IDLE, null, false));
        assertEquals(GUARD, forPhase(Phase.GUARDING, null, false));
        assertEquals(PARRY, forPhase(Phase.PARRYING, null, false));
        assertEquals(STAGGER, forPhase(Phase.STAGGERED, null, false));
        // an attack phase without a kind (should not be sent) still animates
        assertEquals(SLASH_WINDUP, forPhase(Phase.WINDUP, null, false));
    }

    @Test
    void guardIsLoweredOnStopNothingElse() {
        assertEquals(GUARD_LOWER, onStop(Phase.GUARDING));
        for (Phase p : Phase.values()) if (p != Phase.GUARDING) assertNull(onStop(p), p.name());
        assertNull(onStop(null));
    }

    @Test
    void everyMappedNameIsListedAndUnique() {
        Set<String> used = new HashSet<>();
        for (Phase p : Phase.values()) {
            for (AttackKind k : AttackKind.values()) {
                for (boolean riposte : new boolean[]{false, true}) {
                    String n = forPhase(p, k, riposte);
                    if (n != null) used.add(n);
                }
            }
            String s = onStop(p);
            if (s != null) used.add(s);
        }
        assertEquals(new HashSet<>(ALL), used);
        assertEquals(ALL.size(), new HashSet<>(ALL).size());
    }

    @Test
    void speedStretchesTheAnimationToThePhase() {
        // a 10-tick animation over a 5-tick phase plays twice as fast, over 20 ticks half as fast
        assertEquals(2f, speed(10f, 5), 1e-6);
        assertEquals(0.5f, speed(10f, 20), 1e-6);
        assertEquals(1f, speed(8f, 8), 1e-6);
    }

    @Test
    void openEndedOrBrokenInputsPlayAtNormalSpeed() {
        assertEquals(1f, speed(10f, 0));
        assertEquals(1f, speed(10f, -3));
        assertEquals(1f, speed(0f, 10));
        assertEquals(1f, speed(Float.NaN, 10));
    }

    @Test
    void speedIsClamped() {
        assertEquals(MAX_SPEED, speed(1000f, 1));
        assertEquals(MIN_SPEED, speed(1f, 10_000));
    }

    @Test
    void startTickCatchesUpInAnimationTime() {
        // 3 game ticks into a phase played at speed 2 = 6 animation ticks in
        assertEquals(6f, startTick(3, 2f, 10f), 1e-6);
        assertEquals(0f, startTick(0, 2f, 10f), 1e-6);
        assertEquals(0f, startTick(-4, 2f, 10f), 1e-6);
        // never past the end (a very late packet just shows the final pose)
        assertEquals(10f, startTick(50, 1f, 10f), 1e-6);
        // the animation then ends exactly when the phase does: start + remaining * speed = length
        float s = speed(10f, 8);
        assertEquals(10f, startTick(5, s, 10f) + (8 - 5) * s, 1e-5);
    }
}
