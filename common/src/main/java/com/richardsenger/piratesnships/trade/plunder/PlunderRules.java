package com.richardsenger.piratesnships.trade.plunder;

import com.richardsenger.piratesnships.trade.market.PortKind;

/**
 * Selling plundered goods (design.md §10.3, §13.4). Pure: the market computes the normal sell total, these rules decide
 * what happens to a plundered sale.
 *
 * <ul>
 *   <li>Pirate island: the fence buys at {@code fenceDiscount} off the normal price and never notices.</li>
 *   <li>Navy outpost / seafarer village: plundered goods are refused (LAW3); nothing is sold. The law integration
 *       hears of the refusal through the market's result, never from these rules.</li>
 * </ul>
 */
public final class PlunderRules {

    /**
     * @param enabled       plunder marks matter at all (off = plundered goods sell like any other)
     * @param fenceDiscount share a pirate fence takes off the normal sell price (0..1)
     */
    public record Params(boolean enabled, double fenceDiscount) {
        public static final Params DEFAULTS = new Params(true, 0.35);
    }

    public enum Outcome {
        /** Not plundered (or the rules are off): a normal sale. */
        NORMAL,
        /** Sold to a pirate fence at a discount. */
        FENCED,
        /** Plunder offered at a port other than the fence: refused, nothing sold (LAW3). */
        REFUSED
    }

    /**
     * @param payout doubloons the seller receives (0 when refused)
     */
    public record Verdict(Outcome outcome, long payout) {
        /** Whether the sale goes through the market (false = refused, market unchanged). */
        public boolean sold() {
            return outcome != Outcome.REFUSED;
        }
    }

    private PlunderRules() {
    }

    /** Whether a port of {@code kind} refuses plunder-marked goods: every port but the fence, while marks matter. */
    public static boolean refuses(PortKind kind, boolean marksMatter) {
        return marksMatter && kind != PortKind.PIRATE_ISLAND;
    }

    /** The verdict for a sale whose normal total is {@code normalTotal}. */
    public static Verdict judge(PortKind kind, boolean plundered, long normalTotal, Params p) {
        if (!plundered || !p.enabled()) return new Verdict(Outcome.NORMAL, normalTotal);
        if (refuses(kind, true)) return new Verdict(Outcome.REFUSED, 0);
        double keep = 1.0 - Math.min(1.0, Math.max(0.0, p.fenceDiscount()));
        return new Verdict(Outcome.FENCED, (long) Math.floor(normalTotal * keep));
    }
}
