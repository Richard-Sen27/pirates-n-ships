package com.richardsenger.piratesnships.combat.melee.npc;

import com.richardsenger.piratesnships.combat.melee.MeleeConfig;
import com.richardsenger.piratesnships.combat.melee.rules.MeleeParams;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillTierTest {

    @Test
    void presetsGetStrictlyBetter() {
        DuelistSkill[] all = DuelistSkill.values();
        for (int i = 1; i < all.length; i++) {
            SkillTier lo = all[i - 1].base(), hi = all[i].base();
            assertTrue(hi.parryChance() > lo.parryChance(), all[i] + " parries more");
            assertTrue(hi.reactionTicks() < lo.reactionTicks(), all[i] + " reacts faster");
            assertTrue(hi.feintFrequency() >= lo.feintFrequency(), all[i] + " feints at least as often");
        }
        assertEquals(DuelistSkill.CLUMSY_SAILOR, all[0]);
        assertEquals(DuelistSkill.PIRATE_CAPTAIN, all[all.length - 1]);
    }

    @Test
    void multiplierScalesAndClamps() {
        SkillTier t = new SkillTier(0.6, 6, 0.3);
        assertEquals(t, t.scaled(1.0));
        SkillTier half = t.scaled(0.5);
        assertEquals(0.3, half.parryChance(), 1e-9);
        assertEquals(12, half.reactionTicks());
        assertEquals(0.15, half.feintFrequency(), 1e-9);
        SkillTier dbl = t.scaled(2.0);
        assertEquals(1.0, dbl.parryChance(), 1e-9, "clamped");
        assertEquals(3, dbl.reactionTicks());
        assertEquals(new SkillTier(0, SkillTier.MAX_REACTION_TICKS, 0), t.scaled(0));
        assertEquals(new SkillTier(0, SkillTier.MAX_REACTION_TICKS, 0), t.scaled(Double.NaN));
        assertEquals(SkillTier.MAX_REACTION_TICKS, new SkillTier(0.1, 80, 0).scaled(0.1).reactionTicks());
    }

    @Test
    void configMultiplierReachesTheTier() {
        var h = ConfigOverrides.apply(MeleeConfig.NPC_SKILL, 0.5);
        try {
            MeleeParams p = MeleeConfig.params();
            assertEquals(0.5, p.npcSkillMultiplier());
            assertEquals(DuelistSkill.PIRATE_CAPTAIN.base().scaled(0.5), DuelistSkill.PIRATE_CAPTAIN.tier(p.npcSkillMultiplier()));
        } finally {
            h.restore();
        }
        assertEquals(MeleeParams.DEFAULTS, MeleeConfig.params(), "config defaults equal the pure defaults");
    }
}
