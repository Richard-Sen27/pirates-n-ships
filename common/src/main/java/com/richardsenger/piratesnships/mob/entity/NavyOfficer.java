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
 * Navy officer (docs/design.md §9): a skilled saber duelist ({@link DuelistAttackGoal}, skill
 * {@code mobs.officer_skill}), hostile like the soldiers. Leading soldiers, turn-ins and quests come later. Hitting
 * or killing it is a crime ({@code #pirates_n_ships:navy}).
 */
public class NavyOfficer extends SeafarerMob {

    public NavyOfficer(EntityType<? extends NavyOfficer> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return humanoid(30.0, 0.3, 3.0);
    }

    @Override
    public MobKind kind() {
        return MobKind.NAVY_OFFICER;
    }

    @Override
    protected void equip() {
        setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CombatContent.SABER.get()));
    }

    @Override
    protected void addCombatGoals() {
        goalSelector.addGoal(2, new DuelistAttackGoal(this, 1.0));
        goalSelector.addGoal(3, new VanillaSwordGoal(this, 1.0));
    }

    @Override
    protected SoundEvent ambientSound() {
        return SoundEvents.VINDICATOR_AMBIENT;
    }

    @Override
    protected SoundEvent hurtSound() {
        return SoundEvents.VINDICATOR_HURT;
    }

    @Override
    protected SoundEvent deathSound() {
        return SoundEvents.VINDICATOR_DEATH;
    }
}
