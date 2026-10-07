package com.richardsenger.piratesnships.mob.entity;

import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.mob.MobKind;
import com.richardsenger.piratesnships.mob.ai.DuelistAttackGoal;
import com.richardsenger.piratesnships.mob.ai.VanillaSwordGoal;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Pirate (docs/design.md §9): hostile to players ({@code mobs.pirates_hostile}) and to the navy, a cutlass duelist
 * through the melee engine ({@link DuelistAttackGoal}, skill {@code mobs.pirate_skill}). Pistols and hiring come later.
 * Killing a pirate is no crime.
 */
public class Pirate extends SeafarerMob {

    public Pirate(EntityType<? extends Pirate> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return humanoid(24.0, 0.3, 3.0);
    }

    @Override
    public MobKind kind() {
        return MobKind.PIRATE;
    }

    @Override
    protected void equip() {
        setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CombatContent.CUTLASS.get()));
    }

    @Override
    protected void addCombatGoals() {
        goalSelector.addGoal(2, new DuelistAttackGoal(this, 1.0));
        goalSelector.addGoal(3, new VanillaSwordGoal(this, 1.0));
    }

    @Override
    protected SoundEvent ambientSound() {
        return SoundEvents.PILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent hurtSound() {
        return SoundEvents.PILLAGER_HURT;
    }

    @Override
    protected SoundEvent deathSound() {
        return SoundEvents.PILLAGER_DEATH;
    }
}
