package com.richardsenger.piratesnships.combat.melee.npc;

/**
 * Skill presets from clumsy to dangerous. Reaction times are measured against the default wind-ups (4 to 8 ticks):
 * a clumsy sailor reacts after most attacks have already landed, a captain well inside the wind-up.
 */
public enum DuelistSkill {
    CLUMSY_SAILOR(new SkillTier(0.10, 12, 0.00)),
    SAILOR(new SkillTier(0.25, 9, 0.05)),
    NAVY_SOLDIER(new SkillTier(0.40, 7, 0.10)),
    PIRATE(new SkillTier(0.45, 6, 0.20)),
    NAVY_OFFICER(new SkillTier(0.60, 5, 0.25)),
    PIRATE_CAPTAIN(new SkillTier(0.80, 3, 0.40));

    private final SkillTier tier;

    DuelistSkill(SkillTier tier) {
        this.tier = tier;
    }

    /** The unscaled preset. */
    public SkillTier base() {
        return tier;
    }

    /** The preset with the config's NPC skill multiplier applied. */
    public SkillTier tier(double npcSkillMultiplier) {
        return tier.scaled(npcSkillMultiplier);
    }
}
