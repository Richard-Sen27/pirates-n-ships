package com.richardsenger.piratesnships.combat.boarding;

import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.core.block.Waterlogging;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
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
 * One cell of a boarding plank run, the gangway (BRD1, BRD2, BRD4, docs/design.md §8.3, §8.4), laid only by
 * {@link BoardingPlankItem}. {@link #FACING} is the direction the run is laid in (from segment 0 outward),
 * {@link #SEGMENT} the cell's index from segment 0. Every cell has a {@link BoardingPlankBlockEntity} with the board's
 * heights in it; segment 0's also holds the run and watches both ends. The board itself is drawn as one straight piece
 * by {@code BoardingPlankRenderer} from segment 0; the cell's own model shows nothing (particles only).
 *
 * <p><b>Shape.</b> The straight board's underside crosses the cell from its near to its far edge; the collision (and
 * the outline, the same shape) is a staircase of four 4 px steps on it, each 2 px above the underside's higher end over
 * that step, cut to the cell ({@link PlankRun#steps}): at 45° a step is a quarter block, so players and crew walk up
 * without jumping and the pathfinder sees stairs. Without its block entity (a Ponder scene, a cell of an old save not
 * recomputed yet) the cell is a flat 2 px board. The shape depends on the block entity, so the block has a dynamic shape
 * ({@link BoardingContent}).
 *
 * <p><b>Breaking.</b> Removing any cell (broken, burnt, blown up) takes the whole run down at once
 * ({@link #onRemove}); only segment 0 has loot (one plank), so a run always yields exactly one item.
 *
 * <p><b>Pick-up.</b> Sneak-use with an empty hand on any segment takes the whole run up and gives one plank back
 * ({@code boarding.plank.pick_up}).
 */
public class BoardingPlankBlock extends HorizontalDirectionalBlock implements EntityBlock, SimpleWaterloggedBlock {

    public static final MapCodec<BoardingPlankBlock> CODEC = simpleCodec(BoardingPlankBlock::new);
    public static final IntegerProperty SEGMENT = IntegerProperty.create("segment", 0, PlankRun.MAX_CELLS - 1);

    /** Board thickness in pixels (collision and renderer). */
    public static final int THICKNESS = 2;
    /** Inset of the board from the run's long sides, in pixels (collision and renderer). */
    public static final int INSET = 1;

    private static final Map<Direction, VoxelShape> FLAT = flatShapes();

    /** Set while cells are removed as part of a whole run (breaking it, taking it up): no further cascade. */
    private static boolean quiet;

    public BoardingPlankBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SEGMENT, 0)
                .setValue(Waterlogging.WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SEGMENT, Waterlogging.WATERLOGGED);
    }

    /** The state of segment {@code segment} of a run laid {@code facing}. */
    public BlockState segment(Direction facing, int segment) {
        return defaultBlockState().setValue(FACING, facing).setValue(SEGMENT, Math.min(PlankRun.MAX_CELLS - 1, segment));
    }

    /** Planks are laid as a run by the item, never placed as a single block. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return null;
    }

    /** Runs {@code r} with the cascade of {@link #onRemove} off (server thread only). */
    static void quietly(Runnable r) {
        boolean was = quiet;
        quiet = true;
        try {
            r.run();
        } finally {
            quiet = was;
        }
    }

    // ------------------------------------------------------------------ the run

    /** The positions of the whole run {@code pos} belongs to, segment 0 first (the cells still there). */
    public static List<BlockPos> run(BlockGetter level, BlockPos pos) {
        List<BlockPos> out = new ArrayList<>();
        if (!level.getBlockState(pos).is(BoardingContent.PLANK.get())) return out;
        BlockPos base = base(level, pos);
        if (base != null && level.getBlockEntity(base) instanceof BoardingPlankBlockEntity be && be.isBase()) {
            for (BlockPos c : be.cells()) {
                if (level.getBlockState(c).is(BoardingContent.PLANK.get())) out.add(c);
            }
        }
        if (!out.contains(pos)) out.add(pos.immutable());
        return out;
    }

    /** Position of segment 0 of the run {@code pos} belongs to, or null when the cell knows no run. */
    public static @Nullable BlockPos base(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BoardingPlankBlockEntity be ? be.base() : null;
    }

    // ------------------------------------------------------------------ the run breaks as one

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Waterlogging.tickFluid(state, level, pos);
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !movedByPiston && !newState.is(this) && !quiet) {
            // the rest of the run goes with it; segment 0 (wherever it is) drops the one plank
            BlockPos base = base(level, pos);
            if (base != null && level.getBlockEntity(base) instanceof BoardingPlankBlockEntity be && be.isBase()) {
                List<BlockPos> cells = be.cells();
                quietly(() -> {
                    for (BlockPos c : cells) {
                        if (!c.equals(pos) && level.getBlockState(c).is(this)
                                && level.getBlockEntity(c) instanceof BoardingPlankBlockEntity other && base.equals(other.base())) {
                            level.destroyBlock(c, true);
                        }
                    }
                });
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // creative: take the base (the only segment with loot) first without drops, so the run yields nothing
        if (!level.isClientSide && player.isCreative() && state.getValue(SEGMENT) > 0) {
            BlockPos base = base(level, pos);
            BlockState bs = base == null ? null : level.getBlockState(base);
            if (bs != null && bs.is(this) && bs.getValue(SEGMENT) == 0) {
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

    /**
     * Not a cell to walk through but a floor to walk on, like a slab or stairs: vanilla's walk node evaluator treats a
     * block that is pathfindable on land as open air, so the cell above it would not count as walkable and crew would
     * not path across a plank laid over a gap.
     */
    @Override
    protected boolean isPathfindable(BlockState state, net.minecraft.world.level.pathfinder.PathComputationType type) {
        return false;
    }

    // ------------------------------------------------------------------ shape

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return level.getBlockEntity(pos) instanceof BoardingPlankBlockEntity be ? be.shape() : FLAT.get(state.getValue(FACING));
    }

    /**
     * The collision and outline of a cell of a run laid {@code facing} whose board's underside lies {@code nearPx} and
     * {@code farPx} above the cell's floor at its near and far edge: the steps of {@link PlankRun#steps} across the
     * board's width.
     */
    public static VoxelShape cellShape(Direction facing, double nearPx, double farPx) {
        VoxelShape s = Shapes.empty();
        for (double[] b : PlankRun.steps(nearPx, farPx, THICKNESS)) {
            s = Shapes.or(s, box(facing, b[0], b[1], b[2], b[3]));
        }
        return s.isEmpty() ? FLAT.get(facing) : s.optimize();
    }

    private static Map<Direction, VoxelShape> flatShapes() {
        Map<Direction, VoxelShape> out = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            out.put(facing, box(facing, 0, 16, 0, THICKNESS));
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

    // ------------------------------------------------------------------ block entity on every cell

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BoardingPlankBlockEntity(pos, state);
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
