package com.richardsenger.piratesnships.trade.exchange;

import com.richardsenger.piratesnships.trade.market.Market;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TradePlanTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void buyNeedsQuoteCoinsAndSpace() {
        assertEquals(TransactionResult.Status.OK, TradePlan.buy(Market.Outcome.OK, 100, 100, 64, 64));
        assertEquals(TransactionResult.Status.NOT_ENOUGH_COINS, TradePlan.buy(Market.Outcome.OK, 99, 100, 64, 64));
        assertEquals(TransactionResult.Status.NOT_ENOUGH_SPACE, TradePlan.buy(Market.Outcome.OK, 100, 100, 63, 64));
        assertEquals(TransactionResult.Status.STOCK_LIMIT, TradePlan.buy(Market.Outcome.LIMIT, 1000, 100, 64, 64));
        assertEquals(TransactionResult.Status.NOT_TRADED, TradePlan.buy(Market.Outcome.NOT_TRADED, 1000, 0, 64, 64));
        assertEquals(TransactionResult.Status.INVALID_QUANTITY, TradePlan.buy(Market.Outcome.INVALID_QUANTITY, 1000, 0, 64, 0));
    }

    @Test
    void sellNeedsAllUnits() {
        assertEquals(TransactionResult.Status.OK, TradePlan.sell(64, 64));
        assertEquals(TransactionResult.Status.NOT_ENOUGH_GOODS, TradePlan.sell(63, 64));
        assertEquals(TransactionResult.Status.INVALID_QUANTITY, TradePlan.sell(10, 0));
    }

    @Test
    void roomCountsFreeSlotsAndPartialStacks() {
        List<ItemStack> slots = List.of(ItemStack.EMPTY, new ItemStack(Items.SUGAR, 60), new ItemStack(Items.DIRT, 1), new ItemStack(Items.SUGAR, 64));
        assertEquals(64 + 4, TradePlan.roomFor(slots, new ItemStack(Items.SUGAR)));
        assertEquals(16, TradePlan.roomFor(List.of(ItemStack.EMPTY), new ItemStack(Items.EGG)));
        assertEquals(0, TradePlan.roomFor(List.of(new ItemStack(Items.DIRT, 64)), new ItemStack(Items.SUGAR)));
    }
}
