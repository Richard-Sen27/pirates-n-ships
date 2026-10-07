package com.richardsenger.piratesnships.combat.melee.npc;

/**
 * How well an NPC duelist fights (docs/design.md §8.5 "NPC duelists"). Data only; the duel AI ({@link DuelistBrain}) reads it.
 *
 * @param parryChance    probability 0..1 that the NPC tries to parry a readable attack
 * @param reactionTicks  ticks between seeing a telegraph and reacting
 * @param feintFrequency probability 0..1 per attack of using a feint ({@link DuelistBrain#planFeint})
 */
public record SkillTier(double parryChance, int reactionTicks, double feintFrequency) {

    /** Slowest possible reaction (used when the multiplier is zero). */
    public static final int MAX_REACTION_TICKS = 100;

    public SkillTier {
        parryChance = clamp01(parryChance);
        feintFrequency = clamp01(feintFrequency);
        reactionTicks = Math.max(0, Math.min(MAX_REACTION_TICKS, reactionTicks));
    }

    /**
     * Applies {@code melee.npc_skill_multiplier}: chances are multiplied (clamped to 0..1), the reaction time is
     * divided (rounded, clamped to 0..{@link #MAX_REACTION_TICKS}). 1 = unchanged, 0 = helpless.
     */
    public SkillTier scaled(double multiplier) {
        if (!(multiplier > 0)) return new SkillTier(0, MAX_REACTION_TICKS, 0);
        return new SkillTier(parryChance * multiplier, (int) Math.round(reactionTicks / multiplier), feintFrequency * multiplier);
    }

    private static double clamp01(double v) {
        return Double.isNaN(v) ? 0 : Math.max(0, Math.min(1, v));
    }
}
