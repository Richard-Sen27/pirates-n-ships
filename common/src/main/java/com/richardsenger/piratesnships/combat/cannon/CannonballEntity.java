package com.richardsenger.piratesnships.combat.cannon;

import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.winch.CaptainsWhistleItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A fired cannonball (docs/design.md §8.2, §4.6). A heavy thrown projectile with {@code cannons.gravity}; it can't be
 * picked up. On an entity it deals {@code cannons.damage} once, as vanilla {@code arrow} damage with the ball as the
 * direct and the firing player as the causing entity (law, kill credit, "was shot by"). On a block it destroys up to
 * {@link CannonConfig#blocksPerHit()} blocks along its path (the hit block first) that {@link CannonRules#destroyable}
 * allows, pushes a ship it hits and is gone. A ship block destroyed below the waterline becomes a breach through the
 * hull runtime's block-change listener like any removed hull block. Q2: a glancing hit breaks fewer blocks and pushes
 * less ({@link CannonImpact#glancingBlocks}); a grazing one bounces off and flies on. In water it splashes once, slows down hard and
 * sinks; it is removed when slower than {@code cannons.sink_speed} or after {@code cannons.ball_lifetime_ticks}.
 *
 * <p>CAN3: the same entity flies chain shot ({@link ShotKind#CHAIN}: through sails and rigging, {@link RiggingDamage}, no
 * block but ratlines broken) and grapeshot pellets ({@link ShotKind#GRAPE}: hurt the entity they hit, never the people on
 * the firing ship, nothing else).
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
    /** Set by a glancing hit that bounced (Q2) for {@link #onHit}: the ball flies on. Not saved. */
    private boolean bounced;
    /** The ship the ball was fired from (FL2, for {@link CannonShipHits}), or null. Saved. */
    private @Nullable UUID firingShip;
    /** The ship this ball last reported a hit on, so a bounce and the following hit on one ship report once. Not saved. */
    private @Nullable UUID reportedShip;
    /** Scales the blocks a hit breaks ({@link CannonRules#scaledBlocks}); WS4a crews firing by themselves. Saved. */
    private double blockDamageFactor = 1.0;
    /** CAN3: what this is, a ball, chain shot or a grapeshot pellet. Saved. */
    private ShotKind kind = ShotKind.BALL;
    /** CAN3: where a chain shot struck this tick (world), which ends this tick's sweep through the rigging. Not saved. */
    private @Nullable Vec3 struckAt;

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

    /** The ship the ball was fired from, or null. */
    public @Nullable UUID firingShip() {
        return firingShip;
    }

    /** Set by the gun that fires the ball ({@link CannonService#fire}, {@link SwivelService#fire}). */
    public void setFiringShip(@Nullable UUID ship) {
        this.firingShip = ship;
    }

    public double blockDamageFactor() {
        return blockDamageFactor;
    }

    /** Scales the blocks this ball breaks on a hit (1 = as configured). Set by {@link CannonService#fire}. */
    public void setBlockDamageFactor(double factor) {
        this.blockDamageFactor = Math.max(0.0, factor);
    }

    public ShotKind kind() {
        return kind;
    }

    /**
     * What this shot is (CAN3), set by the gun before it is added: the drawn item follows (chain shot, or an iron nugget
     * for a grapeshot pellet).
     */
    public void setKind(ShotKind kind) {
        this.kind = kind;
        setItem(switch (kind) {
            case BALL -> new ItemStack(CombatContent.CANNONBALL.get());
            case CHAIN -> new ItemStack(CannonContent.CHAIN_SHOT.get());
            case GRAPE -> new ItemStack(Items.IRON_NUGGET);
        });
    }

    @Override
    protected Item getDefaultItem() {
        return CombatContent.CANNONBALL.get();
    }

    /**
     * A grapeshot pellet never hits the people on the ship it was fired from, nor the player who fired it (CAN3): the gun
     * crew and the boarders around the gun stand in the cone's way.
     */
    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target)) return false;
        if (kind != ShotKind.GRAPE || !(level() instanceof ServerLevel level)) return true;
        if (target == getOwner()) return false;
        if (firingShip == null) return true;
        ShipBody on = CaptainsWhistleItem.shipOf(level, target);
        return on == null || !on.id().equals(firingShip);
    }

    @Override
    protected double getDefaultGravity() {
        // server config is synced, so client and server simulate the same arc
        return CannonConfig.GRAVITY.get();
    }

    @Override
    public void tick() {
        Vec3 from = position();
        struckAt = null;
        super.tick();
        if (kind == ShotKind.CHAIN && level() instanceof ServerLevel level && CannonConfig.CHAIN_RIGGING_DAMAGE.get()) {
            // CAN3: the step through the rigging, up to where it struck
            Vec3 to = struckAt != null ? struckAt : position();
            RiggingDamage.sweep(level, firingShip, from, to, CannonConfig.CHAIN_CLOTH_RADIUS.get(), this::mayBreak);
        }
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
        if (kind == ShotKind.GRAPE) { // CAN3: nine small pellets, nine small splashes
            level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.3, getZ(), 6, 0.1, 0.1, 0.1, 0.1);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 0.3f, 1.6f);
            return;
        }
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
        if (kind == ShotKind.GRAPE) {
            pelletHit(level, result);
            return;
        }
        if (kind == ShotKind.CHAIN) {
            chainHit(level, result);
            return;
        }
        BlockPos hitPos = result.getBlockPos();
        // A ship block is hit in plot space (Sable's clip returns the sub-level result as is, sable-notes §9.0e), so the
        // hit point, the block positions and the hit face are plot coordinates; the flight direction is turned into the
        // plot frame.
        ShipBody ship = SableShips.containing(level, hitPos);
        Vec3 flight = getDeltaMovement();
        Vec3 dir = flight.lengthSqr() < 1.0e-9 ? Vec3.ZERO : flight.normalize();
        Vec3 localDir = ship != null ? CannonService.rotateInverse(ship.orientation(), dir) : dir;
        Vec3 hit = result.getLocation();
        Vec3 worldHit = ship != null ? ship.toWorld(hit) : hit;
        Vec3 localNormal = Vec3.atLowerCornerOf(result.getDirection().getNormal());

        // Glancing hits (Q2): how squarely the ball meets the face scales the push and the blocks it breaks.
        boolean glancing = CannonConfig.GLANCING_HITS.get();
        double square = glancing ? CannonImpact.squareness(localDir, localNormal) : 1.0;
        boolean bounce = glancing && CannonImpact.bounces(square, CannonConfig.GLANCING_BOUNCE_DEGREES.get());

        // FL2: a hit on another ship is announced (the law decides whether it is a crime), once per ship and ball
        if (ship != null && !ship.id().equals(firingShip) && !ship.id().equals(reportedShip)) {
            reportedShip = ship.id();
            CannonShipHits.fire(new CannonShipHits.ShipHit(level, ship.id(), getOwner(), firingShip, worldHit));
        }

        double push = (impactImpulse >= 0 ? impactImpulse : CannonConfig.IMPACT_IMPULSE.get()) * square;
        if (ship != null && push > 0 && localDir != Vec3.ZERO) {
            ship.applyImpulseNow(hit, localDir.scale(push));
        }

        if (bounce) {
            Vec3 worldNormal = ship != null ? CannonService.rotate(ship.orientation(), localNormal) : localNormal;
            setDeltaMovement(CannonImpact.deflect(flight, worldNormal, CannonConfig.GLANCING_BOUNCE_FACTOR.get()));
            // off the face, so this tick's move starts outside the block
            setPos(worldHit.add(worldNormal.scale(0.05)));
            hasImpulse = true;
            bounced = true;
            level.playSound(null, worldHit.x, worldHit.y, worldHit.z, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.4f, 1.6f);
            level.sendParticles(ParticleTypes.CRIT, worldHit.x, worldHit.y, worldHit.z, 6, 0.1, 0.1, 0.1, 0.1);
            return;
        }

        int limit = blocksPerHit >= 0 ? blocksPerHit : CannonConfig.blocksPerHit();
        limit = CannonRules.scaledBlocks(limit, blockDamageFactor, random.nextDouble());
        if (glancing) {
            limit = CannonImpact.glancingBlocks(limit, square);
        }
        if (!CannonRules.worldAllowsBlockDamage(CannonConfig.RESPECT_MOB_GRIEFING.get(),
                level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING))) {
            limit = 0;
        }
        boolean drops = CannonConfig.DESTROYED_BLOCKS_DROP.get();
        int destroyed = 0;
        for (BlockPos p : CannonImpact.blocksAlong(hitPos, hit, localDir, limit, pos -> !level.getBlockState(pos).isAir())) {
            BlockState state = level.getBlockState(p);
            ShipBody owner = ship != null ? ship : SableShips.containing(level, p);
            if (!CannonRules.destroyable(true, state.isAir(), state.getDestroySpeed(level, p), owner != null,
                    state.is(CannonContent.BREAKABLE), state.is(CannonContent.PROOF))) {
                break; // a block that holds stops the ball
            }
            // spawn protection is a world-space rule: a ship block counts where it is in the world
            BlockPos worldPos = owner != null ? BlockPos.containing(owner.toWorld(Vec3.atCenterOf(p))) : p;
            if (spawnProtected(level, worldPos)) {
                break; // a protected block holds like a cannon-proof one
            }
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), worldHit.x, worldHit.y, worldHit.z,
                    24, 0.3, 0.3, 0.3, 0.15);
            // The drops spawn at the plot position; Sable's popResource and addFreshEntity mixins move them into the
            // world at the block's world position (refs/sable common mixin/entity/entity_kicking/BlockMixin and
            // ServerLevelMixin).
            level.destroyBlock(p, drops, this);
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

    /** A grapeshot pellet on a block (CAN3): a spark and a ping, nothing breaks, nothing is pushed. */
    private void pelletHit(ServerLevel level, BlockHitResult result) {
        ShipBody ship = SableShips.containing(level, result.getBlockPos());
        Vec3 at = ship != null ? ship.toWorld(result.getLocation()) : result.getLocation();
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 3, 0.05, 0.05, 0.05, 0.05);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.4f, 1.8f);
    }

    /**
     * Chain shot on a block (CAN3): the hit is announced and pushes the ship like a ball's, it tears the cloth, cuts the
     * ropes and breaks the ratlines within {@code cannons.chain_shot.cloth_radius} ({@link RiggingDamage#strike}) and no
     * other block, so no hull is breached. A shot that only broke ratlines flies on.
     */
    private void chainHit(ServerLevel level, BlockHitResult result) {
        BlockPos hitPos = result.getBlockPos();
        ShipBody ship = SableShips.containing(level, hitPos);
        Vec3 flight = getDeltaMovement();
        Vec3 dir = flight.lengthSqr() < 1.0e-9 ? Vec3.ZERO : flight.normalize();
        Vec3 localDir = ship != null ? CannonService.rotateInverse(ship.orientation(), dir) : dir;
        Vec3 hit = result.getLocation();
        Vec3 worldHit = ship != null ? ship.toWorld(hit) : hit;
        struckAt = worldHit;
        if (ship != null && !ship.id().equals(firingShip) && !ship.id().equals(reportedShip)) {
            reportedShip = ship.id();
            CannonShipHits.fire(new CannonShipHits.ShipHit(level, ship.id(), getOwner(), firingShip, worldHit));
        }
        double push = impactImpulse >= 0 ? impactImpulse : CannonConfig.IMPACT_IMPULSE.get();
        if (ship != null && push > 0 && localDir != Vec3.ZERO) {
            ship.applyImpulseNow(hit, localDir.scale(push));
        }
        if (CannonConfig.CHAIN_RIGGING_DAMAGE.get()) {
            RiggingDamage.strike(level, ship, hit, CannonConfig.CHAIN_CLOTH_RADIUS.get(), this::mayBreak);
        }
        level.playSound(null, worldHit.x, worldHit.y, worldHit.z, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.5f, 0.7f);
        level.sendParticles(ParticleTypes.POOF, worldHit.x, worldHit.y, worldHit.z, 6, 0.2, 0.2, 0.2, 0.05);
        if (level.getBlockState(hitPos).isAir()) {
            bounced = true; // the block it struck (ratlines) is gone: it flies on
            struckAt = null;
        }
    }

    /**
     * Whether a chain shot may break the rigging block at {@code pos} (frame position; {@code ship} its ship or null):
     * the cannon block damage toggle, {@code mobGriefing} and the spawn protection, as for a ball's blocks.
     */
    boolean mayBreak(BlockPos pos, @Nullable ShipBody ship) {
        if (!(level() instanceof ServerLevel level) || CannonConfig.blocksPerHit() <= 0) return false;
        if (!CannonRules.worldAllowsBlockDamage(CannonConfig.RESPECT_MOB_GRIEFING.get(),
                level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING))) return false;
        BlockPos worldPos = ship != null ? BlockPos.containing(ship.toWorld(Vec3.atCenterOf(pos))) : pos;
        return !spawnProtected(level, worldPos);
    }

    /**
     * Whether the block at the world position {@code worldPos} is under the server's spawn protection
     * ({@code cannons.respect_spawn_protection}). A ball fired by a player asks the server as a block break by that
     * player would; any other ball follows the same vanilla rule for a shooter who is no operator. Claims of claim mods
     * have no vanilla API and are not checked.
     */
    private boolean spawnProtected(ServerLevel level, BlockPos worldPos) {
        if (!CannonConfig.RESPECT_SPAWN_PROTECTION.get()) return false;
        MinecraftServer server = level.getServer();
        if (getOwner() instanceof Player player) {
            return server.isUnderSpawnProtection(level, worldPos, player);
        }
        BlockPos spawn = level.getSharedSpawnPos();
        return CannonRules.spawnProtected(server.isDedicatedServer(), level.dimension() == Level.OVERWORLD,
                !server.getPlayerList().getOps().isEmpty(), false, server.getSpawnProtectionRadius(),
                spawn.getX(), spawn.getZ(), worldPos.getX(), worldPos.getZ());
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) {
            if (bounced) {
                bounced = false; // a glancing ball flies on
            } else {
                discard();
            }
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
        if (firingShip != null) tag.putUUID("firing_ship", firingShip);
        tag.putDouble("block_damage_factor", blockDamageFactor);
        tag.putString("shot", kind.getSerializedName());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("damage");
        if (tag.contains("lifetime")) lifetime = tag.getInt("lifetime");
        splashed = tag.getBoolean("splashed");
        blocksPerHit = tag.contains("blocks_per_hit") ? tag.getInt("blocks_per_hit") : -1;
        impactImpulse = tag.contains("impact_impulse") ? tag.getDouble("impact_impulse") : -1;
        firingShip = tag.hasUUID("firing_ship") ? tag.getUUID("firing_ship") : null;
        blockDamageFactor = tag.contains("block_damage_factor") ? tag.getDouble("block_damage_factor") : 1.0;
        kind = ShotKind.BALL;
        for (ShotKind k : ShotKind.values()) {
            if (k.getSerializedName().equals(tag.getString("shot"))) kind = k;
        }
    }
}
