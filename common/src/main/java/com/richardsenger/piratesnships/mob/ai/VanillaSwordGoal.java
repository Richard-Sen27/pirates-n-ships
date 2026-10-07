package com.richardsenger.piratesnships.mob.ai;

import com.richardsenger.piratesnships.combat.melee.MeleeService;
import com.richardsenger.piratesnships.mob.entity.SeafarerMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/**
 * Vanilla melee for duelists while {@code melee.skill_based_combat} is off (mod swords then fight like vanilla
 * swords for everyone, docs/design.md §8.5), or when the mob lost its sword.
 */
public class VanillaSwordGoal extends MeleeAttackGoal {

    private final SeafarerMob seafarer;

    public VanillaSwordGoal(SeafarerMob mob, double speed) {
        super(mob, speed, true);
        this.seafarer = mob;
    }

    private boolean allowed() {
        LivingEntity t = mob.getTarget();
        boolean duelist = MeleeService.skillBasedCombat() && MeleeService.weaponInHand(mob).isPresent();
        // keepsTarget: the goal selector ticks before customServerAiStep re-checks a target set from outside
        return !duelist && t != null && seafarer.keepsTarget(t);
    }

    @Override
    public boolean canUse() {
        return allowed() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return allowed() && super.canContinueToUse();
    }
}
