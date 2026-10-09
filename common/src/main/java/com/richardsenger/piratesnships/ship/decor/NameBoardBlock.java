package com.richardsenger.piratesnships.ship.decor;

import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.core.block.Waterlogging;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * The name board (NAME2, docs/design.md §4.8 "Ship name boards"), the nameplate's successor under the same registry id
 * {@code nameplate}, so every plate in a saved world or a ship template loads as a board (its {@code facing} and
 * {@code waterlogged} keep their meaning; {@link #LEFT} and {@link #RIGHT} start unjoined and are fixed on the next
 * neighbour update or the block entity's first tick).
 *
 * <p>A wooden plate fixed flat on a hull's side face: placed like a ladder (on the sturdy side face of the block
 * behind it, {@link #FACING} looking away from that block, popping off when that block goes), 2 px thick and 14 px tall
 * against the back of its block, a 1 px stripped-log frame around a plank panel (the hand-made models
 * {@code block/name_board*}, {@code art/models/name_board.bbmodel}, through the wood family's texture variables).
 * Boards side by side on one face join into one board ({@link NameBoardRow}): {@link #LEFT} and {@link #RIGHT} say
 * whether a board continues the row on the viewer's left and right; the block state draws the frame's ends only where
 * the row ends. The row's place (index, length) lives in {@link NameplateBlockEntity}, which also holds the ship's
 * name; the row's master (its leftmost board) draws the name across the row ({@code client.NameplateRenderer}).
 * Waterloggable (WLOG1), not climbable.
 */
public class NameBoardBlock extends HorizontalDirectionalBlock implements EntityBlock, SimpleWaterloggedBlock {

    public static final MapCodec<NameBoardBlock> CODEC = simpleCodec(NameBoardBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** A board of the same row continues on the viewer's left ({@link NameBoardRow#left}). */
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    /** A board of the same row continues on the viewer's right ({@link NameBoardRow#right}). */
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        // north-facing: against the south side of the block
        float bottom = NameplateText.BOARD_BOTTOM, top = NameplateText.BOARD_TOP, front = NameplateText.FRAME_FRONT;
        SHAPES.put(Direction.NORTH, Block.box(0, bottom, front, 16, top, 16));
        SHAPES.put(Direction.SOUTH, Block.box(0, bottom, 0, 16, top, 16 - front));
        SHAPES.put(Direction.WEST, Block.box(front, bottom, 0, 16, top, 16));
        SHAPES.put(Direction.EAST, Block.box(0, bottom, 0, 16 - front, top, 16));
    }

    public NameBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LEFT, false).setValue(RIGHT, false)
                .setValue(Waterlogging.WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LEFT, RIGHT, Waterlogging.WATERLOGGED);
    }

    // ------------------------------------------------------------------ placement and support

    /** Whether a board facing {@code facing} at {@code pos} has the sturdy face of the block behind it to hang on. */
    private static boolean canAttach(LevelReader level, BlockPos pos, Direction facing) {
        BlockPos behind = pos.relative(facing.getOpposite());
        return level.getBlockState(behind).isFaceSturdy(level, behind, facing);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return canAttach(level, pos, state.getValue(FACING));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        LevelReader level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction clicked = context.getClickedFace();
        if (!context.replacingClickedOnBlock()) {
            // a click on a board's own front does not stack a board in front of it
            BlockState on = level.getBlockState(pos.relative(clicked.getOpposite()));
            if (on.is(this) && on.getValue(FACING) == clicked) return null;
        }
        if (clicked.getAxis().isHorizontal() && canAttach(level, pos, clicked)) {
            return Waterlogging.placed(connected(defaultBlockState().setValue(FACING, clicked), level, pos), context);
        }
        for (Direction looking : context.getNearestLookingDirections()) {
            if (!looking.getAxis().isHorizontal()) continue;
            Direction facing = looking.getOpposite();
            if (canAttach(level, pos, facing)) {
                return Waterlogging.placed(connected(defaultBlockState().setValue(FACING, facing), level, pos), context);
            }
        }
        return null;
    }

    /** {@code state} with {@link #LEFT} and {@link #RIGHT} set from its neighbours at {@code pos}. */
    public BlockState connected(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        NameBoardRow.Lookup lookup = lookup(level);
        return state.setValue(LEFT, NameBoardRow.joins(lookup, pos.getX(), pos.getY(), pos.getZ(), facing, NameBoardRow.left(facing)))
                .setValue(RIGHT, NameBoardRow.joins(lookup, pos.getX(), pos.getY(), pos.getZ(), facing, NameBoardRow.right(facing)));
    }

    /** The world read as {@link NameBoardRow} needs it: the facing of each board of this block. */
    public NameBoardRow.Lookup lookup(BlockGetter level) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        return (x, y, z) -> {
            BlockState s = level.getBlockState(at.set(x, y, z));
            return s.is(this) ? s.getValue(FACING) : null;
        };
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        Waterlogging.tickFluid(state, level, pos);
        Direction facing = state.getValue(FACING);
        if (direction == facing.getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        if (direction == NameBoardRow.left(facing) || direction == NameBoardRow.right(facing)) {
            BlockState next = connected(state, level, pos);
            // the indices of the whole row may have changed: re-link it on the next tick
            if (next != state) level.scheduleTick(pos, this, 1);
            return next;
        }
        return state;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !oldState.is(this)) level.scheduleTick(pos, this, 1);
    }

    /** Re-links the row this board belongs to: every member's index and the master's length ({@link NameplateBlockEntity#relinkRow}). */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof NameplateBlockEntity be) be.relinkRow();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /** A mirror image reverses the row: the viewer's left and right swap. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) return state;
        BlockState swapped = state.setValue(LEFT, state.getValue(RIGHT)).setValue(RIGHT, state.getValue(LEFT));
        return swapped.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Waterlogging.fluid(state, super.getFluidState(state));
    }

    // ------------------------------------------------------------------ block entity

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NameplateBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ShipDecor.NAMEPLATE_BLOCK_ENTITY.get()) return null;
        return (l, p, s, be) -> ((NameplateBlockEntity) be).serverTick();
    }
}
