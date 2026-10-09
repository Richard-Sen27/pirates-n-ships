package com.richardsenger.piratesnships.mob.ai;

import com.richardsenger.piratesnships.combat.melee.npc.DuelistSkill;
import net.minecraft.world.entity.LivingEntity;

/**
 * A mob that fights with {@link DuelistAttackGoal} and {@link VanillaSwordGoal} (docs/design.md §8.5 "NPC duelists"):
 * the humanoid mobs ({@code entity.SeafarerMob}) and, while boarding or defending their deck, the crew (BRD3,
 * {@code crew.npc.CrewMember}). It says whom it keeps fighting and how well it fences.
 */
public interface Duelist {

    /** Whether it keeps fighting {@code target} (checked every tick by the goals). */
    boolean keepsTarget(LivingEntity target);

    /** Its sword skill preset, before {@code melee.npc_skill_multiplier}. */
    DuelistSkill duelistSkill();
}
