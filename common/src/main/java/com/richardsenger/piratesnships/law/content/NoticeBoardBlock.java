package com.richardsenger.piratesnships.law.content;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import com.richardsenger.piratesnships.core.block.Waterlogging;
import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.law.LawConfig;
import com.richardsenger.piratesnships.law.net.NoticeBoardBackend;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The notice board (docs/design.md §13.2): a standing wooden board with bounty notices pinned to it. Using it opens
 * the notice board screen ({@link NoticeBoardBackend#open}): every active bounty, the bounty on the viewer's own head
 * and a form to place one. With {@code law.bounty.notice_boards} off it is decoration. {@link #FACING} is the side
 * with the notices (towards the player who placed it; north in the unrotated model). The model is hand-made
 * ({@code art/models/notice_board.bbmodel}).
 */
public class NoticeBoardBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock {

    public static final MapCodec<NoticeBoardBlock> CODEC = simpleCodec(NoticeBoardBlock::new);

    /** Posts, board and feet of the model (x 1..15, z 6..10 facing north); the roof overhang stays outside. */
    private static final VoxelShape SHAPE_NS = Block.box(1, 0, 6, 15, 16, 10);
    private static final VoxelShape SHAPE_EW = Block.box(6, 0, 1, 10, 16, 15);

    public NoticeBoardBlock(Properties properties) {
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
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return Waterlogging.placed(defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()), context);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!LawConfig.NOTICE_BOARDS.get()) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        NoticeBoardBackend.open(sp, pos);
        return InteractionResult.CONSUME;
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
