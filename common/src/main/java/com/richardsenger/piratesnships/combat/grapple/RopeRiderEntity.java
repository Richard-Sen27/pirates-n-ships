package com.richardsenger.piratesnships.combat.grapple;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The invisible handle a player hangs on while sliding along a latched grappling rope (GR2, docs/design.md §8.3). It
 * lives in world space (unlike the station seat, which sits in a ship's plot), {@code hang_offset} below its point on
 * the rope, and carries exactly one player: its passenger's feet are at the rider's position. Riding makes the
 * player's position server-authoritative (the client does not move a passenger it does not control), so the slide
 * needs no client cooperation.
 *
 * <p>Every server tick it recomputes the rope from the hook's live ends ({@link GrapplingHookEntity#ropeNearEnd},
 * {@link GrapplingHookEntity#ropeFarEnd}), so it follows both ships, and moves one {@link RopeSlide#step}. On arrival
 * the player lands on the end's standing spot ({@link RopeSlideService#landing}); sneaking (vanilla's dismount) drops
 * the player where they hang; a released or snapped rope, a disabled slide or a lost hook drops them too. Only players
 * ride; anything else is thrown off. Not saved: a player who logs out mid-slide is back where they hung.
 */
public class RopeRiderEntity extends Entity {

    private static final EntityDataAccessor<Integer> HOOK_ID = SynchedEntityData.defineId(RopeRiderEntity.class, EntityDataSerializers.INT);

    // server state
    private double t;
    private double speed;
    private int dir;
    /** Where the passenger is put when it gets off now; null = where it hangs. */
    private @Nullable Vec3 landing;

    public RopeRiderEntity(EntityType<? extends RopeRiderEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    /** A rider on {@code hook}'s rope at parameter {@code t}, placed at {@code pos} (not yet added to the level). */
    static RopeRiderEntity create(ServerLevel level, GrapplingHookEntity hook, double t, Vec3 pos) {
        RopeRiderEntity rider = new RopeRiderEntity(GrappleContent.ROPE_RIDER.get(), level);
        rider.entityData.set(HOOK_ID, hook.getId());
        rider.t = t;
        rider.moveTo(pos.x, pos.y, pos.z, 0, 0);
        return rider;
    }

    /** Network id of the hook whose rope this rider hangs on (both sides). */
    public int hookId() {
        return entityData.get(HOOK_ID);
    }

    /** The hook whose rope this rider hangs on, or null when it is gone. */
    public @Nullable GrapplingHookEntity hook() {
        return level().getEntity(hookId()) instanceof GrapplingHookEntity h && !h.isRemoved() ? h : null;
    }

    /** Parameter of the rider on the rope (server): 0 near end, 1 hook. */
    public double t() {
        return t;
    }

    /** Current speed (server) [blocks per tick]. */
    public double speed() {
        return speed;
    }

    /** The player hanging on this rider, or null. */
    public @Nullable Player rider() {
        return getFirstPassenger() instanceof Player p ? p : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level) {
            RopeSlideService.tickRider(level, this);
        }
    }

    /** Moves the rider to rope parameter {@code t} after a slide step (server). */
    void slideTo(double t, double speed, int dir, Vec3 pos) {
        this.t = t;
        this.speed = speed;
        this.dir = dir;
        setPos(pos.x, pos.y, pos.z);
        setDeltaMovement(Vec3.ZERO);
    }

    int dir() {
        return dir;
    }

    /**
     * Lets the passenger go, at {@code at} (it lands there, see {@link RopeSlideService#landing}) or, when null, where
     * it hangs, and removes the rider.
     */
    void letGo(@Nullable Vec3 at) {
        landing = at;
        Entity passenger = getFirstPassenger();
        ejectPassengers();
        if (passenger != null && at != null) {
            passenger.teleportTo(at.x, at.y, at.z); // a server player's own dismount does not tell its client
        }
        if (passenger != null) {
            passenger.resetFallDistance();
        }
        discard();
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        // the passenger's feet hang at the rider (vanilla would seat it on top and lower it by its riding offset)
        move.accept(passenger, getX(), getY(), getZ());
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        // sneaking or a dropped rope: let go where it hangs (the client keeps that position too)
        return landing != null ? landing : passenger.position();
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof Player && getPassengers().isEmpty();
    }

    @Override
    public boolean dismountsUnderwater() {
        return false; // a low rope may dip the hanging player's eyes into a wave
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(HOOK_ID, -1);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
