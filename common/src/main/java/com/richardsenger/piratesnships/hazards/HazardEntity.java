package com.richardsenger.piratesnships.hazards;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * A sea hazard (docs/design.md §12): an invisible, server-authoritative marker entity without hitbox collision,
 * gravity or AI that lives for {@link #lifetime()} ticks and is saved with the world. The server applies its force
 * field every tick ({@link HazardForces}); the client only spawns its particles and sounds ({@link HazardVisuals}).
 * The radius (and the waterspout's funnel height) follow the config and are synced so the client draws the right size.
 * A hazard whose kind is disabled in the config removes itself.
 */
public abstract class HazardEntity extends Entity {

    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(HazardEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEIGHT = SynchedEntityData.defineId(HazardEntity.class, EntityDataSerializers.FLOAT);

    private int age;
    private int lifetime = 1200;

    protected HazardEntity(EntityType<? extends HazardEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    public abstract HazardKind kind();

    /** Whether this kind is enabled in the server config. */
    protected abstract boolean enabled();

    /** Copies the size from the config into the synced data (cheap when unchanged). */
    protected abstract void syncSize();

    /** One server tick of the force field. */
    protected abstract void serverTick(ServerLevel level);

    /** Sets up a freshly spawned hazard from the config (lifetime, size, drift). */
    public abstract void configure(net.minecraft.util.RandomSource random);

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RADIUS, 8.0f);
        builder.define(HEIGHT, 0.0f);
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    public float height() {
        return entityData.get(HEIGHT);
    }

    protected void setSize(float radius, float height) {
        if (entityData.get(RADIUS) != radius) entityData.set(RADIUS, radius);
        if (entityData.get(HEIGHT) != height) entityData.set(HEIGHT, height);
    }

    public int age() {
        return age;
    }

    public int lifetime() {
        return lifetime;
    }

    public void setLifetime(int ticks) {
        this.lifetime = Math.max(1, ticks);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) {
            HazardVisuals.tick(this);
            return;
        }
        if (!enabled()) {
            discard();
            return;
        }
        age++;
        if (age >= lifetime) {
            discard();
            return;
        }
        syncSize();
        serverTick(server);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        age = tag.getInt("Age");
        lifetime = Math.max(1, tag.getInt("Lifetime"));
        setSize(tag.getFloat("Radius"), tag.getFloat("FunnelHeight"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", age);
        tag.putInt("Lifetime", lifetime);
        tag.putFloat("Radius", radius());
        tag.putFloat("FunnelHeight", height());
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
    public boolean ignoreExplosion(net.minecraft.world.level.Explosion explosion) {
        return true;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }
}
