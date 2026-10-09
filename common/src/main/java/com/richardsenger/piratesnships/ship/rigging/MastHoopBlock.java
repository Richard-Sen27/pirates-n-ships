package com.richardsenger.piratesnships.ship.rigging;

import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.core.block.Waterlogging;
import com.richardsenger.piratesnships.ship.decor.DecorShapes;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.Point;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The mast hoop (RL2): an iron band round a mast with a ring, the top end of a ratline net. Placed on a mast's side
 * like a ladder: {@link #FACING} points away from the mast. The mast is any sturdy face or a
 * {@link RiggingContent#RATLINES_ANCHORS} block (a fence-post mast, a yard, the nest). Its ring stands
 * {@link NetFitting#RING_FROM_SUPPORT} off the mast's face at half height. Waterloggable. Its
 * {@link NetFittingBlockEntity} names the net it holds; breaking it (or its mast) breaks the net. Hand-made model
 * {@code block/mast_hoop} ({@code art/models/mast_hoop.bbmodel}), made facing north; the band wraps round the mast block
 * behind it.
 */
public class MastHoopBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock, EntityBlock, NetFitting {

    public static final MapCodec<MastHoopBlock> CODEC = simpleCodec(MastHoopBlock::new);
    public static final BooleanProperty WATERLOGGED = Waterlogging.WATERLOGGED;

    /** Facing north (the mast to the south, px): the band on the mast's face and the ring standing out of it. */
    private static final Map<Direction, VoxelShape> SHAPES = DecorShapes.horizontal(
            new double[] {0, 6, 14.5, 16, 10, 16}, new double[] {7, 5.5, 8.5, 9, 10.5, 14.5});

    public MastHoopBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    @Override
    public Point ring(BlockState state, BlockPos pos) {
        return RatlineNets.ringOnFace(pos, state.getValue(FACING), RING_FROM_SUPPORT, 0.5);
    }

    /** On the clicked side of the mast; a click on a top or bottom face tries the player's facing reversed. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite();
        BlockState state = defaultBlockState().setValue(FACING, facing);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? Waterlogging.placed(state, context) : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos mast = pos.relative(facing.getOpposite());
        BlockState m = level.getBlockState(mast);
        return m.isFaceSturdy(level, mast, facing) || m.is(RiggingContent.RATLINES_ANCHORS);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        Waterlogging.tickFluid(state, level, pos);
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Waterlogging.fluid(state, super.getFluidState(state));
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
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        RatlineNets.onFittingRemoved(level, pos, state, movedByPiston, newState);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NetFittingBlockEntity(pos, state);
    }
}
