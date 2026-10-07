package com.richardsenger.piratesnships.combat.melee.npc;

import com.richardsenger.piratesnships.combat.melee.rules.CombatState;
import com.richardsenger.piratesnships.combat.melee.rules.Phase;

/**
 * The NPC duelist's decision per tick (docs/design.md §8.5 "NPC duelists"), pure. The AI goal turns the result into
 * the same {@code MeleeService} inputs a player's keys produce, so NPCs follow the players' rules: they telegraph
 * (wind-up), guard, parry inside the parry window and riposte after a successful parry.
 *
 * <p>Rules, in order:
 * <ol>
 *   <li>Own attack in its wind-up and meant as a feint ({@link View#feintPlanned}, decided by {@link #planFeint} when
 *       the attack started): abort it ({@link Action#FEINT}) as soon as the opponent opens a parry, or at wind-up tick
 *       {@link #FEINT_TICK_AGAINST_GUARD} if the opponent guards; otherwise let it run (if the wind-up ends first, the
 *       attack is real).</li>
 *   <li>Staggered, attacking or parrying: nothing to decide ({@link Action#NONE}).</li>
 *   <li>An opponent attack (wind-up or hit frames) that can reach us: before the reaction time has passed the NPC has
 *       not seen it yet ({@code NONE}). After it, if the per-attack parry roll is below the tier's parry chance (and the
 *       attack is no riposte, which can't be parried), it guards and opens the parry when the hit is due inside the
 *       window ({@link Action#PARRY}): the opponent's remaining wind-up is at most {@code window - 1}, or the hit
 *       frames run. Otherwise it guards if it has the stamina for it.</li>
 *   <li>No attack coming: a raised guard is lowered; a riposte window is used at once with a slash; else, in reach
 *       and off cooldown, it attacks: a thrust into an opponent's recovery or stagger (a thrust into recovery
 *       staggers), otherwise a slash or, if the thrust roll is below {@link #THRUST_SHARE}, a thrust. It never swings
 *       into an open parry window.</li>
 * </ol>
 * After a feint the opponent's parry runs out and locks them out; the next attack is always real
 * ({@link #planFeint}), and the brain does not swing while the opponent's parry is still open, so the follow-up
 * lands after the window.
 */
public final class DuelistBrain {

    /** Share of normal attacks that are thrusts. */
    public static final double THRUST_SHARE = 0.3;
    /** Wind-up tick at which a planned feint is aborted against a guarding (not parrying) opponent. */
    public static final int FEINT_TICK_AGAINST_GUARD = 2;

    public enum Action { NONE, SLASH, THRUST, GUARD, RELEASE_GUARD, PARRY, FEINT }

    /**
     * What the NPC sees this tick.
     *
     * @param self                its own combat state
     * @param opponent            the target's combat state (fresh idle for an entity that never fought)
     * @param inReach             the target is inside our weapon's reach
     * @param threatened          we stand inside the target's reach, in front of it (its attack can hit us)
     * @param ticksSinceTelegraph ticks since the current opponent attack started (-1 = no attack)
     * @param parryWindowTicks    {@code melee.parry_window_ticks}
     * @param attackCooldown      ticks before we may start our next attack (0 = ready)
     * @param guardStamina        minimum stamina to raise the guard
     * @param feintPlanned        our current attack was started as a feint ({@link #planFeint})
     */
    public record View(CombatState self, CombatState opponent, boolean inReach, boolean threatened,
                       int ticksSinceTelegraph, int parryWindowTicks, int attackCooldown, float guardStamina,
                       boolean feintPlanned) {

        /** No feint planned. */
        public View(CombatState self, CombatState opponent, boolean inReach, boolean threatened,
                    int ticksSinceTelegraph, int parryWindowTicks, int attackCooldown, float guardStamina) {
            this(self, opponent, inReach, threatened, ticksSinceTelegraph, parryWindowTicks, attackCooldown, guardStamina, false);
        }
    }

    private DuelistBrain() {
    }

    /**
     * @param parryRoll  0..1, drawn once per opponent attack (compared with the tier's parry chance)
     * @param thrustRoll 0..1, drawn once per own attack opportunity
     */
    public static Action decide(View v, SkillTier tier, double parryRoll, double thrustRoll) {
        CombatState self = v.self();
        Phase phase = self.phase();
        if (phase == Phase.WINDUP && v.feintPlanned()) {
            Phase opp = v.opponent().phase();
            if (opp == Phase.PARRYING) return Action.FEINT;
            if (opp == Phase.GUARDING && self.elapsed() >= FEINT_TICK_AGAINST_GUARD) return Action.FEINT;
            return Action.NONE;
        }
        if (phase == Phase.STAGGERED || phase == Phase.PARRYING || phase.attacking()) return Action.NONE;

        CombatState opp = v.opponent();
        boolean incoming = (opp.phase() == Phase.WINDUP || opp.phase() == Phase.ACTIVE) && v.threatened();
        if (incoming) {
            if (v.ticksSinceTelegraph() < tier.reactionTicks()) return Action.NONE;
            boolean canParry = !self.lockedOut() && !self.exhausted() && !opp.riposteAttack();
            if (canParry && parryRoll < tier.parryChance()) {
                if (hitDueInWindow(opp, v.parryWindowTicks())) return Action.PARRY;
                return phase == Phase.GUARDING || self.stamina() < v.guardStamina() ? Action.NONE : Action.GUARD;
            }
            if (phase != Phase.GUARDING && self.stamina() >= v.guardStamina()) return Action.GUARD;
            return Action.NONE;
        }

        if (phase == Phase.GUARDING) return Action.RELEASE_GUARD;
        if (!v.inReach()) return Action.NONE;
        if (self.riposteReady()) return Action.SLASH;
        if (v.attackCooldown() > 0 || opp.phase() == Phase.PARRYING) return Action.NONE;
        if (opp.phase() == Phase.RECOVERY || opp.phase() == Phase.STAGGERED) return Action.THRUST;
        return thrustRoll < THRUST_SHARE ? Action.THRUST : Action.SLASH;
    }

    /**
     * Whether an attack the NPC starts now is meant as a feint: with the tier's {@link SkillTier#feintFrequency()},
     * never for a riposte (it can't be parried anyway) and never right after a feint (the follow-up is real).
     *
     * @param feintRoll  0..1, drawn once per own attack
     * @param riposte    the attack will be a riposte
     * @param afterFeint the NPC's previous attack was a feint
     */
    public static boolean planFeint(SkillTier tier, double feintRoll, boolean riposte, boolean afterFeint) {
        return !riposte && !afterFeint && feintRoll < tier.feintFrequency();
    }

    /** The opponent's hit lands while a parry opened now is still open (hit frames running count as due). */
    public static boolean hitDueInWindow(CombatState opponent, int parryWindowTicks) {
        if (opponent.phase() == Phase.ACTIVE) return true;
        return opponent.phase() == Phase.WINDUP && opponent.remaining() <= parryWindowTicks - 1;
    }
}
