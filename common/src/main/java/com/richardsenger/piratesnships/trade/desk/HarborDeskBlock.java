package com.richardsenger.piratesnships.trade.desk;

import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.trade.TradeConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.richardsenger.piratesnships.ship.template.ShipOrderContent;
import com.richardsenger.piratesnships.ship.template.ShipOrders;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

/**
 * The harbor master's desk (design.md §10.3): a writing desk bound to one port ({@link HarborDeskBlockEntity}). Its
 * front faces the player who placed it. Using it opens the port's market screen ({@link HarborDeskService#use}); an
 * unbound desk says so on the action bar. Using it with a ship receipt picks up the ordered ship (SW1). With {@code harbor_desks.desks_enabled} off the desk is inert.
 *
 * <p>The model is hand-made in Blockbench ({@code art/models/harbor_desk.bbmodel}): a partner's desk with drawers and a
 * kneehole on both sides. {@link #FACING} is the customer's side (towards the player who placed it; north in the
 * unrotated model, with the bell); the open ledger, the quill in its inkwell and the coin stacks face the harbor
 * master on the opposite side.
 */
public class HarborDeskBlock extends BaseEntityBlock {

    public static final MapCodec<HarborDeskBlock> CODEC = simpleCodec(HarborDeskBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // Top board over two drawer pedestals; the model faces north, so the long side runs along x
    private static final VoxelShape SHAPE_NS = Shapes.or(Block.box(0, 12, 2, 16, 15, 14),
            Block.box(1, 0, 3, 6, 12, 13), Block.box(10, 0, 3, 15, 12, 13));
    private static final VoxelShape SHAPE_EW = Shapes.or(Block.box(2, 12, 0, 14, 15, 16),
            Block.box(3, 0, 1, 13, 12, 6), Block.box(3, 0, 10, 13, 12, 15));

    public HarborDeskBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HarborDeskBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel server) HarborDeskService.autoBind(server, pos);
    }

    /**
     * Using the desk with a ship receipt (SW1) picks up the ordered ship ({@code ShipOrders.pickup}) instead of opening
     * the market; any other item opens the market as an empty hand does.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!TradeConfig.DESKS_ENABLED.get() || !stack.is(ShipOrderContent.SHIP_RECEIPT.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        return ShipOrders.pickup(sp, pos, stack).map(r -> {
            sp.displayClientMessage(r.message(), false);
            return ItemInteractionResult.CONSUME;
        }).orElse(ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!TradeConfig.DESKS_ENABLED.get()) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        HarborDeskService.Use use = HarborDeskService.use(sp, pos);
        if (use.message() != null) sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(use.message()), true);
        return use == HarborDeskService.Use.DISABLED ? InteractionResult.PASS : InteractionResult.CONSUME;
    }
}
