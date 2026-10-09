package com.richardsenger.piratesnships.ship.decor;

import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.core.block.Waterlogging;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleFoot;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleFooting;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleRun;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The flag pedestal (FLG3, docs/design.md §4.7 "Flag pedestal and the pole on the crow's nest"): a tapered plinth
 * carved from a log, 10 px across at the foot, 8 px at the top and 6 px high, with an iron-collared socket for a
 * flagpole, in the flagpole's stripped spruce (hand-made model {@code block/flag_pedestal},
 * {@code art/models/flag_pedestal.bbmodel}).
 * <ul>
 *   <li>Placement: on any block with a sturdy top face; it pops off and drops when that support goes.</li>
 *   <li>A flagpole placed on it stands in the socket: {@link FlagpoleFooting#POLE} turns on and the model shows the
 *       pole stub with the halyard's cleat, so the pedestal is the pole's foot and the lowest pole block shows no cleat
 *       ({@link FlagpoleFoot}). The pole's height and head are the flagpole blocks only.</li>
 *   <li>Breaking the pedestal brings the pole down ({@link FlagpoleFooting#dropPole}).</li>
 *   <li>Using the pedestal with a pole in it works the pole as using the pole would (hoist, strike or raise, take
 *       down at the head): the halyard is belayed here.</li>
 *   <li>Waterloggable (WLOG1).</li>
 * </ul>
 */
public class FlagPedestalBlock extends Block implements FlagpoleFooting, SimpleWaterloggedBlock {

    public static final MapCodec<FlagPedestalBlock> CODEC = simpleCodec(FlagPedestalBlock::new);

    /** Height of the pedestal in px (the top of its socket collar). */
    public static final double HEIGHT = 6.0;

    private static final VoxelShape PLINTH = Shapes.or(Block.box(3, 0, 3, 13, 2.25, 13), Block.box(4, 2.25, 4, 12, HEIGHT, 12));
    /** With a pole: the plinth and the pole stub (the flagpole's 4×4 post) from the socket to the block's top. */
    private static final VoxelShape WITH_POLE = Shapes.or(PLINTH, Block.box(6, 5, 6, 10, 16, 10));

    public FlagPedestalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POLE, false).setValue(Waterlogging.WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POLE, Waterlogging.WATERLOGGED);
    }

    @Override
    public FlagpoleFoot.Support footing() {
        return FlagpoleFoot.Support.PEDESTAL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(POLE) ? WITH_POLE : PLINTH;
    }

    /** Stands on anything with a sturdy top face. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = Waterlogging.placed(defaultBlockState(), context);
        return state == null ? null : FlagpoleFooting.withPole(state, context.getLevel(), context.getClickedPos());
    }

    /** Pops off without its support; shows the socketed model while a flagpole stands on it. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        Waterlogging.tickFluid(state, level, pos);
        if (direction == Direction.DOWN && !state.canSurvive(level, pos)) return Blocks.AIR.defaultBlockState();
        if (direction == Direction.UP) return FlagpoleFooting.withPole(state, level, pos);
        return state;
    }

    /** With a pole in the socket, a use works the pole (at its head) as a use of the pole would. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        BlockPos pole = pos.above();
        if (!FlagpoleRun.isPole(level, pole)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        BlockState poleState = level.getBlockState(pole);
        return poleState.useItemOn(stack, level, player, hand, hit.withPosition(pole));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos pole = pos.above();
        if (!FlagpoleRun.isPole(level, pole)) return InteractionResult.PASS;
        return level.getBlockState(pole).useWithoutItem(level, player, hit.withPosition(pole));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) FlagpoleFooting.dropPole(level, pos, movedByPiston);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Waterlogging.fluid(state, super.getFluidState(state));
    }
}
