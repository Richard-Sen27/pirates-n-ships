package com.richardsenger.piratesnships.mob.harbor;

import com.richardsenger.piratesnships.mob.MobConfig;
import com.richardsenger.piratesnships.mob.MobKind;
import com.richardsenger.piratesnships.mob.ai.ReturnToPostGoal;
import com.richardsenger.piratesnships.mob.entity.Sailor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The harbor master (PRT1a, docs/design.md §10.3): a sailor in a coat and hat who stands behind a port's harbor desk.
 * Placed by world generation ({@link HarborMasters#place}), he keeps his post ({@code stationary}, walks back to it
 * with {@link ReturnToPostGoal}), never despawns, and a new one takes the post {@code mobs.harbor_master.respawn_days}
 * after his death. Talking to him opens the desk ({@link HarborMasterInteractions}). Like any sailor he never fights,
 * runs from pirates and panics when hurt; the law treats him as it treats sailors.
 */
public class HarborMaster extends Sailor {

    private static final String TAG_PORT = "pirates_n_ships:port";
    private static final String TAG_POST = "pirates_n_ships:post";
    private static final String TAG_FACING = "pirates_n_ships:post_facing";
    /** Ticks after a hit (by anyone) during which he is too shaken to talk. */
    static final int SHAKEN_TICKS = 100;

    private @Nullable ResourceLocation port;
    private @Nullable BlockPos post;
    private Direction postFacing = Direction.NORTH;
    /** The player who last hit him and until which game time he resents it (memory only). */
    private @Nullable UUID resented;
    private long resentUntil;

    public HarborMaster(EntityType<? extends HarborMaster> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Sailor.createAttributes();
    }

    @Override
    public MobKind kind() {
        return MobKind.HARBOR_MASTER;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(5, new ReturnToPostGoal(this, this::post, this::postFacing, HarborMasterConfig.RETURN_DISTANCE.get()));
    }

    // --- post ---------------------------------------------------------------------------------------------------

    /** The port whose desk he keeps ({@code null}: one spawned outside any port, e.g. by a command). */
    public @Nullable ResourceLocation port() {
        return port;
    }

    public @Nullable BlockPos post() {
        return post;
    }

    public Direction postFacing() {
        return postFacing;
    }

    /** Binds him to {@code port}'s post {@code post}, facing {@code facing}. */
    public void assign(@Nullable ResourceLocation port, @Nullable BlockPos post, Direction facing) {
        this.port = port;
        this.post = post == null ? null : post.immutable();
        this.postFacing = facing;
    }

    // --- mood ---------------------------------------------------------------------------------------------------

    /** Whether {@code player} hit him within {@code mobs.grudge_ticks}: he won't serve them meanwhile. */
    public boolean resents(Player player) {
        return resented != null && resented.equals(player.getUUID()) && level().getGameTime() < resentUntil;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && source.getEntity() instanceof Player player) {
            resented = player.getUUID();
            resentUntil = level().getGameTime() + MobConfig.GRUDGE_TICKS.get();
        }
        return hurt;
    }

    /** Whether he is running or about to: hurt a moment ago, burning, or something he flees from is near. */
    public boolean fleeing() {
        if (isOnFire() || getLastHurtByMob() != null && tickCount - getLastHurtByMobTimestamp() < SHAKEN_TICKS) return true;
        double range = MobConfig.SAILOR_FLEE_RANGE.get();
        return !level().getEntitiesOfClass(LivingEntity.class, new AABB(blockPosition()).inflate(range),
                e -> e != this && e.isAlive() && fleesFrom(e)).isEmpty();
    }

    // --- death and removal --------------------------------------------------------------------------------------

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide && dead) HarborMasters.onLost(this);
    }

    /** Discarded alive (a command, a disabled config, a test): the port has lost its harbor master. */
    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason == RemovalReason.DISCARDED && !dead) HarborMasters.onLost(this);
        super.remove(reason);
    }

    // --- persistence --------------------------------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (port != null) tag.putString(TAG_PORT, port.toString());
        if (post != null) tag.put(TAG_POST, NbtUtils.writeBlockPos(post));
        tag.putString(TAG_FACING, postFacing.getSerializedName());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        port = tag.contains(TAG_PORT) ? ResourceLocation.tryParse(tag.getString(TAG_PORT)) : null;
        post = NbtUtils.readBlockPos(tag, TAG_POST).orElse(null);
        Direction facing = Direction.byName(tag.getString(TAG_FACING));
        postFacing = facing != null && facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
    }
}
