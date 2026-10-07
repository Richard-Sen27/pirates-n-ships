package com.richardsenger.piratesnships.mob.entity;

import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.mob.MobConfig;
import com.richardsenger.piratesnships.mob.SharkRules;
import com.richardsenger.piratesnships.mob.ai.SharkCruiseGoal;
import com.richardsenger.piratesnships.mob.ai.SharkHuntGoal;
import com.richardsenger.piratesnships.mob.ai.SharkTargetGoal;
import com.richardsenger.piratesnships.ship.sable.ShipEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The shark (work package M4, docs/design.md §9, §12): a water animal on its own GeckoLib rig ({@code art/README.md},
 * "Entities", shark). Swims with vanilla's smooth swimming controls on a water-bound path navigation, breathes water,
 * takes no fall damage, and strands like a fish: on land it suffocates (vanilla water-animal air) and flops towards the
 * nearest water. Hunting follows {@link SharkRules} (whom) and {@code SharkHunt} (circle, charge, bite, frenzy, give
 * up), driven by {@link SharkHuntGoal}. A bite bumps a synced counter, which the client turns into the {@code bite}
 * animation. Natural spawning and despawning are vanilla's water-creature rules; {@code mobs.shark.enabled} off makes
 * existing sharks disappear.
 */
public class Shark extends WaterAnimal implements GeoEntity {

    private static final String TAG_GRUDGE = "pirates_n_ships:grudge";
    private static final String TAG_GRUDGE_UNTIL = "pirates_n_ships:grudge_until";

    private static final EntityDataAccessor<Integer> DATA_BITES = SynchedEntityData.defineId(Shark.class, EntityDataSerializers.INT);

    public static final String CONTROLLER_BODY = "body";
    public static final String CONTROLLER_BITE = "bite";
    public static final String ANIM_SWIM = "swim";
    public static final String ANIM_IDLE = "idle";
    public static final String ANIM_BITE = "bite";
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop(ANIM_SWIM);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(ANIM_IDLE);
    private static final RawAnimation BITE = RawAnimation.begin().thenPlay(ANIM_BITE);

    /** Blocks the bite reaches beyond the hitbox horizontally (the hitbox is the middle of a 2.4-block body). */
    private static final double BITE_REACH = 1.0;
    private static final double BITE_REACH_UP = 0.5;
    /** Blocks around a stranded shark searched for water to flop towards. */
    private static final int STRANDED_SEARCH = 6;
    private static final double FLOP_PUSH = 0.15;
    /** Client: blocks per tick at which the swim animation plays at normal speed. */
    private static final double SWIM_REFERENCE_SPEED = 0.12;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private @Nullable UUID grudgeTarget;
    private long grudgeUntil;
    /** Targets it gave up on, until the game time it ignores them (not saved: a fresh start after a reload). */
    private final Map<UUID, Long> ignored = new HashMap<>();
    private int bites;

