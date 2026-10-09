package com.richardsenger.piratesnships.combat.boarding;

import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.core.block.Waterlogging;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One cell of a boarding plank run, the gangway (BRD1, BRD2, docs/design.md §8.3, §8.4), laid only by
 * {@link BoardingPlankItem}. {@link #FACING} is the direction the run is laid in (from segment 0 outward),
 * {@link #SEGMENT} the cell's index from segment 0 (0..5), {@link #TIP} marks the far end, {@link #PITCH} the slope of
 * this cell ({@link PlankRun.Pitch}). Segment 0 holds the {@link BoardingPlankBlockEntity} that watches both ends.
 *
 * <p><b>Shape.</b> A flat cell is a 2 px board. A ramp cell is a staircase of 4 px steps under the ideal slope, two steps
 * for a half of a 1-in-2 ramp and four for a 45° cell, each step's top 2 px above the slope (at most the cell's top),
 * so players and crew walk up without jumping and the pathfinder sees stairs.
 *
 * <p><b>Links.</b> Consecutive segments join where their walking surfaces meet: segment {@code k + 1} lies one cell out
 * in {@link #FACING}, level with segment {@code k} or one block higher or lower ({@link #outer}, {@link #inner}). A
 * segment that lost a link breaks: a face neighbour at once through {@link #updateShape} (vanilla destroys a block
 * whose shape update returns air, with drops), a diagonal one (a ramp's step to the next level) through a tick scheduled
 * when its neighbour is removed. Only segment 0 has loot (one plank), so a run always yields exactly one item.
 *
 * <p><b>Pick-up.</b> Sneak-use with an empty hand on any segment takes the whole run up and gives one plank back
 * ({@code boarding.plank.pick_up}).
 */
public class BoardingPlankBlock extends HorizontalDirectionalBlock implements EntityBlock, SimpleWaterloggedBlock {

    public static final MapCodec<BoardingPlankBlock> CODEC = simpleCodec(BoardingPlankBlock::new);
    public static final IntegerProperty SEGMENT = IntegerProperty.create("segment", 0, PlankRun.MAX_SEGMENTS - 1);
    public static final BooleanProperty TIP = BooleanProperty.create("tip");
    public static final EnumProperty<PlankRun.Pitch> PITCH = EnumProperty.create("pitch", PlankRun.Pitch.class);

    /** Board thickness in pixels (collision and model). */
    public static final int THICKNESS = 2;
    /** Inset of the board from the run's long sides, in pixels (collision and model). */
    public static final int INSET = 1;
    /** Height of one collision step of a ramp cell, in pixels. */
    public static final int STEP_DEPTH = 4;

    private static final Map<PlankRun.Pitch, Map<Direction, VoxelShape>> SHAPES = shapes();

    public BoardingPlankBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SEGMENT, 0).setValue(TIP, true)
                .setValue(PITCH, PlankRun.Pitch.FLAT).setValue(Waterlogging.WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SEGMENT, TIP, PITCH, Waterlogging.WATERLOGGED);
    }

    /** The state of flat segment {@code segment} of a run of {@code length} cells laid {@code facing}. */
    public BlockState segment(Direction facing, int segment, int length) {
        return segment(facing, segment, length, PlankRun.Pitch.FLAT);
    }

    /** The state of segment {@code segment} of a run of {@code length} cells laid {@code facing}, with this pitch. */
    public BlockState segment(Direction facing, int segment, int length, PlankRun.Pitch pitch) {
        return defaultBlockState().setValue(FACING, facing).setValue(SEGMENT, segment)
                .setValue(TIP, PlankRun.isTip(segment, length)).setValue(PITCH, pitch);
    }

    /** Planks are laid as a run by the item, never placed as a single block. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return null;
    }

    // ------------------------------------------------------------------ links

    /** Surface height in half blocks of a cell's near ({@code far = false}) or far edge, absolute. */
    private static int edge(BlockPos pos, BlockState state, boolean far) {
        PlankRun.Pitch p = state.getValue(PITCH);
        return 2 * pos.getY() + (far ? p.far : p.near);
    }

    /** Position of segment {@code segment + 1} of the run {@code pos} belongs to, or null when it is missing or the tip. */
    public static @Nullable BlockPos outer(BlockGetter level, BlockPos pos, BlockState state) {
        if (!state.is(BoardingContent.PLANK.get()) || state.getValue(TIP)) return null;
        Direction facing = state.getValue(FACING);
        int segment = state.getValue(SEGMENT);
        BlockPos ahead = pos.relative(facing);
        for (int dy = -1; dy <= 1; dy++) {
            BlockPos n = ahead.above(dy);
            BlockState ns = level.getBlockState(n);
            if (isSegment(ns, facing, segment + 1) && edge(n, ns, false) == edge(pos, state, true)) return n;
        }
        return null;
    }

    /** Position of segment {@code segment − 1} of the run {@code pos} belongs to, or null when it is missing or segment 0. */
    public static @Nullable BlockPos inner(BlockGetter level, BlockPos pos, BlockState state) {
        if (!state.is(BoardingContent.PLANK.get()) || state.getValue(SEGMENT) == 0) return null;
        Direction facing = state.getValue(FACING);
        int segment = state.getValue(SEGMENT);
        BlockPos behind = pos.relative(facing.getOpposite());
        for (int dy = -1; dy <= 1; dy++) {
            BlockPos n = behind.above(dy);
            BlockState ns = level.getBlockState(n);
            if (isSegment(ns, facing, segment - 1) && edge(n, ns, true) == edge(pos, state, false)) return n;
        }
        return null;
    }

    private static boolean linked(BlockGetter level, BlockPos pos, BlockState state) {
        return (state.getValue(SEGMENT) == 0 || inner(level, pos, state) != null)
                && (state.getValue(TIP) || outer(level, pos, state) != null);
    }

    private static boolean isSegment(BlockState s, Direction facing, int segment) {
        return s.is(BoardingContent.PLANK.get()) && s.getValue(FACING) == facing && s.getValue(SEGMENT) == segment;
    }

    /** The positions of the whole run {@code pos} belongs to, segment 0 first (only the linked part). */
    public static List<BlockPos> run(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        List<BlockPos> out = new ArrayList<>();
        if (!state.is(BoardingContent.PLANK.get())) return out;
        BlockPos base = pos;
        for (int guard = 0; guard < PlankRun.MAX_SEGMENTS; guard++) {
            BlockPos in = inner(level, base, level.getBlockState(base));
            if (in == null) break;
            base = in;
        }
        BlockPos at = base;
        for (int guard = 0; guard < PlankRun.MAX_SEGMENTS && at != null; guard++) {
            out.add(at.immutable());
            at = outer(level, at, level.getBlockState(at));
        }
        return out;
    }

    /** Position of segment 0 of the run {@code pos} belongs to. */
    public static BlockPos base(BlockGetter level, BlockPos pos) {
        List<BlockPos> run = run(level, pos);
        return run.isEmpty() ? pos : run.get(0);
    }

    // ------------------------------------------------------------------ the run breaks as one

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Waterlogging.tickFluid(state, level, pos);
        if (dir.getAxis() == state.getValue(FACING).getAxis() && !linked(level, pos, state)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !movedByPiston && !newState.is(this)) {
            // the diagonal neighbours of a ramp get no shape update: let them check their links next tick
            Direction facing = state.getValue(FACING);
            for (Direction d : new Direction[]{facing, facing.getOpposite()}) {
                for (int dy = -1; dy <= 1; dy += 2) {
                    BlockPos n = pos.relative(d).above(dy);
                    if (level.getBlockState(n).is(this)) {
                        level.scheduleTick(n, this, 1);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!linked(level, pos, state)) {
            level.destroyBlock(pos, true);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // creative: take the base (the only segment with loot) first without drops, so the run yields nothing
        if (!level.isClientSide && player.isCreative() && state.getValue(SEGMENT) > 0) {
            BlockPos base = base(level, pos);
            BlockState bs = level.getBlockState(base);
            if (bs.is(this) && bs.getValue(SEGMENT) == 0) {
                level.setBlock(base, Waterlogging.leftBehind(bs), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, base, Block.getId(bs));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isSecondaryUseActive() || !player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        return BoardingPlanks.pickUp((ServerLevel) level, pos, player) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(BoardingContent.PLANK_ITEM.get());
    }

    // ------------------------------------------------------------------ shape

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shape(state.getValue(PITCH), state.getValue(FACING));
    }

    /** The collision and outline shape of a cell of this pitch in a run laid {@code facing}. */
    public static VoxelShape shape(PlankRun.Pitch pitch, Direction facing) {
        return SHAPES.get(pitch).get(facing);
    }

    private static Map<PlankRun.Pitch, Map<Direction, VoxelShape>> shapes() {
        Map<PlankRun.Pitch, Map<Direction, VoxelShape>> out = new EnumMap<>(PlankRun.Pitch.class);
        for (PlankRun.Pitch pitch : PlankRun.Pitch.values()) {
            Map<Direction, VoxelShape> byFacing = new EnumMap<>(Direction.class);
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                VoxelShape s = Shapes.empty();
                for (double[] b : PlankRun.steps(pitch, THICKNESS, STEP_DEPTH)) {
                    s = Shapes.or(s, box(facing, b[0], b[1], b[2], b[3]));
                }
                byFacing.put(facing, s.optimize());
            }
            out.put(pitch, byFacing);
        }
        return out;
    }

    /** A box {@code u0..u1} from the near edge along {@code facing}, {@code y0..y1} high, the board's width across. */
    private static VoxelShape box(Direction facing, double u0, double u1, double y0, double y1) {
        double a0 = INSET, a1 = 16 - INSET;
        return switch (facing) {
            case NORTH -> Block.box(a0, y0, 16 - u1, a1, y1, 16 - u0);
            case SOUTH -> Block.box(a0, y0, u0, a1, y1, u1);
            case EAST -> Block.box(u0, y0, a0, u1, y1, a1);
            default -> Block.box(16 - u1, y0, a0, 16 - u0, y1, a1);
        };
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
