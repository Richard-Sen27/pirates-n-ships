package com.richardsenger.piratesnships.mob.village;

import com.richardsenger.piratesnships.mob.village.ResidentPosts.Post;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The invisible place a villager sits on at dusk (LIFE1): a stair seat at a tavern table, a bench of a square or a
 * house's chair, a sibling of the crew's meal seat. Riding it the villager plays the rig's {@code sit} pose
 * ({@code CrewPose#SIT}), its hips on the stair's top: the seat stands {@link #HIP_DROP} below the top, since the
 * rig's hips are 12 px above its feet. It removes itself once its rider got up (or never sat down).
 */
public class BenchSeat extends Entity {

    /** How far the rider's feet hang below the seat's top (the rig's hip height, 12 px). */
    public static final double HIP_DROP = 12.0 / 16.0;
    /** The top of a bottom-half stair. */
    public static final double STAIR_TOP = 0.5;

    private boolean hadRider;

    public BenchSeat(EntityType<? extends BenchSeat> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    /** Spawns a seat on the stair at {@code seat}'s block, its sitter facing {@code seat}'s facing. */
    public static BenchSeat spawn(ServerLevel level, Post seat) {
        BenchSeat s = new BenchSeat(VillageLifeContent.BENCH_SEAT.get(), level);
        Vec3 at = riderPosition(seat.pos());
        s.moveTo(at.x, at.y, at.z, seat.facing().toYRot(), 0);
        level.addFreshEntity(s);
        return s;
    }

    /** Where a sitter's feet are on the stair at {@code stair}. */
    public static Vec3 riderPosition(BlockPos stair) {
        return new Vec3(stair.getX() + 0.5, stair.getY() + STAIR_TOP - HIP_DROP, stair.getZ() + 0.5);
    }

    /** The seats on the stair at {@code stair} (taken or not). */
    public static List<BenchSeat> on(ServerLevel level, BlockPos stair) {
        Vec3 at = riderPosition(stair);
        return level.getEntitiesOfClass(BenchSeat.class, new AABB(at, at).inflate(0.3), s -> !s.isRemoved());
    }

    /** Whether somebody sits on the stair at {@code stair}. */
    public static boolean taken(ServerLevel level, BlockPos stair) {
        return on(level, stair).stream().anyMatch(Entity::isVehicle);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel)) return;
        if (isVehicle()) {
            hadRider = true;
        } else if (hadRider || tickCount > 1) {
            discard();
        }
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        move.accept(passenger, getX(), getY(), getZ());
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        // up onto the stair's top; the routine walks it off from there
        return new Vec3(getX(), getY() + HIP_DROP, getZ());
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty();
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
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        hadRider = true; // the rider is loaded with the seat: without one it is a leftover
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
