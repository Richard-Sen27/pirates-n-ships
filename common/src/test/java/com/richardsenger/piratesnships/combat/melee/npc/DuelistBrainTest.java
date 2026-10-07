package com.richardsenger.piratesnships.combat.melee.npc;

import com.richardsenger.piratesnships.combat.melee.npc.DuelistBrain.Action;
import com.richardsenger.piratesnships.combat.melee.rules.AttackKind;
import com.richardsenger.piratesnships.combat.melee.rules.CombatRules;
import com.richardsenger.piratesnships.combat.melee.rules.CombatState;
import com.richardsenger.piratesnships.combat.melee.rules.MeleeParams;
import com.richardsenger.piratesnships.combat.melee.weapon.DefaultWeapons;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelistBrainTest {

    private static final MeleeParams P = MeleeParams.DEFAULTS;
    private static final SkillTier SKILLED = new SkillTier(0.5, 4, 0.0);
    private static final CombatState IDLE = CombatState.fresh(100f);

    private static CombatState windup(AttackKind kind, int ticks) {
        CombatState s = CombatRules.startAttack(IDLE, kind, DefaultWeapons.CUTLASS, P).state();
        for (int i = 0; i < ticks; i++) s = CombatRules.tick(s, DefaultWeapons.CUTLASS, P);
        return s;
    }

    private static DuelistBrain.View view(CombatState self, CombatState opp, boolean inReach, int sinceTelegraph, int cooldown) {
        return new DuelistBrain.View(self, opp, inReach, true, sinceTelegraph, P.parryWindowTicks(), cooldown, 25f);
    }

    @Test
    void attacksWhenNothingIsComing() {
        assertEquals(Action.SLASH, DuelistBrain.decide(view(IDLE, IDLE, true, -1, 0), SKILLED, 0.9, 0.9));
        assertEquals(Action.THRUST, DuelistBrain.decide(view(IDLE, IDLE, true, -1, 0), SKILLED, 0.9, 0.1));
        assertEquals(Action.NONE, DuelistBrain.decide(view(IDLE, IDLE, false, -1, 0), SKILLED, 0.9, 0.9), "out of reach");
        assertEquals(Action.NONE, DuelistBrain.decide(view(IDLE, IDLE, true, -1, 5), SKILLED, 0.9, 0.9), "pause between attacks");
    }

    @Test
    void reactsOnlyAfterTheReactionTime() {
        CombatState opp = windup(AttackKind.THRUST, 1);
        assertEquals(Action.NONE, DuelistBrain.decide(view(IDLE, opp, true, 1, 0), SKILLED, 0.0, 0.9), "not seen yet");
        assertEquals(Action.GUARD, DuelistBrain.decide(view(IDLE, opp, true, 4, 0), SKILLED, 0.9, 0.9), "failed roll: guard");
    }

    @Test
    void parriesInsideTheWindowWhenTheRollSucceeds() {
        // cutlass thrust wind-up 8 ticks > parry window 7: guard first, parry once the hit is due inside the window
        CombatState early = windup(AttackKind.THRUST, 0);
        assertFalse(DuelistBrain.hitDueInWindow(early, P.parryWindowTicks()));
        assertEquals(Action.GUARD, DuelistBrain.decide(view(IDLE, early, true, 5, 0), SKILLED, 0.1, 0.9));
        CombatState late = windup(AttackKind.THRUST, 3);
        assertTrue(DuelistBrain.hitDueInWindow(late, P.parryWindowTicks()));
        assertEquals(Action.PARRY, DuelistBrain.decide(view(IDLE, late, true, 5, 0), SKILLED, 0.1, 0.9));
        CombatState guarding = CombatRules.guardDown(IDLE, DefaultWeapons.CUTLASS, P).state();
        assertEquals(Action.PARRY, DuelistBrain.decide(view(guarding, late, true, 5, 0), SKILLED, 0.1, 0.9), "parry from the guard");
    }

    @Test
    void neverParriesARiposteOrWhileLockedOut() {
        CombatState riposte = new CombatState(windup(AttackKind.SLASH, 2).phase(), AttackKind.SLASH, 2, 5, 100f, 0, 0, 0, false, true, java.util.Set.of());
        assertEquals(Action.GUARD, DuelistBrain.decide(view(IDLE, riposte, true, 5, 0), SKILLED, 0.0, 0.9));
        CombatState lockedOut = CombatRules.failParry(IDLE, DefaultWeapons.CUTLASS, P);
        assertTrue(lockedOut.lockedOut());
        assertEquals(Action.GUARD, DuelistBrain.decide(view(lockedOut, windup(AttackKind.SLASH, 2), true, 5, 0), SKILLED, 0.0, 0.9));
    }

    @Test
    void ripostesAfterASuccessfulParryAndPunishesRecovery() {
        CombatState afterParry = CombatRules.parrySucceeded(CombatRules.parry(IDLE, DefaultWeapons.CUTLASS, P).state(), P);
        assertTrue(afterParry.riposteReady());
        assertEquals(Action.SLASH, DuelistBrain.decide(view(afterParry, CombatRules.stagger(IDLE, 25), true, -1, 10), SKILLED, 0.9, 0.0),
                "riposte ignores the pause");
        CombatState recovering = windup(AttackKind.SLASH, 5 + 3); // wind-up 5 + active 3 = recovery
        assertEquals(Action.THRUST, DuelistBrain.decide(view(IDLE, recovering, true, -1, 0), SKILLED, 0.9, 0.9));
    }

    @Test
    void lowersTheGuardAndWaitsOutAnOpenParry() {
        CombatState guarding = CombatRules.guardDown(IDLE, DefaultWeapons.CUTLASS, P).state();
        assertEquals(Action.RELEASE_GUARD, DuelistBrain.decide(view(guarding, IDLE, true, -1, 0), SKILLED, 0.9, 0.9));
        CombatState parrying = CombatRules.parry(IDLE, DefaultWeapons.CUTLASS, P).state();
        assertEquals(Action.NONE, DuelistBrain.decide(view(IDLE, parrying, true, -1, 0), SKILLED, 0.9, 0.9));
    }

    @Test
    void busyOrStaggeredDuelistsDoNothing() {
        assertEquals(Action.NONE, DuelistBrain.decide(view(windup(AttackKind.SLASH, 1), IDLE, true, -1, 0), SKILLED, 0, 0));
        assertEquals(Action.NONE, DuelistBrain.decide(view(CombatRules.stagger(IDLE, 10), windup(AttackKind.SLASH, 3), true, 5, 0), SKILLED, 0, 0));
    }

    @Test
    void exhaustedDuelistsDontGuard() {
        CombatState tired = new CombatState(IDLE.phase(), null, 0, 0, 10f, 0, 0, 0, false, false, java.util.Set.of());
        assertEquals(Action.NONE, DuelistBrain.decide(view(tired, windup(AttackKind.SLASH, 2), true, 5, 0), SKILLED, 0.9, 0.9));
    }

    private static DuelistBrain.View feintView(CombatState self, CombatState opp, boolean feintPlanned) {
        return new DuelistBrain.View(self, opp, true, true, -1, P.parryWindowTicks(), 0, 25f, feintPlanned);
    }

    @Test
    void aPlannedFeintAbortsWhenTheOpponentParries() {
        CombatState mine = windup(AttackKind.SLASH, 1);
        CombatState parrying = CombatRules.parry(IDLE, DefaultWeapons.CUTLASS, P).state();
        assertEquals(Action.FEINT, DuelistBrain.decide(feintView(mine, parrying, true), SKILLED, 0.9, 0.9));
        assertEquals(Action.NONE, DuelistBrain.decide(feintView(mine, IDLE, true), SKILLED, 0.9, 0.9), "no bait taken yet");
        assertEquals(Action.NONE, DuelistBrain.decide(feintView(mine, parrying, false), SKILLED, 0.9, 0.9), "a real attack runs on");
        assertEquals(Action.NONE, DuelistBrain.decide(view(mine, parrying, true, -1, 0), SKILLED, 0.9, 0.9), "old constructor: no feint");
    }

    @Test
    void aPlannedFeintAbortsAtAFixedTickAgainstAGuard() {
        CombatState guarding = CombatRules.guardDown(IDLE, DefaultWeapons.CUTLASS, P).state();
        CombatState early = windup(AttackKind.THRUST, DuelistBrain.FEINT_TICK_AGAINST_GUARD - 1);
        CombatState due = windup(AttackKind.THRUST, DuelistBrain.FEINT_TICK_AGAINST_GUARD);
        assertEquals(Action.NONE, DuelistBrain.decide(feintView(early, guarding, true), SKILLED, 0.9, 0.9));
        assertEquals(Action.FEINT, DuelistBrain.decide(feintView(due, guarding, true), SKILLED, 0.9, 0.9));
    }

    @Test
    void feintsFollowTheTierFrequencyAndAreFollowedByARealAttack() {
        SkillTier feinter = new SkillTier(0.5, 4, 0.4);
        assertTrue(DuelistBrain.planFeint(feinter, 0.39, false, false));
        assertFalse(DuelistBrain.planFeint(feinter, 0.4, false, false));
        assertFalse(DuelistBrain.planFeint(feinter, 0.0, true, false), "never feint a riposte");
        assertFalse(DuelistBrain.planFeint(feinter, 0.0, false, true), "the attack after a feint is real");
        assertFalse(DuelistBrain.planFeint(SKILLED, 0.0, false, false), "feint frequency 0");
        assertTrue(DuelistBrain.planFeint(new SkillTier(0.5, 4, 1.0), 0.999, false, false), "frequency 1 always feints");
    }

    @Test
    void afterAFeintTheDuelistWaitsOutTheBaitedParryThenAttacks() {
        CombatState feinted = CombatRules.feint(windup(AttackKind.SLASH, 1), P).state();
        CombatState parrying = CombatRules.parry(IDLE, DefaultWeapons.CUTLASS, P).state();
        assertEquals(Action.NONE, DuelistBrain.decide(feintView(feinted, parrying, false), SKILLED, 0.9, 0.9), "in feint recovery");
        assertEquals(Action.NONE, DuelistBrain.decide(feintView(IDLE, parrying, false), SKILLED, 0.9, 0.9), "parry still open");
        CombatState lockedOut = CombatRules.failParry(parrying, DefaultWeapons.CUTLASS, P);
        assertEquals(Action.SLASH, DuelistBrain.decide(feintView(IDLE, lockedOut, false), SKILLED, 0.9, 0.9));
    }

    /** The debug trace's reasons: fixed texts naming the rule, the same action as {@link DuelistBrain#decide}. */
    @Test
    void explainNamesTheRule() {
        assertEquals(new DuelistBrain.Decision(Action.NONE, "out of reach"),
                DuelistBrain.explain(view(IDLE, IDLE, false, -1, 0), SKILLED, 0.9, 0.9));
        assertEquals(new DuelistBrain.Decision(Action.NONE, "attack pause"),
                DuelistBrain.explain(view(IDLE, IDLE, true, -1, 5), SKILLED, 0.9, 0.9));
        assertEquals(new DuelistBrain.Decision(Action.SLASH, "attack"),
                DuelistBrain.explain(view(IDLE, IDLE, true, -1, 0), SKILLED, 0.9, 0.9));
        CombatState incoming = windup(AttackKind.SLASH, 0);
        assertEquals(new DuelistBrain.Decision(Action.NONE, "incoming: not seen yet"),
                DuelistBrain.explain(view(IDLE, incoming, true, 0, 0), SKILLED, 0.9, 0.9));
    }
}
