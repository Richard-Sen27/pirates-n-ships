package com.richardsenger.piratesnships.almanac;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * The Captain's Almanac as a book (ALM1, docs/design.md §3.4): use it in either hand to read it
 * ({@link AlmanacReading#readInHand}), also while looking at a block; sneak-use on a block sets it down as the placed
 * tome. With {@code almanac.enabled} off it only places, like a plain block item.
 */
public class AlmanacItem extends BlockItem {

    public static final String TOOLTIP = "item." + Constants.MOD_ID + ".captains_almanac.tooltip";

    public AlmanacItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!AlmanacConfig.ENABLED.get()) return InteractionResultHolder.pass(stack);
        if (player instanceof ServerPlayer sp) AlmanacReading.readInHand(sp);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && !player.isSecondaryUseActive()) {
            InteractionResult read = use(context.getLevel(), player, context.getHand()).getResult();
            if (read != InteractionResult.PASS) return read;
        }
        return super.useOn(context);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable(TOOLTIP).withStyle(ChatFormatting.GRAY));
    }
}
