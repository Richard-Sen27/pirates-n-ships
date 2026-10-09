package com.richardsenger.piratesnships.combat.boarding;

import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import com.richardsenger.piratesnships.core.block.Waterlogging;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * One cell of a boarding plank run (BRD1, docs/design.md §8.3), laid only by {@link BoardingPlankItem}. {@link #FACING}
 * is the direction the run goes out from the ship it was laid from, {@link #SEGMENT} the cell's index from the gunwale
 * (0..3), {@link #TIP} marks the far end. Segment 0 holds the {@link BoardingPlankBlockEntity} that watches the far
 * end. Collision is a 2 px board, so it walks like a carpet on a slab.
 * <p>
 * Breaking any segment breaks the whole run, like the hammock's two halves: a segment needs its inner neighbour
 * (segment − 1) and, unless it is the tip, its outer neighbour (segment + 1); {@link #updateShape} returns air
 * otherwise, which vanilla destroys with drops, so the break runs along the plank. Only segment 0 has loot (one
 * plank), so the run always yields exactly one item.
 */
public class BoardingPlankBlock extends HorizontalDirectionalBlock implements EntityBlock, SimpleWaterloggedBlock {

    public static final MapCodec<BoardingPlankBlock> CODEC = simpleCodec(BoardingPlankBlock::new);
    public static final IntegerProperty SEGMENT = IntegerProperty.create("segment", 0, PlankRun.MAX_SEGMENTS - 1);
    public static final BooleanProperty TIP = BooleanProperty.create("tip");

    /** Board thickness in pixels (collision and model). */
    public static final int THICKNESS = 2;
    /** Inset of the board from the run's long sides, in pixels (collision and model). */
    public static final int INSET = 1;

    private static final VoxelShape SHAPE_Z = Block.box(INSET, 0, 0, 16 - INSET, THICKNESS, 16);
    private static final VoxelShape SHAPE_X = Block.box(0, 0, INSET, 16, THICKNESS, 16 - INSET);

    public BoardingPlankBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SEGMENT, 0).setValue(TIP, true).setValue(Waterlogging.WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SEGMENT, TIP, Waterlogging.WATERLOGGED);
    }

    /** The state of segment {@code segment} of a run of {@code length} cells going {@code facing}. */
    public BlockState segment(Direction facing, int segment, int length) {
        return defaultBlockState().setValue(FACING, facing).setValue(SEGMENT, segment)
                .setValue(TIP, PlankRun.isTip(segment, length));
    }

    /** Planks are laid as a run by the item, never placed as a single block. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return null;
    }

    // ------------------------------------------------------------------ the run breaks as one

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Waterlogging.tickFluid(state, level, pos);
        Direction facing = state.getValue(FACING);
        int segment = state.getValue(SEGMENT);
        if (dir == facing.getOpposite() && segment > 0) {
            return isSegment(neighbor, facing, segment - 1) ? state : Blocks.AIR.defaultBlockState();
        }
        if (dir == facing && !state.getValue(TIP)) {
            return isSegment(neighbor, facing, segment + 1) ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    private boolean isSegment(BlockState s, Direction facing, int segment) {
        return s.is(this) && s.getValue(FACING) == facing && s.getValue(SEGMENT) == segment;
    }

    /** Plot position of segment 0 of the run {@code pos} belongs to. */
    public static BlockPos base(BlockPos pos, BlockState state) {
        return pos.relative(state.getValue(FACING).getOpposite(), state.getValue(SEGMENT));
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // creative: take the base (the only segment with loot) first without drops, so the run yields nothing
        if (!level.isClientSide && player.isCreative() && state.getValue(SEGMENT) > 0) {
            BlockPos base = base(pos, state);
            BlockState bs = level.getBlockState(base);
            if (bs.is(this) && bs.getValue(SEGMENT) == 0) {
                level.setBlock(base, Waterlogging.leftBehind(bs), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, base, Block.getId(bs));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(BoardingContent.PLANK_ITEM.get());
    }

    // ------------------------------------------------------------------ shape

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? SHAPE_X : SHAPE_Z;
    }

    // ------------------------------------------------------------------ block entity on segment 0

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(SEGMENT) == 0 ? new BoardingPlankBlockEntity(pos, state) : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || state.getValue(SEGMENT) != 0 || type != BoardingContent.PLANK_BLOCK_ENTITY.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<BoardingPlankBlockEntity>) BoardingPlankBlockEntity::serverTick;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Waterlogging.fluid(state, super.getFluidState(state));
    }
}
