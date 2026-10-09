package com.richardsenger.piratesnships.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fires {@code CommonEvents.BLOCK_PLACE} when a block item placed its block (server side), around the
 * {@code placeBlock} call in {@code BlockItem#place} (subclass overrides of {@code placeBlock}, doors and beds among
 * them, included). A cancelling listener gets the old block state put back and the placement fails, like a cancelled
 * NeoForge {@code BlockEvent.EntityPlaceEvent}. Only the clicked position is reported (NeoForge also reports the other
 * half of multi-blocks); entity placements without a block item (endermen, snow golems) are not covered.
 *
 * <p>Why a mixin: Fabric API has no block place event.
 */
@Mixin(BlockItem.class)
public abstract class MixinBlockItem {

    @WrapOperation(method = "place",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/BlockItem;placeBlock(Lnet/minecraft/world/item/context/BlockPlaceContext;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean pirates_n_ships$blockPlace(BlockItem item, BlockPlaceContext ctx, BlockState state, Operation<Boolean> original) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState before = level.getBlockState(pos);
        boolean placed = original.call(item, ctx, state);
        if (placed && !level.isClientSide
                && CommonEvents.BLOCK_PLACE.invoker().onPlace(level, pos, level.getBlockState(pos), ctx.getPlayer())) {
            level.setBlock(pos, before, Block.UPDATE_ALL);
            return false;
        }
        return placed;
    }
}
