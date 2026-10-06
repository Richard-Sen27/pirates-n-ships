package com.richardsenger.piratesnships.combat.melee.rules;

import com.richardsenger.piratesnships.combat.melee.weapon.WeaponDefinition;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * The pure combat state machine (docs/design.md §8.5): inputs and the per-tick transition. No world access.
 *
 * <p>Tick convention: an input applied during server tick {@code t} enters its phase with {@code elapsed = 0}; hits
 * are resolved against that state during the same tick; {@link #tick} then advances it. A phase of duration
 * {@code d} therefore covers exactly {@code d} server ticks.
 */
public final class CombatRules {

    private CombatRules() {
    }

    // --- Inputs -------------------------------------------------------------------------------------------------

    /** Start a slash or thrust. A started attack consumes an open riposte window and becomes a riposte. */
    public static InputResult startAttack(CombatState s, AttackKind kind, WeaponDefinition weapon, MeleeParams p) {
        Refusal busy = actionGate(s, p);
        if (busy != Refusal.NONE) return InputResult.refused(s, busy);
        if (s.phase() == Phase.PARRYING) return InputResult.refused(s, Refusal.BUSY);
        float cost = cost(weapon.staminaCost(kind), p);
        if (s.stamina() + CombatState.EXHAUSTED_EPSILON < cost || (cost > 0 && s.exhausted())) {
            return InputResult.refused(s, Refusal.NO_STAMINA);
        }
        boolean riposte = s.riposteReady();
        int windup = weapon.windupTicks(kind);
        Phase first = windup > 0 ? Phase.WINDUP : Phase.ACTIVE;
        int dur = windup > 0 ? windup : weapon.activeTicks(kind);
        CombatState next = new CombatState(first, kind, 0, dur, s.stamina(), s.sinceSpend(), s.lockoutTicks(), 0,
                false, riposte, Set.of()).spend(cost);
        return InputResult.accepted(next);
    }

    /** Raise the guard (hold). Needs stamina above zero. Already guarding = accepted, no change. */
    public static InputResult guardDown(CombatState s, @Nullable WeaponDefinition weapon, MeleeParams p) {
        Refusal busy = actionGate(s, p);
        if (busy != Refusal.NONE) return InputResult.refused(s, busy);
        if (s.phase() == Phase.GUARDING) return InputResult.accepted(s);
        if (s.phase() == Phase.PARRYING) return InputResult.accepted(s.withGuardHeld(true));
        if (weapon == null) return InputResult.refused(s, Refusal.BUSY);
        if (s.exhausted()) return InputResult.refused(s, Refusal.NO_STAMINA);
        return InputResult.accepted(s.withGuardHeld(true).enter(Phase.GUARDING, null, 0));
    }

    /** Release the guard. Always accepted. */
    public static InputResult guardUp(CombatState s) {
        CombatState next = s.withGuardHeld(false);
        if (next.phase() == Phase.GUARDING) next = next.enter(Phase.IDLE, null, 0);
        return InputResult.accepted(next);
    }

    /** Open the parry window. Allowed from idle or guarding, not while locked out or at zero stamina. */
    public static InputResult parry(CombatState s, @Nullable WeaponDefinition weapon, MeleeParams p) {
        Refusal busy = actionGate(s, p);
        if (busy != Refusal.NONE) return InputResult.refused(s, busy);
        if (s.phase() == Phase.PARRYING || weapon == null) return InputResult.refused(s, Refusal.BUSY);
        if (s.lockedOut()) return InputResult.refused(s, Refusal.LOCKED_OUT);
        if (s.exhausted()) return InputResult.refused(s, Refusal.NO_STAMINA);
        return InputResult.accepted(s.enter(Phase.PARRYING, null, p.parryWindowTicks()));
    }

    /** DISABLED, STAGGERED and attacking checks shared by every action input. */
    private static Refusal actionGate(CombatState s, MeleeParams p) {
        if (!p.skillBased()) return Refusal.DISABLED;
        if (s.phase() == Phase.STAGGERED) return Refusal.STAGGERED;
        if (s.phase().attacking()) return Refusal.BUSY;
        return Refusal.NONE;
    }

    /** Whether {@code s} could open a parry right now (used to decide whether to hold a hit for the latency allowance). */
    public static boolean canParry(CombatState s, @Nullable WeaponDefinition weapon, MeleeParams p) {
        return parry(s, weapon, p).accepted();
    }

    // --- Tick ---------------------------------------------------------------------------------------------------

    /**
     * Advances one server tick: timers, phase transitions (wind-up → active → recovery → idle, parry window expiry
     * = failed parry, stagger end), guard drain and stamina regeneration. {@code weapon} is the weapon in hand
     * ({@code null} = none: a guard drops).
     */
    public static CombatState tick(CombatState s, @Nullable WeaponDefinition weapon, MeleeParams p) {
        CombatState n = s.withTimers(Math.max(0, s.lockoutTicks() - 1), Math.max(0, s.riposteTicks() - 1))
                .withStamina(Math.min(s.stamina(), p.staminaMax()), s.sinceSpend() >= Integer.MAX_VALUE / 2 ? s.sinceSpend() : s.sinceSpend() + 1);
        int e = n.elapsed() + 1;
        switch (n.phase()) {
            case WINDUP -> n = e >= n.duration() || weapon == null
                    ? (weapon == null ? n.enter(Phase.IDLE, null, 0) : n.enter(Phase.ACTIVE, n.attack(), weapon.activeTicks(n.attack())))
                    : n.withElapsed(e);
            case ACTIVE -> {
                if (e < n.duration()) n = n.withElapsed(e);
                else {
                    int rec = weapon == null ? 0 : weapon.recoveryTicks(n.attack(), n.attackHit());
                    n = rec > 0 ? n.enter(Phase.RECOVERY, n.attack(), rec) : n.enter(Phase.IDLE, null, 0);
                }
            }
            case RECOVERY -> n = e >= n.duration() ? n.enter(Phase.IDLE, null, 0) : n.withElapsed(e);
            case PARRYING -> n = e >= n.duration() ? failParry(n, weapon, p) : n.withElapsed(e);
            case STAGGERED -> n = e >= n.duration() ? afterDefense(n) : n.withElapsed(e);
            case GUARDING -> {
                if (weapon == null) n = n.enter(Phase.IDLE, null, 0);
                else {
                    n = n.spend(cost(weapon.guard().staminaPerTick(), p)).withElapsed(e);
                    if (n.exhausted()) n = n.withGuardHeld(false).enter(Phase.IDLE, null, 0);
                }
            }
            case IDLE -> n = n.withElapsed(Math.min(e, 1_000_000));
        }
        if ((n.phase() == Phase.IDLE || n.phase() == Phase.STAGGERED) && n.sinceSpend() >= p.staminaRegenDelayTicks()) {
            n = n.withStamina(Math.min(p.staminaMax(), n.stamina() + p.staminaRegenPerTick()), n.sinceSpend());
        }
        return n;
    }

    // --- Outcomes used by the hit resolver ----------------------------------------------------------------------

    /** A parry that ended without deflecting anything: stamina cost, lockout, back to guard if still held. */
    public static CombatState failParry(CombatState s, @Nullable WeaponDefinition weapon, MeleeParams p) {
        CombatState n = s.spend(weapon == null ? 0f : cost(weapon.parry().failedStaminaCost(), p))
                .withTimers(p.parryLockoutTicks(), s.riposteTicks());
        return afterDefense(n);
    }

    /** A parry that deflected a hit: the window closes and the riposte window opens. */
    public static CombatState parrySucceeded(CombatState s, MeleeParams p) {
        return afterDefense(s.withTimers(s.lockoutTicks(), p.riposteWindowTicks()));
    }

    /** Stagger for {@code ticks} (no actions). Cancels any attack, guard or parry; an existing stagger is extended. */
    public static CombatState stagger(CombatState s, int ticks) {
        if (ticks <= 0) return s;
        int dur = s.phase() == Phase.STAGGERED ? Math.max(s.remaining(), ticks) : ticks;
        return s.withTimers(s.lockoutTicks(), 0).withRiposteAttack(false).enter(Phase.STAGGERED, null, dur);
    }

    /** Back to guarding if the guard is still held and there's stamina, else idle. */
    private static CombatState afterDefense(CombatState s) {
        if (s.guardHeld() && !s.exhausted()) return s.enter(Phase.GUARDING, null, 0);
        return s.withGuardHeld(s.guardHeld() && !s.exhausted()).enter(Phase.IDLE, null, 0);
    }

    static float cost(float base, MeleeParams p) {
        return (float) (base * p.staminaCostMultiplier());
    }
}
