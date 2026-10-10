package com.richardsenger.piratesnships.ship.decor.railing;

import com.richardsenger.piratesnships.ship.decor.wood.WoodBlock;
import com.richardsenger.piratesnships.combat.boarding.BoardingContent;
import com.richardsenger.piratesnships.core.block.Waterlogging;
import com.richardsenger.piratesnships.ship.decor.DecorConfig;
import com.richardsenger.piratesnships.ship.decor.wood.ShipWood;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The railing gate (RAIL3, docs/design.md §4.8 "Railing extras (RAIL3–RAIL5)"): one railing section that swings open,
 * the boarding opening that can be shut. Registered per wood through {@code ship.decor.wood.WoodFamily}.
 *
 * <p><b>State.</b> {@link #FACING}: the outboard edge (the edge its posts stand on, like the railing port's);
 * {@link #OPEN}; {@link #HINGE}: the post it hangs on, left or right looking outboard; {@link #POWERED}: a redstone
 * signal holds it open, like a fence gate; {@link Waterlogging#WATERLOGGED}.
 *
 * <p><b>Looks.</b> The port's two posts and sill, and between them a leaf of the railing's rails and balusters with two
 * iron hinge straps on the hinge post's side and a latch on the other; open, the leaf turns 90° outboard about the hinge
 * post (out of the block, like a fence gate's leaf turn). A railing next to it along the same edge continues its run
 * into the gate ({@link RailingBlock#carries}), open or closed: no double posts.
 *
 * <p><b>Use</b> opens and closes it with that wood's fence-gate sounds; a redstone signal opens it and its loss closes
 * it. A boarding plank used on it is not taken as a use: the plank lays its gangway ({@link #useItemOn}).
 *
 * <p><b>Shapes.</b> Closed: collision like the railing (posts and leaf raised to
 * {@code ship_decor.railing_collision_blocks}), crew do not path through it. Open: the port's collision (2 px posts in
 * the corners and the sill), crew path through it ({@link #isPathfindable}), and a plank used on the sill facing
 * outboard lays the gangway from the block beyond it (the gate is in {@code #pirates_n_ships:gangway_openings}, which
 * the plank reads for open gates only, {@code PlankRun#opening}). Its posts carry a lantern or a chain like a railing
 * post's ({@link RailingRules#supportBoxes}).
 *
 * <p><b>Double gates</b> (RAIL7, docs/design.md "Wide ports, double gates and the net's foot (RAIL7, RL6)"): two gates
 * side by side on one edge, facing the same way with mirrored hinges, are one double gate ({@link #PAIRED}, worked out
 * on placement and on every neighbour update, {@link RailingRules#pairs}): the leaves hang on the outer posts, the
 * post where they meet is left out and each leaf runs on to the middle (the {@code _meet} part), the sill runs through.
 * A gate placed beside a free gate (one not yet in a pair) takes the hinge away from it, turning that gate's hinge
 * round if it hung on the new gate's side ({@link RailingRules#placedHingeLeft}, {@link #setPlacedBy}); a gate already
 * in a pair is never taken, so a row placed one by one pairs as first and second, third and fourth. Using either leaf
 * opens or closes both ({@link #partner}); redstone moves each leaf by its own signal, like vanilla's doors. Open, both
 * cells are gangway openings and crew path through either.
 */
public class RailingGateBlock extends Block implements SimpleWaterloggedBlock, WoodBlock {
    /** The wood of this member (WOOD1). */
    private final ShipWood wood;

    @Override
    public ShipWood wood() {
        return wood;
    }


    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final EnumProperty<DoorHingeSide> HINGE = BlockStateProperties.DOOR_HINGE;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** RAIL7: one leaf of a double gate (its latch-side neighbour is its mirrored partner); the latch post is left out. */
    public static final BooleanProperty PAIRED = BooleanProperty.create("paired");

    private final SoundEvent openSound;
    private final SoundEvent closeSound;
    private final Map<BlockState, VoxelShape> outlines = new ConcurrentHashMap<>();
    private final Map<BlockState, VoxelShape> supports = new ConcurrentHashMap<>();
    private volatile double collisionHeight = -1;
    private volatile Map<BlockState, VoxelShape> collisions = new ConcurrentHashMap<>();

    public RailingGateBlock(ShipWood wood, Properties properties) {
        super(properties);
        this.wood = wood;
        this.openSound = wood.woodType().fenceGateOpen();
        this.closeSound = wood.woodType().fenceGateClose();
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false)
                .setValue(HINGE, DoorHingeSide.LEFT).setValue(POWERED, false).setValue(PAIRED, false)
                .setValue(Waterlogging.WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, HINGE, POWERED, PAIRED, Waterlogging.WATERLOGGED);
    }

    // ------------------------------------------------------------------ placement

    /** Placed like a railing's edge ({@link RailingRules#edgeForClick}); hinged on the post of the half clicked. */
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
        double fx = Math.max(0, Math.min(1, hit.x - pos.getX()));
        double fz = Math.max(0, Math.min(1, hit.z - pos.getZ()));
        Direction edge = RailingRules.edgeForClick(fx, fz, context.getClickedFace().getAxis() == Direction.Axis.Y,
                context.getHorizontalDirection());
        Level level = context.getLevel();
        boolean powered = level.hasNeighborSignal(pos);
        // RAIL7: beside a free gate on the same edge, take the hinge away from it so the two make a double gate
        boolean hingeLeft = RailingRules.placedHingeLeft(RailingRules.gateHingeLeft(fx, fz, edge),
                neighbour(level, pos, edge, true), neighbour(level, pos, edge, false));
        BlockState state = defaultBlockState().setValue(FACING, edge)
                .setValue(HINGE, hingeLeft ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT)
                .setValue(POWERED, powered).setValue(OPEN, powered);
        BlockState mate = level.getBlockState(pos.relative(RailingRules.latchSide(edge, hingeLeft)));
        if (!powered && isGateFacing(mate, edge) && !isPaired(level, pos.relative(RailingRules.latchSide(edge, hingeLeft)), mate, pos)) {
            state = state.setValue(OPEN, mate.getValue(OPEN)); // join the leaf as it stands
        }
        return Waterlogging.placed(withPaired(state, level, pos), context);
    }

    /**
     * RAIL7: the gate placed by a player pairs with the free gate on its latch side; if that one hung on this side it
     * turns its hinge round (and opens or shuts like this one), so the two leaves hang on the outer posts.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;
        Direction facing = state.getValue(FACING);
        boolean hingeLeft = state.getValue(HINGE) == DoorHingeSide.LEFT;
        BlockPos matePos = pos.relative(RailingRules.latchSide(facing, hingeLeft));
        BlockState mate = level.getBlockState(matePos);
        if (!(mate.getBlock() instanceof RailingGateBlock)) return;
        if (!RailingRules.flipsNeighbour(neighbourInfo(level, matePos, mate, pos, facing))) return;
        BlockState turned = mate.setValue(HINGE, hingeLeft ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
        if (!mate.getValue(POWERED)) turned = turned.setValue(OPEN, state.getValue(OPEN));
        level.setBlock(matePos, withPaired(turned, level, matePos), Block.UPDATE_ALL);
        BlockState self = level.getBlockState(pos);
        if (self.is(this)) level.setBlock(pos, withPaired(self, level, pos), Block.UPDATE_CLIENTS);
    }

    /** What a gate placed at {@code pos} facing {@code facing} sees of the block on its left ({@code left}) or right. */
    private static RailingRules.GateNeighbour neighbour(Level level, BlockPos pos, Direction facing, boolean left) {
        BlockPos at = pos.relative(left ? RailingRules.leftOf(facing) : RailingRules.rightOf(facing));
        return neighbourInfo(level, at, level.getBlockState(at), pos, facing);
    }

    /** {@link RailingRules.GateNeighbour} of the block {@code s} at {@code at}, beside a new gate at {@code newPos}. */
    private static RailingRules.GateNeighbour neighbourInfo(BlockGetter level, BlockPos at, BlockState s, BlockPos newPos, Direction facing) {
        if (!isGateFacing(s, facing)) return RailingRules.GateNeighbour.NONE;
        // its hinge is toward the new gate when its latch side points away from it
        boolean hingeToward = !at.relative(RailingRules.latchSide(facing, s.getValue(HINGE) == DoorHingeSide.LEFT)).equals(newPos);
        return new RailingRules.GateNeighbour(true, hingeToward, isPaired(level, at, s, newPos));
    }

    /** Whether the gate {@code s} at {@code at} pairs with a gate other than the one at {@code exclude}. */
    private static boolean isPaired(BlockGetter level, BlockPos at, BlockState s, BlockPos exclude) {
        BlockPos latch = at.relative(RailingRules.latchSide(s.getValue(FACING), s.getValue(HINGE) == DoorHingeSide.LEFT));
        return !latch.equals(exclude) && partner(level, at, s) != null;
    }

    private static boolean isGateFacing(BlockState s, Direction facing) {
        return s.getBlock() instanceof RailingGateBlock && s.getValue(FACING) == facing;
    }

    /**
     * RAIL7: the position of the other leaf of the double gate {@code state} at {@code pos} belongs to, or null for a
     * single gate: the gate on its latch side, facing the same way with the mirrored hinge ({@link RailingRules#pairs}).
     */
    public static @Nullable BlockPos partner(BlockGetter level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof RailingGateBlock)) return null;
        Direction facing = state.getValue(FACING);
        boolean hingeLeft = state.getValue(HINGE) == DoorHingeSide.LEFT;
        BlockPos at = pos.relative(RailingRules.latchSide(facing, hingeLeft));
        BlockState n = level.getBlockState(at);
        boolean gate = n.getBlock() instanceof RailingGateBlock;
        return RailingRules.pairs(facing, hingeLeft, gate, gate ? n.getValue(FACING) : null,
                gate && n.getValue(HINGE) == DoorHingeSide.LEFT) ? at : null;
    }

    /** RAIL7: {@code state} with {@link #PAIRED} worked out from its latch-side neighbour. */
    public static BlockState withPaired(BlockState state, BlockGetter level, BlockPos pos) {
        return state.setValue(PAIRED, partner(level, pos, state) != null);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        Waterlogging.tickFluid(state, level, pos);
        if (direction.getAxis().isHorizontal()) {
            return withPaired(state, level, pos);
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    // ------------------------------------------------------------------ opening

    /**
     * A boarding plank in hand skips the gate's own use so the plank lays its gangway from the sill (otherwise the
     * click would swing the gate shut, as a door or fence gate takes any item's click).
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.is(BoardingContent.PLANK_ITEM.get())) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        boolean open = !state.getValue(OPEN);
        level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
        // RAIL7: the other leaf of a double gate swings with it
        BlockPos other = partner(level, pos, state);
        if (other != null) {
            BlockState o = level.getBlockState(other);
            if (o.getValue(OPEN) != open) {
                level.setBlock(other, o.setValue(OPEN, open), Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
            }
        }
        swingSound(level, player, pos, open);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** A redstone signal opens it, its loss closes it (like a fence gate). */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (level.isClientSide) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (state.getValue(POWERED) != powered) {
            level.setBlock(pos, state.setValue(POWERED, powered).setValue(OPEN, powered), Block.UPDATE_CLIENTS);
            if (state.getValue(OPEN) != powered) {
                swingSound(level, null, pos, powered);
            }
        }
    }

    private void swingSound(Level level, @Nullable Player player, BlockPos pos, boolean open) {
        level.playSound(player, pos, open ? openSound : closeSound, SoundSource.BLOCKS, 1.0f, level.getRandom().nextFloat() * 0.1f + 0.9f);
        level.gameEvent(player, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
    }

    /** The sound of the gate opening ({@code true}) or closing: that wood's fence gate's. */
    public SoundEvent swingSound(boolean open) {
        return open ? openSound : closeSound;
    }

    // ------------------------------------------------------------------ shapes

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return outlines.computeIfAbsent(state, s -> RailingBlock.shape(RailingRules.gateBoxes(s.getValue(FACING), s.getValue(OPEN),
                RailingRules.POST_TOP, RailingRules.RAIL_TOP, RailingRules.POST, hingeLeft(s), s.getValue(PAIRED))));
    }

    /** Closed: the posts and the leaf at the railing's collision height; open: the port's collision. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        double height = RailingBlock.collisionHeightPx();
        if (height != collisionHeight) {
            collisions = new ConcurrentHashMap<>();
            collisionHeight = height;
        }
        return collisions.computeIfAbsent(state, s -> RailingBlock.shape(s.getValue(OPEN)
                ? RailingRules.gateBoxes(s.getValue(FACING), true, RailingRules.POST_TOP, 0, RailingRules.PORT_COLLISION_POST,
                        hingeLeft(s), s.getValue(PAIRED))
                : RailingRules.gateBoxes(s.getValue(FACING), false, height, height, RailingRules.POST, hingeLeft(s), s.getValue(PAIRED))));
    }

    /** Its posts carry a lantern or a chain like a railing post ({@link RailingRules#supportBoxes}). */
    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return supports.computeIfAbsent(state, s -> RailingBlock.shape(RailingRules.supportBoxes(
                RailingRules.gateBoxes(s.getValue(FACING), true, RailingRules.RAIL_TOP, 0, RailingRules.POST, hingeLeft(s),
                        s.getValue(PAIRED)), true)));
    }

    private static boolean hingeLeft(BlockState s) {
        return s.getValue(HINGE) == DoorHingeSide.LEFT;
    }

    /** Crew and mobs walk through an open gate, not a closed one (like a fence gate). */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return type != PathComputationType.WATER && state.getValue(OPEN);
    }

    // ------------------------------------------------------------------ turning

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /** A mirror turns the gate and moves its hinge to the other post (like a door). */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return mirror == Mirror.NONE ? state : state.rotate(mirror.getRotation(state.getValue(FACING))).cycle(HINGE);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Waterlogging.fluid(state, super.getFluidState(state));
    }
}
