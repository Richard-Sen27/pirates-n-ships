package com.richardsenger.piratesnships.mob.entity;

import com.richardsenger.piratesnships.combat.melee.MeleeService;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewPose;
import com.richardsenger.piratesnships.law.LawAttachments;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.mob.HostilityRules;
import com.richardsenger.piratesnships.mob.MeleePose;
import com.richardsenger.piratesnships.mob.MobConfig;
import com.richardsenger.piratesnships.mob.MobFaction;
import com.richardsenger.piratesnships.mob.MobKind;
import com.richardsenger.piratesnships.mob.ai.DuelistDebug;
import com.richardsenger.piratesnships.mob.ai.HostileTargetGoal;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.rpg.reputation.Reputation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Base of the humanoid mobs of docs/design.md §9 (pirate, sailor, navy soldier, navy officer) on the shared GeckoLib
 * rig ({@code art/README.md}, "Entities"). It holds what all four share:
 * <ul>
 *   <li>targets: {@link HostileTargetGoal} picks targets by {@link HostilityRules}; every server tick the current target
 *       is checked again ({@link HostilityRules#keepsTarget}), so a target set by anyone (a hit, a command, a test) is
 *       dropped as soon as the rules no longer allow it (a pardoned player, {@code mobs.pirates_hostile} switched off);
 *       being hit gives a grudge ({@code mobs.grudge_ticks}) that allows retaliation;</li>
 *   <li>the telegraph: the melee phase (the same attachment players use, {@link MeleeService#state}) is mirrored into
 *       a synced int ({@link MeleePose}) that the client model turns into sword-arm poses;</li>
 *   <li>the config toggles: a disabled type disappears ({@link #checkDespawn}), drops follow {@code mobs.drops}.</li>
 * </ul>
 * Body animations are the crew member's idle/walk/sit ({@link CrewPose}). Mobs standing on a Sable ship move with it
 * through Sable's entity tracking (sable-notes §6), like any entity on a deck. This package and {@code mob/client} are
 * the only GeckoLib importers of the module.
 */
public abstract class SeafarerMob extends PathfinderMob implements GeoEntity {

    private static final String TAG_GRUDGE = "pirates_n_ships:grudge";
    private static final String TAG_GRUDGE_UNTIL = "pirates_n_ships:grudge_until";
    private static final String TAG_STATIONARY = "pirates_n_ships:stationary";

    private static final EntityDataAccessor<Integer> DATA_MELEE_POSE = SynchedEntityData.defineId(SeafarerMob.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_AIMING = SynchedEntityData.defineId(SeafarerMob.class, EntityDataSerializers.BOOLEAN);

    private static final int POSE_TRANSITION_TICKS = 5;
    private static final Map<CrewPose, RawAnimation> POSE_ANIMATIONS = new EnumMap<>(CrewPose.class);

    static {
        for (CrewPose pose : CrewPose.values()) {
            POSE_ANIMATIONS.put(pose, RawAnimation.begin().thenLoop(pose.animation()));
        }
    }

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private @Nullable UUID grudgeTarget;
    private long grudgeUntil;
    /** Client: {@link #tickCount} when the melee pose last changed (for the pose's progress). */
    private int poseChangedAt;
    private boolean stationary;
    /** BOS1: who this mob holds a truce with, until which game time (a pirate captain's duel). Not saved. */
    private final Map<UUID, Long> truces = new HashMap<>();

    protected SeafarerMob(EntityType<? extends SeafarerMob> type, Level level) {
        super(type, level);
        // carried weapons don't drop: the loot table decides (a cutlass sometimes, see MobLoot)
        setDropChance(EquipmentSlot.MAINHAND, 0f);
        setDropChance(EquipmentSlot.OFFHAND, 0f);
        if (!level.isClientSide) equip();
    }

    public abstract MobKind kind();

    public MobFaction faction() {
        return kind().faction();
    }

    /** Puts the type's weapon in hand (called on creation; a loaded save overwrites it with the saved hands). */
    protected abstract void equip();

    /** Goals of the type's way of fighting (or fleeing), added between swimming and strolling. */
    protected abstract void addCombatGoals();

    protected static AttributeSupplier.Builder humanoid(double health, double speed, double attackDamage) {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, health)
                .add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        addCombatGoals();
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6) {
            @Override
            public boolean canUse() {
                return !stationary && super.canUse();
            }
        });
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        if (faction() != MobFaction.CIVILIAN) {
            targetSelector.addGoal(2, new HostileTargetGoal(this));
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MELEE_POSE, MeleePose.IDLE.pack());
        builder.define(DATA_AIMING, false);
    }

    // --- hostility ----------------------------------------------------------------------------------------------

    /**
     * What the hostility rules need to know about {@code e}, from this mob's point of view. Players and crew members
     * aboard a ship also carry what its flag tells NPCs ({@link LawService#shipStance}, FL2).
     */
    public HostilityRules.Target describe(LivingEntity e) {
        boolean navy = faction() == MobFaction.NAVY;
        if (e instanceof SeafarerMob m) return HostilityRules.Target.ofMob(m.faction());
        if (e instanceof Player p) {
            boolean exempt = p.isCreative() || p.isSpectator();
            if (exempt) return HostilityRules.Target.ofPlayer(true, false);
            // REP1: the navy also attacks players it hates, pirates leave players they like alone (rpg.reputation)
            boolean wanted = navy && (LawService.navyShouldAttack(this, p) || Reputation.navyHostile(p));
            return HostilityRules.Target.ofPlayer(false, wanted, LawService.shipStance(p))
                    .withLikedByPirates(faction() == MobFaction.PIRATE && (Reputation.piratesFriendly(p)
                            || com.richardsenger.piratesnships.rpg.career.CareerRewards.piratesFriendly(p))) // CAR2: infamy
                    .withTruce(hasTruce(p));
        }
        MobFaction faction = LawService.isNavy(e) ? MobFaction.NAVY : null;
        // only entities that already have a criminal record can be wanted (don't attach records to every animal)
        boolean wanted = navy && faction == null && Services.ATTACHMENTS.has(e, LawAttachments.CRIMINAL_RECORD)
                && LawService.navyShouldAttack(this, e);
        HostilityRules.Target t = new HostilityRules.Target(faction, false, false, wanted, e instanceof Enemy);
        return e instanceof CrewMember ? t.withShip(LawService.shipStance(e)) : t;
    }

    /** Whether this mob attacks {@code e} on sight. */
    public boolean attacksOnSight(LivingEntity e) {
        return e != this && e.isAlive() && HostilityRules.attacksOnSight(faction(), describe(e), MobConfig.hostility(kind()));
    }

    /**
     * Holds a truce with {@code e} until game time {@code until} (BOS1, a pirate captain's duel): this mob does not attack
     * it on sight meanwhile ({@link HostilityRules.Target#truce}) and drops it as a target, unless it holds a grudge
     * (it was hit by it). Kept in memory only: a reloaded mob has no truces.
     */
    public void truce(LivingEntity e, long until) {
        truces.put(e.getUUID(), until);
        LivingEntity target = getTarget();
        if (target != null && target.getUUID().equals(e.getUUID()) && !hasGrudge(e)) setTarget(null);
    }

    /** Ends the truce with the entity of {@code id}, if any. */
    public void endTruce(UUID id) {
        truces.remove(id);
    }

    /** Whether this mob holds a truce with {@code e} now. */
    public boolean hasTruce(LivingEntity e) {
        Long until = truces.get(e.getUUID());
        if (until == null) return false;
        if (level().getGameTime() < until) return true;
        truces.remove(e.getUUID());
        return false;
    }

    /** {@code e} hit this mob within the last {@code mobs.grudge_ticks}. */
    public boolean hasGrudge(LivingEntity e) {
        return grudgeTarget != null && grudgeTarget.equals(e.getUUID()) && level().getGameTime() < grudgeUntil;
    }

    /** Whether this mob keeps fighting {@code e} (see {@link HostilityRules#keepsTarget}). */
    public boolean keepsTarget(LivingEntity e) {
        return e != this && e.isAlive() && !e.isRemoved()
                && HostilityRules.keepsTarget(faction(), describe(e), MobConfig.hostility(kind()), hasGrudge(e));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && source.getEntity() instanceof LivingEntity attacker) takeGrudge(attacker);
        return hurt;
    }

    /**
     * Takes a grudge against {@code attacker} as if it had hit this mob (the retaliation rule of
     * {@link HostilityRules#retaliates}), and targets it if this mob has no living target. Called when this mob is hit
     * and when a squad mate is (MOB2, {@code mob.squad.SquadCombat}). Returns whether the grudge was taken.
     */
    public boolean takeGrudge(LivingEntity attacker) {
        if (attacker == this || level().isClientSide) return false;
        if (!HostilityRules.retaliates(faction(), describe(attacker), MobConfig.hostility(kind()))) return false;
        grudgeTarget = attacker.getUUID();
        grudgeUntil = level().getGameTime() + MobConfig.GRUDGE_TICKS.get();
        if (getTarget() == null || !getTarget().isAlive()) {
            setTarget(attacker);
            DuelistDebug.report(this, "target", "acquired (fights back)", attacker, "");
        }
        return true;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        LivingEntity target = getTarget();
        if (target != null && !keepsTarget(target)) {
            setTarget(null);
            DuelistDebug.stats(this).targetsLost++;
            DuelistDebug.report(this, "target", "dropped (hostility rules: creative, pardoned or not hostile)", target, "");
        }
        MeleePose pose = MeleeService.isActive(this) ? MeleePose.of(MeleeService.state(this)) : MeleePose.IDLE;
        entityData.set(DATA_MELEE_POSE, pose.pack()); // only sends when it changes
    }

    // --- telegraph (client) -------------------------------------------------------------------------------------

    public MeleePose meleePose() {
        return MeleePose.unpack(entityData.get(DATA_MELEE_POSE));
    }

    /** 0..1 through the current melee phase (client; open-ended phases report 1). */
    public float meleePoseProgress(float partialTick) {
        MeleePose pose = meleePose();
        if (pose.duration() <= 0) return 1f;
        return Math.min(1f, (tickCount - poseChangedAt + partialTick) / pose.duration());
    }

    public boolean isAiming() {
        return entityData.get(DATA_AIMING);
    }

    protected void setAiming(boolean aiming) {
        entityData.set(DATA_AIMING, aiming);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_MELEE_POSE.equals(key)) poseChangedAt = tickCount;
    }

    // --- animation ----------------------------------------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "body", POSE_TRANSITION_TICKS,
                state -> state.setAndContinue(POSE_ANIMATIONS.get(CrewPose.choose(state.isMoving(), false, getVehicle() != null)))));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    // --- config toggles, persistence, drops, sounds -------------------------------------------------------------

    @Override
    public void checkDespawn() {
        if (!MobConfig.enabled(kind()).get()) {
            discard();
            return;
        }
        super.checkDespawn();
    }

    /** Spawned mobs stay (no natural spawning yet; structures will place them, docs/design.md §10.1). */
    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected void dropFromLootTable(DamageSource source, boolean causedByPlayer) {
        if (MobConfig.DROPS.get()) super.dropFromLootTable(source, causedByPlayer);
    }

    protected abstract SoundEvent ambientSound();

    protected abstract SoundEvent hurtSound();

    protected abstract SoundEvent deathSound();

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return ambientSound();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return hurtSound();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return deathSound();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (grudgeTarget != null) {
            tag.putUUID(TAG_GRUDGE, grudgeTarget);
            tag.putLong(TAG_GRUDGE_UNTIL, grudgeUntil);
        }
        if (stationary) tag.putBoolean(TAG_STATIONARY, true);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        grudgeTarget = tag.hasUUID(TAG_GRUDGE) ? tag.getUUID(TAG_GRUDGE) : null;
        grudgeUntil = tag.getLong(TAG_GRUDGE_UNTIL);
        stationary = tag.getBoolean(TAG_STATIONARY);
    }

    /**
     * A stationary mob doesn't stroll about when idle (a guard at its post, a deckhand on a small deck); it still turns,
     * fights, flees and is moved by the ship it stands on. Structures will place such mobs; saved with the entity.
     */
    public void setStationary(boolean stationary) {
        this.stationary = stationary;
    }

    public boolean isStationary() {
        return stationary;
    }
}
