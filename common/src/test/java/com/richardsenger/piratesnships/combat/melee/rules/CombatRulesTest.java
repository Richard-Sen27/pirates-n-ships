package com.richardsenger.piratesnships.combat.melee.rules;

import com.richardsenger.piratesnships.combat.melee.weapon.DefaultWeapons;
import com.richardsenger.piratesnships.combat.melee.weapon.WeaponDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class CombatRulesTest {

    static final MeleeParams P = MeleeParams.DEFAULTS;
    static final WeaponDefinition W = DefaultWeapons.SABER;
    static final CombatState FRESH = CombatState.fresh(P.staminaMax());

    static CombatState ticks(CombatState s, int n) {
        for (int i = 0; i < n; i++) s = CombatRules.tick(s, W, P);
        return s;
    }

    static CombatState ok(InputResult r) {
        assertTrue(r.accepted(), () -> "refused: " + r.refusal());
        return r.state();
    }

    static CombatState withStamina(CombatState s, float stamina) {
        return s.withStamina(stamina, 0);
    }

    @ParameterizedTest
    @EnumSource(AttackKind.class)
    void attackRunsThroughEveryPhaseWithWeaponTimings(AttackKind kind) {
        CombatState s = ok(CombatRules.startAttack(FRESH, kind, W, P));
        assertEquals(Phase.WINDUP, s.phase());
        assertEquals(kind, s.attack());
        for (int i = 0; i < W.windupTicks(kind) - 1; i++) {
            s = CombatRules.tick(s, W, P);
            assertEquals(Phase.WINDUP, s.phase());
        }
        s = CombatRules.tick(s, W, P);
        assertEquals(Phase.ACTIVE, s.phase());
        s = ticks(s, W.activeTicks(kind));
        assertEquals(Phase.RECOVERY, s.phase());
        // nothing was hit: a thrust recovers longer
        int rec = W.recoveryTicks(kind, false);
        s = ticks(s, rec - 1);
        assertEquals(Phase.RECOVERY, s.phase());
        s = CombatRules.tick(s, W, P);
        assertEquals(Phase.IDLE, s.phase());
        assertNull(s.attack());
    }

    @Test
    void thrustThatHitRecoversFaster() {
        assertTrue(W.recoveryTicks(AttackKind.THRUST, false) > W.recoveryTicks(AttackKind.THRUST, true));
        CombatState s = ok(CombatRules.startAttack(FRESH, AttackKind.THRUST, W, P));
        s = ticks(s, W.thrust().windupTicks()).withHitTarget(42);
        s = ticks(s, W.thrust().activeTicks());
        assertEquals(Phase.RECOVERY, s.phase());
        assertEquals(W.thrust().recoveryTicks(), s.duration());
    }

    @Test
    void attackCostsStamina() {
        CombatState s = ok(CombatRules.startAttack(FRESH, AttackKind.SLASH, W, P));
        assertEquals(P.staminaMax() - W.slash().staminaCost(), s.stamina(), 1e-4);
        CombatState t = ok(CombatRules.startAttack(FRESH, AttackKind.THRUST, W, P.withStamina(100, 1, 20, 2.0)));
        assertEquals(100 - 2 * W.thrust().staminaCost(), t.stamina(), 1e-4);
    }

    @Test
    void refusesWhileBusy() {
        CombatState s = ok(CombatRules.startAttack(FRESH, AttackKind.SLASH, W, P));
        for (int i = 0; i < W.totalTicks(AttackKind.SLASH); i++) {
            assertEquals(Refusal.BUSY, CombatRules.startAttack(s, AttackKind.THRUST, W, P).refusal());
            assertEquals(Refusal.BUSY, CombatRules.parry(s, W, P).refusal());
            assertEquals(Refusal.BUSY, CombatRules.guardDown(s, W, P).refusal());
            s = CombatRules.tick(s, W, P);
        }
        assertEquals(Phase.IDLE, s.phase());
        assertTrue(CombatRules.startAttack(s, AttackKind.THRUST, W, P).accepted());
    }

    @Test
    void refusedInputLeavesStateUnchanged() {
        CombatState s = withStamina(FRESH, 1f);
        InputResult r = CombatRules.startAttack(s, AttackKind.SLASH, W, P);
        assertEquals(Refusal.NO_STAMINA, r.refusal());
        assertSame(s, r.state());
    }

    @Test
    void attackNeedsItsFullCost() {
        assertEquals(Refusal.NO_STAMINA, CombatRules.startAttack(withStamina(FRESH, W.slash().staminaCost() - 0.1f), AttackKind.SLASH, W, P).refusal());
        CombatState s = ok(CombatRules.startAttack(withStamina(FRESH, W.slash().staminaCost()), AttackKind.SLASH, W, P));
        assertTrue(s.exhausted());
    }

    @Test
    void zeroStaminaBlocksGuardAndParry() {
        CombatState s = withStamina(FRESH, 0f);
        assertEquals(Refusal.NO_STAMINA, CombatRules.guardDown(s, W, P).refusal());
        assertEquals(Refusal.NO_STAMINA, CombatRules.parry(s, W, P).refusal());
        assertTrue(CombatRules.guardDown(withStamina(FRESH, 0.5f), W, P).accepted());
    }

    @Test
    void disabledRefusesEverything() {
        MeleeParams off = P.withSkillBased(false);
        assertEquals(Refusal.DISABLED, CombatRules.startAttack(FRESH, AttackKind.SLASH, W, off).refusal());
        assertEquals(Refusal.DISABLED, CombatRules.guardDown(FRESH, W, off).refusal());
        assertEquals(Refusal.DISABLED, CombatRules.parry(FRESH, W, off).refusal());
    }

    @Test
    void staggerRefusesAllActionsUntilItEnds() {
        CombatState s = CombatRules.stagger(FRESH, 10);
        for (int i = 0; i < 10; i++) {
            assertEquals(Phase.STAGGERED, s.phase());
            assertEquals(Refusal.STAGGERED, CombatRules.startAttack(s, AttackKind.SLASH, W, P).refusal());
            assertEquals(Refusal.STAGGERED, CombatRules.parry(s, W, P).refusal());
            assertEquals(Refusal.STAGGERED, CombatRules.guardDown(s, W, P).refusal());
            s = CombatRules.tick(s, W, P);
        }
        assertEquals(Phase.IDLE, s.phase());
    }

    @Test
    void staggerCancelsAttackAndExtendsButNeverShortens() {
        CombatState s = ok(CombatRules.startAttack(FRESH, AttackKind.SLASH, W, P));
        s = CombatRules.stagger(s, 10);
        assertNull(s.attack());
        s = ticks(s, 2);
        assertEquals(8, CombatRules.stagger(s, 3).remaining());
        assertEquals(20, CombatRules.stagger(s, 20).remaining());
        assertSame(s, CombatRules.stagger(s, 0));
    }

    @Test
    void guardDrainsPerTickAndDropsAtZero() {
        CombatState s = ok(CombatRules.guardDown(FRESH, W, P));
        assertEquals(Phase.GUARDING, s.phase());
        s = ticks(s, 10);
        assertEquals(P.staminaMax() - 10 * W.guard().staminaPerTick(), s.stamina(), 1e-3);
        CombatState low = ok(CombatRules.guardDown(withStamina(FRESH, W.guard().staminaPerTick() * 1.5f), W, P));
        low = ticks(low, 2);
        assertEquals(Phase.IDLE, low.phase());
        assertFalse(low.guardHeld());
    }

    @Test
    void guardUpReturnsToIdleAndGuardWithoutWeaponIsRefused() {
        CombatState s = ok(CombatRules.guardDown(FRESH, W, P));
        s = ok(CombatRules.guardUp(s));
        assertEquals(Phase.IDLE, s.phase());
        assertFalse(s.guardHeld());
        assertEquals(Refusal.BUSY, CombatRules.guardDown(FRESH, null, P).refusal());
        assertEquals(Refusal.BUSY, CombatRules.parry(FRESH, null, P).refusal());
    }

    @Test
    void attackFromGuardDropsTheGuard() {
        CombatState s = ok(CombatRules.guardDown(FRESH, W, P));
        s = ok(CombatRules.startAttack(s, AttackKind.SLASH, W, P));
        assertFalse(s.guardHeld());
        assertEquals(Phase.WINDUP, s.phase());
    }

    @Test
    void unusedParryFailsAtWindowEndWithCostAndLockout() {
        CombatState s = ok(CombatRules.parry(FRESH, W, P));
        assertEquals(Phase.PARRYING, s.phase());
        s = ticks(s, P.parryWindowTicks() - 1);
        assertEquals(Phase.PARRYING, s.phase());
        s = CombatRules.tick(s, W, P);
        assertEquals(Phase.IDLE, s.phase());
        assertEquals(P.staminaMax() - W.parry().failedStaminaCost(), s.stamina(), 1e-4);
        assertEquals(P.parryLockoutTicks(), s.lockoutTicks());
        // locked out for exactly the lockout duration
        for (int i = 0; i < P.parryLockoutTicks(); i++) {
            assertEquals(Refusal.LOCKED_OUT, CombatRules.parry(s, W, P).refusal());
            s = CombatRules.tick(s, W, P);
        }
        assertTrue(CombatRules.parry(s, W, P).accepted());
    }

    @Test
    void lockoutBlocksOnlyParry() {
        CombatState s = ticks(ok(CombatRules.parry(FRESH, W, P)), P.parryWindowTicks());
        assertTrue(s.lockedOut());
        assertTrue(CombatRules.guardDown(s, W, P).accepted());
        assertTrue(CombatRules.startAttack(s, AttackKind.SLASH, W, P).accepted());
    }

    @Test
    void parryFromGuardReturnsToGuard() {
        CombatState s = ok(CombatRules.guardDown(FRESH, W, P));
        s = ok(CombatRules.parry(s, W, P));
        assertTrue(s.guardHeld());
        s = ticks(s, P.parryWindowTicks());
        assertEquals(Phase.GUARDING, s.phase());
        CombatState ok = CombatRules.parrySucceeded(ok(CombatRules.parry(ok(CombatRules.guardDown(FRESH, W, P)), W, P)), P);
        assertEquals(Phase.GUARDING, ok.phase());
    }

    @Test
    void successfulParryOpensRiposteWindowAndNextAttackIsRiposte() {
        CombatState s = CombatRules.parrySucceeded(ok(CombatRules.parry(FRESH, W, P)), P);
        assertEquals(Phase.IDLE, s.phase());
        assertFalse(s.lockedOut());
        assertEquals(P.riposteWindowTicks(), s.riposteTicks());
        CombatState a = ok(CombatRules.startAttack(ticks(s, P.riposteWindowTicks() - 1), AttackKind.SLASH, W, P));
        assertTrue(a.riposteAttack());
        assertFalse(a.riposteReady(), "the window is consumed");
        a = ticks(a, W.slash().windupTicks());
        assertEquals(Phase.ACTIVE, a.phase());
        assertTrue(a.riposteAttack(), "riposte flag survives into the active phase");
        CombatState late = ok(CombatRules.startAttack(ticks(s, P.riposteWindowTicks()), AttackKind.SLASH, W, P));
        assertFalse(late.riposteAttack());
    }

    @Test
    void regenerationWaitsForDelayAndOnlyWhileNotActing() {
        CombatState s = ok(CombatRules.startAttack(FRESH, AttackKind.SLASH, W, P));
        float after = s.stamina();
        s = ticks(s, W.totalTicks(AttackKind.SLASH));
        assertEquals(Phase.IDLE, s.phase());
        assertEquals(after, s.stamina(), 1e-4, "no regen during the attack");
        // regen starts once sinceSpend reaches the delay
        CombatState idle = withStamina(FRESH, 50f);
        idle = ticks(idle, P.staminaRegenDelayTicks() - 1);
        assertEquals(50f, idle.stamina(), 1e-4);
        idle = CombatRules.tick(idle, W, P);
        assertEquals(50f + P.staminaRegenPerTick(), idle.stamina(), 1e-4);
        idle = ticks(idle, 1000);
        assertEquals(P.staminaMax(), idle.stamina(), 1e-4, "capped at max");
        // guarding never regenerates
        CombatState g = ok(CombatRules.guardDown(withStamina(FRESH, 50f), W, P));
        assertTrue(ticks(g, 100).stamina() < 50f);
    }

    @Test
    void staminaAboveNewMaxIsClamped() {
        CombatState s = CombatRules.tick(CombatState.fresh(200f), W, P);
        assertEquals(P.staminaMax(), s.stamina(), 1e-4);
    }

    @Test
    void zeroWindupStartsActiveImmediately() {
        WeaponDefinition fast = new WeaponDefinition(new WeaponDefinition.Slash(0, 2, 3, 4f, 2.5, 90, 0.5, 5f),
                W.thrust(), W.guard(), W.parry(), W.poise());
        CombatState s = ok(CombatRules.startAttack(FRESH, AttackKind.SLASH, fast, P));
        assertEquals(Phase.ACTIVE, s.phase());
    }

    @Test
    void dormantOnlyWhenNothingIsPending() {
        assertTrue(FRESH.dormant(P.staminaMax()));
        assertFalse(withStamina(FRESH, 50).dormant(P.staminaMax()));
        assertFalse(CombatRules.parrySucceeded(FRESH, P).dormant(P.staminaMax()));
    }

    // --- feints ------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(AttackKind.class)
    void feintIsAllowedOnlyDuringTheWindup(AttackKind kind) {
        assertEquals(Refusal.BUSY, CombatRules.feint(FRESH, P).refusal(), "idle");
        assertEquals(Refusal.BUSY, CombatRules.feint(ok(CombatRules.guardDown(FRESH, W, P)), P).refusal(), "guarding");
        assertEquals(Refusal.BUSY, CombatRules.feint(ok(CombatRules.parry(FRESH, W, P)), P).refusal(), "parrying");
        assertEquals(Refusal.STAGGERED, CombatRules.feint(CombatRules.stagger(FRESH, 10), P).refusal(), "staggered");
        CombatState windup = ok(CombatRules.startAttack(FRESH, kind, W, P));
        assertEquals(Refusal.DISABLED, CombatRules.feint(windup, P.withSkillBased(false)).refusal(), "disabled");
        for (int i = 0; i < W.windupTicks(kind); i++) {
            assertEquals(Phase.WINDUP, windup.phase());
            assertTrue(CombatRules.feint(windup, P).accepted(), "wind-up tick " + i);
            windup = CombatRules.tick(windup, W, P);
        }
        assertEquals(Phase.ACTIVE, windup.phase());
        assertEquals(Refusal.BUSY, CombatRules.feint(windup, P).refusal(), "hit frames");
        CombatState recovery = ticks(windup, W.activeTicks(kind));
        assertEquals(Phase.RECOVERY, recovery.phase());
        assertEquals(Refusal.BUSY, CombatRules.feint(recovery, P).refusal(), "recovery");
    }

    @Test
    void feintRecoversForTheConfiguredTicksWithoutHitFrames() {
        CombatState start = ok(CombatRules.startAttack(FRESH, AttackKind.THRUST, W, P));
        CombatState s = ok(CombatRules.feint(CombatRules.tick(start, W, P), P));
        assertEquals(Phase.RECOVERY, s.phase());
        assertTrue(s.feint());
        assertEquals(AttackKind.THRUST, s.attack());
        assertEquals(P.feintRecoveryTicks(), s.duration());
        assertEquals(6, MeleeParams.DEFAULTS.feintRecoveryTicks(), "default feint recovery");
        assertEquals(start.stamina(), s.stamina(), 1e-4, "the aborted attack's stamina stays spent");
        for (int i = 0; i < P.feintRecoveryTicks() - 1; i++) {
            s = CombatRules.tick(s, W, P);
            assertEquals(Phase.RECOVERY, s.phase(), "tick " + i);
            assertTrue(s.feint());
        }
        s = CombatRules.tick(s, W, P);
        assertEquals(Phase.IDLE, s.phase());
        assertFalse(s.feint());

        CombatState none = ok(CombatRules.feint(start, P.withFeintRecovery(0)));
        assertEquals(Phase.IDLE, none.phase(), "no feint recovery: straight back to idle");
        assertFalse(none.feint());
    }

    @Test
    void feintRecoveryAllowsAnAttackButNoDefense() {
        CombatState s = ok(CombatRules.feint(ok(CombatRules.startAttack(FRESH, AttackKind.SLASH, W, P)), P));
        assertEquals(Refusal.BUSY, CombatRules.parry(s, W, P).refusal());
        assertEquals(Refusal.BUSY, CombatRules.guardDown(s, W, P).refusal());
        CombatState again = ok(CombatRules.startAttack(s, AttackKind.THRUST, W, P));
        assertEquals(Phase.WINDUP, again.phase());
        assertFalse(again.feint());
        // an ordinary recovery still blocks attacks
        CombatState recovery = ticks(ok(CombatRules.startAttack(FRESH, AttackKind.SLASH, W, P)),
                W.windupTicks(AttackKind.SLASH) + W.activeTicks(AttackKind.SLASH));
        assertEquals(Phase.RECOVERY, recovery.phase());
        assertEquals(Refusal.BUSY, CombatRules.startAttack(recovery, AttackKind.SLASH, W, P).refusal());
        // a stagger ends the feint recovery
        assertFalse(CombatRules.stagger(s, 10).feint());
    }

    @Test
    void feintedRiposteLosesTheRiposte() {
        CombatState ready = CombatRules.parrySucceeded(ok(CombatRules.parry(FRESH, W, P)), P);
        CombatState riposte = ok(CombatRules.startAttack(ready, AttackKind.SLASH, W, P));
        assertTrue(riposte.riposteAttack());
        CombatState feinted = ok(CombatRules.feint(riposte, P));
        assertFalse(feinted.riposteAttack());
        assertFalse(feinted.riposteReady());
    }
}
