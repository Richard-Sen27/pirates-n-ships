package com.richardsenger.piratesnships.combat.cannon;

import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A fired cannonball (docs/design.md §8.2, §4.6). A heavy thrown projectile with {@code cannons.gravity}; it can't be
 * picked up. On an entity it deals {@code cannons.damage} once, as vanilla {@code arrow} damage with the ball as the
 * direct and the firing player as the causing entity (law, kill credit, "was shot by"). On a block it destroys up to
 * {@link CannonConfig#blocksPerHit()} blocks along its path (the hit block first) that {@link CannonRules#destroyable}
 * allows, pushes a ship it hits and is gone. A ship block destroyed below the waterline becomes a breach through the
 * hull runtime's block-change listener like any removed hull block. In water it splashes once, slows down hard and
 * sinks; it is removed when slower than {@code cannons.sink_speed} or after {@code cannons.ball_lifetime_ticks}.
 *
 * <p>Fired with an explicit velocity (not {@code shootFromRotation}), so neither vanilla nor Sable's
 * {@code ProjectileMixin} adds a shooter's motion: {@link CannonService#fire} adds the ship's velocity itself.
 */
public class CannonballEntity extends ThrowableItemProjectile {

    private float damage;
    private int lifetime = 200;
    private boolean splashed;
    /** Blocks one hit may destroy; negative = the cannon's config. */
    private int blocksPerHit = -1;
    /** Impulse on a hit ship; negative = the cannon's config. */
    private double impactImpulse = -1;

    public CannonballEntity(EntityType<? extends CannonballEntity> type, Level level) {
        super(type, level);
    }

    public CannonballEntity(Level level, Vec3 pos, Vec3 velocity, float damage, int lifetime) {
        this(level, pos, velocity, damage, lifetime, -1, -1);
    }

    /**
     * A shot with its own block damage and impact impulse (the swivel gun, P2): {@code blocksPerHit} blocks at most (the
     * caller applies the block damage toggle), {@code impactImpulse} on a hit ship; a negative value means the cannon's
     * config ({@link CannonConfig#blocksPerHit()}, {@code cannons.impact_impulse}) at the time of the hit.
     */
    public CannonballEntity(Level level, Vec3 pos, Vec3 velocity, float damage, int lifetime, int blocksPerHit, double impactImpulse) {
        super(CannonContent.CANNONBALL.get(), pos.x, pos.y, pos.z, level);
        this.damage = damage;
        this.lifetime = lifetime;
        this.blocksPerHit = blocksPerHit;
        this.impactImpulse = impactImpulse;
        setDeltaMovement(velocity);
        double h = velocity.horizontalDistance();
        setYRot((float) Math.toDegrees(Math.atan2(velocity.x, velocity.z)));
        setXRot((float) Math.toDegrees(Math.atan2(velocity.y, h)));
        yRotO = getYRot();
        xRotO = getXRot();
    }

    public float damage() {
        return damage;
    }

    @Override
    protected Item getDefaultItem() {
        return CombatContent.CANNONBALL.get();
    }

    @Override
    protected double getDefaultGravity() {
        // server config is synced, so client and server simulate the same arc
        return CannonConfig.GRAVITY.get();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || isRemoved()) return;
        if (isInWater()) {
            if (!splashed) {
                splashed = true;
                splash((ServerLevel) level());
            }
            setDeltaMovement(getDeltaMovement().scale(CannonConfig.WATER_SLOWDOWN.get()));
            if (getDeltaMovement().length() < CannonConfig.SINK_SPEED.get()) {
                discard();
                return;
            }
        }
        if (tickCount > lifetime) {
            discard();
        }
    }

    private void splash(ServerLevel level) {
        level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.5, getZ(), 40, 0.4, 0.2, 0.4, 0.3);
        level.sendParticles(ParticleTypes.BUBBLE, getX(), getY(), getZ(), 20, 0.3, 0.3, 0.3, 0.1);
        level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.4, getZ(), 6, 0.3, 0.1, 0.3, 0.02);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 1.5f, 0.7f);
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
        if (!(level() instanceof ServerLevel level)) return;
        BlockPos hitPos = result.getBlockPos();
        // A ship block is hit in plot space (Sable's clip returns the sub-level result as is, sable-notes §9.0e), so the
        // hit point and the block positions are plot coordinates; the flight direction is turned into the plot frame.
        ShipBody ship = SableShips.containing(level, hitPos);
        Vec3 flight = getDeltaMovement();
        Vec3 dir = flight.lengthSqr() < 1.0e-9 ? Vec3.ZERO : flight.normalize();
        Vec3 localDir = ship != null ? CannonService.rotateInverse(ship.orientation(), dir) : dir;
        Vec3 hit = result.getLocation();
        Vec3 worldHit = ship != null ? ship.toWorld(hit) : hit;

        double push = impactImpulse >= 0 ? impactImpulse : CannonConfig.IMPACT_IMPULSE.get();
        if (ship != null && push > 0 && localDir != Vec3.ZERO) {
            ship.applyImpulseNow(hit, localDir.scale(push));
        }

        int destroyed = 0;
        int limit = blocksPerHit >= 0 ? blocksPerHit : CannonConfig.blocksPerHit();
        for (BlockPos p : CannonImpact.blocksAlong(hitPos, hit, localDir, limit, pos -> !level.getBlockState(pos).isAir())) {
            BlockState state = level.getBlockState(p);
            boolean onShip = ship != null || SableShips.containing(level, p) != null;
            if (!CannonRules.destroyable(true, state.isAir(), state.getDestroySpeed(level, p), onShip,
                    state.is(CannonContent.BREAKABLE), state.is(CannonContent.PROOF))) {
                break; // a block that holds stops the ball
            }
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), worldHit.x, worldHit.y, worldHit.z,
                    24, 0.3, 0.3, 0.3, 0.15);
            level.destroyBlock(p, false, this);
            destroyed++;
        }
        if (destroyed > 0) {
            level.playSound(null, worldHit.x, worldHit.y, worldHit.z, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 2.0f, 0.7f);
            level.playSound(null, worldHit.x, worldHit.y, worldHit.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 1.0f, 0.8f);
        } else {
            level.playSound(null, worldHit.x, worldHit.y, worldHit.z, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 1.5f, 0.6f);
        }
        level.sendParticles(ParticleTypes.POOF, worldHit.x, worldHit.y, worldHit.z, 8, 0.2, 0.2, 0.2, 0.05);
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
        tag.putBoolean("splashed", splashed);
        tag.putInt("blocks_per_hit", blocksPerHit);
        tag.putDouble("impact_impulse", impactImpulse);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("damage");
        if (tag.contains("lifetime")) lifetime = tag.getInt("lifetime");
        splashed = tag.getBoolean("splashed");
        blocksPerHit = tag.contains("blocks_per_hit") ? tag.getInt("blocks_per_hit") : -1;
        impactImpulse = tag.contains("impact_impulse") ? tag.getDouble("impact_impulse") : -1;
    }
}
