package com.richardsenger.piratesnships.sailing.block;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import com.richardsenger.piratesnships.core.block.Waterlogging;
import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.sail.SailBanner;
import com.richardsenger.piratesnships.sailing.sail.SailDecorations;
import com.richardsenger.piratesnships.sailing.sail.YardSails;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A yard block (docs/design.md §5.2, rule F5a): a horizontal beam along {@link #AXIS}. A straight row of them is a yard;
 * two yards on one mast column make a square sail ({@link com.richardsenger.piratesnships.sailing.sail.YardLinker}).
 *
 * <p>{@link #TRIM} is the sail's trim. It counts on the upper yard's middle block (the head); trim changes are written
 * to every block of the upper yard, and a block placed next to a yard takes over its trim, so the row agrees.
 *
 * <p>Placed, a yard runs across the player's view; placed against the end of a yard, it continues that yard. Every
 * yard block has a {@link YardBlockEntity}; the head's carries the cloth geometry for the renderer.
 *
 * <p>SAIL2: a dye used on the upper yard dyes the sail, a banner hangs on a large one, sneaking with an empty hand
 * takes the banner back ({@link SailDecorations}).
 *
 * <p>Model: hand-made in Blockbench ({@code art/models/yard.bbmodel}, design.md §4.8), a chamfered spar along x on the
 * 6 px collision beam, with a rope band between two iron hoops in the middle and an iron jackstay on top (up to 1.8 px
 * above the beam); {@code axis=z} turns it by 90°. The model never shows {@link #TRIM}: the cloth is drawn by
 * {@code YardClothRenderer}.
 */
public class YardBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final EnumProperty<SailTrim> TRIM = EnumProperty.create("trim", SailTrim.class);
    public static final MapCodec<YardBlock> CODEC = simpleCodec(YardBlock::new);

    public static final String KEY_NO_SAIL = "message." + Constants.MOD_ID + ".yard.no_sail";

    /** A 6 px beam through the block center. */
    private static final VoxelShape SHAPE_X = Block.box(0, 5, 5, 16, 11, 11);
    private static final VoxelShape SHAPE_Z = Block.box(5, 5, 0, 11, 11, 16);

    public YardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(TRIM, SailTrim.FURLED).setValue(Waterlogging.WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, TRIM, Waterlogging.WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction.Axis axis = context.getHorizontalDirection().getClockWise().getAxis();
        Direction face = context.getClickedFace();
        if (face.getAxis().isHorizontal()) {
            BlockState against = context.getLevel().getBlockState(context.getClickedPos().relative(face.getOpposite()));
            if (against.getBlock() instanceof YardBlock && against.getValue(AXIS) == face.getAxis()) {
                axis = face.getAxis(); // continue the yard we were placed against
            }
        }
        BlockState state = Waterlogging.placed(defaultBlockState().setValue(AXIS, axis), context);
        for (Direction d : new Direction[] {Direction.get(Direction.AxisDirection.NEGATIVE, axis), Direction.get(Direction.AxisDirection.POSITIVE, axis)}) {
            BlockState n = context.getLevel().getBlockState(context.getClickedPos().relative(d));
            if (n.getBlock() instanceof YardBlock && n.getValue(AXIS) == axis) {
                return state.setValue(TRIM, n.getValue(TRIM)); // the row keeps one trim
            }
        }
        return state;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.X ? SHAPE_X : SHAPE_Z;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90, COUNTERCLOCKWISE_90 ->
                    state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
            default -> state;
        };
    }

    /**
     * SAIL2: a dye dyes the sail this yard heads ({@code sails.dyeing}), a banner hangs on it ({@code sails.banners}),
     * see {@link SailDecorations}; the item is spent only when it took. Any other item goes on to the trim.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        DyeColor color = stack.getItem() instanceof DyeItem dye ? dye.getDyeColor() : null;
        boolean banner = SailBanner.isBanner(stack);
        if (color != null && SailingConfig.SAIL_DYEING.get() || banner && SailingConfig.SAIL_BANNERS.get()) {
            if (level instanceof ServerLevel) {
                SailDecorations.Outcome o = color != null ? SailDecorations.dyeYard(level, pos, color)
                        : SailDecorations.hangBanner(level, pos, stack);
                if (o.consumes()) {
                    level.playSound(null, pos, color != null ? SoundEvents.DYE_USE : SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1f, 1f);
                    stack.consume(1, player);
                }
                player.displayClientMessage(SailDecorations.message(o, color), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Sneaking with an empty hand takes a hung banner back (SAIL2); otherwise the empty hand cycles the trim. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSecondaryUseActive()) {
            if (level instanceof ServerLevel) {
                ItemStack taken = SailDecorations.takeBanner(level, pos);
                if (!taken.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(taken);
                    player.displayClientMessage(SailDecorations.message(SailDecorations.Outcome.TAKEN, null), true);
                    return InteractionResult.SUCCESS;
                }
            } else if (SailDecorations.hasBanner(level, pos)) {
                return InteractionResult.SUCCESS;
            }
        }
        if (!SailingConfig.SAIL_BLOCK_TRIM.get()) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel) {
            SailTrim next = YardSails.cycle(serverLevel, pos);
            var rules = SailingConfig.yardRules();
            player.displayClientMessage(next == null ? Component.translatable(KEY_NO_SAIL, rules.minGap(), rules.maxGap())
                    : Component.translatable(SailWinchBlock.KEY_SAIL_SET, Component.translatable(SailWinchBlock.trimKey(next))), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this) && level instanceof ServerLevel serverLevel) {
            YardSails.refreshAround(serverLevel, pos);
        }
    }

    /**
     * Breaking a yard block that keeps a banner drops the banner (SAIL2). A block moved by Sable's assembly or
     * disassembly ({@code movedByPiston}) keeps it: its data travels, and the old block entity was cleared after saving
     * ({@link YardBlockEntity#clearContent}).
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!newState.is(this) && !movedByPiston && level instanceof ServerLevel
                && level.getBlockEntity(pos) instanceof YardBlockEntity be && !be.banner().isEmpty()) {
            ItemStack banner = be.banner();
            be.clearContent();
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, banner);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!newState.is(this) && level instanceof ServerLevel serverLevel) {
            YardSails.refreshAround(serverLevel, pos);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new YardBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != SailingBlocks.YARD_BLOCK_ENTITY.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<YardBlockEntity>) YardBlockEntity::serverTick;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        Waterlogging.tickFluid(state, level, pos);
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Waterlogging.fluid(state, super.getFluidState(state));
    }
}
