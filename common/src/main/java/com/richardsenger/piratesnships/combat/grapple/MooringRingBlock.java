package com.richardsenger.piratesnships.combat.grapple;

import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.Constants;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The mooring ring (GR1, docs/design.md §8.3): a small iron ring on a plate, mounted on any sturdy face like a button
 * (on a deck, on a wall, under a beam), waterloggable. A ship block like any other (light in Sable's physics tags).
 * <ul>
 *   <li><b>A sure target:</b> a flying hook passing within {@code grapple.ring_catch_radius} of a ring on another ship
 *       latches onto it ({@link GrappleService#findRing}), and a hook on a ring snaps only at
 *       {@code ring_hold_multiplier} times the rope's length ({@link GrappleRules#breakLength}).</li>
 *   <li><b>A tie-off:</b> using a ring while one of your hooks is out ties the rope's near end to it
 *       ({@link GrappleService#tieOff}): the rope then runs from the ring to the hook, the ring's ship hauls instead of
 *       yours, and you can let go. The release action, or breaking either ring, lets the hook go.</li>
 * </ul>
 * With {@code grapple.rings_enabled} off the ring is decoration.
 */
public class MooringRingBlock extends FaceAttachedHorizontalDirectionalBlock implements SimpleWaterloggedBlock {

    public static final MapCodec<MooringRingBlock> CODEC = simpleCodec(MooringRingBlock::new);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final String TOOLTIP_KEY = "block." + Constants.MOD_ID + ".mooring_ring.tooltip";

    /** Distance of the ring's middle from the block center toward its support [blocks]. */
    private static final double RING_INSET = 0.35;

    private static final VoxelShape FLOOR = box(4, 0, 4, 12, 3, 12);
    private static final VoxelShape CEILING = box(4, 13, 4, 12, 16, 12);
    private static final VoxelShape WALL_NORTH = box(4, 4, 13, 12, 12, 16);
    private static final VoxelShape WALL_SOUTH = box(4, 4, 0, 12, 12, 3);
    private static final VoxelShape WALL_EAST = box(0, 4, 4, 3, 12, 12);
    private static final VoxelShape WALL_WEST = box(13, 4, 4, 16, 12, 12);

    public MooringRingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FACE, AttachFace.FLOOR)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE, WATERLOGGED);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) return null;
        return state.setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level,
                                     BlockPos pos, BlockPos facingPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, facing, facingState, level, pos, facingPos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> FLOOR;
            case CEILING -> CEILING;
            case WALL -> switch (state.getValue(FACING)) {
                case SOUTH -> WALL_SOUTH;
                case EAST -> WALL_EAST;
                case WEST -> WALL_WEST;
                default -> WALL_NORTH;
            };
        };
    }

    /**
     * The middle of the ring, where the rope ties and hooks are caught: {@code pos}'s center moved toward the block the
     * ring is mounted on. In the coordinates of {@code pos} (plot coordinates for a ring on a ship).
     */
    public static Vec3 ringCenter(BlockState state, BlockPos pos) {
        Direction out = getConnectedDirection(state);
        return Vec3.atCenterOf(pos).subtract(out.getStepX() * RING_INSET, out.getStepY() * RING_INSET, out.getStepZ() * RING_INSET);
    }

    /** {@link #ringCenter} of the ring at {@code pos}, or the block center when there is none. */
    public static Vec3 ringCenter(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof MooringRingBlock ? ringCenter(state, pos) : Vec3.atCenterOf(pos);
    }

    public static boolean isRing(BlockGetter level, @Nullable BlockPos pos) {
        return pos != null && level.getBlockState(pos).getBlock() instanceof MooringRingBlock;
    }

    /**
     * Using the ring while a hook of yours is out ties the rope to it. With an empty hand vanilla calls this for a
     * sneaking use too; the client does not send its release request when the use aims at a ring
     * ({@code client.GrappleClient}). Without a hook out the use passes on (an item in hand is used as usual).
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!GrappleConfig.ENABLED.get() || !GrappleConfig.RINGS_ENABLED.get()) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            return GrappleService.tieOff(server, player, pos);
        }
        // the client predicts from the hooks it sees, so a use with a hook in hand does not also throw it
        return GrappleService.hasHookOutClientSide(level, player) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(TOOLTIP_KEY).withStyle(ChatFormatting.GRAY));
    }
}
