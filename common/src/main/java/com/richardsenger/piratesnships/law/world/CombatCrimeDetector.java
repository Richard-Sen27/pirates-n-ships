package com.richardsenger.piratesnships.law.world;

import com.richardsenger.piratesnships.law.LawConfig;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.crime.CrimeType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Reports combat crimes (docs/design.md §13.1) from {@code LIVING_INCOMING_DAMAGE} and {@code LIVING_DEATH}. The
 * decision is {@link CombatCrimes}; this class only identifies the parties. Repeats are handled by
 * {@code CriminalRecord} (one fight = one attack crime per repeat window).
 *
 * <p>The offender is {@link DamageSource#getEntity()}: the shooter for projectiles, the igniter for TNT, the attacker
 * for melee. A tamed animal's damage is the owner's crime when the owner is online. Damage without an entity
 * (fall, fire, lava, cactus, {@code /kill}) is no crime. A kill also reports the attack of the killing blow, so a
 * one-hit kill of a villager costs attack + kill points, the same as hitting it and finishing it off.
 */
public final class CombatCrimeDetector {

    private CombatCrimeDetector() {
    }

    /** Listener for {@code CommonEvents.LIVING_INCOMING_DAMAGE}. Never changes the damage. */
    public static float onIncomingDamage(LivingEntity victim, DamageSource source, float amount) {
        if (amount > 0) report(victim, source, false);
        return amount;
    }

    /** Listener for {@code CommonEvents.LIVING_DEATH}. Never cancels the death. */
    public static boolean onDeath(LivingEntity victim, DamageSource source) {
        report(victim, source, true);
        return false;
    }

    /** Classifies and reports; returns the reported crime (tests). */
    public static Optional<CrimeType> report(LivingEntity victim, DamageSource source, boolean kill) {
        if (victim.level().isClientSide() || !LawConfig.COMBAT_CRIMES.get()) return Optional.empty();
        LivingEntity offender = offender(source);
        CombatCrimes.Party attacker = offender == null ? null : party(offender);
        Optional<CrimeType> crime = CombatCrimes.classify(attacker, party(victim), offender == victim, kill,
                LawConfig.PROSECUTE_MONSTERS.get());
        crime.ifPresent(type -> LawService.reportCrime(offender, type, victim));
        return crime;
    }

    /** The living entity responsible for {@code source}: the attacker, or a tamed attacker's owner. */
    public static @Nullable LivingEntity offender(DamageSource source) {
        Entity e = source.getEntity();
        if (!(e instanceof LivingEntity living)) return null;
        if (living instanceof OwnableEntity pet && pet.getOwner() != null) return pet.getOwner();
        return living;
    }

    static CombatCrimes.Party party(LivingEntity e) {
        boolean navy = e.getType().is(LawTags.NAVY);
        return new CombatCrimes.Party(navy, e.getType().is(LawTags.LAW_PROTECTED),
                navy || e.getType().is(LawTags.LAW_ENFORCERS), e instanceof Enemy);
    }
}
