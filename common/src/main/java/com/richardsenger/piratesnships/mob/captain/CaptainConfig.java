package com.richardsenger.piratesnships.mob.captain;

import com.richardsenger.piratesnships.combat.melee.npc.DuelistSkill;
import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.mob.MobConfig;
import com.richardsenger.piratesnships.mob.MobKind;

/**
 * Server config section {@code mobs.captain} (BOS1, docs/design.md §15, §17): the named pirate captain of each pirate
 * island. {@code enabled} and {@code peaceful} are the per-type toggles every mob has ({@link MobConfig#enabled},
 * {@link MobConfig#peaceful}); this class adds the rest to the same section.
 */
public final class CaptainConfig {

    private static final ConfigSection S = MobConfig.CAPTAIN;

    public static final ConfigValue<Double> HEALTH = S.doubleRange("health", 40.0, 1.0, 1024.0,
            "Health of a pirate captain (a pirate has 24), applied when he is placed");
    public static final ConfigValue<DuelistSkill> SKILL = S.enumValue("skill", DuelistSkill.PIRATE_CAPTAIN,
            "Sword skill of pirate captains; scaled by melee.npc_skill_multiplier");
    public static final ConfigValue<Integer> BOUNTY = S.intRange("bounty", 300, 0, 1_000_000,
            "Doubloons of the navy's standing bounty on every pirate captain (0 = none); listed on the notice boards");
    public static final ConfigValue<Integer> RESPAWN_DAYS = S.intRange("respawn_days", 5, 0, 365,
            "Days after a captain's death or capture until a successor takes over his post (once its chunk is loaded)");
    public static final ConfigValue<Boolean> DUEL_ENABLED = S.bool("duel_enabled", true,
            "A player can challenge a captain to a duel: sneak and use him with a sword in hand");
    public static final ConfigValue<Double> DUEL_TRUCE_RANGE = S.doubleRange("duel_truce_range", 16.0, 0.0, 64.0,
            "Blocks around the captain within which his crew keep out of a duel (a truce with the challenger)");
    public static final ConfigValue<Double> DUEL_LEAVE_RANGE = S.doubleRange("duel_leave_range", 32.0, 2.0, 128.0,
            "A challenger farther than this from the captain has left the duel; the truce ends");
    public static final ConfigValue<Integer> DUEL_MAX_SECONDS = S.intRange("duel_max_seconds", 300, 10, 3600,
            "Longest duel; after it the truce ends and the crew fight again");
    public static final ConfigValue<Boolean> DROP_MAP = S.bool("drop_map", true,
            "A slain captain drops a treasure map of his island (while it has unlooted treasure)");

    private CaptainConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code MobConfig.init()}. */
    public static void init() {
    }

    /** {@code mobs.captain.enabled}: off = no captain is placed or succeeds, existing ones disappear. */
    public static boolean enabled() {
        return MobConfig.enabled(MobKind.PIRATE_CAPTAIN).get();
    }

    /** The duel rules' config snapshot. */
    public static DuelRules.Params duel() {
        return new DuelRules.Params(DUEL_ENABLED.get(), DUEL_TRUCE_RANGE.get(), DUEL_LEAVE_RANGE.get(), DUEL_MAX_SECONDS.get() * 20L);
    }
}
