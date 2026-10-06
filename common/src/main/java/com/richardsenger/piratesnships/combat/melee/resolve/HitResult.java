package com.richardsenger.piratesnships.combat.melee.resolve;

import com.richardsenger.piratesnships.combat.melee.rules.CombatState;

/**
 * Result of one hit.
 *
 * @param outcome           what happened
 * @param damage            damage to apply (0 when parried)
 * @param defenderStaggered the defender was staggered by this hit
 * @param attacker          the attacker's new state
 * @param defender          the defender's new state
 */
public record HitResult(Outcome outcome, float damage, boolean defenderStaggered, CombatState attacker,
                        CombatState defender) {

    public enum Outcome {
        /** Deflected: no damage, attacker staggered, defender gets a riposte window. */
        PARRIED,
        /** Blocked by the guard: reduced damage, stamina drained. */
        GUARDED,
        /** Blocked, but the stamina ran out: reduced damage and the defender is staggered. */
        GUARD_BROKEN,
        /** Full hit. */
        HIT,
        /** Not melee (projectile, other): the skill system leaves it alone. */
        UNAFFECTED
    }
}
