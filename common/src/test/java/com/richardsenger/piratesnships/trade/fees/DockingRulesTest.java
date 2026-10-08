package com.richardsenger.piratesnships.trade.fees;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.trade.fees.DockingRules.Params;
import com.richardsenger.piratesnships.trade.fees.DockingRules.Payer;
import org.junit.jupiter.api.Test;

class DockingRulesTest {

    private static final Params P = Params.DEFAULTS;
    private static final Params NO_CHEST = new Params(100, 6.0, 0.1, 1, false, true);
    private static final Params THREE_DAYS = new Params(100, 6.0, 0.1, 3, true, true);

    @Test
    void anchoredShipIsDockedAtAnySpeedAndDistance() {
        assertTrue(DockingRules.docked(true, Double.POSITIVE_INFINITY, 5.0, P));
    }

    @Test
    void shipAtRestBesideABerthIsDocked() {
        assertTrue(DockingRules.docked(false, 0.0, 0.0, P));
        assertTrue(DockingRules.docked(false, 6.0, 0.09, P));
    }

    @Test
    void shipDriftingPastABerthIsNotDocked() {
        assertFalse(DockingRules.docked(false, 2.0, 0.1, P));
        assertFalse(DockingRules.docked(false, 2.0, 1.5, P));
    }

    @Test
    void shipAtRestFarFromEveryBerthIsNotDocked() {
        assertFalse(DockingRules.docked(false, 6.01, 0.0, P));
        assertFalse(DockingRules.docked(false, Double.POSITIVE_INFINITY, 0.0, P));
    }

    @Test
    void firstVisitIsDue() {
        assertTrue(DockingRules.due(null, 12, P));
    }

    @Test
    void chargedOncePerPeriod() {
        assertFalse(DockingRules.due(12L, 12, P));
        assertTrue(DockingRules.due(12L, 13, P));
        assertFalse(DockingRules.due(12L, 14, THREE_DAYS));
        assertTrue(DockingRules.due(12L, 15, THREE_DAYS));
    }

    @Test
    void dayGoingBackwardsChargesAgain() {
        assertTrue(DockingRules.due(12L, 3, P));
    }

    @Test
    void zeroPeriodCountsAsOneDay() {
        Params zero = new Params(100, 6.0, 0.1, 0, true, true);
        assertFalse(DockingRules.due(5L, 5, zero));
        assertTrue(DockingRules.due(5L, 6, zero));
    }

    @Test
    void payerOrder() {
        assertEquals(Payer.WAIVED, DockingRules.payer(0, 0, 0, P));
        assertEquals(Payer.WALLET, DockingRules.payer(5, 5, 100, P));
        assertEquals(Payer.SHIP, DockingRules.payer(5, 4, 5, P));
        assertEquals(Payer.OWED, DockingRules.payer(5, 4, 4, P));
    }

    @Test
    void walletAndShipNeverSplitAFee() {
        assertEquals(Payer.OWED, DockingRules.payer(10, 6, 6, P));
    }

    @Test
    void shipChestCanBeSwitchedOff() {
        assertEquals(Payer.OWED, DockingRules.payer(5, 0, 100, NO_CHEST));
        assertEquals(Payer.WALLET, DockingRules.payer(5, 5, 0, NO_CHEST));
    }

    @Test
    void distanceToBox() {
        assertEquals(0.0, DockingRules.horizontalDistanceToBox(1, 1, 0, 0, 2, 2), 1e-9);
        assertEquals(3.0, DockingRules.horizontalDistanceToBox(-3, 1, 0, 0, 2, 2), 1e-9);
        assertEquals(5.0, DockingRules.horizontalDistanceToBox(5, 6, 0, 0, 2, 2), 1e-9);
    }
}
