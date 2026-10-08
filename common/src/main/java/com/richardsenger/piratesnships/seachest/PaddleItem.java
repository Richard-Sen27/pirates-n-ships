package com.richardsenger.piratesnships.seachest;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * The paddle (work package SC2, docs/design.md §11). Used on a floating {@link SeaChestEntity} it seats the player on
 * the chest ({@link SeaChestEntity#interact}); held in either hand while riding, the movement keys paddle it
 * ({@link PaddleRules}). Has no use of its own otherwise.
 */
public class PaddleItem extends Item {

    public static final String HINT_KEY = "item." + Constants.MOD_ID + ".paddle.hint";

    public PaddleItem(Properties properties) {
        super(properties);
    }

    /** Whether {@code entity} holds a paddle in either hand. */
    public static boolean inHand(LivingEntity entity) {
        return entity.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof PaddleItem
                || entity.getItemInHand(InteractionHand.OFF_HAND).getItem() instanceof PaddleItem;
    }

    /** The hand holding the paddle, main hand first, or null. */
    public static InteractionHand handOf(LivingEntity entity) {
        if (entity.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof PaddleItem) return InteractionHand.MAIN_HAND;
        if (entity.getItemInHand(InteractionHand.OFF_HAND).getItem() instanceof PaddleItem) return InteractionHand.OFF_HAND;
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(HINT_KEY).withStyle(ChatFormatting.GRAY));
    }
}
