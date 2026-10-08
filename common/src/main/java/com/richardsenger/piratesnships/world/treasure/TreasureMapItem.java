package com.richardsenger.piratesnships.world.treasure;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The treasure map (TM1, design.md §10.1). Used blank, it binds itself to the nearest pirate island's nearest unfound
 * treasure ({@link TreasureMapService#use}); used bound, it reads out the bearing. While held, the client draws it
 * ({@code world.treasure.client.TreasureMapHud}). Once a second it checks whether its treasure has been found.
 */
public class TreasureMapItem extends Item {

    /** Ticks between two checks of a bound map against the port registry. */
    public static final int CHECK_INTERVAL = 20;

    public TreasureMapItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResultHolder.success(player.getItemInHand(hand));
        return TreasureMapService.use((ServerLevel) level, player, hand);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel server) || entity.tickCount % CHECK_INTERVAL != 0) return;
        if (TreasureMapService.refresh(stack, server.getServer()) && entity instanceof Player player) {
            player.displayClientMessage(Component.translatable(TreasureMapText.FOUND), true);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        TreasureMapData data = stack.get(TreasureMapContent.TREASURE_MAP_DATA.get());
        String key = data == null ? TreasureMapText.TOOLTIP_BLANK : data.found() ? TreasureMapText.TOOLTIP_FOUND : TreasureMapText.TOOLTIP_BOUND;
        lines.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
    }
}
