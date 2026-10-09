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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The chainplate (RL2): an iron strap with a ring, the bottom end of a ratline net. Mounted like a lever on the top or
 * the side of a block (the rail), on a sturdy face or a {@link RiggingContent#RATLINES_ANCHORS} block (a fence or wall
 * rail), not under a ceiling. Its ring stands {@link NetFitting#RING_FROM_SUPPORT} off the face it is mounted on.
 * Waterloggable. Its {@link NetFittingBlockEntity} names the net it holds; breaking it breaks the net
 * ({@link RatlineNets#onFittingRemoved}). Hand-made model {@code block/chainplate}
 * ({@code art/models/chainplate.bbmodel}), made for the floor and turned like a button.
 */
public class ChainplateBlock extends FaceAttachedHorizontalDirectionalBlock implements SimpleWaterloggedBlock, EntityBlock, NetFitting {

    public static final MapCodec<ChainplateBlock> CODEC = simpleCodec(ChainplateBlock::new);
    public static final BooleanProperty WATERLOGGED = Waterlogging.WATERLOGGED;

    /** On the floor (north frame, px): the strap along z and the ring standing on it. */
    private static final double[][] FLOOR_BOXES = {{6, 0, 3, 10, 1.5, 13}, {7, 1.5, 5.5, 9, 7.2, 10.5}};
    private static final Map<Direction, VoxelShape> FLOOR = DecorShapes.horizontal(FLOOR_BOXES);
    /** On a wall, facing north (the support to the south): the floor boxes turned like the block state's x 90. */
    private static final Map<Direction, VoxelShape> WALL = DecorShapes.horizontal(onWall(FLOOR_BOXES));

    public ChainplateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FACE, AttachFace.FLOOR)
                .setValue(WATERLOGGED, false));
    }

    /** Floor boxes as the wall model shows them: x stays, y becomes z, z becomes 16 - y. */
    private static double[][] onWall(double[][] floor) {
        double[][] out = new double[floor.length][];
        for (int i = 0; i < floor.length; i++) {
            double[] b = floor[i];
            out[i] = new double[] {b[0], b[2], 16 - b[4], b[3], b[5], 16 - b[1]};
        }
        return out;
    }

    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE, WATERLOGGED);
    }

    @Override
    public Point ring(BlockState state, BlockPos pos) {
        if (state.getValue(FACE) == AttachFace.FLOOR) {
            return new Point(pos.getX() + 0.5, pos.getY() + RING_FROM_SUPPORT, pos.getZ() + 0.5);
        }
        return RatlineNets.ringOnFace(pos, state.getValue(FACING), RING_FROM_SUPPORT, 0.5);
    }

    /** On the top of the clicked block (facing the way the player looks) or on its side; never under a ceiling. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face == Direction.DOWN) {
            return null;
        }
        BlockState state = face == Direction.UP
                ? defaultBlockState().setValue(FACE, AttachFace.FLOOR).setValue(FACING, context.getHorizontalDirection())
                : defaultBlockState().setValue(FACE, AttachFace.WALL).setValue(FACING, face);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? Waterlogging.placed(state, context) : null;
    }

    /** A sturdy face, or a fence, wall, yard or the nest ({@link RiggingContent#RATLINES_ANCHORS}). */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(FACE) == AttachFace.CEILING) {
            return false;
        }
        Direction toSupport = getConnectedDirection(state).getOpposite();
        return super.canSurvive(state, level, pos)
                || level.getBlockState(pos.relative(toSupport)).is(RiggingContent.RATLINES_ANCHORS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(FACE) == AttachFace.FLOOR ? FLOOR : WALL).get(state.getValue(FACING));
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Waterlogging.fluid(state, super.getFluidState(state));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        Waterlogging.tickFluid(state, level, pos);
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
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
