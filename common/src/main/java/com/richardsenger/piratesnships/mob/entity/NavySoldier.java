package com.richardsenger.piratesnships.mob.entity;

import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.combat.firearms.FirearmContent;
import com.richardsenger.piratesnships.combat.firearms.FirearmKind;
import com.richardsenger.piratesnships.combat.firearms.FirearmService;
import com.richardsenger.piratesnships.combat.firearms.FirearmsConfig;
import com.richardsenger.piratesnships.mob.MobConfig;
import com.richardsenger.piratesnships.mob.MobKind;
import com.richardsenger.piratesnships.mob.ai.MusketeerGoal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Navy soldier (docs/design.md §9): patrols, hostile to wanted players and to pirates, fights with a musket
 * ({@link MusketeerGoal}) through {@code FirearmService}, like a player would. It needs no ammunition items: with
 * {@code mobs.navy_infinite_ammo} on it never runs out, otherwise it carries {@code mobs.musket_shots} shots. Reloading
 * takes {@code mobs.musket_reload_ticks}. Hitting or killing it is a crime ({@code #pirates_n_ships:navy}).
 */
public class NavySoldier extends SeafarerMob {

    private static final String TAG_RELOAD = "pirates_n_ships:reload";
    private static final String TAG_SHOTS_USED = "pirates_n_ships:shots_used";

    private int reloadLeft;
    private int shotsUsed;
    private int shotsFired;

    public NavySoldier(EntityType<? extends NavySoldier> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return humanoid(24.0, 0.28, 2.0);
    }

    @Override
    public MobKind kind() {
        return MobKind.NAVY_SOLDIER;
    }

    @Override
    protected void equip() {
        ItemStack musket = new ItemStack(CombatContent.MUSKET.get());
        FirearmContent.setLoaded(musket, true);
        setItemInHand(InteractionHand.MAIN_HAND, musket);
    }

    @Override
    protected void addCombatGoals() {
        goalSelector.addGoal(2, new MusketeerGoal(this, 0.9));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (reloadLeft > 0 && --reloadLeft == 0) {
            ItemStack gun = getMainHandItem();
            if (gun.is(CombatContent.MUSKET.get())) FirearmContent.setLoaded(gun, true);
        }
    }

    /** Musket in hand, loaded (reload done), a shot left, firearms enabled. */
    public boolean musketReady() {
        return reloadLeft == 0 && getMainHandItem().is(CombatContent.MUSKET.get()) && FirearmsConfig.ENABLED.get()
                && (MobConfig.NAVY_INFINITE_AMMO.get() || shotsUsed < MobConfig.MUSKET_SHOTS.get());
    }

    /** Pulls the trigger (the caller aimed). A misfire leaves the musket loaded; a shot starts the reload. */
    public void fireMusket() {
        if (!(level() instanceof ServerLevel level) || !musketReady()) return;
        ItemStack gun = getMainHandItem();
        FirearmContent.setLoaded(gun, true);
        if (FirearmService.fire(level, this, gun, FirearmKind.MUSKET) == FirearmService.Shot.FIRED) {
            shotsFired++;
            shotsUsed++;
            reloadLeft = MobConfig.MUSKET_RELOAD_TICKS.get();
        }
    }

    public void setMusketAiming(boolean aiming) {
        setAiming(aiming);
    }

    /** Shots fired since this soldier was created or loaded (tests, debugging). */
    public int shotsFired() {
        return shotsFired;
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

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(TAG_RELOAD, reloadLeft);
        tag.putInt(TAG_SHOTS_USED, shotsUsed);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        reloadLeft = tag.getInt(TAG_RELOAD);
        shotsUsed = tag.getInt(TAG_SHOTS_USED);
    }
}
