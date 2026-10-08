package com.richardsenger.piratesnships.mob.captain;

import com.richardsenger.piratesnships.apparel.ApparelContent;
import com.richardsenger.piratesnships.mob.MobConfig;
import com.richardsenger.piratesnships.mob.MobKind;
import com.richardsenger.piratesnships.mob.entity.Pirate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

/**
 * A named pirate captain (BOS1, docs/design.md §9, §15): the pirate of the island's captain's hut, a dangerous duelist
 * (skill {@code mobs.captain.skill}, {@code PIRATE_CAPTAIN}), 40 health ({@code mobs.captain.health}), his own look
 * (ART6: model and texture {@code pirate_captain}) with his captain's hat in the head slot. Placed by world
 * generation ({@link IslandCaptains#place}), he keeps his
 * post ({@code stationary}, walks back to it after a fight), never despawns, carries a standing navy bounty, and can
 * be challenged to a duel ({@link DuelChallenge}). On death he drops his hat, a purse of doubloons (loot table
 * {@code entities/pirate_captain}) and a map of his island's treasure; the navy pays the captain's tier for him alive.
 * A successor takes his post after {@code mobs.captain.respawn_days}.
 */
public class PirateCaptain extends Pirate {

    private static final String TAG_PORT = "pirates_n_ships:port";
    private static final String TAG_POST = "pirates_n_ships:post";
    private static final String TAG_FACING = "pirates_n_ships:post_facing";
    /** Blocks from his post beyond which an idle captain walks back. */
    static final double POST_SLACK = 2.0;

    private @Nullable ResourceLocation port;
    private @Nullable BlockPos post;
    private Direction postFacing = Direction.NORTH;
    /** The challenger of the running duel (memory only: a reloaded captain duels nobody). */
    private @Nullable UUID duelOpponent;
    private long duelDeadline;

    public PirateCaptain(EntityType<? extends PirateCaptain> type, Level level) {
        super(type, level);
        // the hat drops whole on every death (above 1: no random wear, no need of a player kill)
        setDropChance(EquipmentSlot.HEAD, 2.0f);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return humanoid(40.0, 0.3, 4.0);
    }

    @Override
    public MobKind kind() {
        return MobKind.PIRATE_CAPTAIN;
    }

    @Override
    protected void equip() {
        super.equip();
        setItemSlot(EquipmentSlot.HEAD, new ItemStack(ApparelContent.CAPTAINS_HAT.get()));
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(5, new ReturnToPostGoal(this));
    }

    // --- island -------------------------------------------------------------------------------------------------

    /** The port id of his island ({@code null}: a captain spawned outside any island registry). */
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

    // --- duel ---------------------------------------------------------------------------------------------------

    public @Nullable UUID duelOpponent() {
        return duelOpponent;
    }

    public long duelDeadline() {
        return duelDeadline;
    }

    public boolean inDuel() {
        return duelOpponent != null;
    }

    void startDuel(UUID challenger, long deadline) {
        duelOpponent = challenger;
        duelDeadline = deadline;
    }

    void endDuel() {
        duelOpponent = null;
        duelDeadline = 0L;
    }

    private boolean isOpponent(LivingEntity e) {
        return duelOpponent != null && duelOpponent.equals(e.getUUID());
    }

    /** In a duel he fights his challenger only; else like any pirate. */
    @Override
    public boolean attacksOnSight(LivingEntity e) {
        if (inDuel()) return e != this && e.isAlive() && isOpponent(e);
        return super.attacksOnSight(e);
    }

    @Override
    public boolean keepsTarget(LivingEntity e) {
        if (inDuel()) return e != this && e.isAlive() && !e.isRemoved() && isOpponent(e);
        return super.keepsTarget(e);
    }

    /** Sneak-use with a sword: the duel challenge ({@link DuelChallenge}). */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        InteractionResult duel = DuelChallenge.interact(this, player, hand);
        return duel != InteractionResult.PASS ? duel : super.mobInteract(player, hand);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (inDuel() && tickCount % 20 == 0) DuelChallenge.tick(this);
    }

    // --- death and removal --------------------------------------------------------------------------------------

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide && dead) {
            if (inDuel()) DuelChallenge.end(this, DuelRules.End.CAPTAIN_DIED);
            IslandCaptains.onDeath(this, source);
        }
    }

    /** Handed over to the navy, discarded by a command or a disabled config: the island has lost its captain. */
    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason == RemovalReason.DISCARDED && !dead) {
            if (inDuel()) DuelChallenge.end(this, DuelRules.End.CAPTAIN_DIED);
            IslandCaptains.onLost(this);
        }
        super.remove(reason);
    }

    /** His hat (equipment, drop chance above 1) and the map of his island; nothing while {@code mobs.drops} is off. */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        if (!MobConfig.DROPS.get()) return;
        super.dropCustomDeathLoot(level, source, recentlyHit);
        ItemStack map = IslandCaptains.islandMap(level, this);
        if (!map.isEmpty()) spawnAtLocation(map);
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

    /** An idle captain more than {@link #POST_SLACK} blocks from his post walks back to it and turns to his door. */
    static final class ReturnToPostGoal extends Goal {

        private final PirateCaptain captain;
        private int cooldown;

        ReturnToPostGoal(PirateCaptain captain) {
            this.captain = captain;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (captain.getTarget() != null || captain.post == null) return false;
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            cooldown = reducedTickDelay(40);
            return captain.post.distToCenterSqr(captain.position()) > POST_SLACK * POST_SLACK;
        }

        @Override
        public void start() {
            BlockPos p = captain.post;
            if (p != null) captain.getNavigation().moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0.8);
        }

        @Override
        public boolean canContinueToUse() {
            return captain.getTarget() == null && !captain.getNavigation().isDone();
        }

        @Override
        public void stop() {
            captain.getNavigation().stop();
            if (captain.post != null && captain.post.distToCenterSqr(captain.position()) <= POST_SLACK * POST_SLACK) {
                float yaw = captain.postFacing.toYRot();
                captain.setYRot(yaw);
                captain.setYHeadRot(yaw);
                captain.yBodyRot = yaw;
            }
        }
    }
}
