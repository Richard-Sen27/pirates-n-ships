package com.richardsenger.piratesnships.trade.market;

import com.richardsenger.piratesnships.trade.good.TradeGood;

/**
 * Market rules as plain values (filled from config by {@code TradeConfig.marketParams()}).
 *
 * @param volatility        log-price change per 64 units traded, for a good of sensitivity 1 in a port of depth 1
 *                          (0.05 = buying a stack raises the price by about 5 %)
 * @param recoveryPerDay    fraction of the price and stock deviation that recovers per in-game day (24000 ticks)
 * @param spread            buy price = mid × (1 + spread/2), sell price = mid × (1 − spread/2); must be &gt; 0
 * @param producedFactor    mid price multiplier for goods the port produces
 * @param neutralFactor     mid price multiplier for neutral goods
 * @param demandedFactor    mid price multiplier for goods the port demands
 * @param stockLimits       whether stock and absorption limits apply
 * @param stockProduced     units in stock for a produced good (× good stock factor × port depth)
 * @param stockNeutral      units in stock for a neutral good
 * @param stockDemanded     units in stock for a demanded good
 * @param absorbProduced    units of a produced good the port buys before it is saturated
 * @param absorbNeutral     units of a neutral good the port buys before it is saturated
 * @param absorbDemanded    units of a demanded good the port buys before it is saturated
 * @param depthVillage      market depth of a seafarer village (deeper = smaller price impact, bigger stock)
 * @param depthNavy         market depth of a navy outpost
 * @param depthPirate       market depth of a pirate island
 */
public record MarketParams(double volatility, double recoveryPerDay, double spread,
                           double producedFactor, double neutralFactor, double demandedFactor,
                           boolean stockLimits, double stockProduced, double stockNeutral, double stockDemanded,
                           double absorbProduced, double absorbNeutral, double absorbDemanded,
                           double depthVillage, double depthNavy, double depthPirate) {

    public static final long TICKS_PER_DAY = 24000L;
    public static final double UNITS_PER_STEP = 64.0;

    public static final MarketParams DEFAULTS = new MarketParams(0.05, 0.3, 0.2,
            0.6, 1.0, 1.6,
            true, 768, 192, 48,
            128, 512, 1536,
            1.0, 0.75, 0.5);

    public MarketParams {
        if (!(spread > 0)) throw new IllegalArgumentException("spread must be > 0 (it is what prevents money loops)");
    }

    public double buyMultiplier() {
        return 1.0 + spread / 2.0;
    }

    public double sellMultiplier() {
        return Math.max(0.0, 1.0 - spread / 2.0);
    }

    public double roleFactor(GoodRole role) {
        return switch (role) {
            case PRODUCES -> producedFactor;
            case DEMANDS -> demandedFactor;
            case NEUTRAL, NOT_TRADED -> neutralFactor;
        };
    }

    public double depth(PortKind kind) {
        double d = switch (kind) {
            case SEAFARER_VILLAGE -> depthVillage;
            case NAVY_OUTPOST -> depthNavy;
            case PIRATE_ISLAND -> depthPirate;
        };
        return Math.max(0.01, d);
    }

    /** Log-price change per unit traded. */
    public double pressurePerUnit(TradeGood good, PortKind kind) {
        return volatility * good.sensitivity() / (UNITS_PER_STEP * depth(kind));
    }

    /** Units in stock at equilibrium. */
    public double stockCap(GoodRole role, TradeGood good, PortKind kind) {
        double base = switch (role) {
            case PRODUCES -> stockProduced;
            case DEMANDS -> stockDemanded;
            case NEUTRAL, NOT_TRADED -> stockNeutral;
        };
        return base * good.stockFactor() * depth(kind);
    }

    /** Units the port buys from equilibrium before it stops buying. */
    public double absorbCap(GoodRole role, TradeGood good, PortKind kind) {
        double base = switch (role) {
            case PRODUCES -> absorbProduced;
            case DEMANDS -> absorbDemanded;
            case NEUTRAL, NOT_TRADED -> absorbNeutral;
        };
        return base * good.stockFactor() * depth(kind);
    }

    /** Fraction of a deviation left after {@code ticks} (exact for any split of the interval). */
    public double remainingAfter(long ticks) {
        if (ticks <= 0) return 1.0;
        double keep = 1.0 - Math.min(1.0, Math.max(0.0, recoveryPerDay));
        return Math.pow(keep, ticks / (double) TICKS_PER_DAY);
    }
}
