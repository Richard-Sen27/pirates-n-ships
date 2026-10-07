package com.richardsenger.piratesnships.combat.cannon;

import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.station.StationBlock;
import com.richardsenger.piratesnships.station.StationKind;
import com.richardsenger.piratesnships.station.Stations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The cannon (docs/design.md §8.2): an iron barrel on a four-wheeled truck carriage, placed on a ship's deck or on land.
 * {@link #FACING} is the way the muzzle points (the traverse), {@link #LOAD} what is in the barrel; the elevation and the
 * reload cooldown live in the {@link CannonBlockEntity}. The models are hand-made in Blockbench
 * ({@code art/models/cannon*.bbmodel}) with the muzzle to the north: {@code powder} shows a rammer leaning against the
 * barrel, {@code loaded} also the ball in the muzzle. The muzzle reaches about 2.5 px past the block face.
 *
 * <p>Controls, all on the server through {@link CannonService}:
 * <ul>
 *   <li>use with gunpowder: powder in; use with a cannonball: ball in (each takes one item, none in creative);</li>
 *   <li>sneak-use with an empty hand: aim, one elevation step up when the upper half of the block is clicked, one
 *       step down for the lower half (like the helm's click position for the rudder);</li>
 *   <li>use with an empty hand: fire when loaded, otherwise say what is missing.</li>
 * </ul>
 * Any other item in hand does its own thing. It is also a crew station ({@link CannonStation}).
 */
public class CannonBlock extends Block implements EntityBlock, StationBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<CannonLoad> LOAD = EnumProperty.create("load", CannonLoad.class);

    private static final VoxelShape CARRIAGE = box(1, 0, 2, 15, 7, 14);
    private static final VoxelShape SHAPE_NS = Shapes.or(CARRIAGE, box(4.5, 6, 0, 11.5, 13, 16));
    private static final VoxelShape SHAPE_EW = Shapes.or(box(2, 0, 1, 14, 7, 15), box(0, 6, 4.5, 16, 13, 11.5));

    public CannonBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LOAD, CannonLoad.EMPTY));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LOAD);
    }

    /** The muzzle points the way the placing player looks. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
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
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CannonBlockEntity(pos, state);
    }

    @Override
    public StationKind<?> stationKind() {
        return CannonStation.INSTANCE;
    }

    static boolean isPowder(ItemStack stack) {
        return stack.is(Items.GUNPOWDER);
    }

    static boolean isBall(ItemStack stack) {
        return stack.is(CombatContent.CANNONBALL.get());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; // empty hand: fire or aim (useWithoutItem)
        }
        if (stack.getItem() instanceof StationBlock.Tool || !isPowder(stack) && !isBall(stack)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION; // the item's own use; never fires the cannon
        }
        if (level instanceof ServerLevel server) {
            CannonService.Use use = CannonService.load(server, pos, player, stack);
            player.displayClientMessage(use.message(), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            CannonService.Use use;
            if (player.isSecondaryUseActive()) {
                // the hit is in plot space on a ship, so the click height works unchanged there (sable-notes §9.0e)
                boolean up = hit.getLocation().y - pos.getY() >= 0.5;
                use = CannonService.aim(server, pos, up);
            } else {
                use = CannonService.fire(server, pos, player);
            }
            player.displayClientMessage(use.message(), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Breaking the cannon frees the station and removes its seat. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            Stations.onStationRemoved(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
