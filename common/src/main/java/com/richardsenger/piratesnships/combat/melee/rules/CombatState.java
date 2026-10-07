package com.richardsenger.piratesnships.combat.melee.rules;

import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Immutable combat state of one combatant. Change it only through {@link CombatRules} (inputs, {@code tick}) and
 * {@code HitResolver} (hits).
 *
 * @param phase         current phase
 * @param attack        the attack of an attack phase, else {@code null}
 * @param elapsed       ticks spent in the current phase (0 on the tick the phase was entered)
 * @param duration      length of the current phase in ticks (0 for open-ended phases: idle, guarding)
 * @param stamina       current stamina, 0..{@code staminaMax}
 * @param sinceSpend    ticks since stamina was last spent (regeneration waits for {@code staminaRegenDelayTicks})
 * @param lockoutTicks  remaining parry lockout after a failed parry
 * @param riposteTicks  remaining riposte window after a successful parry
 * @param guardHeld     the guard input is held (a parry or stagger returns to guarding afterwards)
 * @param riposteAttack the current attack is a riposte (bonus damage, can't be parried)
 * @param hitTargets    ids of the targets the current attack already hit (each target is hit once per attack)
 * @param feint         the current {@link Phase#RECOVERY} is a feint recovery (the attack was aborted during its
 *                      wind-up, {@link CombatRules#feint}); false in every other phase
 */
public record CombatState(Phase phase, @Nullable AttackKind attack, int elapsed, int duration, float stamina,
                          int sinceSpend, int lockoutTicks, int riposteTicks, boolean guardHeld, boolean riposteAttack,
                          Set<Integer> hitTargets, boolean feint) {

    /** Below this, stamina counts as zero. */
    public static final float EXHAUSTED_EPSILON = 1.0e-3f;

    public CombatState {
        hitTargets = Set.copyOf(hitTargets);
        feint = feint && phase == Phase.RECOVERY;
    }

    /** A state that is not a feint recovery. */
    public CombatState(Phase phase, @Nullable AttackKind attack, int elapsed, int duration, float stamina,
                       int sinceSpend, int lockoutTicks, int riposteTicks, boolean guardHeld, boolean riposteAttack,
                       Set<Integer> hitTargets) {
        this(phase, attack, elapsed, duration, stamina, sinceSpend, lockoutTicks, riposteTicks, guardHeld, riposteAttack,
                hitTargets, false);
    }

    /** A fresh, idle combatant with the given stamina. */
    public static CombatState fresh(float stamina) {
        return new CombatState(Phase.IDLE, null, 0, 0, stamina, Integer.MAX_VALUE / 2, 0, 0, false, false, Set.of());
    }

    public boolean exhausted() {
        return stamina < EXHAUSTED_EPSILON;
    }

    public boolean lockedOut() {
        return lockoutTicks > 0;
    }

    public boolean riposteReady() {
        return riposteTicks > 0;
    }

    public boolean attackHit() {
        return !hitTargets.isEmpty();
    }

    /** Ticks left in a timed phase (0 for open-ended phases). */
    public int remaining() {
        return duration <= 0 ? 0 : Math.max(0, duration - elapsed);
    }

    /** Nothing happening and nothing pending: the integration layer may stop ticking this combatant. */
    public boolean dormant(float staminaMax) {
        return phase == Phase.IDLE && lockoutTicks == 0 && riposteTicks == 0 && !guardHeld && stamina >= staminaMax;
    }

    // --- Withers (package-private builders keep the rules readable) ------------------------------------------

    /** Enters a phase; a feint recovery ends here (re-mark it with {@link #withFeint}). */
    CombatState enter(Phase p, @Nullable AttackKind a, int dur) {
        return new CombatState(p, a, 0, dur, stamina, sinceSpend, lockoutTicks, riposteTicks, guardHeld,
                p.attacking() ? riposteAttack : false, p.attacking() ? hitTargets : Set.of(), false);
    }

    CombatState withFeint(boolean f) {
        return new CombatState(phase, attack, elapsed, duration, stamina, sinceSpend, lockoutTicks, riposteTicks, guardHeld, riposteAttack, hitTargets, f);
    }

    CombatState withElapsed(int e) {
        return new CombatState(phase, attack, e, duration, stamina, sinceSpend, lockoutTicks, riposteTicks, guardHeld, riposteAttack, hitTargets, feint);
    }

    CombatState withDuration(int d) {
        return new CombatState(phase, attack, elapsed, d, stamina, sinceSpend, lockoutTicks, riposteTicks, guardHeld, riposteAttack, hitTargets, feint);
    }

    CombatState withStamina(float s, int since) {
        return new CombatState(phase, attack, elapsed, duration, s, since, lockoutTicks, riposteTicks, guardHeld, riposteAttack, hitTargets, feint);
    }

    CombatState withTimers(int lockout, int riposte) {
        return new CombatState(phase, attack, elapsed, duration, stamina, sinceSpend, lockout, riposte, guardHeld, riposteAttack, hitTargets, feint);
    }

    CombatState withGuardHeld(boolean held) {
        return new CombatState(phase, attack, elapsed, duration, stamina, sinceSpend, lockoutTicks, riposteTicks, held, riposteAttack, hitTargets, feint);
    }

    CombatState withRiposteAttack(boolean riposte) {
        return new CombatState(phase, attack, elapsed, duration, stamina, sinceSpend, lockoutTicks, riposteTicks, guardHeld, riposte, hitTargets, feint);
    }

    /** The current attack hit {@code targetId} (each target once per attack). */
    public CombatState withHitTarget(int targetId) {
        Set<Integer> s = new HashSet<>(hitTargets);
        s.add(targetId);
        return new CombatState(phase, attack, elapsed, duration, stamina, sinceSpend, lockoutTicks, riposteTicks, guardHeld, riposteAttack, s, feint);
    }

    /** Spends stamina (clamped at zero) and restarts the regeneration delay. */
    CombatState spend(float amount) {
        if (amount <= 0) return this;
        return withStamina(Math.max(0f, stamina - amount), 0);
    }
}
