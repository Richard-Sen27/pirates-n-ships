package com.richardsenger.piratesnships.combat.melee.resolve;

import com.richardsenger.piratesnships.combat.melee.rules.AttackKind;
import com.richardsenger.piratesnships.combat.melee.rules.MeleeParams;
import com.richardsenger.piratesnships.combat.melee.weapon.WeaponDefinition;
import org.jetbrains.annotations.Nullable;

/**
 * One hit arriving at a defender.
 *
 * @param kind    where it comes from
 * @param attack  slash or thrust for mod melee, {@code null} otherwise
 * @param damage  damage before guard (riposte bonus already included, see {@link #modMelee})
 * @param riposte the hit is a riposte (can't be parried)
 * @param frontal the source is inside the defender's guard arc
 */
public record IncomingHit(HitKind kind, @Nullable AttackKind attack, float damage, boolean riposte, boolean frontal) {

    /** A mod sword hit; the riposte bonus (global times weapon multiplier) is applied here. */
    public static IncomingHit modMelee(WeaponDefinition weapon, AttackKind attack, boolean riposte, boolean frontal, MeleeParams p) {
        float dmg = weapon.damage(attack);
        if (riposte) dmg = (float) (dmg * p.riposteDamageBonus() * weapon.parry().riposteMultiplier());
        return new IncomingHit(HitKind.MOD_MELEE, attack, dmg, riposte, frontal);
    }

    public static IncomingHit vanillaMelee(float damage, boolean frontal) {
        return new IncomingHit(HitKind.VANILLA_MELEE, null, damage, false, frontal);
    }

    public static IncomingHit projectile(float damage) {
        return new IncomingHit(HitKind.PROJECTILE, null, damage, false, true);
    }

    public static IncomingHit other(float damage) {
        return new IncomingHit(HitKind.OTHER, null, damage, false, true);
    }

    public boolean parryable() {
        return kind.melee() && !riposte && frontal;
    }
}
