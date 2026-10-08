package com.richardsenger.piratesnships.ship.decor;

import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.ship.decor.flag.FlagConfig;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleBlockEntity;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleMachine;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpolePart;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleRun;
import com.richardsenger.piratesnships.ship.decor.flag.Flags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A thin vertical post (design.md §4.7) that flies a flag. Its look is one of four hand-made Blockbench models by
 * {@link #PART} ({@code art/models/flagpole*.bbmodel}): a 3 px pole at the block centre, with a gilded finial on a
 * truck above the block at the head of a pole and an iron cleat with the wound halyard near its foot, set on a
 * diagonal so the cloth never passes through it. The shape (outline and collision) is a 4×4 post, slightly wider than
 * the drawn pole.
 * <ul>
 *   <li>Use a flag item (ours or a banner) to hoist it, an empty hand to strike or raise the colors, sneak with an
 *       empty hand to take the flag down. Each takes the configured delay; see {@link FlagpoleMachine}.</li>
 *   <li><b>Tall poles (VIS1a).</b> Stacked flagpole blocks form one pole ({@link FlagpoleRun}): the top block, the
 *       head, owns the flag; every use of a block below it goes to the head. A flagpole placed on a head takes over
 *       the pole's flag and pending action ({@link FlagpoleBlockEntity#handUp}); placing one that would make the pole
 *       taller than {@code flags.max_pole_height} is refused. With {@code flags.stacked_poles} off every block is its
 *       own pole; the parts still follow the neighbours.</li>
 *   <li>{@link #PART} is the part the block shows ({@link FlagpolePart}, from its flagpole neighbours above and below
 *       as fences connect; a pole saved before VIS1a loads as {@code single} and its block entity's first tick puts
 *       the right part). {@link #FLAG} is the flag the block <em>shows</em> (none while struck, always none on a
 *       shaft); the real state is in {@link FlagpoleBlockEntity}. {@link #FACING} is unused since FL1 and kept so old
 *       poles load with their starting angle.</li>
 *   <li>The flag is a full-size cloth ({@code FlagClothModel}: one block high, reaching 1.5 blocks from the pole's
 *       center) drawn by the block entity renderer into the space downwind of the pole. It is a visual overlap only:
 *       the block's shape stays the 4×4 post, so the cloth needs free space downwind to look right and clips through
 *       any blocks there.</li>
 * </ul>
 */
public class FlagpoleBlock extends Block implements EntityBlock {

    public static final MapCodec<FlagpoleBlock> CODEC = simpleCodec(FlagpoleBlock::new);
    public static final EnumProperty<FlagKind> FLAG = EnumProperty.create("flag", FlagKind.class);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<FlagpolePart> PART = EnumProperty.create("part", FlagpolePart.class);
    /** Action-bar message when a pole would get too tall; argument: the limit. */
    public static final String TOO_TALL_KEY = "message." + Constants.MOD_ID + ".flag.pole_too_tall";
    private static final VoxelShape SHAPE = Block.box(6, 0, 6, 10, 16, 10);

    public FlagpoleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FLAG, FlagKind.NONE).setValue(FACING, Direction.NORTH)
                .setValue(PART, FlagpolePart.SINGLE));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FLAG, FACING, PART);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** The part a flagpole at {@code pos} shows, from the blocks above and below it. */
    public static FlagpolePart partAt(BlockGetter level, BlockPos pos) {
        return FlagpolePart.of(FlagpoleRun.isPole(level, pos.below()), FlagpoleRun.isPole(level, pos.above()));
    }

    /**
     * The part from the neighbours; null (placement refused, with an action-bar message to the player on the server)
     * if stacking is on and the pole would get taller than {@code flags.max_pole_height}.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (FlagpoleRun.stacked()) {
            int max = FlagConfig.MAX_POLE_HEIGHT.get();
            int below = FlagpoleRun.countBelow(level, pos, max);
            int above = FlagpoleRun.countAbove(level, pos, max);
            if (FlagpoleRun.tooTall(below, above, max)) {
                Player player = context.getPlayer();
                if (!level.isClientSide && player != null) player.displayClientMessage(Component.translatable(TOO_TALL_KEY, max), true);
                return null;
            }
        }
        return defaultBlockState().setValue(PART, partAt(level, pos));
    }

    /** A flagpole above or below came or went: show the matching part. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (direction.getAxis() != Direction.Axis.Y) return state;
        return state.setValue(PART, partAt(level, pos));
    }

    /** A flagpole placed on a pole's head takes over its flag (VIS1a). */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !FlagpoleRun.stacked()) return;
        if (level.getBlockEntity(pos.below()) instanceof FlagpoleBlockEntity oldHead) {
            oldHead.handUp(placer == null ? null : placer.getUUID());
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FlagpoleBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != Flags.FLAGPOLE_BLOCK_ENTITY.get()) return null;
        return (l, p, s, be) -> ((FlagpoleBlockEntity) be).serverTick();
    }

    /**
     * A flag item hoists, at the pole's head; any other item does its own thing (e.g. stacking another flagpole on
     * top).
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        FlagKind kind = Flags.kindOf(stack);
        if (kind == null) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide && FlagpoleRun.owner(level, pos) instanceof FlagpoleBlockEntity be) {
            be.interact(player, new FlagpoleMachine.Input.Hoist(kind, stack.copyWithCount(1)));
            stack.consume(1, player);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Empty hand: strike or raise the colors; sneaking: take the flag down (at the pole's head). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide && FlagpoleRun.owner(level, pos) instanceof FlagpoleBlockEntity be) {
            be.interact(player, player.isSecondaryUseActive() ? new FlagpoleMachine.Input.TakeDown() : new FlagpoleMachine.Input.Toggle());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof FlagpoleBlockEntity be) be.dropContents();
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
