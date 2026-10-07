package com.richardsenger.piratesnships.mob;

import com.richardsenger.piratesnships.combat.melee.npc.DuelistSkill;
import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;

import java.util.EnumMap;
import java.util.Map;

/**
 * Server config section {@code mobs} (docs/design.md §9, §17): the humanoid mobs' toggles, hostility, duelist skill,
 * musket and drop settings. The navy's wanted-level threshold is the law's {@code law.world.navy_hostility_threshold}.
 */
public final class MobConfig {

    private static final ConfigSection S = ModConfigs.server("mobs", "Pirates, sailors, navy soldiers and officers");

    // --- hostility ----------------------------------------------------------------------------------------------
    public static final ConfigValue<Boolean> PIRATES_HOSTILE = S.bool("pirates_hostile", true,
            "Pirates attack players on sight (off = they only fight back)");
    public static final ConfigValue<Boolean> NAVY_HOSTILE = S.bool("navy_hostile", true,
            "Navy soldiers and officers attack players and NPCs the law wants (wanted level from law.world.navy_hostility_threshold)");
    public static final ConfigValue<Boolean> FACTIONS_FIGHT = S.bool("factions_fight", true,
            "Pirates and navy attack each other on sight");
    public static final ConfigValue<Double> DETECTION_RANGE = S.doubleRange("detection_range", 16.0, 1.0, 64.0,
            "Blocks within which a mob notices a player it is hostile to (and keeps following a target)");
    public static final ConfigValue<Double> FACTION_FIGHT_RANGE = S.doubleRange("faction_fight_range", 12.0, 1.0, 64.0,
            "Blocks within which pirates and navy notice each other");
    public static final ConfigValue<Integer> GRUDGE_TICKS = S.intRange("grudge_ticks", 600, 0, 24000,
            "Ticks a mob keeps fighting someone who hit it, even if it would not attack them on sight");
    public static final ConfigValue<Double> SAILOR_FLEE_RANGE = S.doubleRange("sailor_flee_range", 8.0, 1.0, 32.0,
            "Blocks within which sailors run from pirates, monsters and anyone targeting them");

    // --- duelists -----------------------------------------------------------------------------------------------
    public static final ConfigValue<DuelistSkill> PIRATE_SKILL = S.enumValue("pirate_skill", DuelistSkill.PIRATE,
            "Sword skill of pirates (parry chance, reaction time); scaled by melee.npc_skill_multiplier");
    public static final ConfigValue<DuelistSkill> OFFICER_SKILL = S.enumValue("officer_skill", DuelistSkill.NAVY_OFFICER,
            "Sword skill of navy officers; scaled by melee.npc_skill_multiplier");
    public static final ConfigValue<Integer> DUELIST_ATTACK_PAUSE = S.intRange("duelist_attack_pause_ticks", 15, 0, 200,
            "Least ticks a duelist waits between two attacks (a random extra of up to the same again is added)");
    public static final ConfigValue<Double> DUELIST_GUARD_STAMINA = S.doubleRange("duelist_guard_stamina", 25.0, 0.0, 10000.0,
            "Duelists only raise their guard with at least this much stamina");

    // --- musketeers ---------------------------------------------------------------------------------------------
    public static final ConfigValue<Integer> MUSKET_RELOAD_TICKS = S.intRange("musket_reload_ticks", 100, 1, 1200,
            "Ticks a navy soldier needs to reload its musket");
    public static final ConfigValue<Integer> MUSKET_AIM_TICKS = S.intRange("musket_aim_ticks", 20, 0, 200,
            "Ticks of steady aim before a navy soldier fires");
    public static final ConfigValue<Boolean> NAVY_INFINITE_AMMO = S.bool("navy_infinite_ammo", true,
            "Navy soldiers never run out of shot (off = each carries musket_shots shots)");
    public static final ConfigValue<Integer> MUSKET_SHOTS = S.intRange("musket_shots", 12, 0, 1000,
            "Shots a navy soldier carries when navy_infinite_ammo is off");
    public static final ConfigValue<Double> MUSKET_RANGE = S.doubleRange("musket_range", 20.0, 2.0, 64.0,
            "Farthest distance a navy soldier shoots at");
    public static final ConfigValue<Double> MUSKET_MIN_RANGE = S.doubleRange("musket_min_range", 4.0, 0.0, 32.0,
            "A navy soldier backs off from targets closer than this");
    public static final ConfigValue<Double> SHOVE_RANGE = S.doubleRange("shove_range", 1.8, 0.0, 8.0,
            "A navy soldier shoves targets this close with the musket butt");
    public static final ConfigValue<Double> SHOVE_DAMAGE = S.doubleRange("shove_damage", 2.0, 0.0, 100.0,
            "Damage of a musket-butt shove");
    public static final ConfigValue<Double> SHOVE_KNOCKBACK = S.doubleRange("shove_knockback", 1.0, 0.0, 10.0,
            "Knockback strength of a musket-butt shove");
    public static final ConfigValue<Integer> SHOVE_COOLDOWN = S.intRange("shove_cooldown_ticks", 30, 1, 1200,
            "Ticks between two shoves");

    // --- drops --------------------------------------------------------------------------------------------------
    public static final ConfigValue<Boolean> DROPS = S.bool("drops", true,
            "Mobs drop their loot table (doubloons and cutlasses from pirates, shot and gunpowder from navy)");

    // --- per type -----------------------------------------------------------------------------------------------
    private static final Map<MobKind, ConfigValue<Boolean>> ENABLED = new EnumMap<>(MobKind.class);
    private static final Map<MobKind, ConfigValue<Boolean>> PEACEFUL = new EnumMap<>(MobKind.class);

    static {
        for (MobKind kind : MobKind.values()) {
            ConfigSection t = S.section(kind.id(), "The " + kind.id().replace('_', ' '));
            ENABLED.put(kind, t.bool("enabled", true,
                    "This mob exists: off = it can't be spawned and existing ones disappear"));
            if (kind.faction() != MobFaction.CIVILIAN) {
                PEACEFUL.put(kind, t.bool("peaceful", false, "Never attacks anyone and doesn't fight back"));
            }
        }
    }

    private MobConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }

    public static ConfigValue<Boolean> enabled(MobKind kind) {
        return ENABLED.get(kind);
    }

    /** The peaceful toggle of a fighting type ({@code null} for sailors, which never fight). */
    public static ConfigValue<Boolean> peaceful(MobKind kind) {
        return PEACEFUL.get(kind);
    }

    public static boolean isPeaceful(MobKind kind) {
        ConfigValue<Boolean> v = PEACEFUL.get(kind);
        return v == null || v.get();
    }

    /** Hostility switches as seen by a mob of {@code kind}. */
    public static HostilityRules.Params hostility(MobKind kind) {
        return new HostilityRules.Params(PIRATES_HOSTILE.get(), NAVY_HOSTILE.get(), FACTIONS_FIGHT.get(), isPeaceful(kind));
    }

    /** The configured sword skill of a duelist type ({@code null} for the others). */
    public static DuelistSkill skill(MobKind kind) {
        return switch (kind) {
            case PIRATE -> PIRATE_SKILL.get();
            case NAVY_OFFICER -> OFFICER_SKILL.get();
            default -> kind.defaultSkill();
        };
    }

    public static MusketRules.Params musket() {
        return new MusketRules.Params(MUSKET_RANGE.get(), MUSKET_MIN_RANGE.get(), SHOVE_RANGE.get(), MUSKET_AIM_TICKS.get());
    }
}
