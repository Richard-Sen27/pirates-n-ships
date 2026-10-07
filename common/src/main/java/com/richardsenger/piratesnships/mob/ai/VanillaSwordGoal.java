package com.richardsenger.piratesnships.mob.ai;

import com.richardsenger.piratesnships.combat.melee.MeleeService;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/**
 * Vanilla melee for duelists while {@code melee.skill_based_combat} is off (mod swords then fight like vanilla
 * swords for everyone, docs/design.md §8.5), or when the mob lost its sword.
 */
public class VanillaSwordGoal extends MeleeAttackGoal {

    public VanillaSwordGoal(PathfinderMob mob, double speed) {
        super(mob, speed, true);
    }

    private boolean duelistActive() {
        return MeleeService.skillBasedCombat() && MeleeService.weaponInHand(mob).isPresent();
    }

    @Override
    public boolean canUse() {
        return !duelistActive() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return !duelistActive() && super.canContinueToUse();
    }
}
