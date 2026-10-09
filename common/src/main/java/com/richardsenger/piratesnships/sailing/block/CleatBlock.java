package com.richardsenger.piratesnships.sailing.block;

import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.item.RopeItem;
import com.richardsenger.piratesnships.sailing.rope.RopeAnchor;
import com.richardsenger.piratesnships.sailing.rope.RopeAnchorUse;
import com.richardsenger.piratesnships.sailing.rope.RopeLines;
import com.richardsenger.piratesnships.sailing.sail.SailBanner;
import com.richardsenger.piratesnships.sailing.sail.SailDecorations;
import com.richardsenger.piratesnships.sailing.sail.TriangularSailContent;
import com.richardsenger.piratesnships.sailing.sail.TriangularSails;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
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
 * <p>A cleat is a general rope anchor ({@link RopeAnchor}, RP1): a rope between it and another cleat or a mooring ring
 * that makes no sail is a decorative rope line, a flying grappling hook latches onto its horn, and the grappling
 * rope's near end can be tied off on it.
 *
 * <p>{@link #TRIM} is the sail's trim; only the head's counts (as with the yards). Every cleat has a
 * {@link CleatBlockEntity} that holds its end of the stay and, on the head, the cloth for the renderer. Besides
 * sturdy faces, a cleat also holds on a mast block ({@link SailingBlocks#MASTS}), so it can be fixed to a fence mast.
 *
 * <p>Model: a hand-made horn cleat (art/models/cleat.bbmodel, design.md §4.8): an iron horn bar on two legs over a
 * dark wooden pad, modelled on the floor with its horns along {@link #FACING} and turned for the wall and the ceiling
 * by the block state. {@link #TRIM} does not change the model (the cloth is drawn by the stay renderer).
 */
public class CleatBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock, RopeAnchor {

    public static final EnumProperty<SailTrim> TRIM = EnumProperty.create("trim", SailTrim.class);
    public static final MapCodec<CleatBlock> CODEC = simpleCodec(CleatBlock::new);

    public static final String KEY_NO_SAIL = "message." + Constants.MOD_ID + ".cleat.no_sail";

    /** Distance of the horn bar's middle (where ropes tie) from the block center toward the support [blocks]. */
    public static final double HORN_INSET = 0.27;

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
    public Kind anchorKind() {
        return Kind.CLEAT;
    }

    @Override
    public double anchorInset() {
        return HORN_INSET;
    }

    /** A rope in hand goes to the rope item (it rigs stays and lines), not to the trim. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof RopeItem) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        // SAIL2: a dye on the head cleat of a triangular sail dyes its cloth; a banner is refused (stay sails take dye only)
        boolean heads = level.getBlockEntity(pos) instanceof CleatBlockEntity be && be.cloth() != null;
        if (heads && stack.getItem() instanceof DyeItem dye && SailingConfig.SAIL_DYEING.get()) {
            if (level instanceof ServerLevel) {
                SailDecorations.Outcome o = SailDecorations.dyeStay(level, pos, dye.getDyeColor());
                if (o.consumes()) {
                    level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1f, 1f);
                    stack.consume(1, player);
                }
                player.displayClientMessage(SailDecorations.message(o, dye.getDyeColor()), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (heads && SailBanner.isBanner(stack) && SailingConfig.SAIL_BANNERS.get()) {
            if (level instanceof ServerLevel) {
                player.displayClientMessage(SailDecorations.message(SailDecorations.Outcome.STAY_DYE_ONLY, null), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    /**
     * With a grappling hook out, using the cleat ties the hook's rope off here, as on a mooring ring (RP1,
     * {@link RopeAnchorUse}); otherwise it cycles the trim of the sail it heads.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel) {
            InteractionResult tie = RopeAnchorUse.tieOff(serverLevel, player, pos);
            if (tie != InteractionResult.PASS) {
                return tie;
            }
        } else if (RopeAnchorUse.willTieOff(level, player, pos)) {
            return InteractionResult.SUCCESS;
        }
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

    /** A player breaking a cleat gets back one rope per stay or rope line on it (not in creative mode). */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel && !player.isCreative()) {
            RopeLines.dropRopes(level, pos, new ItemStack(TriangularSailContent.ROPE.get()));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Breaking a cleat takes its ropes down (the other ends forget them) and updates the sails around it. A cleat moved
     * by Sable's assembly ({@code movedByPiston} is true there, see {@code SubLevelAssemblyHelper#moveBlocks}) keeps its
     * ropes: its block entity data travels with it, and the offsets it stores are relative.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        List<BlockPos> partners = List.of();
        boolean gone = !newState.is(this) && !movedByPiston && level instanceof ServerLevel;
        if (gone) {
            partners = RopeLines.partners(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (gone) {
            RopeLines.onAnchorRemoved((ServerLevel) level, pos, partners);
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
