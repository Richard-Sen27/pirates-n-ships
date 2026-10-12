package com.richardsenger.piratesnships.sailing.block;

import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.block.Waterlogging;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.sail.BoomRules;
import com.richardsenger.piratesnships.sailing.sail.TriangularSails;
import com.richardsenger.piratesnships.ship.decor.wood.ShipWood;
import com.richardsenger.piratesnships.ship.decor.wood.WoodBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The boom of a triangular sail (SAIL5, docs/design.md §5.2 "The boom and the triangular-sail scene"): the gooseneck
 * fitting on the side of a mast, in the column of the sail's head cleat (the yard rule's column), where the clew cleat
 * would go. A stay whose head has a boom straight below it (with only air, mast or yard blocks between) is a
 * <b>boomed sail</b> ({@link com.richardsenger.piratesnships.sailing.sail.StayLinker}): its foot runs along the boom,
 * which pivots here and reaches out as far as the tack lies horizontally.
 *
 * <p>{@link #FACING} points away from the supporting block (the mast); the boom needs a mast block or a sturdy face
 * behind it, like a wall cleat. {@link #SHEET} is the sheet: 9 steps of 15° from −60° to +60°, 4 being centred on the
 * tack, positive clockwise seen from above ({@link BoomRules}). A block state, not a block entity value, so that the
 * runtime hears every change through the block-change hook and Ponder scenes set it like any state.
 *
 * <p>Using the boom with an empty hand sheets it one step toward the side clicked (the side of the boom's centre line the
 * hit lies on, else the player's side); sneak-use centres it. On a ship with {@code sailing.boom_auto_trim} the crew
 * leaves a boom sheeted by hand alone for {@code sailing.boom_hand_hold_ticks}.
 *
 * <p>Model: hand-made in Blockbench ({@code art/models/boom.bbmodel}): the gooseneck (an iron strap on the mast face,
 * the jaw and the pivot pin) built for {@code facing=north} (the mast to the south); the arm is drawn by
 * {@code sailing.client.BoomRenderer} from the block entity (part {@code block/boom_arm} of the boom's wood).
 */
public class BoomBlock extends Block implements EntityBlock, SimpleWaterloggedBlock, WoodBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty SHEET = IntegerProperty.create("sheet", 0, BoomRules.STEPS - 1);
    public static final MapCodec<BoomBlock> CODEC = WoodBlock.codec(BoomBlock::new);

    static final String KEY = "message." + Constants.MOD_ID + ".boom.";
    /** Action bar after a hand on the boom: the sheet angle, a number the eye cannot read off the boom (MSG1). */
    public static final String KEY_SHEETED = KEY + "sheeted";
    /** Refusal: {@code sailing.booms} is off. */
    public static final String KEY_OFF = KEY + "off";

    // the gooseneck against the support (south for facing=north): 8 x 8 px, 7 px deep
    private static final VoxelShape NORTH = Block.box(4, 4, 9, 12, 12, 16);
    private static final VoxelShape SOUTH = Block.box(4, 4, 0, 12, 12, 7);
    private static final VoxelShape EAST = Block.box(0, 4, 4, 7, 12, 12);
    private static final VoxelShape WEST = Block.box(9, 4, 4, 16, 12, 12);

    private final ShipWood wood;

    public BoomBlock(ShipWood wood, Properties properties) {
        super(properties);
        this.wood = wood;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SHEET, BoomRules.CENTRE)
                .setValue(Waterlogging.WATERLOGGED, false));
    }

    @Override
    public ShipWood wood() {
        return wood;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHEET, Waterlogging.WATERLOGGED);
    }

    /** The sheet angle of {@code state} [degrees, clockwise seen from above]. */
    public static double sheetDegrees(BlockState state) {
        return BoomRules.angle(state.getValue(SHEET));
    }

    /**
     * Against the clicked side face, pointing away from it; on a top or bottom face the boom points the way the player
     * looks, with its back to a support behind it. Null when there is nothing to hold it.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : context.getHorizontalDirection();
        BlockState state = defaultBlockState().setValue(FACING, facing);
        if (!state.canSurvive(context.getLevel(), context.getClickedPos())) {
            return null;
        }
        return Waterlogging.placed(state, context);
    }

    /** A mast block ({@link SailingBlocks#MASTS}) or a sturdy face behind it, as a wall cleat. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction toSupport = state.getValue(FACING).getOpposite();
        BlockPos supportPos = pos.relative(toSupport);
        BlockState support = level.getBlockState(supportPos);
        return support.is(SailingBlocks.MASTS) || support.isFaceSturdy(level, supportPos, toSupport.getOpposite());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        Waterlogging.tickFluid(state, level, pos);
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return Waterlogging.leftBehind(state); // water stays where a waterlogged boom falls off
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Waterlogging.fluid(state, super.getFluidState(state));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> NORTH;
        };
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /** A mirror turns the facing and swaps the sheet's side (clockwise becomes counter-clockwise). */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) {
            return state;
        }
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)))
                .setValue(SHEET, BoomRules.mirror(state.getValue(SHEET)));
    }

    /** A hand on the boom sheets it one step toward the side clicked; sneaking centres it. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel) {
            if (!SailingConfig.BOOMS.get()) {
                player.displayClientMessage(Component.translatable(KEY_OFF), true);
                return InteractionResult.SUCCESS;
            }
            int target;
            if (player.isSecondaryUseActive()) {
                target = BoomRules.CENTRE;
            } else {
                Vec3 at = hit.getLocation().subtract(Vec3.atCenterOf(pos));
                int side = clickSide(serverLevel, pos, state, at.x, at.z);
                if (side == 0) {
                    Vec3 eye = player.getEyePosition().subtract(Vec3.atCenterOf(pos));
                    side = clickSide(serverLevel, pos, state, eye.x, eye.z);
                }
                target = state.getValue(SHEET) + side;
            }
            int step = sheetByHand(serverLevel, pos, target);
            if (step >= 0) {
                double deg = BoomRules.angle(step);
                player.displayClientMessage(Component.translatable(KEY_SHEETED,
                        String.format(java.util.Locale.ROOT, deg == 0.0 ? "%.0f" : "%+.0f", deg)), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * +1, −1 or 0: which way a push at {@code (x, z)} (relative to the pivot) turns the boom of {@code state}: the side of
     * its centre line the push is on, so a side of the gooseneck always sheets the same way.
     */
    private static int clickSide(ServerLevel level, BlockPos pos, BlockState state, double x, double z) {
        double[] arm = arm(level, pos, state);
        return BoomRules.clickSide(arm[0], arm[1], x, z);
    }

    /** The boom's centred direction {x, z}: toward its sail's tack, or along {@link #FACING} without a sail. */
    public static double[] arm(BlockGetter level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof BoomBlockEntity be && be.arm() != null) {
            int[] a = be.arm();
            return new double[] {a[0], a[1]};
        }
        Direction f = state.getValue(FACING);
        return new double[] {f.getStepX(), f.getStepZ()};
    }

    /**
     * Sheets the boom at {@code pos} to {@code step} (clamped) by hand: sets the state, plays the sound, and holds it
     * against the crew's auto-trim for {@code sailing.boom_hand_hold_ticks}. Returns the new step, or −1 when there is
     * no boom. Public for GameTests.
     */
    public static int sheetByHand(ServerLevel level, BlockPos pos, int step) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BoomBlock)) {
            return -1;
        }
        int target = BoomRules.clamp(step);
        if (level.getBlockEntity(pos) instanceof BoomBlockEntity be) {
            be.holdByHand(level.getGameTime() + SailingConfig.BOOM_HAND_HOLD_TICKS.get());
        }
        if (target != state.getValue(SHEET)) {
            level.setBlock(pos, state.setValue(SHEET, target), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.LEASH_KNOT_PLACE, SoundSource.BLOCKS, 0.6f, 0.8f);
        }
        return target;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this) && level instanceof ServerLevel serverLevel) {
            TriangularSails.refreshAround(serverLevel, pos); // it may be the clew of a stay's head above
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!newState.is(this) && level instanceof ServerLevel serverLevel) {
            TriangularSails.refreshAround(serverLevel, pos);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BoomBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != SailingBlocks.BOOM_BLOCK_ENTITY.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<BoomBlockEntity>) BoomBlockEntity::serverTick;
    }
}
