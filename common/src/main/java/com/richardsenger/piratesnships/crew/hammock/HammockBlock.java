package com.richardsenger.piratesnships.crew.hammock;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The hammock (HM1, docs/design.md §7.1): the crew's bunk, a two-block canvas bed that hangs between two supports at
 * the same height ({@link HammockRules}). Two halves like a bed: the {@link BedPart#FOOT} at the clicked block, the
 * {@link BedPart#HEAD} one block further in {@link #FACING}. Each half has its own model ({@code block/hammock_foot},
 * {@code block/hammock_head}, rotated by the block state), so the Blockbench model (ART1d) replaces the placeholders
 * without code changes.
 * <p>
 * Losing either half or either support brings the whole hammock down; only the foot has loot, so that drops one
 * hammock ({@link #updateShape} returns air, which vanilla destroys with drops). Players cannot sleep in it: it is
 * crew-only (vanilla's bed sleeping is tied to {@code BedBlock} and world positions, not to a block in a ship's plot).
 */
public class HammockBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<HammockBlock> CODEC = simpleCodec(HammockBlock::new);
    public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;

    /** Top of the canvas in pixels: the sleeper lies on it. */
    public static final int CANVAS_TOP = 7;
    /** Bottom of the canvas in pixels. */
    public static final int CANVAS_BOTTOM = 5;
    /** Inset of the canvas from the block's long sides, in pixels. */
    public static final int CANVAS_INSET = 2;

    private static final VoxelShape SHAPE_Z = Block.box(CANVAS_INSET, CANVAS_BOTTOM, 0, 16 - CANVAS_INSET, CANVAS_TOP, 16);
    private static final VoxelShape SHAPE_X = Block.box(0, CANVAS_BOTTOM, CANVAS_INSET, 16, CANVAS_TOP, 16 - CANVAS_INSET);

    public static final String KEY_CREW_ONLY = "message.pirates_n_ships.hammock.crew_only";

    public HammockBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, BedPart.FOOT));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    // ------------------------------------------------------------------ supports

    /** What kind of support the block at {@code pos} is for a hammock end touching its face {@code face}. */
    public static HammockRules.Support supportAt(BlockGetter level, BlockPos pos, Direction face) {
        BlockState state = level.getBlockState(pos);
        if (state.is(HammockTags.SUPPORTS)) {
            return HammockRules.Support.POST;
        }
        return state.isFaceSturdy(level, pos, face) ? HammockRules.Support.SOLID : HammockRules.Support.NONE;
    }

    /** Whether a hammock with its foot at {@code foot} along {@code facing} can hang there (supports only). */
    public static boolean canHang(BlockGetter level, BlockPos foot, Direction facing) {
        return HammockRules.canHang(foot, facing, (p, face) -> supportAt(level, p, face));
    }

    // ------------------------------------------------------------------ placing and breaking

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getHorizontalDirection();
        BlockPos foot = ctx.getClickedPos();
        BlockPos head = HammockRules.head(foot, facing);
        Level level = ctx.getLevel();
        if (!level.getBlockState(head).canBeReplaced(ctx) || !level.getWorldBorder().isWithinBounds(head)
                || !canHang(level, foot, facing)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, facing).setValue(PART, BedPart.FOOT);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            level.setBlock(HammockRules.head(pos, state.getValue(FACING)), state.setValue(PART, BedPart.HEAD), Block.UPDATE_ALL);
            level.blockUpdated(pos, Blocks.AIR);
            state.updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        BedPart part = state.getValue(PART);
        Direction facing = state.getValue(FACING);
        if (dir == HammockRules.towardOther(part, facing)) {
            boolean other = neighbor.is(this) && neighbor.getValue(PART) != part && neighbor.getValue(FACING) == facing;
            return other ? state : Blocks.AIR.defaultBlockState();
        }
        if (dir == HammockRules.outward(part, facing)) {
            return supportAt(level, neighborPos, dir.getOpposite()).holds() ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // creative: take the other half along without drops (vanilla's bed does the same)
        if (!level.isClientSide && player.isCreative()) {
            BlockPos other = HammockRules.otherHalf(pos, state.getValue(PART), state.getValue(FACING));
            BlockState os = level.getBlockState(other);
            if (os.is(this) && os.getValue(PART) != state.getValue(PART)) {
                level.setBlock(other, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, other, Block.getId(os));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // ------------------------------------------------------------------ use, shape

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable(KEY_CREW_ONLY), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? SHAPE_X : SHAPE_Z;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
