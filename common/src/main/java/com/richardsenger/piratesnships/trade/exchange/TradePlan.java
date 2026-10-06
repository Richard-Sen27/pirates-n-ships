package com.richardsenger.piratesnships.trade.exchange;

import com.richardsenger.piratesnships.trade.market.Market;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Pure checks behind {@link MarketTransactions}: every trade is planned completely before anything moves. */
public final class TradePlan {

    private TradePlan() {
    }

    /** The status a market quote maps to ({@code OK} only for {@link Market.Outcome#OK}). */
    public static TransactionResult.Status ofQuote(Market.Outcome outcome) {
        return switch (outcome) {
            case OK -> TransactionResult.Status.OK;
            case NOT_TRADED -> TransactionResult.Status.NOT_TRADED;
            case LIMIT -> TransactionResult.Status.STOCK_LIMIT;
            case INVALID_QUANTITY -> TransactionResult.Status.INVALID_QUANTITY;
        };
    }

    /** A buy of {@code quantity} units for {@code cost}: needs the coins and room for every unit. */
    public static TransactionResult.Status buy(Market.Outcome quote, long coins, long cost, int room, int quantity) {
        if (quote != Market.Outcome.OK) return ofQuote(quote);
        if (coins < cost) return TransactionResult.Status.NOT_ENOUGH_COINS;
        if (room < quantity) return TransactionResult.Status.NOT_ENOUGH_SPACE;
        return TransactionResult.Status.OK;
    }

    /** A sale (or delivery) of {@code quantity} units: needs them all on hand. */
    public static TransactionResult.Status sell(int have, int quantity) {
        if (quantity < 1) return TransactionResult.Status.INVALID_QUANTITY;
        return have < quantity ? TransactionResult.Status.NOT_ENOUGH_GOODS : TransactionResult.Status.OK;
    }

    /** How many items of {@code kind} fit into {@code slots}: free slots plus the rest of matching partial stacks. */
    public static int roomFor(List<ItemStack> slots, ItemStack kind) {
        int max = kind.getMaxStackSize();
        long room = 0;
        for (ItemStack s : slots) {
            if (s.isEmpty()) room += max;
            else if (ItemStack.isSameItemSameComponents(s, kind)) room += Math.max(0, Math.min(max, s.getMaxStackSize()) - s.getCount());
        }
        return (int) Math.min(Integer.MAX_VALUE, room);
    }
}
