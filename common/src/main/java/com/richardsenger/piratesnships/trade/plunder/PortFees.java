package com.richardsenger.piratesnships.trade.plunder;

import com.richardsenger.piratesnships.trade.market.PortKind;

/** Docking fees (design.md §10.3 "Port fees (optional)"): only navy outposts charge, waived for high navy standing. */
public final class PortFees {

    /**
     * @param enabled         port fees on/off
     * @param navyDockingFee  doubloons per docking at a navy outpost
     * @param waiverStanding  navy standing at or above which the fee is waived
     */
    public record Params(boolean enabled, int navyDockingFee, int waiverStanding) {
        public static final Params DEFAULTS = new Params(true, 5, 50);
    }

    private PortFees() {
    }

    /** The fee a ship pays to dock, given the captain's navy standing (a plain number from the reputation system). */
    public static int dockingFee(PortKind kind, int navyStanding, Params p) {
        if (!p.enabled() || kind != PortKind.NAVY_OUTPOST) return 0;
        if (navyStanding >= p.waiverStanding()) return 0;
        return Math.max(0, p.navyDockingFee());
    }
}
