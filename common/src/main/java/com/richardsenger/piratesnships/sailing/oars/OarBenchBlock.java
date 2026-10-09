package com.richardsenger.piratesnships.sailing.oars;

import com.richardsenger.piratesnships.core.block.Waterlogging;
import com.richardsenger.piratesnships.station.StationBlock;
import com.richardsenger.piratesnships.station.StationKind;
import com.richardsenger.piratesnships.station.Stations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The oar bench (OAR1, docs/design.md §5.3b): a thwart with a thole pin on its outboard end, a crew station with one
 * seat. {@link #FACING} is the outboard side (where the thole pin and the oar are): set at placement to the way the
 * placing player looks, so a bench placed while looking over the rail faces outboard. The rower sits on the thwart
 * ({@link #seatSpot}: the seat stands inside the bench's own cell) facing aft, the stern being the way the oar pulls;
 * seated it rows {@code oars.bench_bonus} times as hard as a rower at the rail ({@link Rowing}).
 *
 * <p>Model: {@code block/oar_bench} (north frame: the thole pin at the north edge), a child per wood from the wood
 * family; built by {@code tools/gen_oar_models.py}. The shape is the thwart on its legs (8 px) and the gunwale board (9 px), so the crew step
 * over it like a slab.
 */
public class OarBenchBlock extends Block implements StationBlock, SimpleWaterloggedBlock {

    /** The outboard side: the thole pin's end of the thwart. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            // north frame: the thwart x 4..12 on its legs, the gunwale board with the thole pins at the north end
            VoxelShape thwart = box(4, 0, 2, 12, 8, 16);
            VoxelShape thole = box(3, 0, 0, 13, 9, 2);
            SHAPES[d.get2DDataValue()] = turned(Shapes.or(thwart, thole), d);
        }
    }

    public OarBenchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(Waterlogging.WATERLOGGED, false));
    }

    /** {@code shape} of the north frame turned so its north side faces {@code d}. */
    private static VoxelShape turned(VoxelShape shape, Direction d) {
        VoxelShape[] out = {Shapes.empty()};
        int turns = (d.get2DDataValue() - Direction.NORTH.get2DDataValue() + 4) % 4; // clockwise quarter turns from north
        shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            double ax0 = x0, az0 = z0, ax1 = x1, az1 = z1;
            for (int i = 0; i < turns; i++) { // (x, z) → (1 − z, x): a clockwise quarter turn seen from above
                double nx0 = 1 - az1, nx1 = 1 - az0, nz0 = ax0, nz1 = ax1;
                ax0 = nx0; ax1 = nx1; az0 = nz0; az1 = nz1;
            }
            out[0] = Shapes.or(out[0], Shapes.box(ax0, y0, az0, ax1, y1, az1));
        });
        return out[0].optimize();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, Waterlogging.WATERLOGGED);
    }

    /** The thole pin goes to the side the placing player looks at: over the rail, outboard. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return Waterlogging.placed(defaultBlockState().setValue(FACING, context.getHorizontalDirection()), context);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    /** WLOG1: a partial block takes water; a waterlogged bench lets its water flow on. */
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

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
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
    public StationKind<?> stationKind() {
        return OarBenchStation.INSTANCE;
    }

    /** The rower sits on the thwart: the seat stands in the bench's own cell. */
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

    /** Breaking the bench frees the station and removes its seat (the rower gets off on deck). */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            Stations.onStationRemoved(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
