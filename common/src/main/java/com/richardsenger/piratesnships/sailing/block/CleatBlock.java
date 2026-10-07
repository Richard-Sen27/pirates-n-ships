package com.richardsenger.piratesnships.sailing.block;

import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.sail.TriangularSailContent;
import com.richardsenger.piratesnships.sailing.sail.TriangularSails;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A cleat (docs/design.md §5.2, rule F5b): a small fitting on a floor, a wall or a ceiling (placed like a lever:
 * {@link #FACE} plus a horizontal {@link #FACING}). A rope used on two cleats rigs a stay between them
 * ({@link com.richardsenger.piratesnships.sailing.item.RopeItem}); the higher one is the stay's head, and with a third
 * cleat straight below the head they carry a triangular sail ({@link com.richardsenger.piratesnships.sailing.sail.StayLinker}).
 *
 * <p>{@link #TRIM} is the sail's trim; only the head's counts (as with the yards). Every cleat has a
 * {@link CleatBlockEntity} that holds its end of the stay and, on the head, the cloth for the renderer. Besides
 * sturdy faces, a cleat also holds on a mast block ({@link SailingBlocks#MASTS}), so it can be fixed to a fence mast.
 */
public class CleatBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock {

    public static final EnumProperty<SailTrim> TRIM = EnumProperty.create("trim", SailTrim.class);
    public static final MapCodec<CleatBlock> CODEC = simpleCodec(CleatBlock::new);

    public static final String KEY_NO_SAIL = "message." + Constants.MOD_ID + ".cleat.no_sail";

    // horns along the facing on a floor or ceiling, upright on a wall (the wall shape sits against the supporting face)
    private static final VoxelShape FLOOR_NS = Block.box(5, 0, 2, 11, 5, 14);
    private static final VoxelShape FLOOR_EW = Block.box(2, 0, 5, 14, 5, 11);
    private static final VoxelShape CEILING_NS = Block.box(5, 11, 2, 11, 16, 14);
    private static final VoxelShape CEILING_EW = Block.box(2, 11, 5, 14, 16, 11);
    private static final VoxelShape WALL_NORTH = Block.box(5, 2, 11, 11, 14, 16);
    private static final VoxelShape WALL_SOUTH = Block.box(5, 2, 0, 11, 14, 5);
    private static final VoxelShape WALL_EAST = Block.box(0, 2, 5, 5, 14, 11);
    private static final VoxelShape WALL_WEST = Block.box(11, 2, 5, 16, 14, 11);

    public CleatBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACE, AttachFace.WALL).setValue(FACING, Direction.NORTH)
                .setValue(TRIM, SailTrim.FURLED));
    }

    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, TRIM);
    }

    /** Sturdy faces as vanilla's face-attached blocks, and mast blocks (a fence mast has no sturdy side). */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction toSupport = getConnectedDirection(state).getOpposite();
        BlockPos supportPos = pos.relative(toSupport);
        BlockState support = level.getBlockState(supportPos);
        return support.isFaceSturdy(level, supportPos, toSupport.getOpposite()) || support.is(SailingBlocks.MASTS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction f = state.getValue(FACING);
        boolean ns = f.getAxis() == Direction.Axis.Z;
        return switch (state.getValue(FACE)) {
            case FLOOR -> ns ? FLOOR_NS : FLOOR_EW;
            case CEILING -> ns ? CEILING_NS : CEILING_EW;
            case WALL -> switch (f) {
                case SOUTH -> WALL_SOUTH;
                case EAST -> WALL_EAST;
                case WEST -> WALL_WEST;
                default -> WALL_NORTH;
            };
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!SailingConfig.SAIL_BLOCK_TRIM.get()) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel) {
            SailTrim next = TriangularSails.cycle(serverLevel, pos);
            player.displayClientMessage(next == null ? Component.translatable(KEY_NO_SAIL)
                    : Component.translatable(SailWinchBlock.KEY_SAIL_SET, Component.translatable(SailWinchBlock.trimKey(next))), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this) && level instanceof ServerLevel serverLevel) {
            TriangularSails.refreshAround(serverLevel, pos);
        }
    }

    /** A player breaking one end of a stay gets the rope back (not in creative mode). */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel serverLevel && !player.isCreative() && TriangularSails.partner(serverLevel, pos) != null) {
            Block.popResource(level, pos, new ItemStack(TriangularSailContent.ROPE.get()));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Breaking a cleat takes its stay down (the other end forgets it) and updates the sails around it. A cleat moved by
     * Sable's assembly ({@code movedByPiston} is true there, see {@code SubLevelAssemblyHelper#moveBlocks}) keeps its
     * stay: its block entity data travels with it, and the offset it stores is relative.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        BlockPos partner = null;
        if (!newState.is(this) && !movedByPiston && level instanceof ServerLevel serverLevel) {
            partner = TriangularSails.partner(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!newState.is(this) && !movedByPiston && level instanceof ServerLevel serverLevel) {
            if (partner != null && serverLevel.getBlockEntity(partner) instanceof CleatBlockEntity other) {
                other.setStay(null);
            }
            TriangularSails.refreshAround(serverLevel, pos);
            if (partner != null) {
                TriangularSails.refresh(serverLevel, partner);
            }
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CleatBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != TriangularSailContent.CLEAT_BLOCK_ENTITY.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<CleatBlockEntity>) CleatBlockEntity::serverTick;
    }
}
