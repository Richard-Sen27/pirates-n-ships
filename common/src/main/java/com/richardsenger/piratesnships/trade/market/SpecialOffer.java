package com.richardsenger.piratesnships.trade.market;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * A market line that is not a trade good (TM1): an item a port of one {@link PortKind} sells at a fixed price, with no
 * stock, no price movement and no buying back. Registered by other modules in {@link SpecialOffers} (the treasure map
 * at pirate-island fences); {@code trade.net.MarketBackend} lists it with the goods and sells it.
 *
 * @param id        the line's id, sent as the "good" of the market line and trade request; equal to the item id so the
 *                  client can name the item without knowing the offer
 * @param item      the item shown on the line
 * @param kind      the kind of port that sells it
 * @param unitPrice doubloons per unit (read live, a config value)
 * @param enabled   whether the line is offered right now (read live, a config toggle)
 * @param stack     a new stack of one unit, made for each unit bought
 */
public record SpecialOffer(ResourceLocation id, ResourceLocation item, PortKind kind, LongSupplier unitPrice,
                           BooleanSupplier enabled, Supplier<ItemStack> stack) {

    /** The total for {@code quantity} units; never negative, saturating instead of overflowing. */
    public static long total(long unitPrice, int quantity) {
        if (quantity <= 0 || unitPrice <= 0) return 0;
        return unitPrice > Long.MAX_VALUE / quantity ? Long.MAX_VALUE : unitPrice * quantity;
    }

    /** Whether ports of {@code portKind} list this offer right now. */
    public boolean offeredAt(PortKind portKind) {
        return kind == portKind && enabled.getAsBoolean();
    }
}
