package com.richardsenger.piratesnships.ship.assembly;

import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The helm (docs/design.md §4.1, §5.3). In the world, using it assembles the connected blocks into a ship. On an
 * assembled ship, using it steers (handled by the {@link SteeringHandler} the sailing module installs), and sneak-use
 * with an empty hand disassembles the ship. A named name tag names the ship. All logic runs on the server.
 *
 * <p>{@link #RUDDER} is the rudder position, stored as {@code step + 5} (5 = midships, 0..4 port, 6..10 starboard; see
 * {@code sailing.ship.RudderSteps}). It lives in the block state, so it is visible (F3) and is saved with the ship's
 * blocks.
 */
public class HelmBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<HelmBlock> CODEC = simpleCodec(HelmBlock::new);
    public static final int MIDSHIPS = 5;
    public static final IntegerProperty RUDDER = IntegerProperty.create("rudder", 0, 2 * MIDSHIPS);
    public static final String KEY_DISASSEMBLE_HINT = com.richardsenger.piratesnships.Constants.MOD_ID + ".assembly.disassemble_hint";

    /** Steering on an assembled ship. Returns the message for the helmsman (shown in the action bar). */
    @FunctionalInterface
    public interface SteeringHandler {
        Component steer(ServerLevel level, BlockPos pos, BlockState state, Player player, BlockHitResult hit);
    }

    private static volatile @Nullable SteeringHandler steering;

    /** Installed by the sailing module. Without one, using the helm on a ship only shows the disassembly hint. */
    public static void setSteeringHandler(@Nullable SteeringHandler handler) {
        steering = handler;
    }

    public HelmBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH).setValue(RUDDER, MIDSHIPS));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, RUDDER);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel) {
            ShipBody ship = SableShips.containing(serverLevel, pos);
            if (ship == null) {
                player.displayClientMessage(ShipAssembler.assemble(serverLevel, pos, player).message(), false);
            } else if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
                player.displayClientMessage(ShipAssembler.disassemble(ship, pos, player).message(), false);
            } else {
                SteeringHandler h = steering;
                player.displayClientMessage(h == null ? Component.translatable(KEY_DISASSEMBLE_HINT)
                        : h.steer(serverLevel, pos, state, player, hit), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        if (!stack.is(Items.NAME_TAG) || name == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level instanceof ServerLevel serverLevel) {
            ShipBody ship = SableShips.containing(serverLevel, pos);
            AssemblyResult result = ship == null ? AssemblyResult.of(AssemblyResult.Outcome.NO_SHIP)
                    : ShipAssembler.name(ship, name.getString());
            if (result.success() && !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.displayClientMessage(result.success()
                    ? Component.translatable(result.outcome().key(), name.getString()) : result.message(), false);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
