package com.richardsenger.piratesnships.combat.cannon;

import com.richardsenger.piratesnships.station.StationBlock;
import com.richardsenger.piratesnships.station.StationKind;
import com.richardsenger.piratesnships.station.Stations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The swivel gun (docs/design.md §8.2, P2): a small gun on a yoke that sits on top of a fence, wall, iron bars, brig
 * bars or other railing ({@link CannonContent#SWIVEL_MOUNTS}) or on a full block, and turns freely. It has no facing:
 * yaw and elevation live in the {@link SwivelGunBlockEntity}, and the block entity renderer draws the yoke turned by
 * the yaw and the barrel also raised by the elevation (the chunk draws nothing). {@link #LOAD} is what is in the barrel;
 * {@link #PIECE} only selects the model the renderer draws (the placed block is always the yoke).
 *
 * <p>Controls, on the server through {@link SwivelService}:
 * <ul>
 *   <li>use with gunpowder: powder in; use with the ammo ({@code cannons.swivel.ammo}): shot in;</li>
 *   <li>hold use with an empty hand: take the gun; it follows the view (yaw and pitch) while the button is held, and
 *       fires when it is let go if loaded ({@code client/SwivelAimClient} sends the release);</li>
 *   <li>sneak-use with an empty hand: say what the gun needs.</li>
 * </ul>
 * A gun whose mount goes is dropped. It is also a crew station ({@link SwivelStation}): the whistle's "Fire!" fires it.
 */
public class SwivelGunBlock extends Block implements EntityBlock, StationBlock {

    public static final EnumProperty<CannonLoad> LOAD = CannonBlock.LOAD;
    public static final EnumProperty<SwivelPiece> PIECE = EnumProperty.create("piece", SwivelPiece.class);

    /** The yoke's pintle and the barrel's middle; the turning barrel itself has no collision. */
    private static final VoxelShape SHAPE = box(5, 0, 5, 11, 9, 11);

    public SwivelGunBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LOAD, CannonLoad.EMPTY).setValue(PIECE, SwivelPiece.YOKE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LOAD, PIECE);
    }

    /** Whether the block below {@code pos} carries a swivel gun: a mount (fence, wall, bars) or a full top face. */
    public static boolean mountedAt(LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState mount = level.getBlockState(below);
        return mount.is(CannonContent.SWIVEL_MOUNTS) || mount.isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return mountedAt(level, pos);
    }

    /** The gun falls off (drops) when its mount goes. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    /** A new gun points the way the placing player looks (level). */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel server && placer != null && level.getBlockEntity(pos) instanceof SwivelGunBlockEntity be) {
            SwivelRules.Aim view = SwivelService.aimFor(server, pos, be, placer);
            be.setAim(new SwivelRules.Aim(view.yawDegrees(), 0));
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** The block entity renderer draws the gun; the chunk mesh draws nothing. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SwivelGunBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != CannonContent.SWIVEL_GUN_ENTITY.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<SwivelGunBlockEntity>) SwivelService::serverTick;
    }

    @Override
    public StationKind<?> stationKind() {
        return SwivelStation.INSTANCE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; // empty hand: aim (useWithoutItem)
        }
        if (stack.getItem() instanceof StationBlock.Tool || !CannonBlock.isPowder(stack) && !SwivelService.isAmmo(stack)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION; // the item's own use
        }
        if (level instanceof ServerLevel server) {
            player.displayClientMessage(SwivelService.load(server, pos, player, stack).message(), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            SwivelService.Use use = player.isSecondaryUseActive()
                    ? SwivelService.status(server, pos) : SwivelService.startAim(server, pos, player);
            player.displayClientMessage(use.message(), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * A gun broken with drops (by a player in survival, a ball, an explosion, its mount going) gives back its powder and
     * shot next to its own item (Q2); a creative break drops nothing, as for the gun itself.
     */
    @Override
    protected void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos, ItemStack tool, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, tool, dropExperience);
        SwivelGunBlockEntity be = level.getBlockEntity(pos) instanceof SwivelGunBlockEntity s ? s : null;
        for (ItemStack stack : SwivelService.loadContents(state.getValue(LOAD), be)) {
            popResource(level, pos, stack);
        }
    }

    /** Breaking the gun frees the station and removes its seat. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            Stations.onStationRemoved(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
