package com.richardsenger.piratesnships.mob.entity;

import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.combat.firearms.FirearmContent;
import com.richardsenger.piratesnships.combat.firearms.FirearmKind;
import com.richardsenger.piratesnships.combat.firearms.FirearmService;
import com.richardsenger.piratesnships.combat.firearms.FirearmsConfig;
import com.richardsenger.piratesnships.mob.MobConfig;
import com.richardsenger.piratesnships.mob.MobKind;
import com.richardsenger.piratesnships.mob.MusketAction;
import com.richardsenger.piratesnships.mob.ai.MusketeerGoal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Navy soldier (docs/design.md §9): patrols, hostile to wanted players and to pirates, fights with a musket
 * ({@link MusketeerGoal}) through {@code FirearmService}, like a player would. It needs no ammunition items: with
 * {@code mobs.navy_infinite_ammo} on it never runs out, otherwise it carries {@code mobs.musket_shots} shots. Reloading
 * takes {@code mobs.musket_reload_ticks}. Hitting or killing it is a crime ({@code #pirates_n_ships:navy}).
 * <p>
 * What it does with the musket ({@link MusketAction}: aim, reload, shove) is synced every tick; the client plays the
 * rig's {@code musket_aim} (held), {@code musket_reload} (once, stretched to the reload time) on an arm controller over
 * the body animations, and {@code musket_shove} on a third controller on top (M6).
 */
public class NavySoldier extends SeafarerMob {

    private static final String TAG_RELOAD = "pirates_n_ships:reload";
    private static final String TAG_SHOTS_USED = "pirates_n_ships:shots_used";

    public static final String CONTROLLER_MUSKET = "musket";
    public static final String CONTROLLER_SHOVE = "musket_shove";
    public static final String ANIM_AIM = "musket_aim";
    public static final String ANIM_RELOAD = "musket_reload";
    public static final String ANIM_SHOVE = "musket_shove";
    private static final RawAnimation AIM = RawAnimation.begin().thenPlayAndHold(ANIM_AIM);
    private static final RawAnimation RELOAD = RawAnimation.begin().thenPlay(ANIM_RELOAD);
    private static final RawAnimation SHOVE = RawAnimation.begin().thenPlay(ANIM_SHOVE);
    private static final int MUSKET_TRANSITION_TICKS = 4;

    private static final EntityDataAccessor<Integer> DATA_MUSKET = SynchedEntityData.defineId(NavySoldier.class, EntityDataSerializers.INT);

    private int reloadLeft;
    /** Length of the running (or last) reload, for the client's animation speed. */
    private int reloadTotal = MusketAction.RELOAD_ANIMATION_TICKS;
    private int shotsUsed;
    private int shotsFired;
    private int shoveLeft;
    private boolean musketAiming;
    /** Client: what the arm controller plays (never SHOVE; see {@link MusketAction#held}). */
    private MusketAction heldAction = MusketAction.NONE;

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

    /** Musket drill is right-handed: the musket animations hold the gun in the right hand (no 5% left-handers). */
    @Override
    public boolean isLeftHanded() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MUSKET, MusketAction.pack(MusketAction.NONE, MusketAction.RELOAD_ANIMATION_TICKS));
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
        if (shoveLeft > 0) shoveLeft--;
        // after the goals ran this tick (Mob#serverAiStep ticks the goal selector first); only sends when it changes
        MusketAction action = MusketAction.choose(shoveLeft > 0, reloadLeft > 0, musketAiming);
        entityData.set(DATA_MUSKET, MusketAction.pack(action, reloadTotal));
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
            reloadTotal = reloadLeft;
        }
    }

    /** The musketeer goal keeps the musket on a target this tick (or stopped doing so). */
    public void setMusketAiming(boolean aiming) {
        musketAiming = aiming;
        setAiming(aiming);
    }

    /** The musketeer goal just shoved with the musket butt: the shove shows for {@link MusketAction#SHOVE_TICKS}. */
    public void musketShoved() {
        shoveLeft = MusketAction.SHOVE_TICKS + 1; // counted down once in this tick's customServerAiStep
    }

    /** What the soldier does with its musket (synced; server and client). */
    public MusketAction musketAction() {
        return MusketAction.unpackAction(entityData.get(DATA_MUSKET));
    }

    /** Length of the current or last reload in ticks (synced). */
    public int musketReloadTicks() {
        return MusketAction.unpackReloadTicks(entityData.get(DATA_MUSKET));
    }

    // --- animation (client) -------------------------------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        super.registerControllers(controllers);
        controllers.add(new AnimationController<>(this, CONTROLLER_MUSKET, MUSKET_TRANSITION_TICKS, this::musketState)
                .setAnimationSpeedHandler(s -> heldAction == MusketAction.RELOAD ? MusketAction.reloadSpeed(musketReloadTicks()) : 1.0));
        controllers.add(new AnimationController<>(this, CONTROLLER_SHOVE, 2, this::shoveState));
    }

    /** Arm layer over idle/walk: the held aim or the reload (keys arms, the hand locator and the waist; legs keep walking). */
    private PlayState musketState(AnimationState<NavySoldier> state) {
        heldAction = MusketAction.held(musketAction(), heldAction);
        return switch (heldAction) {
            case AIM -> state.setAndContinue(AIM);
            case RELOAD -> state.setAndContinue(RELOAD);
            default -> {
                state.resetCurrentAnimation(); // the next aim or reload starts from its first frame
                yield PlayState.STOP;
            }
        };
    }

    /** The butt-stroke, once per shove, on top of everything. */
    private PlayState shoveState(AnimationState<NavySoldier> state) {
        if (musketAction() == MusketAction.SHOVE) return state.setAndContinue(SHOVE);
        state.resetCurrentAnimation();
        return PlayState.STOP;
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
