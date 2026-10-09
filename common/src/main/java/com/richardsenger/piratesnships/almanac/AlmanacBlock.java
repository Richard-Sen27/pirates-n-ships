package com.richardsenger.piratesnships.almanac;

import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.core.block.Waterlogging;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The placed Captain's Almanac (ALM1, docs/design.md §3.4): a thick tome lying on top of any block that supports its
 * centre (no wall mounting), waterloggable (WLOG1). Right-click reads it: it opens, its pages turn, and the reader's
 * client opens the almanac ({@link AlmanacReading#readTome}); when the reader is done it closes again
 * ({@link AlmanacOpenRule}). With {@code almanac.enabled} off it is decoration. {@link #FACING} points at the player
 * who set it down; the spine runs towards them. The renderer ({@code AlmanacRenderer}) draws the whole book; the block
 * model is only a particle texture.
 */
public class AlmanacBlock extends HorizontalDirectionalBlock implements EntityBlock, SimpleWaterloggedBlock {

    public static final MapCodec<AlmanacBlock> CODEC = simpleCodec(AlmanacBlock::new);

    /** The open book ({@code AlmanacBookPose}): 15 px across its spread, 12 px along the spine, 4 px high; centred. */
    private static final VoxelShape SHAPE_NS = Block.box(0.5, 0, 2, 15.5, 4, 14);
    private static final VoxelShape SHAPE_EW = Block.box(2, 0, 0.5, 14, 4, 15.5);

    public AlmanacBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(Waterlogging.WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, Waterlogging.WATERLOGGED);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? Waterlogging.placed(state, context) : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        Waterlogging.tickFluid(state, level, pos);
        if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return Waterlogging.leftBehind(state);
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!AlmanacConfig.ENABLED.get()) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        return AlmanacReading.readTome(sp, pos) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Waterlogging.fluid(state, super.getFluidState(state));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AlmanacBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != AlmanacContent.ALMANAC_BLOCK_ENTITY.get()) return null;
        BlockEntityTicker<AlmanacBlockEntity> ticker = level.isClientSide ? AlmanacBlockEntity::clientTick : AlmanacBlockEntity::serverTick;
        return (BlockEntityTicker<T>) ticker;
    }
}
