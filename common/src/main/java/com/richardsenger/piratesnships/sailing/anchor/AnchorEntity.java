package com.richardsenger.piratesnships.sailing.anchor;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The visible anchor of a ship (docs/design.md §5.3). Purely a view of the ship's {@code ShipAnchor}: the server
 * places it every tick ({@link AnchorEntities#sync}), and it is never saved, so it can't be orphaned on disk; after
 * a load it is spawned again from the ship's anchor state.
 *
 * <ul>
 *   <li><b>Stowed:</b> lives inside the ship's plot (entity-type tags {@code sable:retain_in_sub_level} and
 *       {@code sable:destroy_with_sub_level}, sable-notes §9.0e) and moves with the ship.</li>
 *   <li><b>Out:</b> lives in world space where the anchor's body is (AN2a: falling, resting on the seabed, dragging or
 *       being heaved in); never a rider of the ship and never in its plot.</li>
 * </ul>
 *
 * <p>The client renders the chain from the hawse, which it computes from the ship's render pose every frame, so it
 * needs the capstan (to find the ship) and the hawse offset from the capstan. For the chain's look (AN2b) the entity
 * also carries the chain's state ({@link AnchorStatus}): the paid-out length, the flags resting, taut, dragging,
 * holding and anchored, and the server's world hawse relative to the anchor (precise far from the origin).
 */
public class AnchorEntity extends Entity {

    private static final EntityDataAccessor<Boolean> OUT = SynchedEntityData.defineId(AnchorEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<BlockPos> CAPSTAN = SynchedEntityData.defineId(AnchorEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Vector3f> HAWSE = SynchedEntityData.defineId(AnchorEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Boolean> ARMS_ALONG_X = SynchedEntityData.defineId(AnchorEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> PAID_OUT = SynchedEntityData.defineId(AnchorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> FLAGS = SynchedEntityData.defineId(AnchorEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Vector3f> HAWSE_FROM_ANCHOR = SynchedEntityData.defineId(AnchorEntity.class, EntityDataSerializers.VECTOR3);

    /** Ticks without a sync after which the server discards the entity (its ship is gone or not ticking). */
    static final int ORPHAN_TICKS = 10;

    private @Nullable UUID ship;
    private long lastSync;

    public AnchorEntity(EntityType<? extends AnchorEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    /** A new anchor of {@code ship}, worked from plot position {@code capstan}, hawse at plot position {@code hawse}. */
    static AnchorEntity create(Level level, UUID ship, BlockPos capstan, Vec3 hawse, boolean armsAlongX, boolean out) {
        AnchorEntity e = new AnchorEntity(AnchorContent.ANCHOR.get(), level);
        e.ship = ship;
        e.lastSync = level.getGameTime();
        e.entityData.set(OUT, out);
        e.entityData.set(CAPSTAN, capstan.immutable());
        e.entityData.set(HAWSE, new Vector3f((float) (hawse.x - capstan.getX()), (float) (hawse.y - capstan.getY()),
                (float) (hawse.z - capstan.getZ())));
        e.entityData.set(ARMS_ALONG_X, armsAlongX);
        return e;
    }

    public @Nullable UUID ship() {
        return ship;
    }

    /** Whether the anchor is out of the plot (dropping, holding or raising). */
    public boolean isOut() {
        return entityData.get(OUT);
    }

    public BlockPos capstan() {
        return entityData.get(CAPSTAN);
    }

    /** Plot position of the hawse. Plot coordinates are in the millions, so the offset is kept relative to the capstan. */
    public Vec3 hawse() {
        BlockPos c = capstan();
        Vector3f o = entityData.get(HAWSE);
        return new Vec3(c.getX() + (double) o.x(), c.getY() + (double) o.y(), c.getZ() + (double) o.z());
    }

    /** Whether the arms point along the plot's x axis (the bow axis), else along z. */
    public boolean armsAlongX() {
        return entityData.get(ARMS_ALONG_X);
    }

    /** Chain paid out from the hawse to the ring [blocks] (0 while stowed). */
    public float paidOut() {
        return entityData.get(PAID_OUT);
    }

    /** {@link AnchorStatus#flags()} of the last server tick. */
    public int chainFlags() {
        return entityData.get(FLAGS);
    }

    public boolean isResting() {
        return (chainFlags() & AnchorStatus.RESTING) != 0;
    }

    public boolean isTaut() {
        return (chainFlags() & AnchorStatus.TAUT) != 0;
    }

    public boolean isDragging() {
        return (chainFlags() & AnchorStatus.DRAGGING) != 0;
    }

    public boolean isAnchored() {
        return (chainFlags() & AnchorStatus.ANCHORED) != 0;
    }

    /** The server's world hawse of the last tick, relative to this entity's position. */
    public Vec3 hawseFromAnchor() {
        Vector3f v = entityData.get(HAWSE_FROM_ANCHOR);
        return new Vec3(v.x(), v.y(), v.z());
    }

    /** Server: the chain state of this tick (null = stowed), with the anchor at {@code at}. */
    void setChain(@Nullable AnchorStatus status, Vec3 at) {
        if (status == null) {
            entityData.set(PAID_OUT, 0f);
            entityData.set(FLAGS, (byte) 0);
            entityData.set(HAWSE_FROM_ANCHOR, new Vector3f());
            return;
        }
        entityData.set(PAID_OUT, (float) status.paidOut());
        entityData.set(FLAGS, (byte) status.flags());
        entityData.set(HAWSE_FROM_ANCHOR, new Vector3f((float) (status.hawse().x - at.x), (float) (status.hawse().y - at.y),
                (float) (status.hawse().z - at.z)));
    }

    void markSynced() {
        lastSync = level().getGameTime();
    }

    @Override
    public void tick() {
        // no physics, no water effects of its own: AnchorEntities places it and plays its sounds
        if (!level().isClientSide && level().getGameTime() - lastSync > ORPHAN_TICKS) {
            discard();
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
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
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
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
    public boolean shouldRenderAtSqrDistance(double distance) {
        // stowed, the entity's own position is in plot space (millions away); out, the chain may be long
        return true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OUT, false);
        builder.define(CAPSTAN, BlockPos.ZERO);
        builder.define(HAWSE, new Vector3f());
        builder.define(ARMS_ALONG_X, false);
        builder.define(PAID_OUT, 0f);
        builder.define(FLAGS, (byte) 0);
        builder.define(HAWSE_FROM_ANCHOR, new Vector3f());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // never saved (shouldBeSaved)
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // never saved (shouldBeSaved)
    }
}
