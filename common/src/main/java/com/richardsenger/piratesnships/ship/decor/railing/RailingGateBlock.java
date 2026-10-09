package com.richardsenger.piratesnships.ship.decor.railing;

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
 */
public class RailingGateBlock extends Block implements SimpleWaterloggedBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final EnumProperty<DoorHingeSide> HINGE = BlockStateProperties.DOOR_HINGE;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private final SoundEvent openSound;
    private final SoundEvent closeSound;
    private final Map<BlockState, VoxelShape> outlines = new ConcurrentHashMap<>();
    private final Map<BlockState, VoxelShape> supports = new ConcurrentHashMap<>();
    private volatile double collisionHeight = -1;
    private volatile Map<BlockState, VoxelShape> collisions = new ConcurrentHashMap<>();

    public RailingGateBlock(ShipWood wood, Properties properties) {
        super(properties);
        this.openSound = wood.woodType().fenceGateOpen();
        this.closeSound = wood.woodType().fenceGateClose();
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false)
                .setValue(HINGE, DoorHingeSide.LEFT).setValue(POWERED, false).setValue(Waterlogging.WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, HINGE, POWERED, Waterlogging.WATERLOGGED);
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
        boolean powered = context.getLevel().hasNeighborSignal(pos);
        BlockState state = defaultBlockState().setValue(FACING, edge)
                .setValue(HINGE, RailingRules.gateHingeLeft(fx, fz, edge) ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT)
                .setValue(POWERED, powered).setValue(OPEN, powered);
        return Waterlogging.placed(state, context);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        Waterlogging.tickFluid(state, level, pos);
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
                RailingRules.POST_TOP, RailingRules.RAIL_TOP, RailingRules.POST)));
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
                ? RailingRules.gateBoxes(s.getValue(FACING), true, RailingRules.POST_TOP, 0, RailingRules.PORT_COLLISION_POST)
                : RailingRules.gateBoxes(s.getValue(FACING), false, height, height, RailingRules.POST)));
    }

    /** Its posts carry a lantern or a chain like a railing post ({@link RailingRules#supportBoxes}). */
    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return supports.computeIfAbsent(state, s -> RailingBlock.shape(RailingRules.supportBoxes(
                RailingRules.portBoxes(s.getValue(FACING), RailingRules.RAIL_TOP), true)));
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
