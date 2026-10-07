package com.richardsenger.piratesnships.chart.tile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** What drawing on a map board costs (MAP3). */
class InkCostTest {

    @Test
    void aDrawPaysPerTile() {
        assertEquals(4, InkCost.drawTiles(4));
        assertEquals(4, InkCost.due(true, false, InkCost.drawTiles(4), 1));
        assertEquals(12, InkCost.due(true, false, InkCost.drawTiles(4), 3));
    }

    @Test
    void anUpdatePaysForChangedTilesOnlyAndAtLeastOneWhenAnythingChanged() {
        assertEquals(0, InkCost.updateTiles(0, false));
        assertEquals(2, InkCost.updateTiles(2, true));
        assertEquals(1, InkCost.updateTiles(0, true), "only the markers changed");
        assertEquals(3, InkCost.updateTiles(3, false), "changed tiles always count");
    }

    @Test
    void creativeAndADisabledCostAreFree() {
        assertEquals(0, InkCost.due(true, true, 9, 1));
        assertEquals(0, InkCost.due(false, false, 9, 1));
        assertEquals(0, InkCost.due(true, false, 9, 0));
        assertEquals(0, InkCost.due(true, false, 0, 1));
    }

    @Test
    void krakenInkIsWorthSeveralTiles() {
        assertEquals(8, InkCost.krakenWorth(8, 1));
        assertEquals(16, InkCost.krakenWorth(8, 2));
        assertEquals(3 + 2 * 8, InkCost.held(3, 2, 8));
    }

    @Test
    void paymentUsesTheFewestKrakenInksThenSacs() {
        assertEquals(InkCost.Payment.NONE, InkCost.pay(0, 0, 0, 8));
        assertEquals(new InkCost.Payment(4, 0), InkCost.pay(4, 10, 3, 8), "enough sacs: no kraken ink");
        // 10 due with 3 sacs and 2 kraken inks: one kraken ink (8) and two sacs
        assertEquals(new InkCost.Payment(2, 1), InkCost.pay(10, 3, 2, 8));
        // 8 due with 3 sacs: one kraken ink pays it all
        assertEquals(new InkCost.Payment(0, 1), InkCost.pay(8, 3, 2, 8));
        assertEquals(new InkCost.Payment(0, 2), InkCost.pay(16, 0, 2, 8));
        assertNull(InkCost.pay(4, 3, 0, 8), "not enough");
        assertNull(InkCost.pay(20, 3, 2, 8));
        assertNull(InkCost.pay(4, 3, 5, 0), "worthless kraken ink pays nothing");
    }
}
