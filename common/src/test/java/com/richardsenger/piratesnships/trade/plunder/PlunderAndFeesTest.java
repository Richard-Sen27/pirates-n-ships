package com.richardsenger.piratesnships.trade.plunder;

import com.richardsenger.piratesnships.trade.market.PortKind;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlunderAndFeesTest {

    private static final PlunderRules.Params P = PlunderRules.Params.DEFAULTS;

    @Test
    void cleanGoodsSellNormallyEverywhere() {
        for (PortKind kind : PortKind.values()) {
            var v = PlunderRules.judge(kind, false, 64, 100, 0.0, P);
            assertEquals(PlunderRules.Outcome.NORMAL, v.outcome());
            assertEquals(100, v.payout());
            assertFalse(v.noticed());
        }
    }

    @Test
    void fenceBuysAtADiscountAndNeverNotices() {
        for (double roll = 0; roll < 1; roll += 0.01) {
            var v = PlunderRules.judge(PortKind.PIRATE_ISLAND, true, 640, 100, roll, P);
            assertEquals(PlunderRules.Outcome.FENCED, v.outcome());
            assertEquals((long) Math.floor(100 * (1 - P.fenceDiscount())), v.payout());
            assertFalse(v.noticed());
            assertTrue(v.sold());
        }
        assertEquals(0.0, PlunderRules.noticeChance(PortKind.PIRATE_ISLAND, 6400, P));
    }

    @Test
    void navyNoticesAtTheConfiguredRate() {
        RandomSource random = RandomSource.create(12345);
        int noticed = 0, n = 20000;
        for (int i = 0; i < n; i++) {
            var v = PlunderRules.judge(PortKind.NAVY_OUTPOST, true, 64, 100, random, P);
            if (v.noticed()) {
                noticed++;
                assertEquals(PlunderRules.Outcome.CONFISCATED, v.outcome());
                assertEquals(0, v.payout());
                assertFalse(v.sold());
            } else {
                assertEquals(PlunderRules.Outcome.UNNOTICED, v.outcome());
                assertEquals(100, v.payout());
            }
        }
        assertEquals(P.navyNoticeChance(), noticed / (double) n, 0.015);
        // Seeded: same seed, same verdicts
        assertEquals(PlunderRules.judge(PortKind.NAVY_OUTPOST, true, 64, 100, RandomSource.create(5), P),
                PlunderRules.judge(PortKind.NAVY_OUTPOST, true, 64, 100, RandomSource.create(5), P));
    }

    @Test
    void riskScalesWithStacksSoSplittingDoesNotHelp() {
        double one = PlunderRules.noticeChance(PortKind.NAVY_OUTPOST, 64, P);
        double four = PlunderRules.noticeChance(PortKind.NAVY_OUTPOST, 256, P);
        assertEquals(1 - Math.pow(1 - one, 4), four, 1e-12, "four separate stacks = one sale of four stacks");
        assertTrue(PlunderRules.noticeChance(PortKind.SEAFARER_VILLAGE, 64, P) < one, "villages are less watchful");
        assertEquals(0.0, PlunderRules.noticeChance(PortKind.NAVY_OUTPOST, 0, P));
    }

    @Test
    void togglesForConfiscationAndPlunder() {
        var keep = new PlunderRules.Params(true, 0.35, 1.0, 0.0, false);
        var v = PlunderRules.judge(PortKind.NAVY_OUTPOST, true, 1, 100, 0.5, keep);
        assertEquals(PlunderRules.Outcome.NOTICED_SOLD, v.outcome());
        assertTrue(v.noticed() && v.sold());
        assertEquals(100, v.payout());
        var off = new PlunderRules.Params(false, 0.35, 1.0, 1.0, true);
        assertEquals(PlunderRules.Outcome.NORMAL, PlunderRules.judge(PortKind.NAVY_OUTPOST, true, 64, 100, 0.0, off).outcome());
        assertEquals(PlunderRules.Outcome.NORMAL, PlunderRules.judge(PortKind.PIRATE_ISLAND, true, 64, 100, 0.0, off).outcome());
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
