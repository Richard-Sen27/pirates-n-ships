package com.richardsenger.piratesnships.mob.kraken;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * One hit box of the kraken: a tentacle (index 0-7, 0.9 × 2.5) or an eye (8 left, 9 right, 0.6 × 0.6).
 *
 * <p>Vanilla's {@code EnderDragonPart} is not added to the level (vanilla's entity lookups special-case the dragon, and
 * NeoForge's {@code PartEntity} replaces that with a NeoForge class), so neither is usable in {@code common}. A part
 * here is a plain entity of its own type that <b>is</b> added to the level: the kraken creates its parts on its first
 * server tick and moves them every tick, the server's entity tracker sends their positions to clients (so players can
 * aim at them and projectiles hit them), and {@link #hurt} forwards the hit to the kraken with the part's index
 * ({@link Kraken#hurtPart}). Parts are never saved; one whose kraken is gone discards itself, and a kraken recreates
 * missing parts. A cut tentacle is not pickable. Client side, a part registers itself with its kraken so the model can
 * aim the tentacle bones at it.
 */
public class KrakenPart extends Entity {

    public static final int EYE_LEFT = 8;
    public static final int EYE_RIGHT = 9;
    public static final int PARTS = 10;

    public static final EntityDimensions TENTACLE_SIZE = EntityDimensions.scalable(0.9f, 2.5f);
    public static final EntityDimensions EYE_SIZE = EntityDimensions.scalable(0.6f, 0.6f);

    private static final EntityDataAccessor<Integer> DATA_PARENT = SynchedEntityData.defineId(KrakenPart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_INDEX = SynchedEntityData.defineId(KrakenPart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_CUT = SynchedEntityData.defineId(KrakenPart.class, EntityDataSerializers.BOOLEAN);

    /** Ticks without a living kraken after which a part discards itself. */
    private static final int ORPHAN_TICKS = 2;

    private int orphanTicks;

    public KrakenPart(EntityType<? extends KrakenPart> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_PARENT, -1);
        builder.define(DATA_INDEX, 0);
        builder.define(DATA_CUT, false);
    }

    /** Sets the owner and the index (call before adding the part to the level). */
    public void bind(Kraken parent, int index) {
        entityData.set(DATA_PARENT, parent.getId());
        entityData.set(DATA_INDEX, index);
        refreshDimensions();
    }

    public int index() {
        return entityData.get(DATA_INDEX);
    }

    public boolean isEye() {
        return index() >= EYE_LEFT;
    }

    public KrakenRules.HitZone zone() {
        return isEye() ? KrakenRules.HitZone.EYE : KrakenRules.HitZone.TENTACLE;
    }

    public boolean isCut() {
        return entityData.get(DATA_CUT);
    }

    void setCut(boolean cut) {
        if (isCut() != cut) entityData.set(DATA_CUT, cut);
    }

    /** The kraken this part belongs to, or null when it is gone. */
    public @Nullable Kraken parent() {
        Entity e = level().getEntity(entityData.get(DATA_PARENT));
        return e instanceof Kraken k && !k.isRemoved() ? k : null;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_INDEX.equals(key)) refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return isEye() ? EYE_SIZE : TENTACLE_SIZE;
    }

    /** The centre of the hit box. */
    public Vec3 centre() {
        return position().add(0, getBbHeight() / 2.0, 0);
    }

    /** Moves the part so the centre of its hit box is at {@code centre}. */
    void moveCentreTo(Vec3 centre) {
        setPos(centre.x, centre.y - getBbHeight() / 2.0, centre.z);
    }

    @Override
    public void tick() {
        super.tick();
        Kraken parent = parent();
        if (parent == null) {
            if (!level().isClientSide && ++orphanTicks > ORPHAN_TICKS) discard();
            return;
        }
        orphanTicks = 0;
        if (level().isClientSide) {
            parent.clientPart(index(), this);
        } else if (parent.part(index()) != this) {
            discard(); // replaced (e.g. a duplicate after a reload)
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isInvulnerableTo(source) || isCut()) {
            return false;
        }
        Kraken parent = parent();
        return parent != null && parent.hurtPart(this, source, amount);
    }

    @Override
    public boolean isPickable() {
        return !isCut();
    }

    @Override
    public boolean canBeHitByProjectile() {
        return !isCut();
    }

    @Override
    public boolean is(Entity other) {
        return this == other || parent() == other;
    }

    @Override
    public @Nullable ItemStack getPickResult() {
        Kraken parent = parent();
        return parent != null ? parent.getPickResult() : null;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return true; // the body takes explosions itself; through the eyes they would count three times
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
