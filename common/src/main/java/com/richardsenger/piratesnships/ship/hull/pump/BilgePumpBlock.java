package com.richardsenger.piratesnships.ship.hull.pump;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import com.richardsenger.piratesnships.core.block.Waterlogging;
import com.richardsenger.piratesnships.station.StationBlock;
import com.richardsenger.piratesnships.station.StationKind;
import com.richardsenger.piratesnships.station.Stations;
import com.richardsenger.piratesnships.station.pump.PumpStation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Bilge pump (docs/design.md §4.5): placed in or above the hold. A player pumps by using it; holding the use key repeats
 * the use every 4 ticks, so the pump works while the key is held and stops a few ticks after it is let go
 * ({@link BilgePumps#operate}). It drains the compartment its intake reaches ({@link PumpIntake}). It is also a crew
 * station ({@link PumpStation}, docs/design.md §6): a crew member assigned to it pumps on a {@code PumpOrder}.
 *
 * <p>The block is a post, not a full cube, so the hull analysis counts its cell as air: a pump standing in the hold
 * takes no volume from it and lies inside the compartment it drains. {@link #FACING} is the side the spout points to,
 * visual only.
 *
 * <p>Model: a hand-made deck pump (art/models/bilge_pump.bbmodel, design.md §4.5, §4.8): a banded wooden casing on a
 * plank foot with an intake ring, an iron cylinder with a piston rod, and a brake handle on a pivot at the back that
 * rises 22.5 degrees towards {@link #FACING}. The spout with its drip points the same way, so both face the player who
 * placed the pump.
 *
 * <p>PMP1: the block model is the pump without its handle and piston rod; {@link BilgePumpBlockEntity} carries the
 * synced pumping flag and {@code client/PumpHandleRenderer} draws the handle rocking about its pin and the rod riding
 * under it.
 */
public class BilgePumpBlock extends Block implements StationBlock, EntityBlock, SimpleWaterloggedBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape SHAPE = Shapes.or(box(3, 0, 3, 13, 2, 13), box(5, 2, 5, 11, 14, 11));

    public BilgePumpBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(Waterlogging.WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, Waterlogging.WATERLOGGED);
    }

    /** The spout faces the placing player. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return Waterlogging.placed(defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()), context);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public StationKind<?> stationKind() {
        return PumpStation.INSTANCE;
    }

    /** Station tools (the captain's whistle) act through their own {@code useOn} instead of pumping. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof StationBlock.Tool) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel) {
            player.displayClientMessage(BilgePumps.operate(serverLevel, pos, player).message(), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BilgePumpBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != HullRepairContent.BILGE_PUMP_ENTITY.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<BilgePumpBlockEntity>) BilgePumpBlockEntity::serverTick;
    }

    /** Breaking the pump frees the station and removes its seat. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            Stations.onStationRemoved(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        Waterlogging.tickFluid(state, level, pos);
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Waterlogging.fluid(state, super.getFluidState(state));
    }
}
