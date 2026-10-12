package com.richardsenger.piratesnships.trade.stall;

/**
 * The fixed prices of a village stall (LIFE1), pure: what a unit costs the player, and what the vendor pays for one.
 * The reputation swing is applied to the totals afterwards, as the market does ({@code rpg.market.MarketReputation}).
 */
public final class StallPricing {

    private StallPricing() {
    }

    /** Doubloons a unit of a ware of {@code basePrice} costs at a stall with {@code scale}: at least 1. */
    public static long unitBuy(double basePrice, double scale) {
        return Math.max(1L, Math.round(basePrice * Math.max(0.0, scale)));
    }

    /**
     * Doubloons the vendor pays for a unit he sells at {@code unitBuy}: {@code buyback} of it, rounded down; 0 when he
     * does not buy the ware back.
     */
    public static long unitSell(long unitBuy, double buyback, boolean buysBack) {
        if (!buysBack) return 0L;
        return (long) Math.floor(unitBuy * Math.max(0.0, Math.min(1.0, buyback)));
    }

    /** {@code unit × quantity}, saturating; 0 for no units. */
    public static long total(long unit, int quantity) {
        if (unit <= 0 || quantity <= 0) return 0L;
        return unit > Long.MAX_VALUE / quantity ? Long.MAX_VALUE : unit * quantity;
    }
}
