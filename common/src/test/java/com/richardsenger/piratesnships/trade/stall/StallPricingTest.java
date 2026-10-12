package com.richardsenger.piratesnships.trade.stall;

import com.richardsenger.piratesnships.trade.good.TradeGoods;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** LIFE1: the village stalls' wares and fixed prices. */
class StallPricingTest {

    @Test
    void pricesScaleAndNeverFallBelowOne() {
        assertEquals(4, StallPricing.unitBuy(4, 1.0));
        assertEquals(6, StallPricing.unitBuy(4, 1.5));
        assertEquals(1, StallPricing.unitBuy(2, 0.1));
        assertEquals(1, StallPricing.unitBuy(0.2, 1.0));
    }

    @Test
    void theVendorBuysBackForAShare() {
        assertEquals(2, StallPricing.unitSell(4, 0.5, true));
        assertEquals(1, StallPricing.unitSell(3, 0.5, true), "rounded down");
        assertEquals(0, StallPricing.unitSell(40, 0.5, false), "the toolkit is not bought back");
        assertEquals(4, StallPricing.unitSell(4, 2.0, true), "never more than the price");
    }

    @Test
    void totalsSaturate() {
        assertEquals(12, StallPricing.total(4, 3));
        assertEquals(0, StallPricing.total(4, 0));
        assertEquals(Long.MAX_VALUE, StallPricing.total(Long.MAX_VALUE / 2, 3));
    }

    @ParameterizedTest
    @EnumSource(StallKind.class)
    void everyStallSellsSomethingWithDistinctLines(StallKind stall) {
        assertFalse(StallWares.of(stall).isEmpty());
        Set<Object> lines = new HashSet<>();
        for (StallWares.Ware w : StallWares.of(stall)) {
            assertTrue(lines.add(w.lineId()), "line " + w.lineId());
            assertTrue(w.basePrice() > 0);
            assertEquals(w, StallWares.byLine(stall, w.lineId()).orElseThrow());
        }
    }

    @Test
    void theStallsCarryTheModsGoods() {
        assertEquals(TradeGoods.FISH, StallWares.of(StallKind.FISH).get(0).lineId(), "fish is the port's trade good");
        assertTrue(StallWares.of(StallKind.PROVISIONS).stream().anyMatch(w -> w.lineId().getPath().equals("salt_pork")));
        assertTrue(StallWares.of(StallKind.PROVISIONS).stream().anyMatch(w -> w.lineId().getPath().equals("hardtack")));
        assertTrue(StallWares.of(StallKind.PROVISIONS).stream().anyMatch(w -> w.lineId().equals(TradeGoods.RUM)));
        assertTrue(StallWares.of(StallKind.CHANDLERY).stream().anyMatch(w -> w.lineId().getPath().equals("rope")));
        assertTrue(StallWares.of(StallKind.CHANDLERY).stream().anyMatch(w -> w.lineId().equals(TradeGoods.CLOTH)));
        assertTrue(StallWares.of(StallKind.CHANDLERY).stream().anyMatch(w -> w.lineId().getPath().equals("nails")));
        assertTrue(StallWares.of(StallKind.SHIPWRIGHT).stream().anyMatch(w -> w.lineId().getPath().equals("hull_patch")));
        assertFalse(StallWares.byLine(StallKind.SHIPWRIGHT, StallWares.of(StallKind.SHIPWRIGHT).get(1).lineId()).orElseThrow().buysBack());
    }
}
