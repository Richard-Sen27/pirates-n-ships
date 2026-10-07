package com.richardsenger.piratesnships.combat.cannon;

import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.station.StationBlock;
import com.richardsenger.piratesnships.station.StationKind;
import com.richardsenger.piratesnships.station.Stations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The cannon (docs/design.md §8.2): an iron barrel on a four-wheeled truck carriage, placed on a ship's deck or on
 * land. Two blocks long (P2), like a bed: the {@link CannonPart#FRONT} block is the master (block entity, station,
 * model, muzzle end) and the {@link CannonPart#REAR} block behind it the back of the carriage; the barrel reaches one
 * block ahead of the master through the model (geometry in {@link CannonRules}). The clicked block becomes the master
 * and the rear goes towards the placing player; both need room and a block to stand on. Breaking either half removes
 * both, and only the master drops the cannon. {@link #FACING} is the way the muzzle points (the traverse), {@link #LOAD}
 * what is in the barrel (on the master; the rear stays empty); the elevation and the reload cooldown live in the
 * {@link CannonBlockEntity}. The master draws the whole gun across both blocks, the rear draws nothing.
 *
 * <p>Controls, all on the server through {@link CannonService}:
 * <ul>
 *   <li>use with gunpowder: powder in; use with a cannonball: ball in (each takes one item, none in creative);</li>
 *   <li>sneak-use with an empty hand: aim, one elevation step up when the upper half of the block is clicked, one
 *       step down for the lower half (like the helm's click position for the rudder);</li>
 *   <li>use with an empty hand: fire when loaded, otherwise say what is missing.</li>
 * </ul>
 * Any other item in hand does its own thing. Each use works on either half and acts on the master. It is also a crew
 * station ({@link CannonStation}).
 */
public class CannonBlock extends Block implements EntityBlock, StationBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<CannonLoad> LOAD = EnumProperty.create("load", CannonLoad.class);
    public static final EnumProperty<CannonPart> PART = EnumProperty.create("part", CannonPart.class);

    /** Collision and outline shapes by facing (2D value index), built in the north frame: carriage plus barrel. */
    private static final VoxelShape[] FRONT_SHAPES = new VoxelShape[4];
    private static final VoxelShape[] REAR_SHAPES = new VoxelShape[4];

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            // master: cheeks and bed of the carriage (12 px), the barrel around the pivot at 14 px, up to 20 px
            FRONT_SHAPES[d.get2DDataValue()] = Shapes.or(turned(2, 0, 1, 14, 12, 16, d), turned(4.5, 12, 0, 11.5, 20, 16, d));
            // rear: the back of the carriage and the breech
            REAR_SHAPES[d.get2DDataValue()] = Shapes.or(turned(2, 0, 0, 14, 12, 14, d), turned(5, 12, 0, 11, 18, 8, d));
        }
    }

    private static VoxelShape turned(double x1, double y1, double z1, double x2, double y2, double z2, Direction facing) {
        double[] b = CannonRules.turnBox(x1, y1, z1, x2, y2, z2, facing);
        return box(b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    public CannonBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LOAD, CannonLoad.EMPTY)
                .setValue(PART, CannonPart.FRONT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LOAD, PART);
    }

    /** The master block of the cannon half {@code state} at {@code pos}. */
    public static BlockPos masterPos(BlockState state, BlockPos pos) {
        return CannonRules.masterOf(pos, state.getValue(FACING), state.getValue(PART));
    }

    /** Whether {@code state} is the master (front) half of a cannon. */
    public static boolean isMaster(BlockState state) {
        return state.getBlock() instanceof CannonBlock && state.getValue(PART).isMaster();
    }

    /** Whether a cannon half may stand on the block below {@code pos}: its top must carry the centre (a deck, not air). */
    static boolean standsOn(LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP, SupportType.CENTER);
    }

    /**
     * The clicked block becomes the master with the muzzle the way the placing player looks; the rear goes behind it
     * (towards the player). Refused (null) when the rear is not free, either half has nothing to stand on, or the rear
     * is outside the world border. On a ship the clicked position is a plot position (sable-notes §9.0e), so the
     * checks look at the plot.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockPos rear = CannonRules.rearOf(pos, facing);
        if (!level.getBlockState(rear).canBeReplaced(context) || !level.getWorldBorder().isWithinBounds(rear)
                || !standsOn(level, pos) || !standsOn(level, rear)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, facing).setValue(PART, CannonPart.FRONT);
    }

    /** Places the rear half behind the master (as a bed places its head). */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && state.getValue(PART).isMaster()) {
            BlockPos rear = CannonRules.rearOf(pos, state.getValue(FACING));
            level.setBlock(rear, rearState(state), Block.UPDATE_ALL);
            level.blockUpdated(pos, Blocks.AIR);
            state.updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
        }
    }

    /** The rear half that belongs to the master state {@code master}. */
    public BlockState rearState(BlockState master) {
        return defaultBlockState().setValue(FACING, master.getValue(FACING)).setValue(PART, CannonPart.REAR);
    }

    /** A half whose other half is gone (broken, blown up, replaced) goes too, as a bed does. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        CannonPart part = state.getValue(PART);
        if (direction == CannonRules.towardsOtherHalf(state.getValue(FACING), part)) {
            boolean partner = neighbor.is(this) && neighbor.getValue(PART) != part
                    && neighbor.getValue(FACING) == state.getValue(FACING);
            return partner ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    /**
     * In creative, breaking the rear removes the master without a drop (vanilla's bed does the same); otherwise the
     * other half goes through {@link #updateShape}, and only the master's loot drops the cannon.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && !state.getValue(PART).isMaster()) {
            BlockPos master = masterPos(state, pos);
            BlockState other = level.getBlockState(master);
            if (other.is(this) && other.getValue(PART).isMaster()) {
                level.setBlock(master, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, master, Block.getId(other));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int i = state.getValue(FACING).get2DDataValue();
        return state.getValue(PART).isMaster() ? FRONT_SHAPES[i] : REAR_SHAPES[i];
    }

    /** Only the master has the block entity (elevation, reload). */
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART).isMaster() ? new CannonBlockEntity(pos, state) : null;
    }

    @Override
    public StationKind<?> stationKind() {
        return CannonStation.INSTANCE;
    }

    /** Both halves are one station, at the master: assigning crew at the rear mans the same gun. */
    @Override
    public BlockPos stationPos(BlockState state, BlockPos pos) {
        return masterPos(state, pos);
    }

    /** The crew's seat goes beside the master or the rear, never on the rear half. */
    @Override
    public List<BlockPos> footprint(BlockState state, BlockPos stationPos) {
        return List.of(stationPos, CannonRules.rearOf(stationPos, state.getValue(FACING)));
    }

    static boolean isPowder(ItemStack stack) {
        return stack.is(Items.GUNPOWDER);
    }

    static boolean isBall(ItemStack stack) {
        return stack.is(CombatContent.CANNONBALL.get());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; // empty hand: fire or aim (useWithoutItem)
        }
        if (stack.getItem() instanceof StationBlock.Tool || !isPowder(stack) && !isBall(stack)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION; // the item's own use; never fires the cannon
        }
        if (level instanceof ServerLevel server) {
            CannonService.Use use = CannonService.load(server, pos, player, stack);
            player.displayClientMessage(use.message(), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            CannonService.Use use;
            if (player.isSecondaryUseActive()) {
                // the hit is in plot space on a ship, so the click height works unchanged there (sable-notes §9.0e)
                boolean up = hit.getLocation().y - pos.getY() >= 0.5;
                use = CannonService.aim(server, pos, up);
            } else {
                use = CannonService.fire(server, pos, player);
            }
            player.displayClientMessage(use.message(), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * A cannon broken with drops (by a player in survival, a ball, an explosion, the other half going) gives back its
     * powder and ball next to the cannon item (Q2). Only the master holds the load. A creative break drops nothing: the
     * master then goes without drops ({@link #playerWillDestroy}, or the creative player's own break).
     */
    @Override
    protected void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos, ItemStack tool, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, tool, dropExperience);
        if (!state.getValue(PART).isMaster()) return;
        CannonLoad load = state.getValue(LOAD);
        if (load != CannonLoad.EMPTY) popResource(level, pos, new ItemStack(Items.GUNPOWDER));
        if (load == CannonLoad.LOADED) popResource(level, pos, new ItemStack(CombatContent.CANNONBALL.get()));
    }

    /** Breaking the cannon frees the station and removes its seat (a station taken at the rear, if any, too). */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            Stations.onStationRemoved(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
