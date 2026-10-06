package com.richardsenger.piratesnships.ship.decor;

import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleBlockEntity;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleMachine;
import com.richardsenger.piratesnships.ship.decor.flag.Flags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
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
 * A thin vertical post (design.md §4.7) that flies a flag. Its look is the hand-made Blockbench model
 * {@code block/flagpole} ({@code art/models/flagpole.bbmodel}): a 3 px pole at the block centre with a gilded finial on
 * a truck above the block, and an iron cleat with wound halyard near the bottom, set on a diagonal so the cloth never
 * passes through it. The shape (outline and collision) is a 4×4 post, slightly wider than the drawn pole.
 * <ul>
 *   <li>Use a flag item (ours or a banner) to hoist it, an empty hand to strike or raise the colors, sneak with an
 *       empty hand to take the flag down. Each takes the configured delay; see {@link FlagpoleMachine}.</li>
 *   <li>{@link #FLAG} is the flag the pole <em>shows</em> (none while struck) and only drives the model; the real
 *       state is in {@link FlagpoleBlockEntity}. {@link #FACING} is the side the flag points to (downwind).</li>
 *   <li>The flag is a full-size cloth ({@code FlagClothModel}: one block high, reaching 1.5 blocks from the pole's
 *       center) that extends into the space downwind of the pole. It is a visual overlap only: the block's shape
 *       (outline and collision) stays the 4×4 post, so the cloth needs free space downwind to look right and
 *       clips through any blocks there.</li>
 * </ul>
 */
public class FlagpoleBlock extends Block implements EntityBlock {

    public static final MapCodec<FlagpoleBlock> CODEC = simpleCodec(FlagpoleBlock::new);
    public static final EnumProperty<FlagKind> FLAG = EnumProperty.create("flag", FlagKind.class);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Block.box(6, 0, 6, 10, 16, 10);

    public FlagpoleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FLAG, FlagKind.NONE).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FLAG, FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
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

    /** A flag item hoists; any other item does its own thing (e.g. stacking another flagpole on top). */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        FlagKind kind = Flags.kindOf(stack);
        if (kind == null) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof FlagpoleBlockEntity be) {
            be.interact(player, new FlagpoleMachine.Input.Hoist(kind, stack.copyWithCount(1)));
            stack.consume(1, player);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Empty hand: strike or raise the colors; sneaking: take the flag down. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof FlagpoleBlockEntity be) {
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
