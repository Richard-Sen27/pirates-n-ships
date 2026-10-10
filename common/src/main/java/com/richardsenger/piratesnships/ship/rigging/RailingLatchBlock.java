package com.richardsenger.piratesnships.ship.rigging;

import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.core.block.Waterlogging;
import com.richardsenger.piratesnships.ship.decor.DecorShapes;
import com.richardsenger.piratesnships.ship.decor.railing.RailingBlock;
import com.richardsenger.piratesnships.ship.decor.railing.RailingLatch;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetRules.Point;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * The net end of a railing latch (RL5, docs/design.md §4.8 "Net sheets and railing latches"): an invisible, climbable
 * fitting in the block over a railing or bulwark whose {@link RailingBlock#LATCH} is set on edge {@link #FACING}. The
 * railing draws the iron eye bolt on its cap ({@code block/railing_latch}); this block holds the net's link
 * ({@link NetFittingBlockEntity}, like a chainplate: one strand) and its ring is the eye bolt's: the edge's centre,
 * {@link #RING_INSET} in from the block's outer face, {@link #RING_HEIGHT} over the cap. A railing therefore never
 * carries a block entity of its own (worldgen structures with railings stay plain), and the cell a climber reaches from
 * the rail is part of the net.
 *
 * <p>Set by the net item ({@link RailingLatches#place}); it lasts only with its railing (it drops to nothing when the
 * railing below goes or loses that edge) and only with its strand ({@link RatlineNets#removeStrands} takes it away when
 * its net goes); breaking it breaks the net like breaking a chainplate, and drops nothing itself: the latch costs
 * nothing beyond the net item. Removing it clears the railing's latch ({@link #onRemove}, and the railing's own shape
 * update). Waterloggable, no item, no collision, a small outline round the eye bolt.
 */
public class RailingLatchBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock, EntityBlock, NetFitting {

    public static final MapCodec<RailingLatchBlock> CODEC = simpleCodec(RailingLatchBlock::new);
    public static final BooleanProperty WATERLOGGED = Waterlogging.WATERLOGGED;

    /** The eye bolt's ring centre: this far in from the block's outer face along the edge's normal [blocks] (1.5 px). */
    public static final double RING_INSET = 1.5 / 16.0;
    /** The eye bolt's ring centre over the cap rail's top (y 16 of the railing) [blocks] (1.75 px). */
    public static final double RING_HEIGHT = 1.75 / 16.0;

    /** Facing north (the latch on the railing's north edge), px: round the eye bolt standing on the cap. */
    private static final Map<Direction, VoxelShape> SHAPES = DecorShapes.horizontal(new double[] {6, 0, 0, 10, 4, 4});

    public RailingLatchBlock(Properties properties) {
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

    /** The ring of a latch on edge {@code edge} of the railing under {@code pos}. */
    public static Point ring(BlockPos pos, Direction edge) {
        double out = 0.5 - RING_INSET;
        return new Point(pos.getX() + 0.5 + edge.getStepX() * out, pos.getY() + RING_HEIGHT, pos.getZ() + 0.5 + edge.getStepZ() * out);
    }

    @Override
    public Point ring(BlockState state, BlockPos pos) {
        return ring(pos, state.getValue(FACING));
    }

    /** Over a railing or bulwark railed on {@link #FACING}. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return below.getBlock() instanceof RailingBlock && below.getValue(RailingBlock.edge(state.getValue(FACING)));
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
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
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

    /** A hub (a boarding net's end B) checks its nets again after a block in them was replaced. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        RatlineNets.resync(level, pos);
    }

    /** Its net breaks (as a chainplate's), and the railing below loses the latch (not when Sable moves the blocks). */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        RatlineNets.onFittingRemoved(level, pos, state, movedByPiston, newState);
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!movedByPiston && !newState.is(this) && !level.isClientSide) {
            BlockPos below = pos.below();
            BlockState rail = level.getBlockState(below);
            if (RailingBlock.latch(rail) == state.getValue(FACING)) {
                level.setBlock(below, rail.setValue(RailingBlock.LATCH, RailingLatch.NONE), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NetFittingBlockEntity(pos, state);
    }
}
