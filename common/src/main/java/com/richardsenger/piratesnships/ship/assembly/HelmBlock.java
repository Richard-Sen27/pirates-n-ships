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
import net.minecraft.world.phys.BlockHitResult;

/**
 * The helm (docs/design.md §4.1): use it in the world to assemble the connected blocks into a ship, use it on a ship to
 * disassemble the ship, use a named name tag on it to name the ship. All logic runs on the server.
 */
public class HelmBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<HelmBlock> CODEC = simpleCodec(HelmBlock::new);

    public HelmBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel) {
            ShipBody ship = SableShips.containing(serverLevel, pos);
            AssemblyResult result = ship == null
                    ? ShipAssembler.assemble(serverLevel, pos, player)
                    : ShipAssembler.disassemble(ship, pos, player);
            player.displayClientMessage(result.message(), false);
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
