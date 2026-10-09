package com.richardsenger.piratesnships.ship.decor;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import com.richardsenger.piratesnships.core.block.Waterlogging;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The captain's chart table (ART2, design.md §4.8): a one-block table with fiddle rails, a chart spread under a glass
 * weight, dividers and an inkwell; the top overhangs the block by a pixel on every side. {@link #FACING} is the drawer
 * side (towards the player who placed it). Decor only, no container. Hand-made model {@code block/chart_table}
 * ({@code art/models/chart_table.bbmodel}).
 */
public class ChartTableBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock {

    public static final MapCodec<ChartTableBlock> CODEC = simpleCodec(ChartTableBlock::new);

    /** The top with its fiddle rails and the four legs (symmetric, so the same for every facing). */
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 12, 0, 16, 14.25, 16),
            Block.box(0.5, 0, 0.5, 2.5, 12, 2.5), Block.box(13.5, 0, 0.5, 15.5, 12, 2.5),
            Block.box(0.5, 0, 13.5, 2.5, 12, 15.5), Block.box(13.5, 0, 13.5, 15.5, 12, 15.5));

    public ChartTableBlock(Properties properties) {
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
        return SHAPE;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
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
