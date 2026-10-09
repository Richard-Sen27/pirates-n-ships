package com.richardsenger.piratesnships.chart.tile;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import com.richardsenger.piratesnships.core.block.Waterlogging;
import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.chart.ChartContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The map tile (work package MAP2): a thin parchment tile on a wooden backing, one pixel thick, lying on a floor or
 * table top ({@code face=floor}) or hanging on a wall ({@code face=wall}); never on a ceiling. {@code facing} is where
 * the drawing's north edge points: away from the placer on the floor, out of the wall on a wall. A floor tile needs
 * only a centre to rest on (a fence post, a slab), a wall tile a full face. Its block entity
 * ({@link MapTileBlockEntity}) holds the drawing.
 *
 * <p>Using it with a chart in either hand (or, with {@code chart.tiles.require_chart_item} off, with anything) opens
 * the chart in "draw on tile" mode ({@link MapTileService#openDrawMode}). Sneak-using a chart on it does the same
 * (vanilla skips the block when sneaking with an item, so {@link com.richardsenger.piratesnships.chart.ChartItem}
 * forwards it).
 */
public class MapTileBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock, SimpleWaterloggedBlock {

    public static final MapCodec<MapTileBlock> CODEC = simpleCodec(MapTileBlock::new);

    private static final VoxelShape FLOOR = Block.box(0, 0, 0, 16, 1, 16);
    private static final VoxelShape WALL_NORTH = Block.box(0, 0, 15, 16, 16, 16);
    private static final VoxelShape WALL_SOUTH = Block.box(0, 0, 0, 16, 16, 1);
    private static final VoxelShape WALL_EAST = Block.box(0, 0, 0, 1, 16, 16);
    private static final VoxelShape WALL_WEST = Block.box(15, 0, 0, 16, 16, 16);

    public MapTileBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FACE, AttachFace.FLOOR).setValue(Waterlogging.WATERLOGGED, false));
    }

    @Override
    protected MapCodec<MapTileBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE, Waterlogging.WATERLOGGED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(FACE) != AttachFace.WALL) return FLOOR;
        return switch (state.getValue(FACING)) {
            case SOUTH -> WALL_SOUTH;
            case EAST -> WALL_EAST;
            case WEST -> WALL_WEST;
            default -> WALL_NORTH;
        };
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> Block.canSupportCenter(level, pos.below(), Direction.UP);
            case WALL -> canAttach(level, pos, state.getValue(FACING).getOpposite());
            case CEILING -> false;
        };
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        for (Direction direction : context.getNearestLookingDirections()) {
            if (direction == Direction.UP) continue;
            BlockState state = direction == Direction.DOWN
                    ? defaultBlockState().setValue(FACE, AttachFace.FLOOR).setValue(FACING, context.getHorizontalDirection())
                    : defaultBlockState().setValue(FACE, AttachFace.WALL).setValue(FACING, direction.getOpposite());
            if (state.canSurvive(context.getLevel(), context.getClickedPos())) return Waterlogging.placed(state, context);
        }
        return null;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MapTileBlockEntity(pos, state);
    }

    // --- use: open the chart in draw mode --------------------------------------------------------------------------

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ChartContent.CHART.get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (player instanceof ServerPlayer sp) MapTileService.openDrawMode(sp, pos);
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        boolean chartInOffhand = player.getOffhandItem().is(ChartContent.CHART.get());
        if (level.isClientSide()) {
            // the server decides about require_chart_item; the client predicts the common case
            return chartInOffhand ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer sp && (chartInOffhand || !com.richardsenger.piratesnships.chart.ChartConfig.REQUIRE_CHART_ITEM.get())) {
            MapTileService.openDrawMode(sp, pos);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
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
