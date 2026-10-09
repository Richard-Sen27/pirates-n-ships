package com.richardsenger.piratesnships.ship.decor.railing;

import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.boarding.BoardingContent;
import com.richardsenger.piratesnships.combat.cannon.CannonBlock;
import com.richardsenger.piratesnships.core.block.Waterlogging;
import com.richardsenger.piratesnships.ship.decor.DecorConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
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
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
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
 * The bulwark gun port (RAIL4, docs/design.md §4.8 "Railing extras (RAIL3–RAIL5)"): a bulwark section on its
 * {@link #FACING} edge (the outboard edge) with a square notch from a 4 px sill up through the cap rail, 10 px wide,
 * and a lid hinged at the notch's top that swings up and out. A bulwark beside it on the same edge runs on into it (no
 * stanchion against it).
 *
 * <p><b>The lid</b> ({@link BulwarkRules#lidOpen}): {@link #OPEN} while a carriage cannon stands in the block inboard of
 * the port with its muzzle toward it (worked out whenever that block changes), otherwise as a player left it: a use
 * with an empty hand props it open or shuts it ({@link #PROPPED}); while a cannon holds it open, shutting it is refused
 * with an action-bar note. A carriage cannon fires through the open port at every elevation; the port is not in
 * {@code barrel_blocking}.
 *
 * <p><b>Gangway.</b> In {@code #pirates_n_ships:gangway_openings}: a boarding plank used on the sill's top facing
 * outboard lays the gangway from the block beyond the port, while the lid is open (the plank reads {@link #OPEN} as
 * for the railing gate; a plank in hand never works the lid).
 *
 * <p><b>Shapes</b> ({@link BulwarkRules#portBoxes}): outline, the jambs, the cap rail's ends, the sill and the closed
 * lid; collision, closed like a bulwark (the whole wall raised to {@code ship_decor.railing_collision_blocks}), open the
 * jambs 2 px wide in the outer corners and the sill, so a player steps out over it as through a railing port. Crew path
 * through it only while open. Waterloggable (WLOG1), refused while {@code ship_decor.railings_enabled} is off.
 */
public class BulwarkPortBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock {

    public static final MapCodec<BulwarkPortBlock> CODEC = simpleCodec(BulwarkPortBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    /** The lid as a player left it, open or shut; a cannon inboard opens it whatever this says. */
    public static final BooleanProperty PROPPED = BooleanProperty.create("propped");

    /** Action bar text when a player tries to shut a lid a cannon holds open. */
    public static final String KEY_HELD_OPEN = "message." + Constants.MOD_ID + ".bulwark_port.held_open";

    private final Map<BlockState, VoxelShape> outlines = new ConcurrentHashMap<>();
    private final Map<BlockState, VoxelShape> supports = new ConcurrentHashMap<>();
    private volatile double collisionHeight = -1;
    private volatile Map<BlockState, VoxelShape> collisions = new ConcurrentHashMap<>();

    public BulwarkPortBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false)
                .setValue(PROPPED, false).setValue(Waterlogging.WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, PROPPED, Waterlogging.WATERLOGGED);
    }

    // ------------------------------------------------------------------ placement and the lid

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
        BlockState state = defaultBlockState().setValue(FACING, edge);
        return Waterlogging.placed(withLid(state, context.getLevel(), pos), context);
    }

    /** Whether a carriage cannon stands in the block inboard of the port at {@code pos} with its muzzle toward it. */
    public static boolean cannonInboard(BlockState port, BlockGetter level, BlockPos pos) {
        Direction facing = port.getValue(FACING);
        BlockState inboard = level.getBlockState(pos.relative(facing.getOpposite()));
        return inboard.getBlock() instanceof CannonBlock
                && BulwarkRules.cannonFacesPort(facing, inboard.getValue(CannonBlock.FACING));
    }

    /** {@code state} with its lid worked out from the block inboard and {@link #PROPPED}. */
    public static BlockState withLid(BlockState state, BlockGetter level, BlockPos pos) {
        return state.setValue(OPEN, BulwarkRules.lidOpen(cannonInboard(state, level, pos), state.getValue(PROPPED)));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        Waterlogging.tickFluid(state, level, pos);
        if (direction == state.getValue(FACING).getOpposite()) {
            BlockState next = withLid(state, level, pos);
            if (next.getValue(OPEN) != state.getValue(OPEN) && level instanceof Level l && !l.isClientSide) {
                lidSound(l, null, pos, next.getValue(OPEN));
            }
            return next;
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    /** A boarding plank in hand skips the lid so the plank lays its gangway from the sill (as on the railing gate). */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.is(BoardingContent.PLANK_ITEM.get())) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    /** Props the lid open or shuts it; while a cannon inboard holds it open, nothing changes but a note. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (cannonInboard(state, level, pos)) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable(KEY_HELD_OPEN), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        BlockState next = withLid(state.cycle(PROPPED), level, pos);
        level.setBlock(pos, next, Block.UPDATE_ALL);
        if (next.getValue(OPEN) != state.getValue(OPEN)) {
            lidSound(level, player, pos, next.getValue(OPEN));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void lidSound(Level level, @Nullable Player player, BlockPos pos, boolean open) {
        level.playSound(player, pos, open ? SoundEvents.WOODEN_TRAPDOOR_OPEN : SoundEvents.WOODEN_TRAPDOOR_CLOSE,
                SoundSource.BLOCKS, 1.0f, level.getRandom().nextFloat() * 0.1f + 0.9f);
        level.gameEvent(player, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
    }

    // ------------------------------------------------------------------ shapes

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return outlines.computeIfAbsent(state, s -> RailingBlock.shape(
                BulwarkRules.portBoxes(s.getValue(FACING), s.getValue(OPEN), false, BulwarkRules.WALL_TOP)));
    }

    /** Closed like a bulwark; open, the jambs 2 px wide and the sill, so a player steps out over the sill. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        double height = RailingBlock.collisionHeightPx();
        if (height != collisionHeight) {
            collisions = new ConcurrentHashMap<>();
            collisionHeight = height;
        }
        return collisions.computeIfAbsent(state, s -> RailingBlock.shape(
                BulwarkRules.portBoxes(s.getValue(FACING), s.getValue(OPEN), true, height)));
    }

    /** No post to carry a lantern: only the wall's own faces (a lantern needs a bulwark's stanchion). */
    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return supports.computeIfAbsent(state, s -> RailingBlock.shape(
                BulwarkRules.portBoxes(s.getValue(FACING), s.getValue(OPEN), false, BulwarkRules.WALL_TOP)));
    }

    /** Crew walk out through an open port onto a gangway, not through a shut one. */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return type != PathComputationType.WATER && state.getValue(OPEN);
    }

    // ------------------------------------------------------------------ turning

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
