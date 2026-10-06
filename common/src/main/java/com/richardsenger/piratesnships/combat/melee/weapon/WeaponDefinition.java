package com.richardsenger.piratesnships.combat.melee.weapon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.combat.melee.rules.AttackKind;

/**
 * Data-driven tuning of one skill-based sword (docs/design.md §8.5), loaded from
 * {@code data/<ns>/pirates_n_ships/weapon/<path>.json}. Damage is in half hearts, reach and thickness in blocks,
 * timings in server ticks, stamina in points of {@code melee.stamina_max}. Every number is range-checked by the codec,
 * so a broken datapack file is rejected (and logged with its id) instead of producing a broken weapon.
 */
public record WeaponDefinition(Slash slash, Thrust thrust, Guard guard, Parry parry, float poise) {

    /** Wide, short arc. {@code arcDegrees} is the full horizontal angle (max 180), centered on the look direction. */
    public record Slash(int windupTicks, int activeTicks, int recoveryTicks, float damage, double reach,
                        double arcDegrees, double verticalTolerance, float staminaCost) {
        public static final Codec<Slash> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, 200).fieldOf("windup_ticks").forGetter(Slash::windupTicks),
                Codec.intRange(1, 200).fieldOf("active_ticks").forGetter(Slash::activeTicks),
                Codec.intRange(0, 200).fieldOf("recovery_ticks").forGetter(Slash::recoveryTicks),
                Codec.floatRange(0f, 1000f).fieldOf("damage").forGetter(Slash::damage),
                Codec.doubleRange(0.1, 16.0).fieldOf("reach").forGetter(Slash::reach),
                Codec.doubleRange(1.0, 180.0).fieldOf("arc_degrees").forGetter(Slash::arcDegrees),
                Codec.doubleRange(0.0, 8.0).optionalFieldOf("vertical_tolerance", 0.6).forGetter(Slash::verticalTolerance),
                Codec.floatRange(0f, 1000f).fieldOf("stamina_cost").forGetter(Slash::staminaCost)
        ).apply(i, Slash::new));
    }

    /**
     * Narrow ray from the eye. {@code thickness} is the ray radius; {@code missRecoveryTicks} are added to the recovery
     * when the thrust hit nothing.
     */
    public record Thrust(int windupTicks, int activeTicks, int recoveryTicks, int missRecoveryTicks, float damage,
                         double reach, double thickness, float staminaCost) {
        public static final Codec<Thrust> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, 200).fieldOf("windup_ticks").forGetter(Thrust::windupTicks),
                Codec.intRange(1, 200).fieldOf("active_ticks").forGetter(Thrust::activeTicks),
                Codec.intRange(0, 200).fieldOf("recovery_ticks").forGetter(Thrust::recoveryTicks),
                Codec.intRange(0, 200).optionalFieldOf("miss_recovery_ticks", 0).forGetter(Thrust::missRecoveryTicks),
                Codec.floatRange(0f, 1000f).fieldOf("damage").forGetter(Thrust::damage),
                Codec.doubleRange(0.1, 16.0).fieldOf("reach").forGetter(Thrust::reach),
                Codec.doubleRange(0.0, 2.0).fieldOf("thickness").forGetter(Thrust::thickness),
                Codec.floatRange(0f, 1000f).fieldOf("stamina_cost").forGetter(Thrust::staminaCost)
        ).apply(i, Thrust::new));
    }

    /**
     * Guard: {@code damageReduction} is the blocked fraction of a frontal hit; {@code arcDegrees} the full horizontal
     * angle counted as "front" (also used for parries); a blocked hit costs {@code staminaPerHit + staminaPerDamage *
     * incoming damage}.
     */
    public record Guard(double damageReduction, double arcDegrees, float staminaPerTick, float staminaPerHit,
                        float staminaPerDamage) {
        public static final Codec<Guard> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(0.0, 1.0).fieldOf("damage_reduction").forGetter(Guard::damageReduction),
                Codec.doubleRange(1.0, 360.0).fieldOf("arc_degrees").forGetter(Guard::arcDegrees),
                Codec.floatRange(0f, 100f).fieldOf("stamina_per_tick").forGetter(Guard::staminaPerTick),
                Codec.floatRange(0f, 1000f).fieldOf("stamina_per_hit").forGetter(Guard::staminaPerHit),
                Codec.floatRange(0f, 100f).fieldOf("stamina_per_damage").forGetter(Guard::staminaPerDamage)
        ).apply(i, Guard::new));
    }

    /** Parry: stamina lost when a parry fails, and this weapon's own multiplier on the global riposte bonus. */
    public record Parry(float failedStaminaCost, double riposteMultiplier) {
        public static final Codec<Parry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0f, 1000f).fieldOf("failed_stamina_cost").forGetter(Parry::failedStaminaCost),
                Codec.doubleRange(0.0, 10.0).optionalFieldOf("riposte_multiplier", 1.0).forGetter(Parry::riposteMultiplier)
        ).apply(i, Parry::new));
    }

    /**
     * {@code poise}: a single unguarded hit with at least this much damage staggers the wielder (scaled by
     * {@code melee.exhausted_poise_factor} at zero stamina).
     */
    public static final Codec<WeaponDefinition> CODEC = RecordCodecBuilder.<WeaponDefinition>create(i -> i.group(
            Slash.CODEC.fieldOf("slash").forGetter(WeaponDefinition::slash),
            Thrust.CODEC.fieldOf("thrust").forGetter(WeaponDefinition::thrust),
            Guard.CODEC.fieldOf("guard").forGetter(WeaponDefinition::guard),
            Parry.CODEC.fieldOf("parry").forGetter(WeaponDefinition::parry),
            Codec.floatRange(0f, 1000f).fieldOf("poise").forGetter(WeaponDefinition::poise)
    ).apply(i, WeaponDefinition::new)).validate(WeaponDefinition::validate);

    private static DataResult<WeaponDefinition> validate(WeaponDefinition w) {
        if (w.slash.windupTicks + w.slash.activeTicks + w.slash.recoveryTicks <= 1) {
            return DataResult.error(() -> "slash must take at least 2 ticks in total");
        }
        if (w.thrust.windupTicks + w.thrust.activeTicks + w.thrust.recoveryTicks <= 1) {
            return DataResult.error(() -> "thrust must take at least 2 ticks in total");
        }
        return DataResult.success(w);
    }

    public float damage(AttackKind kind) {
        return kind == AttackKind.THRUST ? thrust.damage : slash.damage;
    }

    public float staminaCost(AttackKind kind) {
        return kind == AttackKind.THRUST ? thrust.staminaCost : slash.staminaCost;
    }

    public double reach(AttackKind kind) {
        return kind == AttackKind.THRUST ? thrust.reach : slash.reach;
    }

    public int windupTicks(AttackKind kind) {
        return kind == AttackKind.THRUST ? thrust.windupTicks : slash.windupTicks;
    }

    public int activeTicks(AttackKind kind) {
        return kind == AttackKind.THRUST ? thrust.activeTicks : slash.activeTicks;
    }

    /** Recovery after the active phase; a thrust that hit nothing recovers longer. */
    public int recoveryTicks(AttackKind kind, boolean hit) {
        return kind == AttackKind.THRUST ? thrust.recoveryTicks + (hit ? 0 : thrust.missRecoveryTicks) : slash.recoveryTicks;
    }

    /** Wind-up + active + recovery of a hitting attack. */
    public int totalTicks(AttackKind kind) {
        return windupTicks(kind) + activeTicks(kind) + recoveryTicks(kind, true);
    }
}
