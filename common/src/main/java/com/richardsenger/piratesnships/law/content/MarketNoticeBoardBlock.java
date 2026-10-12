package com.richardsenger.piratesnships.law.content;

import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.core.block.PartBlockEntity;
import com.richardsenger.piratesnships.core.block.Waterlogging;
import com.richardsenger.piratesnships.law.LawConfig;
import com.richardsenger.piratesnships.law.net.NoticeBoardBackend;
import com.richardsenger.piratesnships.ship.decor.wood.ShipWood;
import com.richardsenger.piratesnships.ship.decor.wood.WoodBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The market notice board (NB2, docs/design.md §13.2 and "Village life"): a 2 wide × 2 high board on two posts for a
 * village's market square, in every wood (legacy id {@code market_notice_board} for dark oak). Four parts
 * ({@link #PART}, {@link MarketBoardPart}): placing needs the 2×2 in front of the placer free (the clicked cell is the
 * bottom-left, the board runs to the placer's right and up), breaking any part removes the others and drops the board
 * once (only the anchor's loot drops it; in creative nothing drops). The anchor holds the
 * {@link MarketNoticeBoardBlockEntity}, which keeps {@link #LOAD} on all four parts in step with the postings
 * ({@code law.notice_board.*}); the other parts carry an empty {@link PartBlockEntity} (FIX3: worldgen needs a block
 * entity on every state). Using any part opens the small board's screen ({@link NoticeBoardBackend#open}) for the
 * anchor. {@link #FACING} is the notices' side. Models: {@code tools/gen_market_notice_board_models.py}.
 */
public class MarketNoticeBoardBlock extends HorizontalDirectionalBlock implements EntityBlock, SimpleWaterloggedBlock, WoodBlock {

    public static final MapCodec<MarketNoticeBoardBlock> CODEC = WoodBlock.codec(MarketNoticeBoardBlock::new);
    public static final EnumProperty<MarketBoardPart> PART = EnumProperty.create("part", MarketBoardPart.class);
    public static final EnumProperty<NoticeBoardLoad> LOAD = EnumProperty.create("load", NoticeBoardLoad.class);

    /** The board's plane with the posts (z 6..10 facing north); the roof's overhang stays outside. */
    private static final VoxelShape SHAPE_NS = Block.box(0, 0, 6, 16, 16, 10);
    private static final VoxelShape SHAPE_EW = Block.box(6, 0, 0, 10, 16, 16);

    private final ShipWood wood;

    public MarketNoticeBoardBlock(ShipWood wood, Properties properties) {
        super(properties);
        this.wood = wood;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, MarketBoardPart.BOTTOM_LEFT)
                .setValue(LOAD, NoticeBoardLoad.EMPTY).setValue(Waterlogging.WATERLOGGED, false));
    }

    @Override
    public ShipWood wood() {
        return wood;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, LOAD, Waterlogging.WATERLOGGED);
    }

    // --- placing and breaking -----------------------------------------------------------------------------------

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        Level level = context.getLevel();
        BlockPos anchor = context.getClickedPos();
        List<BlockPos> cells = MarketBoardPart.cells(anchor, facing);
        for (int i = 1; i < cells.size(); i++) {
            BlockPos c = cells.get(i);
            if (c.getY() >= level.getMaxBuildHeight() || !level.getWorldBorder().isWithinBounds(c)
                    || !level.getBlockState(c).canBeReplaced(context)) {
                return null;
            }
        }
        return Waterlogging.placed(defaultBlockState().setValue(FACING, facing), context);
    }

    /** Puts the three other parts beside and above the anchor just placed. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !state.getValue(PART).isAnchor()) return;
        placeOthers(level, pos, state);
    }

    /** The other three parts of the board whose anchor {@code anchorState} stands at {@code anchor}. */
    public static void placeOthers(Level level, BlockPos anchor, BlockState anchorState) {
        Direction facing = anchorState.getValue(FACING);
        for (MarketBoardPart p : MarketBoardPart.values()) {
            if (p.isAnchor()) continue;
            BlockPos c = MarketBoardPart.BOTTOM_LEFT.posOf(p, anchor, facing);
            level.setBlock(c, Waterlogging.at(anchorState.setValue(PART, p), level, c), Block.UPDATE_ALL);
        }
    }

    /** A part whose partner beside or above/below is gone goes too (the anchor then drops the board, see the loot). */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        Waterlogging.tickFluid(state, level, pos);
        Direction facing = state.getValue(FACING);
        MarketBoardPart expected = state.getValue(PART).partnerOn(direction, facing);
        if (expected != null) {
            boolean partner = neighbor.is(this) && neighbor.getValue(PART) == expected && neighbor.getValue(FACING) == facing;
            return partner ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    /** In creative, breaking another part removes the anchor first without a drop (like the cannon and vanilla's bed). */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && !state.getValue(PART).isAnchor()) {
            BlockPos anchor = state.getValue(PART).anchor(pos, state.getValue(FACING));
            BlockState other = level.getBlockState(anchor);
            if (other.is(this) && other.getValue(PART).isAnchor()) {
                level.setBlock(anchor, Waterlogging.leftBehind(other), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, anchor, Block.getId(other));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }

    // --- use ----------------------------------------------------------------------------------------------------

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!LawConfig.NOTICE_BOARDS.get()) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        BlockPos anchor = state.getValue(PART).anchor(pos, state.getValue(FACING));
        if (!NoticeBoardBackend.isBoard(level.getBlockState(anchor))) return InteractionResult.PASS;
        NoticeBoardBackend.open(sp, anchor);
        return InteractionResult.CONSUME;
    }

    // --- load ---------------------------------------------------------------------------------------------------

    /**
     * Sets {@code load} on every part of the board anchored at {@code anchor} that does not show it yet; true if any
     * part changed.
     */
    public static boolean applyLoad(Level level, BlockPos anchor, Direction facing, NoticeBoardLoad load) {
        boolean changed = false;
        for (BlockPos c : MarketBoardPart.cells(anchor, facing)) {
            BlockState s = level.getBlockState(c);
            if (!(s.getBlock() instanceof MarketNoticeBoardBlock) || s.getValue(FACING) != facing || s.getValue(LOAD) == load) continue;
            level.setBlock(c, s.setValue(LOAD, load), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            changed = true;
        }
        return changed;
    }

    // --- block entity -------------------------------------------------------------------------------------------

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART).isAnchor() ? new MarketNoticeBoardBlockEntity(pos, state)
                : new PartBlockEntity(LawContent.MARKET_NOTICE_BOARD_PART_ENTITY.get(), pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != LawContent.MARKET_NOTICE_BOARD_ENTITY.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<MarketNoticeBoardBlockEntity>) MarketNoticeBoardBlockEntity::serverTick;
    }

    // --- shape, fluid, rotation ---------------------------------------------------------------------------------

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Waterlogging.fluid(state, super.getFluidState(state));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /** A mirror swaps the viewer's left and right, so every part changes column. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) return state;
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING))).setValue(PART, state.getValue(PART).sidewaysPartner());
    }
}
