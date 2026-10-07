package com.richardsenger.piratesnships.combat.firearms;

import com.richardsenger.piratesnships.combat.content.CombatContent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * A fired lead ball (docs/design.md §8.1). A thrown projectile: it flies with {@code firearms.ball_gravity}, deals its
 * damage once on the first entity it hits, and is gone on any hit or after {@code firearms.ball_lifetime_ticks}. It
 * can't be picked up. The damage type is vanilla {@code arrow} with the ball as the direct and the shooter as the
 * causing entity, so {@code DamageSource#getEntity()} is the shooter (law, kill credit, death message "was shot by").
 *
 * <p>Fired through {@code Projectile#shootFromRotation}, so vanilla adds the shooter's own motion and Sable's
 * {@code ProjectileMixin} adds the velocity a shooter inherits from the ship it stands on.
 */
public class LeadBallEntity extends ThrowableItemProjectile {

    private float damage;
    private int lifetime = 100;

    public LeadBallEntity(EntityType<? extends LeadBallEntity> type, Level level) {
        super(type, level);
    }

    public LeadBallEntity(Level level, LivingEntity shooter, float damage, int lifetime) {
        super(FirearmContent.LEAD_BALL.get(), shooter, level);
        this.damage = damage;
        this.lifetime = lifetime;
    }

    public float damage() {
        return damage;
    }

    @Override
    protected Item getDefaultItem() {
        return CombatContent.LEAD_SHOT.get();
    }

    @Override
    protected double getDefaultGravity() {
        // server config is synced, so client and server simulate the same arc
        return FirearmsConfig.BALL_GRAVITY.get();
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && !isRemoved() && tickCount > lifetime) {
            discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (level().isClientSide) return;
        DamageSource source = new DamageSource(
                registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.ARROW), this, getOwner());
        result.getEntity().hurt(source, damage);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (level() instanceof ServerLevel server) {
            var at = result.getLocation();
            server.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 3, 0.05, 0.05, 0.05, 0.01);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) {
            discard();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("damage", damage);
        tag.putInt("lifetime", lifetime);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("damage");
        if (tag.contains("lifetime")) lifetime = tag.getInt("lifetime");
    }
}
