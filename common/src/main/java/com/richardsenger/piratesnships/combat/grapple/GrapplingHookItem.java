package com.richardsenger.piratesnships.combat.grapple;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The grappling hook (docs/design.md §8.3, G11). Use throws it ({@link GrappleService#throwHook}); the item leaves the
 * hand with the hook (not in creative) and comes back when the hook is released or reeled in. A second throw releases
 * the first hook. Sneak + use with an empty hand releases a hook that is out. With {@code grapple.enabled} off the item
 * does nothing.
 */
public class GrapplingHookItem extends Item {

    public static final String TOOLTIP_KEY = "item." + Constants.MOD_ID + ".grappling_hook.tooltip";

    public GrapplingHookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!GrappleConfig.ENABLED.get()) {
            return InteractionResultHolder.pass(stack);
        }
        if (level instanceof ServerLevel server) {
            boolean consume = !player.getAbilities().instabuild;
            GrappleService.throwHook(server, player, stack.copyWithCount(1), consume);
            if (consume) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(TOOLTIP_KEY).withStyle(ChatFormatting.GRAY));
    }
}
