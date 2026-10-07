package com.richardsenger.piratesnships.combat.melee;

import com.richardsenger.piratesnships.combat.melee.rules.MeleeParams;
import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

/**
 * Server config section {@code melee} (docs/design.md §17). Defaults come from {@link MeleeParams#DEFAULTS};
 * {@link #params()} is the only adapter from config to the pure rules. Per-weapon numbers live in the weapon
 * datapack files, not here.
 */
public final class MeleeConfig {

    private static final MeleeParams D = MeleeParams.DEFAULTS;

    private static final ConfigSection S = ModConfigs.server("melee", "Skill-based swordplay: parry, guard, stamina and stagger");

    public static final ConfigValue<Boolean> SKILL_BASED = S.bool("skill_based_combat", D.skillBased(),
            "Skill-based sword fighting (slash, thrust, guard, parry, riposte, stamina). Off = mod swords fight like vanilla swords");
    public static final ConfigValue<Boolean> DIRECTIONAL_MODE = S.bool("directional_mode", D.directionalMode(),
            "Directional attacks and parries (not implemented yet, no effect)");
    public static final ConfigValue<Integer> PARRY_WINDOW = S.intRange("parry_window_ticks", D.parryWindowTicks(), 1, 40,
            "Ticks a parry stays open after the input; a hit landing in this window is deflected");
    public static final ConfigValue<Integer> LATENCY_ALLOWANCE = S.intRange("latency_allowance_ticks", D.latencyAllowanceTicks(), 0, 10,
            "A sword hit on a defender who could parry is held this many ticks, so a parry input delayed by lag still counts");
    public static final ConfigValue<Integer> PARRY_LOCKOUT = S.intRange("parry_lockout_ticks", D.parryLockoutTicks(), 0, 200,
            "Ticks after a failed parry before the next parry is possible");
    public static final ConfigValue<Double> STAMINA_MAX = S.doubleRange("stamina_max", D.staminaMax(), 1.0, 10000.0,
            "Full stamina");
    public static final ConfigValue<Double> STAMINA_REGEN = S.doubleRange("stamina_regen_per_tick", D.staminaRegenPerTick(), 0.0, 1000.0,
            "Stamina regained per tick when not attacking, guarding or parrying");
    public static final ConfigValue<Integer> STAMINA_REGEN_DELAY = S.intRange("stamina_regen_delay_ticks", D.staminaRegenDelayTicks(), 0, 1200,
            "Ticks after spending stamina before it regenerates");
    public static final ConfigValue<Double> STAMINA_COST_MULTIPLIER = S.doubleRange("stamina_cost_multiplier", D.staminaCostMultiplier(), 0.0, 100.0,
            "Multiplies every stamina cost of every weapon (attacks, guard, failed parries)");
    public static final ConfigValue<Integer> RIPOSTE_WINDOW = S.intRange("riposte_window_ticks", D.riposteWindowTicks(), 0, 200,
            "Ticks after a successful parry in which an attack becomes a riposte");
    public static final ConfigValue<Double> RIPOSTE_BONUS = S.doubleRange("riposte_damage_bonus", D.riposteDamageBonus(), 0.0, 10.0,
            "Damage multiplier of a riposte (times the weapon's own riposte multiplier)");
    public static final ConfigValue<Integer> STAGGER = S.intRange("stagger_ticks", D.staggerTicks(), 0, 200,
            "Stagger from a thrust into recovery, or from a hit that beats the defender's poise");
    public static final ConfigValue<Integer> PARRY_STAGGER = S.intRange("parry_stagger_ticks", D.parryStaggerTicks(), 0, 200,
            "Stagger of an attacker whose hit was parried");
    public static final ConfigValue<Integer> GUARD_BREAK_STAGGER = S.intRange("guard_break_stagger_ticks", D.guardBreakStaggerTicks(), 0, 200,
            "Stagger when a blocked hit empties the defender's stamina");
    public static final ConfigValue<Boolean> GUARD_ABSORBS_ALL = S.bool("guard_absorbs_all", D.guardAbsorbsAll(),
            "A successful guard blocks the whole frontal hit (it still costs stamina). Off = a guard only reduces the damage by the weapon's guard reduction. A guard break always deals the reduced damage");
    public static final ConfigValue<Double> GUARD_ABSORB_STAMINA = S.doubleRange("guard_absorb_stamina_per_damage", D.guardAbsorbStaminaPerDamage(), 0.0, 20.0,
            "Extra stamina a blocked frontal hit costs per point of damage the guard absorbs, on top of the weapon's own block cost (0 = only the weapon's cost)");
    public static final ConfigValue<Double> EXHAUSTED_POISE = S.doubleRange("exhausted_poise_factor", D.exhaustedPoiseFactor(), 0.0, 1.0,
            "Poise multiplier at zero stamina (lower = staggered more easily, 0 = every hit staggers)");
    public static final ConfigValue<Double> NPC_SKILL = S.doubleRange("npc_skill_multiplier", D.npcSkillMultiplier(), 0.0, 10.0,
            "Scales NPC duelists' parry chance, reaction speed and feint frequency");
    public static final ConfigValue<Integer> FEINT_RECOVERY = S.intRange("feint_recovery_ticks", D.feintRecoveryTicks(), 0, 200,
            "Recovery after a feint (an attack aborted during its wind-up): no hit frames, no guard or parry, a new attack may start");
    public static final ConfigValue<Boolean> NPC_FEINTS = S.bool("npc_feints", true,
            "NPC duelists feint (start an attack and abort it when the opponent parries or guards), as often as their skill tier says");
    public static final ConfigValue<Integer> STAMINA_SYNC_INTERVAL = S.intRange("stamina_sync_interval_ticks", 5, 1, 100,
            "Shortest interval between two stamina updates sent to a player for the HUD (phase changes are sent at once)");

