package com.richardsenger.piratesnships.combat.melee.resolve;

import com.richardsenger.piratesnships.combat.melee.rules.AttackKind;
import com.richardsenger.piratesnships.combat.melee.rules.CombatRules;
import com.richardsenger.piratesnships.combat.melee.rules.CombatState;
import com.richardsenger.piratesnships.combat.melee.rules.MeleeParams;
import com.richardsenger.piratesnships.combat.melee.rules.Phase;
import com.richardsenger.piratesnships.combat.melee.weapon.WeaponDefinition;
import org.jetbrains.annotations.Nullable;

/**
 * Pure hit resolution (docs/design.md §8.5), in this order:
 * <ol>
 *   <li>Projectiles and other damage: {@link HitResult.Outcome#UNAFFECTED}.</li>
 *   <li>Defender parrying: a parryable hit (melee, frontal, not a riposte) within the window, counted with
 *       {@link ParryTiming}, is {@link HitResult.Outcome#PARRIED}. Anything else that hits a parrying defender
 *       (riposte, from behind, too late) breaks the parry: it counts as failed (cost, lockout) and the hit lands.</li>
 *   <li>Defender guarding with the hit inside the guard arc: reduced damage and stamina cost; if that empties the
 *       stamina the guard breaks (stagger).</li>
 *   <li>Otherwise a full hit. It staggers when it is a thrust into the defender's recovery, or when the damage reaches
 *       the defender weapon's poise (times {@code exhaustedPoiseFactor} at zero stamina).</li>
 * </ol>
 * A defender without a weapon (an unarmed mob) can't parry or guard and has no poise.
 */
public final class HitResolver {

    private HitResolver() {
    }

    /**
     * @param hitAgeTicks how many ticks ago the hit landed (0 = now; up to the latency allowance for held hits)
     */
    public static HitResult resolve(CombatState attacker, CombatState defender, @Nullable WeaponDefinition defenderWeapon,
                                    IncomingHit hit, int hitAgeTicks, MeleeParams p) {
        if (!p.skillBased() || !hit.kind().melee()) {
            return new HitResult(HitResult.Outcome.UNAFFECTED, hit.damage(), false, attacker, defender);
        }
        CombatState def = defender;
        boolean wasRecovering = def.phase() == Phase.RECOVERY;

        if (def.phase() == Phase.PARRYING && defenderWeapon != null) {
            if (hit.parryable()) {
                int allowance = hit.kind() == HitKind.MOD_MELEE ? p.latencyAllowanceTicks() : 0;
                ParryTiming timing = ParryTiming.classify(def.elapsed() - hitAgeTicks, p.parryWindowTicks(), allowance);
                if (timing == ParryTiming.SUCCESS) {
                    return new HitResult(HitResult.Outcome.PARRIED, 0f, false,
                            CombatRules.stagger(attacker, p.parryStaggerTicks()), CombatRules.parrySucceeded(def, p));
                }
            }
            def = CombatRules.failParry(def, defenderWeapon, p);
        }

        if (def.phase() == Phase.GUARDING && defenderWeapon != null && hit.frontal()) {
            WeaponDefinition.Guard g = defenderWeapon.guard();
            float reduced = (float) (hit.damage() * (1.0 - g.damageReduction()));
            float cost = (float) ((g.staminaPerHit() + g.staminaPerDamage() * hit.damage()) * p.staminaCostMultiplier());
            if (def.stamina() - cost <= CombatState.EXHAUSTED_EPSILON) {
                CombatState broken = CombatRules.stagger(spendAll(def), p.guardBreakStaggerTicks());
                return new HitResult(HitResult.Outcome.GUARD_BROKEN, reduced, true, attacker, broken);
            }
            return new HitResult(HitResult.Outcome.GUARDED, reduced, false, attacker, spend(def, cost));
        }

        boolean stagger = hit.attack() == AttackKind.THRUST && wasRecovering;
        if (!stagger && defenderWeapon != null && hit.damage() > 0) {
            double poise = defenderWeapon.poise() * (def.exhausted() ? p.exhaustedPoiseFactor() : 1.0);
            stagger = hit.damage() >= poise;
        }
        if (stagger) def = CombatRules.stagger(def, p.staggerTicks());
        return new HitResult(HitResult.Outcome.HIT, hit.damage(), stagger, attacker, def);
    }

    private static CombatState spend(CombatState s, float cost) {
        return new CombatState(s.phase(), s.attack(), s.elapsed(), s.duration(), Math.max(0f, s.stamina() - cost), 0,
                s.lockoutTicks(), s.riposteTicks(), s.guardHeld(), s.riposteAttack(), s.hitTargets());
    }

    private static CombatState spendAll(CombatState s) {
        return spend(s, s.stamina());
    }
}
