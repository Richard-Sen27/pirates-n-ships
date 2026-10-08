package com.richardsenger.piratesnships.trade.plunder;

import com.richardsenger.piratesnships.trade.market.PortKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlunderAndFeesTest {

    private static final PlunderRules.Params P = PlunderRules.Params.DEFAULTS;

    @Test
    void cleanGoodsSellNormallyEverywhere() {
        for (PortKind kind : PortKind.values()) {
            var v = PlunderRules.judge(kind, false, 100, P);
            assertEquals(PlunderRules.Outcome.NORMAL, v.outcome());
            assertEquals(100, v.payout());
            assertTrue(v.sold());
        }
    }

    @Test
    void fenceBuysAtADiscount() {
        var v = PlunderRules.judge(PortKind.PIRATE_ISLAND, true, 100, P);
        assertEquals(PlunderRules.Outcome.FENCED, v.outcome());
        assertEquals((long) Math.floor(100 * (1 - P.fenceDiscount())), v.payout());
        assertTrue(v.sold());
        assertFalse(PlunderRules.refuses(PortKind.PIRATE_ISLAND, true));
    }

    /** LAW3: every port but the fence refuses plunder outright; nothing is sold, nothing paid. */
    @Test
    void villagesAndNavyRefusePlunder() {
        for (PortKind kind : new PortKind[]{PortKind.NAVY_OUTPOST, PortKind.SEAFARER_VILLAGE}) {
            var v = PlunderRules.judge(kind, true, 100, P);
            assertEquals(PlunderRules.Outcome.REFUSED, v.outcome(), kind.name());
            assertEquals(0, v.payout());
            assertFalse(v.sold());
            assertTrue(PlunderRules.refuses(kind, true));
        }
    }

    @Test
    void plunderToggleOffSellsLikeAnyOtherGood() {
        var off = new PlunderRules.Params(false, 0.35);
        for (PortKind kind : PortKind.values()) {
            var v = PlunderRules.judge(kind, true, 100, off);
            assertEquals(PlunderRules.Outcome.NORMAL, v.outcome());
            assertEquals(100, v.payout());
            assertFalse(PlunderRules.refuses(kind, false));
        }
    }

    @Test
    void dockingFeesOnlyInNavyPortsAndWaivedForHighStanding() {
        var f = PortFees.Params.DEFAULTS;
        assertEquals(f.navyDockingFee(), PortFees.dockingFee(PortKind.NAVY_OUTPOST, 0, f));
        assertEquals(f.navyDockingFee(), PortFees.dockingFee(PortKind.NAVY_OUTPOST, f.waiverStanding() - 1, f));
        assertEquals(0, PortFees.dockingFee(PortKind.NAVY_OUTPOST, f.waiverStanding(), f));
        assertEquals(0, PortFees.dockingFee(PortKind.SEAFARER_VILLAGE, -500, f));
        assertEquals(0, PortFees.dockingFee(PortKind.PIRATE_ISLAND, -500, f));
        assertEquals(0, PortFees.dockingFee(PortKind.NAVY_OUTPOST, -500, new PortFees.Params(false, 5, 50)));
    }
}
