package com.richardsenger.piratesnships.station.lookout;

import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import com.richardsenger.piratesnships.core.block.Waterlogging;
import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.station.StationBlock;
import com.richardsenger.piratesnships.station.StationKind;
import com.richardsenger.piratesnships.station.Stations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The crow's nest (CN1, docs/design.md §4.8 "Visual backlog 2" item 2, §6, §7): a barrel-shaped lookout platform on a
 * masthead. One block whose hand-made model ({@code block/crows_nest}, {@code art/models/crows_nest.bbmodel}) is wider
 * than the block: a 24 px octagonal barrel of spruce staves with iron hoops round the block's column, its floor at the
 * bottom of the block (the mast top) and its rim {@link #RIM_HEIGHT} px up, so whoever stands in the block stands in
 * the barrel up to the chest.
 *
 * <ul>
 *   <li>Placement: on any block whose top can carry a centred load ({@link Block#canSupportCenter}: a log, a fence,
 *       a wall, any solid top); it breaks and drops when that support goes.</li>
 *   <li>Collision: only the floor ({@link #FLOOR_HEIGHT} px), so a player climbs in from a ladder over the rim's edge
 *       and stands inside; the outline is the block's column up to the rim.</li>
 *   <li>Station: {@link LookoutStation}. A crew member assigned with the whistle is seated inside the block
 *       ({@link #seatSpot}); {@link Lookouts} scans for it and for players standing in the nest.</li>
 * </ul>
 */
public class CrowsNestBlock extends Block implements StationBlock, SimpleWaterloggedBlock {

    public static final MapCodec<CrowsNestBlock> CODEC = simpleCodec(CrowsNestBlock::new);

    /** Top of the floor planks in px. */
    public static final double FLOOR_HEIGHT = 1.5;
    /** Top of the barrel's rim in px above the block's bottom (the model's rim). */
    public static final double RIM_HEIGHT = 20.0;

    private static final VoxelShape COLLISION = Block.box(0, 0, 0, 16, FLOOR_HEIGHT, 16);
    private static final VoxelShape OUTLINE = Block.box(0, 0, 0, 16, RIM_HEIGHT, 16);

    public CrowsNestBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(Waterlogging.WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(Waterlogging.WATERLOGGED);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION;
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    /** Sits on a masthead: anything whose top carries a centred load (log, fence, wall, solid top). */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return canSupportCenter(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        Waterlogging.tickFluid(state, level, pos);
        if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    public StationKind<?> stationKind() {
        return LookoutStation.INSTANCE;
    }

    /** The lookout stands in the nest: its seat is the nest's own block, feet on the floor. */
    @Override
    public @Nullable BlockPos seatSpot(BlockState state, BlockPos stationPos) {
        return stationPos;
    }

    /** Station tools (the captain's whistle) act through their own {@code useOn}. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof StationBlock.Tool) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel && !newState.is(this)) {
            Stations.onStationRemoved(serverLevel, pos); // frees the lookout station and removes its seat
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Waterlogging.fluid(state, super.getFluidState(state));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return Waterlogging.placed(super.getStateForPlacement(context), context);
    }
}
