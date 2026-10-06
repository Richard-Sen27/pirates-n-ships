package com.richardsenger.piratesnships.trade.plunder;

import com.richardsenger.piratesnships.trade.market.PortKind;
import net.minecraft.util.RandomSource;

/**
 * Selling plundered goods (design.md §10.3). Pure: the market computes the normal sell total, these rules decide what
 * happens to a plundered sale. The result tells the integration whether the sale was noticed, so it can report a crime
 * to the law module; the rules never call it.
 *
 * <ul>
 *   <li>Pirate island: the fence buys at {@code fenceDiscount} off the normal price and never notices.</li>
 *   <li>Navy outpost / seafarer village: each started 64 units has the port kind's chance to be noticed
 *       ({@code 1 − (1 − chance)^stacks}, so splitting a sale doesn't change the total risk). Noticed goods are
 *       confiscated (no payout, market unchanged) when {@code confiscateWhenNoticed}, else sold normally.</li>
 * </ul>
 */
public final class PlunderRules {

    /**
     * @param enabled               plunder marks matter at all (off = plundered goods sell like any other)
     * @param fenceDiscount         share a pirate fence takes off the normal sell price (0..1)
     * @param navyNoticeChance      chance per 64 units that a navy outpost notices plundered goods
     * @param villageNoticeChance   the same for a seafarer village
     * @param confiscateWhenNoticed noticed goods are taken without payment
     */
    public record Params(boolean enabled, double fenceDiscount, double navyNoticeChance, double villageNoticeChance,
                         boolean confiscateWhenNoticed) {
        public static final Params DEFAULTS = new Params(true, 0.35, 0.25, 0.05, true);
    }

    public enum Outcome {
        /** Not plundered (or the rules are off): a normal sale. */
        NORMAL,
        /** Sold to a pirate fence at a discount. */
        FENCED,
        /** Plundered, not noticed, sold at the normal price. */
        UNNOTICED,
        /** Noticed and sold anyway (confiscation off). */
        NOTICED_SOLD,
        /** Noticed and confiscated: no payout. */
        CONFISCATED
    }

    /**
     * @param payout  doubloons the seller receives
     * @param noticed whether the port noticed plundered goods (the integration reports this to the law module)
     * @param sold    whether the sale goes through the market (false = confiscated, market unchanged)
     */
    public record Verdict(Outcome outcome, long payout, boolean noticed, boolean sold) {
    }

    private PlunderRules() {
    }

    public static double noticeChance(PortKind kind, int quantity, Params p) {
        double perStack = switch (kind) {
            case NAVY_OUTPOST -> p.navyNoticeChance();
            case SEAFARER_VILLAGE -> p.villageNoticeChance();
            case PIRATE_ISLAND -> 0.0;
        };
        perStack = Math.min(1.0, Math.max(0.0, perStack));
        double stacks = Math.ceil(Math.max(0, quantity) / 64.0);
        return 1.0 - Math.pow(1.0 - perStack, stacks);
    }

    /** The verdict for a sale whose normal total is {@code normalTotal}; {@code roll} is uniform in [0, 1). */
    public static Verdict judge(PortKind kind, boolean plundered, int quantity, long normalTotal, double roll, Params p) {
        if (!plundered || !p.enabled()) return new Verdict(Outcome.NORMAL, normalTotal, false, true);
        if (kind == PortKind.PIRATE_ISLAND) {
            double keep = 1.0 - Math.min(1.0, Math.max(0.0, p.fenceDiscount()));
            return new Verdict(Outcome.FENCED, (long) Math.floor(normalTotal * keep), false, true);
        }
        boolean noticed = roll < noticeChance(kind, quantity, p);
        if (!noticed) return new Verdict(Outcome.UNNOTICED, normalTotal, false, true);
        if (p.confiscateWhenNoticed()) return new Verdict(Outcome.CONFISCATED, 0, true, false);
        return new Verdict(Outcome.NOTICED_SOLD, normalTotal, true, true);
    }

    public static Verdict judge(PortKind kind, boolean plundered, int quantity, long normalTotal, RandomSource random, Params p) {
        return judge(kind, plundered, quantity, normalTotal, random.nextDouble(), p);
    }
}
