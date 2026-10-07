package com.richardsenger.piratesnships.law.content;

import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.law.brig.BrigDoorAccess;
import com.richardsenger.piratesnships.law.brig.DoorLockRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A lockable door (design.md §13.3). {@link #LOCKED} is a block state property on both halves (persisted, synced,
 * own models). The owner (the player who placed it) is stored in a {@link BrigDoorBlockEntity} on the lower half.
 * <ul>
 *   <li>Use: opens and closes like a wooden door, unless locked; a locked door opens only for its owner (and
 *       {@link BrigDoorAccess} rules), like a key. Locking does not change {@code open}.</li>
 *   <li>Sneak-use with an empty hand: the owner (or anyone, for a door without an owner, who then becomes the owner)
 *       locks or unlocks it.</li>
 *   <li>Mobs and redstone can't open or close a locked door. Its block set has {@code canOpenByHand = false}, so
 *       villagers and zombies treat it like an iron door and never open or break it, and mob pathfinding sees it as
 *       closed.</li>
 * </ul>
 *
 * <p>Model: a hand-made cell door (art/models/brig_door_*.bbmodel, design.md §4.8): an iron frame with four round
 * bars per half, a cross band, hinge straps on the hinge side and a lock plate with a pull on the free side. A locked
 * lower half also shows a brass padlock on the side the placing player faced.
 */
public class BrigDoorBlock extends DoorBlock implements EntityBlock {

    public static final MapCodec<BrigDoorBlock> CODEC = simpleCodec(BrigDoorBlock::new);
    public static final BooleanProperty LOCKED = BooleanProperty.create("locked");

    public BrigDoorBlock(Properties properties) {
        super(LawContent.BRIG_SET, properties);
        registerDefaultState(defaultBlockState().setValue(LOCKED, false));
    }

    @Override
    public MapCodec<? extends DoorBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LOCKED);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new BrigDoorBlockEntity(pos, state) : null;
    }

    // --- Queries and API ----------------------------------------------------------------------------------------

    public static BlockPos lowerHalf(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    public static boolean isLocked(BlockState state) {
        return state.getBlock() instanceof BrigDoorBlock && state.getValue(LOCKED);
    }

    public static @Nullable UUID owner(Level level, BlockPos anyHalf) {
        BlockState state = level.getBlockState(anyHalf);
        if (!(state.getBlock() instanceof BrigDoorBlock)) return null;
        return level.getBlockEntity(lowerHalf(state, anyHalf)) instanceof BrigDoorBlockEntity be ? be.owner() : null;
    }

    public static void setOwner(Level level, BlockPos anyHalf, @Nullable Player owner) {
        BlockState state = level.getBlockState(anyHalf);
        if (!(state.getBlock() instanceof BrigDoorBlock)) return;
        if (level.getBlockEntity(lowerHalf(state, anyHalf)) instanceof BrigDoorBlockEntity be) {
            be.setOwner(owner == null ? null : owner.getUUID(), owner == null ? "" : owner.getName().getString());
        }
    }

    /** Sets the lock on both halves (the upper half follows through {@code updateShape}). */
    public static void setLocked(Level level, BlockPos anyHalf, boolean locked) {
        BlockState state = level.getBlockState(anyHalf);
        if (!(state.getBlock() instanceof BrigDoorBlock) || state.getValue(LOCKED) == locked) return;
        BlockPos lower = lowerHalf(state, anyHalf);
        level.setBlock(lower, level.getBlockState(lower).setValue(LOCKED, locked), Block.UPDATE_ALL);
    }

    public static boolean hasAccess(Level level, BlockPos anyHalf, Player player) {
        BlockState state = level.getBlockState(anyHalf);
        return BrigDoorAccess.allows(level, lowerHalf(state, anyHalf), player);
    }

    // --- Behavior -----------------------------------------------------------------------------------------------

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player) setOwner(level, pos, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        UUID owner = owner(level, pos);
        boolean access = hasAccess(level, pos, player);
        if (player.isSecondaryUseActive()) {
            if (!DoorLockRules.canChangeLock(owner, player.getUUID(), access)) {
                player.displayClientMessage(Component.translatable("message.pirates_n_ships.brig.door.not_owner"), true);
                return InteractionResult.CONSUME;
            }
            if (owner == null) setOwner(level, pos, player);
            boolean locked = !state.getValue(LOCKED);
            setLocked(level, pos, locked);
            level.playSound(null, pos, locked ? SoundEvents.IRON_TRAPDOOR_CLOSE : SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.8f, 1.4f);
            player.displayClientMessage(Component.translatable(locked
                    ? "message.pirates_n_ships.brig.door.locked" : "message.pirates_n_ships.brig.door.unlocked"), true);
            return InteractionResult.CONSUME;
        }
        if (!DoorLockRules.canOpen(state.getValue(LOCKED), owner, player.getUUID(), access)) {
            player.displayClientMessage(Component.translatable("message.pirates_n_ships.brig.door.is_locked"), true);
            level.playSound(null, pos, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.6f, 1.2f);
            return InteractionResult.CONSUME;
        }
        BlockState next = state.cycle(OPEN);
        level.setBlock(pos, next, Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
        playDoorSound(player, level, pos, next.getValue(OPEN));
        level.gameEvent(player, next.getValue(OPEN) ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
        return InteractionResult.CONSUME;
    }

    /** Mobs (e.g. villagers' door behavior) and other code: refused while locked. */
    @Override
    public void setOpen(@Nullable Entity entity, Level level, BlockState state, BlockPos pos, boolean open) {
        if (state.is(this) && state.getValue(LOCKED)) return;
        super.setOpen(entity, level, state, pos, open);
    }

    /** Redstone: while locked, only {@code powered} follows the signal, {@code open} stays. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!state.getValue(LOCKED)) {
            super.neighborChanged(state, level, pos, block, fromPos, isMoving);
            return;
        }
        boolean powered = level.hasNeighborSignal(pos)
                || level.hasNeighborSignal(pos.relative(state.getValue(HALF) == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN));
        if (!defaultBlockState().is(block) && powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        }
    }

    private void playDoorSound(@Nullable Entity source, Level level, BlockPos pos, boolean opening) {
        level.playSound(source, pos, opening ? type().doorOpen() : type().doorClose(), SoundSource.BLOCKS, 1.0f,
                level.getRandom().nextFloat() * 0.1f + 0.9f);
    }
}
