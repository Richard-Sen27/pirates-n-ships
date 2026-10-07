package com.richardsenger.piratesnships.mob.entity;

import com.richardsenger.piratesnships.mob.HostilityRules;
import com.richardsenger.piratesnships.mob.MobConfig;
import com.richardsenger.piratesnships.mob.MobFaction;
import com.richardsenger.piratesnships.mob.MobKind;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.level.Level;

/**
 * Sailor (docs/design.md §9): a neutral seafarer. Never attacks; runs from monsters, from pirates that are not
 * peaceful and from anything targeting it ({@link HostilityRules#flees}), and panics when hurt. Hiring comes later.
 */
public class Sailor extends SeafarerMob {

    public Sailor(EntityType<? extends Sailor> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return humanoid(20.0, 0.3, 1.0);
    }

    @Override
    public MobKind kind() {
        return MobKind.SAILOR;
    }

    @Override
    protected void equip() {
    }

    /** Whether the sailor runs from {@code e} (config read when asked). */
    public boolean fleesFrom(LivingEntity e) {
        boolean targetsMe = e instanceof Mob m && m.getTarget() == this;
        return HostilityRules.flees(MobFaction.CIVILIAN, describe(e), targetsMe, !MobConfig.isPeaceful(MobKind.PIRATE));
    }

    @Override
    protected void addCombatGoals() {
        goalSelector.addGoal(1, new PanicGoal(this, 1.2));
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, LivingEntity.class, e -> e != this && fleesFrom(e),
                MobConfig.SAILOR_FLEE_RANGE.get().floatValue(), 0.9, 1.2, e -> true));
    }

    @Override
    protected SoundEvent ambientSound() {
        return SoundEvents.VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent hurtSound() {
        return SoundEvents.VILLAGER_HURT;
    }

    @Override
    protected SoundEvent deathSound() {
        return SoundEvents.VILLAGER_DEATH;
    }
}
