package com.richardsenger.piratesnships.ship.decor.railing;

import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.core.block.Waterlogging;
import com.richardsenger.piratesnships.ship.decor.DecorConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * The railing port (RAIL1, docs/design.md §4.8 "Railings (RAIL1/RAIL2)"): the one-block opening in a railing, the two
 * posts at the ends of its {@link #FACING} edge (the outboard edge) with a 4 px sill between them and no rails. A
 * carriage cannon in the block inboard of it fires through it (the shot's line stays above the sill and between the
 * posts), and a boarding plank used on the sill's top facing outboard lays the gangway from the block beyond the port
 * (the block is in {@code #pirates_n_ships:gangway_openings}), so it is also the boarding opening. A railing next to it
 * along the same edge counts the port as the continuation of its run and draws no post against the port's own.
 *
 * <p>Placement like a railing's edge ({@link RailingRules#edgeForClick}). Outline: the sill and the two posts (y 0..17),
 * nothing else; the collision draws the posts 2 px square in the outer corners, so a player walks through the opening
 * over the sill, also aboard (Sable's step-up test widens the player). Waterloggable (WLOG1), refused
 * while {@code ship_decor.railings_enabled} is off.
 */
public class RailingPortBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock {

    public static final MapCodec<RailingPortBlock> CODEC = simpleCodec(RailingPortBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);
    private static final Map<Direction, VoxelShape> COLLISIONS = new EnumMap<>(Direction.class);

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            SHAPES.put(d, RailingBlock.shape(RailingRules.portBoxes(d, RailingRules.POST_TOP)));
            COLLISIONS.put(d, RailingBlock.shape(RailingRules.portBoxes(d, RailingRules.POST_TOP, RailingRules.PORT_COLLISION_POST)));
        }
    }

    public RailingPortBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(Waterlogging.WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, Waterlogging.WATERLOGGED);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!DecorConfig.RAILINGS_ENABLED.get()) {
            if (context.getPlayer() != null && !context.getLevel().isClientSide) {
                context.getPlayer().displayClientMessage(Component.translatable(RailingBlock.KEY_DISABLED), true);
            }
            return null;
        }
        BlockPos pos = context.getClickedPos();
        Vec3 hit = context.getClickLocation();
        Direction edge = RailingRules.edgeForClick(Math.max(0, Math.min(1, hit.x - pos.getX())),
                Math.max(0, Math.min(1, hit.z - pos.getZ())), context.getClickedFace().getAxis() == Direction.Axis.Y,
                context.getHorizontalDirection());
        return Waterlogging.placed(defaultBlockState().setValue(FACING, edge), context);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        Waterlogging.tickFluid(state, level, pos);
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** The sill and the posts, the posts 2 px square in the outer corners so a player steps through over the sill. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISIONS.get(state.getValue(FACING));
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
    protected FluidState getFluidState(BlockState state) {
        return Waterlogging.fluid(state, super.getFluidState(state));
    }
}
