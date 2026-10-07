package com.richardsenger.piratesnships.combat.melee.net;

import com.richardsenger.piratesnships.combat.melee.rules.AttackKind;
import com.richardsenger.piratesnships.combat.melee.rules.CombatRules;
import com.richardsenger.piratesnships.combat.melee.rules.CombatState;
import com.richardsenger.piratesnships.combat.melee.rules.MeleeParams;
import com.richardsenger.piratesnships.combat.melee.weapon.DefaultWeapons;
import com.richardsenger.piratesnships.combat.melee.weapon.WeaponDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MeleeSyncRulesTest {

    static final MeleeParams P = MeleeParams.DEFAULTS;
    static final WeaponDefinition W = DefaultWeapons.SABER;
    static final CombatState FRESH = CombatState.fresh(P.staminaMax());

    @Test
    void firstStateIsAlwaysSent() {
        assertTrue(MeleeSyncRules.observerRelevant(null, FRESH));
    }

    @Test
    void phaseAndAttackChangesAreRelevantTicksAreNot() {
        CombatState windup = CombatRules.startAttack(FRESH, AttackKind.SLASH, W, P).state();
        assertTrue(MeleeSyncRules.observerRelevant(FRESH, windup), "idle -> wind-up");
        CombatState later = CombatRules.tick(windup, W, P);
        assertEquals(windup.phase(), later.phase());
        assertFalse(MeleeSyncRules.observerRelevant(windup, later), "elapsed and stamina only");
        CombatState s = windup;
        while (s.phase() == windup.phase()) s = CombatRules.tick(s, W, P);
        assertTrue(MeleeSyncRules.observerRelevant(windup, s), "wind-up -> active");
    }

    @Test
    void guardRiposteLockoutAndReentryAreRelevant() {
        CombatState guard = CombatRules.guardDown(FRESH, W, P).state();
        assertTrue(MeleeSyncRules.observerRelevant(FRESH, guard), "guard down");
        CombatState riposte = CombatRules.parrySucceeded(CombatRules.parry(FRESH, W, P).state(), P);
        assertTrue(MeleeSyncRules.observerRelevant(FRESH, riposte), "riposte window opened");
        CombatState locked = CombatRules.failParry(CombatRules.parry(FRESH, W, P).state(), W, P);
        assertTrue(locked.lockedOut());
        assertTrue(MeleeSyncRules.observerRelevant(FRESH, locked), "lockout");
        CombatState staggered = CombatRules.tick(CombatRules.stagger(FRESH, 20), W, P);
        CombatState again = CombatRules.stagger(staggered, 30);
        assertTrue(MeleeSyncRules.observerRelevant(staggered, again), "stagger extended");
    }

    @Test
    void ownerStaminaIsThrottledButEndsAreSentAtOnce() {
        assertFalse(MeleeSyncRules.ownerStaminaDue(50f, 0, 50f, 100f, 100, 5), "unchanged");
        assertFalse(MeleeSyncRules.ownerStaminaDue(50f, 10, 51f, 100f, 12, 5), "too soon");
        assertTrue(MeleeSyncRules.ownerStaminaDue(50f, 10, 51f, 100f, 15, 5), "interval reached");
        assertTrue(MeleeSyncRules.ownerStaminaDue(99f, 10, 100f, 100f, 11, 5), "full");
        assertTrue(MeleeSyncRules.ownerStaminaDue(3f, 10, 0f, 100f, 11, 5), "empty");
    }
}