    public Shark(EntityType<? extends Shark> type, Level level) {
        super(type, level);
        // No buoyancy (last argument false): with it the move control adds 0.005 up every tick in water. Dolphin cancels
        // that in its travel() while it has no target; without the cancel an idle shark drifted to the surface and
        // bobbed out of the water (Q4). A shark is neutrally buoyant.
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.02f, 0.1f, false);
        this.lookControl = new SmoothSwimmingLookControl(this, 10);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 1.2)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    /** Spawn placement predicate (registered through {@code Services.REGISTRY.registerSpawnPlacement}). */
    public static boolean checkSpawnRules(EntityType<Shark> type, ServerLevelAccessor level, MobSpawnType spawnType,
                                          BlockPos pos, RandomSource random) {
        return SharkRules.canSpawnAt(MobConfig.SHARK_ENABLED.get(), level.getFluidState(pos).is(FluidTags.WATER),
                level.getFluidState(pos.above()).is(FluidTags.WATER), pos.getY(), level.getSeaLevel());
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new SharkHuntGoal(this));
        goalSelector.addGoal(4, new SharkCruiseGoal(this, 1.0, 20));
        targetSelector.addGoal(1, new SharkTargetGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BITES, 0);
    }

    // --- whom it hunts ------------------------------------------------------------------------------------------

    /** What {@link SharkRules} need to know about {@code e}. Asks Sable only for prey that is in the water. */
    public SharkRules.Prey describe(LivingEntity e) {
        boolean exempt = e.isInvulnerable() || e instanceof Player p && (p.isCreative() || p.isSpectator());
        boolean prey = e instanceof Player || e instanceof Npc || e instanceof SeafarerMob || e instanceof CrewMember
                || e instanceof Animal;
        boolean inWater = e.isInWater() || e.isUnderWater();
        boolean onShip = inWater && (e.isPassenger() || ShipEntities.standingOrRiding(e) != null);
        return new SharkRules.Prey(e instanceof Shark, exempt, prey, inWater, onShip, distanceTo(e));
    }

    /** Whether the shark starts hunting {@code e} by itself (and hasn't given up on it lately). */
    public boolean huntsOnSight(LivingEntity e) {
        return e != this && e.isAlive() && !isIgnoring(e) && SharkRules.huntsOnSight(describe(e), MobConfig.sharkRules());
    }

    /** Whether the shark keeps hunting {@code e}. */
    public boolean keepsTarget(LivingEntity e) {
        return e != this && e.isAlive() && !e.isRemoved()
                && SharkRules.keepsTarget(describe(e), MobConfig.sharkRules(), hasGrudge(e), getAttributeValue(Attributes.FOLLOW_RANGE));
    }

    public boolean hasGrudge(LivingEntity e) {
        return grudgeTarget != null && grudgeTarget.equals(e.getUUID()) && level().getGameTime() < grudgeUntil;
    }

    private boolean isIgnoring(LivingEntity e) {
        Long until = ignored.get(e.getUUID());
        return until != null && level().getGameTime() < until;
    }

    /** Gives up on {@code target}: drops it and ignores it for {@code mobs.shark.give_up_ticks}. */
    public void loseInterest(LivingEntity target) {
        long now = level().getGameTime();
        ignored.values().removeIf(until -> until <= now);
        ignored.put(target.getUUID(), now + MobConfig.SHARK_GIVE_UP_TICKS.get());
        if (target.getUUID().equals(grudgeTarget)) grudgeTarget = null;
        setTarget(null);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && source.getEntity() instanceof LivingEntity attacker && attacker != this
                && SharkRules.retaliates(describe(attacker), MobConfig.sharkRules())) {
            grudgeTarget = attacker.getUUID();
            grudgeUntil = level().getGameTime() + MobConfig.GRUDGE_TICKS.get();
            ignored.remove(attacker.getUUID());
            if ((getTarget() == null || !getTarget().isAlive()) && keepsTarget(attacker)) setTarget(attacker);
        }
        return hurt;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        LivingEntity target = getTarget();
        if (target != null && !keepsTarget(target)) setTarget(null);
    }

    // --- the bite -----------------------------------------------------------------------------------------------

    /** The target's box is within the jaws' reach (around the hitbox, which sits in the middle of the long body). */
    public boolean inBiteReach(LivingEntity target) {
        AABB reach = getBoundingBox().inflate(BITE_REACH, BITE_REACH_UP, BITE_REACH);
        return reach.intersects(target.getBoundingBox());
    }

    /** Bites {@code target}: {@code mobs.shark.bite_damage}, a little knockback, the jaw snap on the client. */
    public void bite(LivingEntity target) {
        DamageSource source = damageSources().mobAttack(this);
        if (target.hurt(source, MobConfig.SHARK_BITE_DAMAGE.get().floatValue())) {
            double kb = MobConfig.SHARK_KNOCKBACK.get();
            if (kb > 0) target.knockback(kb, Mth.sin(getYRot() * Mth.DEG_TO_RAD), -Mth.cos(getYRot() * Mth.DEG_TO_RAD));
            setLastHurtMob(target);
        }
        playSound(SoundEvents.EVOKER_FANGS_ATTACK, 1.0f, 0.8f + random.nextFloat() * 0.2f);
        entityData.set(DATA_BITES, entityData.get(DATA_BITES) + 1);
    }

    /** Bites so far (synced; the client plays the bite animation when it changes). */
    public int bites() {
        return entityData.get(DATA_BITES);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_BITES.equals(key) && level().isClientSide && entityData.get(DATA_BITES) != bites) {
            bites = entityData.get(DATA_BITES);
            triggerAnim(CONTROLLER_BITE, ANIM_BITE);
        }
    }

    // --- water and land -----------------------------------------------------------------------------------------

    /**
     * Blocks the hitbox's bottom stays below the water's fluid surface when the shark swims up on its own (cruise
     * path nodes in the top water layer, a lunge at a swimmer). Its body then stays in the water and the fin shows.
     */
    public static final double SURFACE_MARGIN = 0.25;
    /** Water blocks searched upwards for the surface. */
    private static final int SURFACE_SEARCH = 16;

    @Override
    public void travel(Vec3 travelVector) {
        if (isEffectiveAi() && isInWater()) {
            moveRelative(getSpeed(), travelVector);
            Vec3 v = getDeltaMovement();
            if (v.y > 0) {
                double surface = waterSurfaceAbove();
                if (!Double.isNaN(surface)) {
                    double room = Math.max(0, surface - SURFACE_MARGIN - getBoundingBox().minY);
                    if (v.y > room) setDeltaMovement(v.x, room, v.z);
                }
            }
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.9));
        } else {
            super.travel(travelVector);
        }
    }

    /**
     * The fluid surface height (absolute y) of the water column the hitbox's bottom is in, or NaN when the bottom is
     * not in water or the column is deeper than {@link #SURFACE_SEARCH}. A source block under air ends at 8/9, not at
     * the block top.
     */
    public double waterSurfaceAbove() {
        BlockPos p = BlockPos.containing(getX(), getBoundingBox().minY, getZ());
        if (!level().getFluidState(p).is(FluidTags.WATER)) return Double.NaN;
        for (int i = 0; i < SURFACE_SEARCH; i++) {
            if (!level().getFluidState(p.above()).is(FluidTags.WATER)) {
                return p.getY() + level().getFluidState(p).getHeight(level(), p);
            }
            p = p.above();
        }
        return Double.NaN;
    }

    @Override
    public void aiStep() {
        if (!level().isClientSide && !isInWater() && onGround() && verticalCollision) flop();
        super.aiStep();
    }

    /** Stranded: hops like a fish, pushed towards the nearest water within {@link #STRANDED_SEARCH} blocks. */
    private void flop() {
        Vec3 push = new Vec3((random.nextFloat() * 2 - 1) * 0.05, 0.4, (random.nextFloat() * 2 - 1) * 0.05);
        BlockPos water = nearestWater();
        if (water != null) {
            Vec3 to = Vec3.atCenterOf(water).subtract(position());
            Vec3 flat = new Vec3(to.x, 0, to.z);
            if (flat.lengthSqr() > 1e-4) push = push.add(flat.normalize().scale(FLOP_PUSH));
        }
        setDeltaMovement(getDeltaMovement().add(push));
        setOnGround(false);
        hasImpulse = true;
        playSound(SoundEvents.COD_FLOP, getSoundVolume(), getVoicePitch() * 0.6f);
    }

    private @Nullable BlockPos nearestWater() {
        BlockPos at = blockPosition();
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-STRANDED_SEARCH, -2, -STRANDED_SEARCH), at.offset(STRANDED_SEARCH, 1, STRANDED_SEARCH))) {
            if (!level().getFluidState(p).is(FluidTags.WATER)) continue;
            double d = p.distSqr(at);
            if (d < bestSq) {
                bestSq = d;
                best = p.immutable();
            }
        }
        return best;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    // --- animation ----------------------------------------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, CONTROLLER_BODY, 5, state -> {
            double speed = new Vec3(getX() - xo, getY() - yo, getZ() - zo).length();
            if (speed > 0.02) {
                state.getController().setAnimationSpeed(Mth.clamp(speed / SWIM_REFERENCE_SPEED, 0.6, 2.5));
                return state.setAndContinue(SWIM);
            }
            state.getController().setAnimationSpeed(1.0);
            return state.setAndContinue(IDLE);
        }));
        controllers.add(new AnimationController<>(this, CONTROLLER_BITE, 0, state -> PlayState.STOP)
                .triggerableAnim(ANIM_BITE, BITE));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    // --- config, drops, sounds, persistence ---------------------------------------------------------------------

    @Override
    public void checkDespawn() {
        if (!MobConfig.SHARK_ENABLED.get()) {
            discard();
            return;
        }
        super.checkDespawn();
    }

    @Override
    protected void dropFromLootTable(DamageSource source, boolean causedByPlayer) {
        if (MobConfig.DROPS.get()) super.dropFromLootTable(source, causedByPlayer);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.COD_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.COD_DEATH;
    }

    @Override
    protected SoundEvent getSwimSound() {
        return SoundEvents.DOLPHIN_SWIM;
    }

    @Override
    public float getVoicePitch() {
        return 0.6f + random.nextFloat() * 0.1f;
    }

    @Override
    public int getMaxHeadYRot() {
        return 30;
    }

    @Override
    public int getMaxHeadXRot() {
        return 30;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (grudgeTarget != null) {
            tag.putUUID(TAG_GRUDGE, grudgeTarget);
            tag.putLong(TAG_GRUDGE_UNTIL, grudgeUntil);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        grudgeTarget = tag.hasUUID(TAG_GRUDGE) ? tag.getUUID(TAG_GRUDGE) : null;
        grudgeUntil = tag.getLong(TAG_GRUDGE_UNTIL);
    }
}
