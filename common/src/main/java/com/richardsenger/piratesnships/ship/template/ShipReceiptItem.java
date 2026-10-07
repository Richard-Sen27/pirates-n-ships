package com.richardsenger.piratesnships.ship.template;

import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A shipwright's receipt for an ordered ship (design.md §4.1, SW1). Carries a {@link ShipReceipt}; its tooltip
 * (added on the client by {@code trade.client.TradeClient} through {@link ShipReceiptText}) shows the ship, the
 * village and the progress. Using the village's harbor master's desk with it once the ship is ready picks the ship up
 * ({@link ShipOrders#pickup}). Whoever holds the receipt owns the ship. One per stack.
 */
public class ShipReceiptItem extends Item {

    public ShipReceiptItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static Optional<ShipReceipt> receipt(ItemStack stack) {
        return stack.isEmpty() ? Optional.empty() : Optional.ofNullable(stack.get(ShipOrderContent.RECEIPT_DATA.get()));
    }

    /** A new receipt stack for {@code receipt}. */
    public static ItemStack stack(ShipReceipt receipt) {
        ItemStack stack = new ItemStack(ShipOrderContent.SHIP_RECEIPT.get());
        stack.set(ShipOrderContent.RECEIPT_DATA.get(), receipt);
        return stack;
    }
}
