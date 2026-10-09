package com.richardsenger.piratesnships.mob.captain;

import com.richardsenger.piratesnships.apparel.ApparelContent;
import com.richardsenger.piratesnships.mob.MobConfig;
import com.richardsenger.piratesnships.mob.MobKind;
import com.richardsenger.piratesnships.mob.ai.ReturnToPostGoal;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A named pirate captain (BOS1, docs/design.md §9, §15): the pirate of the island's captain's hut, a dangerous duelist
 * (skill {@code mobs.captain.skill}, {@code PIRATE_CAPTAIN}), 40 health ({@code mobs.captain.health}), his own look
 * (ART6: model and texture {@code pirate_captain}) with his captain's hat in the head slot. Placed by world
 * generation ({@link IslandCaptains#place}), he keeps his
 * post ({@code stationary}, walks back to it after a fight), never despawns, carries a standing navy bounty, and can
 * be challenged to a duel ({@link DuelChallenge}). On death he drops his hat, maybe pieces of his clothing (ART9), a
 * purse of doubloons (loot table
 * {@code entities/pirate_captain}) and a map of his island's treasure; the navy pays the captain's tier for him alive.
 * A successor takes his post after {@code mobs.captain.respawn_days}.
 */
public class PirateCaptain extends Pirate {

    private static final String TAG_PORT = "pirates_n_ships:port";
    private static final String TAG_POST = "pirates_n_ships:post";
    private static final String TAG_FACING = "pirates_n_ships:post_facing";
    private static final String TAG_SEA_VOYAGE = "pirates_n_ships:sea_voyage";
    /** Blocks from his post beyond which an idle captain walks back. */
    static final double POST_SLACK = 2.0;
    /** How often (ticks) a loaded captain asks the sea hook whether he is where he belongs (BOS2). */
    public static final int SEA_CHECK_INTERVAL = 20;

    private static volatile CaptainSeaHook seaHook = CaptainSeaHook.NONE;

    private @Nullable ResourceLocation port;
    private @Nullable BlockPos post;
    private Direction postFacing = Direction.NORTH;
    /** The challenger of the running duel (memory only: a reloaded captain duels nobody). */
    private @Nullable UUID duelOpponent;
    private long duelDeadline;
    /** The voyage he is aboard (BOS2): no post to walk back to while it is set. Saved. */
    private @Nullable UUID seaVoyage;
    /** Leaving the world without being lost or stowed (a stale copy, BOS2). Memory only. */
    private boolean vanishing;

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
        // at sea (BOS2) he has no post to walk back to
        goalSelector.addGoal(5, new ReturnToPostGoal(this, () -> seaVoyage == null ? post : null, this::postFacing, POST_SLACK));
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

    // --- at sea (BOS2) ------------------------------------------------------------------------------------------

    /** Sets what the captain's voyages hear from captains ({@code worldsim.captain}). */
    public static void setSeaHook(CaptainSeaHook hook) {
        seaHook = hook == null ? CaptainSeaHook.NONE : hook;
    }

    /** The voyage he is aboard, or null at his post. */
    public @Nullable UUID seaVoyage() {
        return seaVoyage;
    }

    /** Aboard {@code voyage}'s ship (no post to walk back to), or back ashore ({@code null}). */
    public void setSeaVoyage(@Nullable UUID voyage) {
        seaVoyage = voyage;
    }

    /** Removes this copy of the captain without losing him: he lives on elsewhere (at sea, or at his post). */
    public void vanish() {
        vanishing = true;
        discard();
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
        if (tickCount % SEA_CHECK_INTERVAL == 7 && !isRemoved()) seaHook.check(this);
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
            if (vanishing) {
                if (inDuel()) DuelChallenge.end(this, DuelRules.End.TIMED_OUT);
            } else if (seaHook.stows(this)) {
                // his ship became a record again (BOS2): he sails on in it; a duel aboard is off
                if (inDuel()) DuelChallenge.end(this, DuelRules.End.TIMED_OUT);
                seaHook.stow(this);
            } else {
                if (inDuel()) DuelChallenge.end(this, DuelRules.End.CAPTAIN_DIED);
                IslandCaptains.onLost(this);
            }
        }
        super.remove(reason);
    }

    /**
     * His hat (equipment, drop chance above 1), each piece of his clothing at {@code mobs.captain.clothing_drop_chance}
     * (ART9, {@link ClothingDrops}) and the map of his island; nothing while {@code mobs.drops} is off.
     */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        if (!MobConfig.DROPS.get()) return;
        super.dropCustomDeathLoot(level, source, recentlyHit);
        for (var piece : ClothingDrops.roll(ApparelContent.CAPTAINS_CLOTHING, CaptainConfig.CLOTHING_DROP_CHANCE.get(), getRandom()::nextDouble)) {
            spawnAtLocation(new ItemStack(piece.get()));
        }
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
        if (seaVoyage != null) tag.putUUID(TAG_SEA_VOYAGE, seaVoyage);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        port = tag.contains(TAG_PORT) ? ResourceLocation.tryParse(tag.getString(TAG_PORT)) : null;
        post = NbtUtils.readBlockPos(tag, TAG_POST).orElse(null);
        Direction facing = Direction.byName(tag.getString(TAG_FACING));
        postFacing = facing != null && facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
        seaVoyage = tag.hasUUID(TAG_SEA_VOYAGE) ? tag.getUUID(TAG_SEA_VOYAGE) : null;
    }
}
