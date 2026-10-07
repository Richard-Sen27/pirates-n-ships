package com.richardsenger.piratesnships.crew.galley;

import com.mojang.serialization.MapCodec;
import com.richardsenger.piratesnships.crew.content.CrewContent;
import com.richardsenger.piratesnships.crew.provisions.ProvisionSettings;
import com.richardsenger.piratesnships.crew.provisions.ProvisionsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The pantry block: use opens the chest screen, use while sneaking with empty hands prints what it holds in
 * provisions terms (chat). Drops its contents when broken, comparator output like a chest. The model is a hand-made
 * Blockbench larder cupboard (art/models/pantry.bbmodel, design.md §4.8); the block has no facing, so its doors always
 * face north, and it does not occlude its neighbours ({@code noOcclusion} in {@code CrewContent}).
 */
public class PantryBlock extends BaseEntityBlock {

    public static final MapCodec<PantryBlock> CODEC = simpleCodec(PantryBlock::new);

    public PantryBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PantryBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof PantryBlockEntity pantry) {
            ProvisionSettings s = ProvisionsConfig.settings();
            pantry.catchUp(level.getGameTime(), s);
            if (player.isSecondaryUseActive()) {
                for (Component line : GalleyText.pantryInfo(PantryInfo.of(pantry.store(s), s))) {
                    player.displayClientMessage(line, false);
                }
            } else {
                player.openMenu(pantry);
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        net.minecraft.world.Containers.dropContentsOnDestroy(state, newState, level, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, CrewContent.PANTRY_BLOCK_ENTITY.get(), PantryBlockEntity::serverTick);
    }
}
