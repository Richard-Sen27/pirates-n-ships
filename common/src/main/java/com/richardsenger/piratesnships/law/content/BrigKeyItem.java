package com.richardsenger.piratesnships.law.content;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;

/**
 * The brig key (design.md §13.3): using it on either half of a brig door locks or unlocks the door
 * ({@link BrigDoorBlock#useKey}). Any key fits any brig door: keys are the security, cells are shared among a crew.
 * The key never changes the door's owner.
 *
 * <p>A plain use reaches the door's {@link BrigDoorBlock#useItemOn} first; a sneak-use with the key in hand skips the
 * block (vanilla) and lands here. Both call the same method.
 */
public class BrigKeyItem extends Item {

    public BrigKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof BrigDoorBlock)) {
            return InteractionResult.PASS;
        }
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        BrigDoorBlock.useKey(context.getLevel(), context.getClickedPos(), context.getPlayer());
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.pirates_n_ships.brig_key.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
