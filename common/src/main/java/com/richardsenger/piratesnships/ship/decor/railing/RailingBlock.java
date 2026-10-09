package com.richardsenger.piratesnships.ship.decor.railing;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.block.Waterlogging;
import com.richardsenger.piratesnships.ship.decor.DecorConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The railing (RAIL1, docs/design.md §4.8 "Railings (RAIL1/RAIL2)"): a rail that lies along one or more edges of its
 * block, at the outward side, with posts drawn where a run stops or turns. One block gives the full-width run, the
 * ended run and (with a gap of one block) the posts on both sides of the gap; a second placement in the same block
 * adds an edge (corners, hatch surrounds). Registered per wood through {@code ship.decor.wood.WoodFamily}.
 *
 * <p><b>State.</b> {@link #NORTH}..{@link #WEST}: the railed edges. {@link #POST_NW}, {@link #POST_NE},
 * {@link #POST_SE}, {@link #POST_SW}: the posts, derived from the edges and the neighbours in
 * {@link #getStateForPlacement} and {@link #updateShape} by {@link RailingRules#posts} (the block state's multipart
 * draws one hand-made edge model per edge and one post model per post). {@link Waterlogging#WATERLOGGED}: WLOG1.
 *
 * <p><b>Placement</b> ({@link RailingRules#edgeForClick}): a click on a block's top rails the edge of the new block
 * nearest the click point, or, within 4 px of the centre, the edge the player faces; a click on a side face rails the
 * edge against that side. Using the railing item on a railing of the same wood (not sneaking) adds the free edge
 * nearest the click ({@link #canBeReplaced}); each edge uses one item, and breaking the block drops one item per edge
 * (loot table). Refused while {@code ship_decor.railings_enabled} is off.
 *
 * <p><b>Shapes.</b> Outline: each edge's rail (2 px deep, y 0..16) and the posts (3 x 3 px, y 0..17). Collision: the
 * same raised to {@code ship_decor.railing_collision_blocks} (1.5 by default: like a fence, players cannot jump it;
 * 1.0 lets them vault it); a changed value is picked up the next time the collision is asked for. Mobs do not path
 * through it ({@link #isPathfindable}).
 *
 * <p><b>Posts carry things</b> (RAIL3): a block with a post has a sturdy centre on its top and bottom faces
 * ({@link #getBlockSupportShape}), so a lantern hangs below it or stands on it and a chain runs on from it; a plain rail
 * does not. The grappling hook holds on a railing like on a cleat ({@code #pirates_n_ships:grapple_latches}).
 */
public class RailingBlock extends Block implements SimpleWaterloggedBlock {

    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty POST_NW = BooleanProperty.create("post_nw");
    public static final BooleanProperty POST_NE = BooleanProperty.create("post_ne");
    public static final BooleanProperty POST_SE = BooleanProperty.create("post_se");
    public static final BooleanProperty POST_SW = BooleanProperty.create("post_sw");

    /** Action bar text when a railing is placed while railings are disabled. */
    public static final String KEY_DISABLED = "message." + Constants.MOD_ID + ".railing.disabled";

    private static final Map<Direction, BooleanProperty> EDGES = new EnumMap<>(Direction.class);
    /** The post properties by corner key ({@link RailingRules}: the corner between {@code d} and its counter-clockwise neighbour). */
    private static final Map<Direction, BooleanProperty> POSTS = new EnumMap<>(Direction.class);

    static {
        EDGES.put(Direction.NORTH, NORTH);
        EDGES.put(Direction.EAST, EAST);
        EDGES.put(Direction.SOUTH, SOUTH);
        EDGES.put(Direction.WEST, WEST);
        POSTS.put(Direction.NORTH, POST_NW);
        POSTS.put(Direction.EAST, POST_NE);
        POSTS.put(Direction.SOUTH, POST_SE);
        POSTS.put(Direction.WEST, POST_SW);
    }

    private final Map<BlockState, VoxelShape> outlines = new ConcurrentHashMap<>();
    private volatile double collisionHeight = -1;
    private volatile Map<BlockState, VoxelShape> collisions = new ConcurrentHashMap<>();

    public RailingBlock(Properties properties) {
        super(properties);
        BlockState s = stateDefinition.any().setValue(Waterlogging.WATERLOGGED, false);
        for (BooleanProperty p : EDGES.values()) s = s.setValue(p, false);
        for (BooleanProperty p : POSTS.values()) s = s.setValue(p, false);
        registerDefaultState(s);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, POST_NW, POST_NE, POST_SE, POST_SW, Waterlogging.WATERLOGGED);
    }

    /** The edge property of {@code edge} (a horizontal direction). */
    public static BooleanProperty edge(Direction edge) {
        return EDGES.get(edge);
    }

    /** The post property of the corner keyed {@code corner} ({@link RailingRules}). */
    public static BooleanProperty post(Direction corner) {
        return POSTS.get(corner);
    }

    /** The railed edges of a railing state (empty for any other block). */
    public static Set<Direction> edges(BlockState state) {
        Set<Direction> out = EnumSet.noneOf(Direction.class);
        if (!(state.getBlock() instanceof RailingBlock)) return out;
        EDGES.forEach((d, p) -> {
            if (state.getValue(p)) out.add(d);
        });
        return out;
    }

    /** The posts of a railing state by corner key. */
    public static Set<Direction> posts(BlockState state) {
        Set<Direction> out = EnumSet.noneOf(Direction.class);
        if (!(state.getBlock() instanceof RailingBlock)) return out;
        POSTS.forEach((d, p) -> {
            if (state.getValue(p)) out.add(d);
        });
        return out;
    }

    /**
     * Whether the block at {@code pos} carries {@code edge}: a railing railed there, or a railing port or railing gate
     * (RAIL3, open or closed: its posts stand either way) facing it.
     */
    public static boolean carries(BlockGetter level, BlockPos pos, Direction edge) {
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof RailingBlock) return s.getValue(edge(edge));
        if (s.getBlock() instanceof RailingGateBlock) return s.getValue(RailingGateBlock.FACING) == edge;
        return s.getBlock() instanceof RailingPortBlock && s.getValue(RailingPortBlock.FACING) == edge;
    }

    /** {@code state} with its posts worked out from its edges and the neighbours at {@code pos}. */
    public static BlockState withPosts(BlockState state, BlockGetter level, BlockPos pos) {
        Set<Direction> posts = RailingRules.posts(edges(state), (towards, edge) -> carries(level, pos.relative(towards), edge));
        for (Map.Entry<Direction, BooleanProperty> e : POSTS.entrySet()) {
            state = state.setValue(e.getValue(), posts.contains(e.getKey()));
        }
        return state;
    }

    // ------------------------------------------------------------------ placement

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!DecorConfig.RAILINGS_ENABLED.get()) {
            if (context.getPlayer() != null && !context.getLevel().isClientSide) {
                context.getPlayer().displayClientMessage(Component.translatable(KEY_DISABLED), true);
            }
            return null;
        }
        BlockPos pos = context.getClickedPos();
        Vec3 hit = context.getClickLocation();
        double fx = clamp01(hit.x - pos.getX());
        double fz = clamp01(hit.z - pos.getZ());
        BlockState existing = context.getLevel().getBlockState(pos);
        BlockState state;
        if (existing.is(this)) {
            Direction free = RailingRules.nearestFree(fx, fz, edges(existing));
            if (free == null) return null;
            state = existing.setValue(edge(free), true);
        } else {
            Direction edge = RailingRules.edgeForClick(fx, fz, context.getClickedFace().getAxis() == Direction.Axis.Y,
                    context.getHorizontalDirection());
            state = Waterlogging.at(defaultBlockState().setValue(edge(edge), true), context.getLevel(), pos);
        }
        return withPosts(state, context.getLevel(), pos);
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    /**
     * The railing item of this block used on it (not sneaking) adds an edge while one is free, like a second slab; any
     * other item places as usual.
     */
    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return !context.isSecondaryUseActive() && context.getItemInHand().is(asItem()) && edges(state).size() < 4;
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

    // ------------------------------------------------------------------ shapes

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return outlines.computeIfAbsent(state, s -> shape(RailingRules.boxes(edges(s), posts(s), RailingRules.RAIL_TOP, RailingRules.POST_TOP)));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        double height = collisionHeightPx();
        if (height != collisionHeight) {
            collisions = new ConcurrentHashMap<>();
            collisionHeight = height;
        }
        return collisions.computeIfAbsent(state, s -> shape(RailingRules.boxes(edges(s), posts(s), height, height)));
    }

    /** {@code ship_decor.railing_collision_blocks} in pixels. */
    public static double collisionHeightPx() {
        return DecorConfig.RAILING_COLLISION_BLOCKS.get() * 16.0;
    }

    static VoxelShape shape(List<double[]> boxes) {
        VoxelShape shape = Shapes.empty();
        for (double[] b : boxes) shape = Shapes.or(shape, Block.box(b[0], b[1], b[2], b[3], b[4], b[5]));
        return shape.optimize();
    }

    /**
     * RAIL3: what lanterns, chains and torches see ({@link RailingRules#supportBoxes}): a block with a post is a sturdy
     * centre on its top and bottom faces (a lantern hangs below a post or stands on it), a plain rail is not. Fixed per
     * state (vanilla caches face sturdiness when the game starts), so no config value changes it.
     */
    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        Set<Direction> posts = posts(state);
        return shape(RailingRules.supportBoxes(RailingRules.boxes(edges(state), posts, RailingRules.RAIL_TOP, RailingRules.RAIL_TOP),
                !posts.isEmpty()));
    }

    /** Crew and mobs do not path through a railing (like a fence). */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    // ------------------------------------------------------------------ turning

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return remap(state, rotation::rotate);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return remap(state, mirror::mirror);
    }

    /** Moves every edge and post by the direction map {@code turn} (a rotation or a mirror). */
    private static BlockState remap(BlockState state, java.util.function.UnaryOperator<Direction> turn) {
        Set<Direction> edges = edges(state);
        Set<Direction> posts = posts(state);
        BlockState out = state;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            out = out.setValue(edge(turn.apply(d)), edges.contains(d));
        }
        for (Direction key : Direction.Plane.HORIZONTAL) {
            Direction a = turn.apply(key);
            Direction b = turn.apply(key.getCounterClockWise());
            out = out.setValue(post(RailingRules.cornerOf(a, b)), posts.contains(key));
        }
        return out;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Waterlogging.fluid(state, super.getFluidState(state));
    }
}
