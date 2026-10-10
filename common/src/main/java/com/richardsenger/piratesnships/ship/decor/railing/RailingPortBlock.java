package com.richardsenger.piratesnships.ship.decor.railing;

import com.richardsenger.piratesnships.ship.decor.wood.WoodBlock;
import com.richardsenger.piratesnships.ship.decor.wood.ShipWood;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The railing port (RAIL1, docs/design.md §4.8 "Railings (RAIL1/RAIL2)"): the one-block opening in a railing, the two
 * posts at the ends of its {@link #FACING} edge (the outboard edge) with a 4 px sill between them and no rails. A
 * carriage cannon in the block inboard of it fires through it (the shot's line stays above the sill and between the
 * posts), and a boarding plank used on the sill's top facing outboard lays the gangway from the block beyond the port
 * (the block is in {@code #pirates_n_ships:gangway_openings}), so it is also the boarding opening. A railing next to it
 * along the same edge counts the port as the continuation of its run and draws no post against the port's own.
 *
 * <p><b>Wide ports</b> (RAIL7, docs/design.md "Wide ports, double gates and the net's foot (RAIL7, RL6)"): each post is
 * its own state property, {@link #POST_LEFT} and {@link #POST_RIGHT} (looking outboard), worked out on placement and on
 * every neighbour update ({@link #withPosts}, the railing's mechanism): a post is left out where the block along the
 * edge on its side is another railing port with the same facing ({@link RailingRules#portPosts}), and the sill then
 * runs on to the block's side, so two or more ports in a row are one opening with posts only at its outer ends. A
 * cannon behind any of its cells fires through; a plank starts from any cell's sill.
 *
 * <p>Placement like a railing's edge ({@link RailingRules#edgeForClick}). Outline: the sill and the two posts (y 0..17),
 * nothing else; the collision draws the posts 2 px square in the outer corners, so a player walks through the opening
 * over the sill, also aboard (Sable's step-up test widens the player). Waterloggable (WLOG1), refused
 * while {@code ship_decor.railings_enabled} is off.
 */
public class RailingPortBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock, WoodBlock {
    /** The wood of this member (WOOD1). */
    private final ShipWood wood;

    @Override
    public ShipWood wood() {
        return wood;
    }


    public static final MapCodec<RailingPortBlock> CODEC = simpleCodec(p -> new RailingPortBlock(ShipWood.OAK, p));
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** RAIL7: the post at the left end of the edge (looking outboard) stands; false beside a port of the same facing. */
    public static final BooleanProperty POST_LEFT = BooleanProperty.create("post_left");
    /** RAIL7: the post at the right end of the edge (looking outboard) stands. */
    public static final BooleanProperty POST_RIGHT = BooleanProperty.create("post_right");

    /** Shapes by {@link #key}: facing and the two posts. */
    private static final Map<Integer, VoxelShape> SHAPES = new HashMap<>();
    private static final Map<Integer, VoxelShape> COLLISIONS = new HashMap<>();
    private static final Map<Integer, VoxelShape> SUPPORTS = new HashMap<>();

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (boolean left : new boolean[]{false, true}) {
                for (boolean right : new boolean[]{false, true}) {
                    int k = key(d, left, right);
                    SHAPES.put(k, RailingBlock.shape(RailingRules.portBoxes(d, RailingRules.POST_TOP, RailingRules.POST, left, right)));
                    COLLISIONS.put(k, RailingBlock.shape(RailingRules.portBoxes(d, RailingRules.POST_TOP,
                            RailingRules.PORT_COLLISION_POST, left, right)));
                    SUPPORTS.put(k, RailingBlock.shape(RailingRules.supportBoxes(RailingRules.portBoxes(d, RailingRules.RAIL_TOP,
                            RailingRules.POST, left, right), left || right)));
                }
            }
        }
    }

    private static int key(Direction facing, boolean left, boolean right) {
        return facing.get2DDataValue() * 4 + (left ? 2 : 0) + (right ? 1 : 0);
    }

    private static int key(BlockState s) {
        return key(s.getValue(FACING), s.getValue(POST_LEFT), s.getValue(POST_RIGHT));
    }

    public RailingPortBlock(ShipWood wood, Properties properties) {
        super(properties);
        this.wood = wood;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POST_LEFT, true)
                .setValue(POST_RIGHT, true).setValue(Waterlogging.WATERLOGGED, false));
    }

    /**
     * RAIL7: {@code state} with its two posts worked out from the blocks beside it along its edge
     * ({@link RailingRules#portPosts}): a post goes where that block is not a railing port facing the same way.
     */
    public static BlockState withPosts(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        boolean[] posts = RailingRules.portPosts(isPortFacing(level.getBlockState(pos.relative(RailingRules.leftOf(facing))), facing),
                isPortFacing(level.getBlockState(pos.relative(RailingRules.rightOf(facing))), facing));
        return state.setValue(POST_LEFT, posts[0]).setValue(POST_RIGHT, posts[1]);
    }

    private static boolean isPortFacing(BlockState s, Direction facing) {
        return s.getBlock() instanceof RailingPortBlock && s.getValue(FACING) == facing;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POST_LEFT, POST_RIGHT, Waterlogging.WATERLOGGED);
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
        BlockState state = Waterlogging.placed(defaultBlockState().setValue(FACING, edge), context);
        return state == null ? null : withPosts(state, context.getLevel(), pos);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        Waterlogging.tickFluid(state, level, pos);
        if (direction.getAxis().isHorizontal()) {
            return withPosts(state, level, pos);
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(key(state));
    }

    /** The sill and the posts, the posts 2 px square in the outer corners so a player steps through over the sill. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISIONS.get(key(state));
    }

    /** RAIL3: its posts carry a lantern or a chain like a railing's ({@link RailingRules#supportBoxes}). */
    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SUPPORTS.get(key(state));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) return state;
        // a mirror swaps the edge's ends (RAIL7: the posts follow them, like the gate's hinge)
        return state.rotate(mirror.getRotation(state.getValue(FACING)))
                .setValue(POST_LEFT, state.getValue(POST_RIGHT)).setValue(POST_RIGHT, state.getValue(POST_LEFT));
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Waterlogging.fluid(state, super.getFluidState(state));
    }
}