    private static final ConfigSection SOUNDS = S.section("sounds", "Sword sounds: swings, hits, parries, guards, staggers and drawing a sword");
    public static final ConfigValue<Boolean> SOUNDS_ENABLED = SOUNDS.bool("enabled", true,
            "Sword fights play sounds (swing, hit, armour, parry, guard, stagger, feint, drawing a sword). Heard by every nearby player");
    public static final ConfigValue<Double> SOUNDS_VOLUME = SOUNDS.doubleRange("volume", 1.0, 0.0, 4.0,
            "Multiplies the volume of every sword sound (above 1 they also carry further)");
    public static final ConfigValue<Integer> UNSHEATHE_COOLDOWN = SOUNDS.intRange("unsheathe_cooldown_ticks", 10, 0, 200,
            "Shortest interval between two sword-drawing sounds of one player (scrolling the hotbar across two swords sounds once)");

    private MeleeConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }

    /** The sound settings from the current config. */
    public static com.richardsenger.piratesnships.combat.melee.sound.MeleeSoundRules.Params soundParams() {
        return new com.richardsenger.piratesnships.combat.melee.sound.MeleeSoundRules.Params(SOUNDS_ENABLED.get(), SOUNDS_VOLUME.get());
    }

    /** The melee rules from the current config. */
    public static MeleeParams params() {
        return new MeleeParams(SKILL_BASED.get(), DIRECTIONAL_MODE.get(), PARRY_WINDOW.get(), LATENCY_ALLOWANCE.get(),
                PARRY_LOCKOUT.get(), STAMINA_MAX.get().floatValue(), STAMINA_REGEN.get().floatValue(), STAMINA_REGEN_DELAY.get(),
                STAMINA_COST_MULTIPLIER.get(), RIPOSTE_WINDOW.get(), RIPOSTE_BONUS.get(), STAGGER.get(), PARRY_STAGGER.get(),
                GUARD_BREAK_STAGGER.get(), EXHAUSTED_POISE.get(), NPC_SKILL.get(), FEINT_RECOVERY.get(),
                GUARD_ABSORBS_ALL.get(), GUARD_ABSORB_STAMINA.get());
    }
}
