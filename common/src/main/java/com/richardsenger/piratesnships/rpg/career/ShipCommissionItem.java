package com.richardsenger.piratesnships.rpg.career;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Optional;

/**
 * A ship commission (docs/design.md §15, SHP1): the grant of a fighting ship to a player who reached the rank. Carries
 * a {@link ShipCommission}. Used on a navy officer or the harbor master's desk of a navy outpost (the desk of a pirate
 * island for the pirate sloop), it delivers the ship to a free berth of that port ({@link ShipGrants#redeem}). Only the
 * player it names can redeem it. One per stack.
 */
public class ShipCommissionItem extends Item {

    static final String KEY = "item." + Constants.MOD_ID + ".ship_commission.";
    public static final String KEY_SHIP = KEY + "ship";
    public static final String KEY_FOR = KEY + "for";
    public static final String KEY_HINT_NAVY = KEY + "hint.navy";
    public static final String KEY_HINT_PIRATES = KEY + "hint.pirates";

    public ShipCommissionItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static Optional<ShipCommission> commission(ItemStack stack) {
        return stack.isEmpty() ? Optional.empty() : Optional.ofNullable(stack.get(ShipGrantContent.COMMISSION_DATA.get()));
    }

    /** A new commission stack for {@code commission}. */
    public static ItemStack stack(ShipCommission commission) {
        ItemStack stack = new ItemStack(ShipGrantContent.SHIP_COMMISSION.get());
        stack.set(ShipGrantContent.COMMISSION_DATA.get(), commission);
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        commission(stack).ifPresent(c -> {
            lines.add(Component.translatable(KEY_SHIP, Component.translatable(c.name())).withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable(KEY_FOR, c.ownerName()).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable(c.ladder() == ShipGrantRules.Ladder.NAVY ? KEY_HINT_NAVY : KEY_HINT_PIRATES)
                    .withStyle(ChatFormatting.DARK_GRAY));
        });
    }
}
