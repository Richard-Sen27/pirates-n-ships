package com.richardsenger.piratesnships.rpg.reputation;

/**
 * Pure reputation arithmetic (docs/design.md §15, REP1): the score range, decay toward 0, the shown score and the
 * market price swing. The service ({@link Reputation}) and the market hook ({@code rpg.market.MarketReputation}) only
 * feed it numbers.
 */
public final class ReputationRules {

    /** Lowest and highest score. */
    public static final double MIN = -100.0;
    public static final double MAX = 100.0;
    public static final long TICKS_PER_DAY = 24000L;

    private ReputationRules() {
    }

    public static double clamp(double score) {
        if (Double.isNaN(score)) return 0.0;
        return Math.max(MIN, Math.min(MAX, score));
    }

    /**
     * {@code score} after {@code ticks} of decay toward 0 at {@code perDay} points per in-game day (24000 ticks), never
     * past 0. Linear, so decaying in two steps gives the same result as in one.
     */
    public static double decay(double score, long ticks, double perDay) {
        if (ticks <= 0 || perDay <= 0 || score == 0.0) return score;
        double amount = perDay * ticks / TICKS_PER_DAY;
        return score > 0 ? Math.max(0.0, score - amount) : Math.min(0.0, score + amount);
    }

    /** The shown, whole score: rounded half away from zero, so −0.5 shows as −1 and 0.4 as 0. */
    public static int display(double score) {
        return (int) (Math.signum(score) * Math.floor(Math.abs(score) + 0.5));
    }

    /**
     * The price swing as a share of the price: {@code maxSwing × score / 100}, so +100 gives {@code +maxSwing} (liked)
     * and −100 gives {@code −maxSwing} (disliked).
     */
    public static double swing(int score, double maxSwing) {
        return Math.max(0.0, maxSwing) * clamp(score) / MAX;
    }

    /**
     * What a customer pays for goods whose market total is {@code total}: liked customers pay less, disliked ones more.
     * Rounded to the nearest doubloon; a price above 0 never drops to 0.
     */
    public static long buyPrice(long total, int score, double maxSwing) {
        if (total <= 0) return total;
        return Math.max(1L, Math.round(total * (1.0 - swing(score, maxSwing))));
    }

    /** What a seller receives for a sale paying {@code payout}: liked sellers get more, disliked ones less. */
    public static long sellPrice(long payout, int score, double maxSwing) {
        if (payout <= 0) return payout;
        return Math.max(0L, Math.round(payout * (1.0 + swing(score, maxSwing))));
    }
}
