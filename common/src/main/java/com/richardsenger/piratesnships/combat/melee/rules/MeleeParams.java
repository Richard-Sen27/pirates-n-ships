package com.richardsenger.piratesnships.combat.melee.rules;

/**
 * Global (not per weapon) melee rules, copied from the {@code melee} server config by {@code MeleeConfig.params()}.
 * Pure logic only ever sees this record. All durations are server ticks.
 *
 * @param skillBased           the one switch: off = the state machine is not used and mod swords act like vanilla swords
 * @param directionalMode      reserved for directional attacks/parries (milestone 21), currently without effect
 * @param parryWindowTicks     how long a parry stays open; a hit landing 0..window-1 ticks after the parry is deflected
 * @param latencyAllowanceTicks a mod melee hit may also be parried by a parry that starts up to this many ticks after it
 * @param parryLockoutTicks    after a failed parry, no new parry for this long
 * @param staminaMax           full stamina
 * @param staminaRegenPerTick  stamina regained per tick while idle or staggered, after the delay
 * @param staminaRegenDelayTicks ticks after the last stamina spend before regeneration starts
 * @param staminaCostMultiplier scales every stamina cost of every weapon (attacks, guard, failed parries)
 * @param riposteWindowTicks   after a successful parry, an attack started within this many ticks is a riposte
 * @param riposteDamageBonus   damage multiplier of a riposte (times the weapon's own riposte multiplier)
 * @param staggerTicks         stagger from a thrust into recovery or from a hit that beats the defender's poise
 * @param parryStaggerTicks    stagger of an attacker whose hit was parried
 * @param guardBreakStaggerTicks stagger when a blocked hit empties the stamina
 * @param exhaustedPoiseFactor poise multiplier at zero stamina (lower = staggered more easily)
 * @param npcSkillMultiplier   scales NPC duelist skill tiers
 * @param feintRecoveryTicks   recovery after a feint (an attack aborted during its wind-up); 0 = straight back to idle
 * @param guardAbsorbsAll      a successful guard (enough stamina for the blocked hit) absorbs the whole frontal hit;
 *                             off = it only takes off the weapon's guard {@code damageReduction} (the pre-P9 rule).
 *                             A guard break always deals the reduced damage.
 * @param guardAbsorbStaminaPerDamage extra stamina per point of damage a guard absorbs, on top of the weapon's own
 *                             block cost (times {@code staminaCostMultiplier}); 0 = only the weapon's cost
 */
public record MeleeParams(boolean skillBased, boolean directionalMode, int parryWindowTicks, int latencyAllowanceTicks,
                          int parryLockoutTicks, float staminaMax, float staminaRegenPerTick, int staminaRegenDelayTicks,
                          double staminaCostMultiplier, int riposteWindowTicks, double riposteDamageBonus, int staggerTicks,
                          int parryStaggerTicks, int guardBreakStaggerTicks, double exhaustedPoiseFactor,
                          double npcSkillMultiplier, int feintRecoveryTicks, boolean guardAbsorbsAll,
                          double guardAbsorbStaminaPerDamage) {

    public static final MeleeParams DEFAULTS = new MeleeParams(true, false, 7, 2, 15, 100f, 1.0f, 20, 1.0,
            20, 1.5, 20, 25, 30, 0.5, 1.0, 6, true, 1.0);

    public MeleeParams {
        if (parryWindowTicks < 1) throw new IllegalArgumentException("parryWindowTicks must be >= 1");
        if (latencyAllowanceTicks < 0) throw new IllegalArgumentException("latencyAllowanceTicks must be >= 0");
        if (!(staminaMax > 0)) throw new IllegalArgumentException("staminaMax must be > 0");
        if (!(guardAbsorbStaminaPerDamage >= 0)) throw new IllegalArgumentException("guardAbsorbStaminaPerDamage must be >= 0");
        if (feintRecoveryTicks < 0) throw new IllegalArgumentException("feintRecoveryTicks must be >= 0");
    }

    public MeleeParams withSkillBased(boolean on) {
        return new MeleeParams(on, directionalMode, parryWindowTicks, latencyAllowanceTicks, parryLockoutTicks, staminaMax,
                staminaRegenPerTick, staminaRegenDelayTicks, staminaCostMultiplier, riposteWindowTicks, riposteDamageBonus,
                staggerTicks, parryStaggerTicks, guardBreakStaggerTicks, exhaustedPoiseFactor, npcSkillMultiplier, feintRecoveryTicks, guardAbsorbsAll, guardAbsorbStaminaPerDamage);
    }

    public MeleeParams withParryTiming(int window, int allowance, int lockout) {
        return new MeleeParams(skillBased, directionalMode, window, allowance, lockout, staminaMax,
                staminaRegenPerTick, staminaRegenDelayTicks, staminaCostMultiplier, riposteWindowTicks, riposteDamageBonus,
                staggerTicks, parryStaggerTicks, guardBreakStaggerTicks, exhaustedPoiseFactor, npcSkillMultiplier, feintRecoveryTicks, guardAbsorbsAll, guardAbsorbStaminaPerDamage);
    }

    public MeleeParams withStamina(float max, float regenPerTick, int regenDelay, double costMultiplier) {
        return new MeleeParams(skillBased, directionalMode, parryWindowTicks, latencyAllowanceTicks, parryLockoutTicks, max,
                regenPerTick, regenDelay, costMultiplier, riposteWindowTicks, riposteDamageBonus,
                staggerTicks, parryStaggerTicks, guardBreakStaggerTicks, exhaustedPoiseFactor, npcSkillMultiplier, feintRecoveryTicks, guardAbsorbsAll, guardAbsorbStaminaPerDamage);
    }

    public MeleeParams withFeintRecovery(int ticks) {
        return new MeleeParams(skillBased, directionalMode, parryWindowTicks, latencyAllowanceTicks, parryLockoutTicks, staminaMax,
                staminaRegenPerTick, staminaRegenDelayTicks, staminaCostMultiplier, riposteWindowTicks, riposteDamageBonus,
                staggerTicks, parryStaggerTicks, guardBreakStaggerTicks, exhaustedPoiseFactor, npcSkillMultiplier, ticks, guardAbsorbsAll, guardAbsorbStaminaPerDamage);
    }

    public MeleeParams withGuardAbsorbsAll(boolean on) {
        return new MeleeParams(skillBased, directionalMode, parryWindowTicks, latencyAllowanceTicks, parryLockoutTicks, staminaMax,
                staminaRegenPerTick, staminaRegenDelayTicks, staminaCostMultiplier, riposteWindowTicks, riposteDamageBonus,
                staggerTicks, parryStaggerTicks, guardBreakStaggerTicks, exhaustedPoiseFactor, npcSkillMultiplier, feintRecoveryTicks, on, guardAbsorbStaminaPerDamage);
    }

    public MeleeParams withGuardAbsorbStaminaPerDamage(double perDamage) {
        return new MeleeParams(skillBased, directionalMode, parryWindowTicks, latencyAllowanceTicks, parryLockoutTicks, staminaMax,
                staminaRegenPerTick, staminaRegenDelayTicks, staminaCostMultiplier, riposteWindowTicks, riposteDamageBonus,
                staggerTicks, parryStaggerTicks, guardBreakStaggerTicks, exhaustedPoiseFactor, npcSkillMultiplier, feintRecoveryTicks,
                guardAbsorbsAll, perDamage);
    }
}
