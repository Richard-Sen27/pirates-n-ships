package com.richardsenger.piratesnships.rpg.career;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The pure rules of the rank rewards (docs/design.md §15, CAR2): what a {@link CareerRecord} is entitled to. No world
 * access and no config reads; {@link CareerRewards} feeds them the config ({@link Params}) and applies the answers.
 */
public final class CareerRewardRules {

    /**
     * The rewards' config.
     *
     * @param flagRight           {@code careers.rewards.flag_right}: navy officers may fly the navy flag
     * @param flagRightRank       lowest navy rank with the flag right
     * @param feeWaiver           {@code careers.rewards.fee_waiver}: navy officers dock at outposts for free
     * @param feeWaiverRank       lowest navy rank whose docking fee is waived
     * @param navyShipyard        {@code careers.rewards.navy_shipyard}: outposts take ship orders from officers
     * @param ordersMinRank       lowest navy rank the navy shipyard builds for
     * @param shipPrice           price factor of a navy-shipyard ship by rank (missing ranks: 1.0)
     * @param infamyPrices        {@code careers.rewards.infamy_prices}: fences favour the infamous
     * @param infamyPriceBonus    price score a Pirate Lord gains at pirate islands (lower ranks: scaled)
     * @param infamyFriendly      {@code careers.rewards.infamy_pirates_friendly}: pirates spare the infamous
     * @param piratesFriendlyRank lowest infamy rank the pirates leave alone
     */
    public record Params(boolean flagRight, NavyRank flagRightRank, boolean feeWaiver, NavyRank feeWaiverRank,
                         boolean navyShipyard, NavyRank ordersMinRank, Map<NavyRank, Double> shipPrice,
                         boolean infamyPrices, int infamyPriceBonus, boolean infamyFriendly, InfamyRank piratesFriendlyRank) {

        public Params {
            Map<NavyRank, Double> copy = new EnumMap<>(NavyRank.class);
            if (shipPrice != null) copy.putAll(shipPrice);
            shipPrice = Collections.unmodifiableMap(copy);
        }

        /** The shipped defaults (docs/design.md §15, CAR2). */
        public static Params defaults() {
            return new Params(true, NavyRank.LIEUTENANT, true, NavyRank.LIEUTENANT, true, NavyRank.CAPTAIN,
                    defaultShipPrices(), true, DEFAULT_INFAMY_PRICE_BONUS, true, InfamyRank.DREAD_CAPTAIN);
        }
    }

    public static final int DEFAULT_INFAMY_PRICE_BONUS = 20;

    private CareerRewardRules() {
    }

    /** Default price factor at the navy shipyard: 1.0 up to Lieutenant, 0.7 Captain, 0.5 Commodore, 0.4 Admiral. */
    public static double defaultShipPrice(NavyRank rank) {
        return switch (rank) {
            case NONE, MIDSHIPMAN, LIEUTENANT -> 1.0;
            case CAPTAIN -> 0.7;
            case COMMODORE -> 0.5;
            case ADMIRAL -> 0.4;
        };
    }

    public static Map<NavyRank, Double> defaultShipPrices() {
        Map<NavyRank, Double> m = new EnumMap<>(NavyRank.class);
        for (NavyRank r : NavyRank.values()) m.put(r, defaultShipPrice(r));
        return m;
    }

    /** Serving at {@code min} or above (a rank requirement is never met outside the navy). */
    static boolean serves(CareerRecord r, NavyRank min) {
        return r.enlisted() && r.navy() != NavyRank.NONE && r.navy().atLeast(min);
    }

    /** Whether the record may fly the navy flag whatever its navy reputation (§4.7). */
    public static boolean hasFlagRight(CareerRecord r, Params p) {
        return p.flagRight() && serves(r, p.flagRightRank());
    }

    /**
     * The navy standing the false-flag rule and the docking fee judge: the navy reputation, raised to
     * {@code navyFlagMinStanding} for a rank with the flag right (an officer's flag is never false for low reputation;
     * a bounty still makes it false).
     */
    public static int effectiveNavyStanding(CareerRecord r, int navyStanding, int navyFlagMinStanding, Params p) {
        return hasFlagRight(r, p) ? Math.max(navyStanding, navyFlagMinStanding) : navyStanding;
    }

    /** Whether navy outposts waive the docking fee for this record. */
    public static boolean feeWaived(CareerRecord r, Params p) {
        return p.feeWaiver() && serves(r, p.feeWaiverRank());
    }

    /** Whether the navy shipyard (the Orders tab at an outpost's desk) builds for this record. */
    public static boolean navyShipyard(CareerRecord r, Params p) {
        return p.navyShipyard() && serves(r, p.ordersMinRank());
    }

    /** The price factor of a ship ordered at a navy outpost (clamped to 0..1; 1.0 outside the navy). */
    public static double shipPriceFactor(CareerRecord r, Params p) {
        if (!r.enlisted()) return 1.0;
        double f = p.shipPrice().getOrDefault(r.navy(), 1.0);
        return Double.isNaN(f) ? 1.0 : Math.max(0.0, Math.min(1.0, f));
    }

    /**
     * The price score {@code rank} adds at pirate islands: {@code max} at the top rank, scaled linearly by the rank's
     * step (defaults: Deckhand 0, Buccaneer 7, Dread Captain 13, Pirate Lord 20).
     */
    public static int infamyPriceBonus(InfamyRank rank, int max) {
        int top = InfamyRank.values().length - 1;
        return (int) Math.round(Math.max(0, max) * (double) rank.ordinal() / top);
    }

    /** {@link #infamyPriceBonus(InfamyRank, int)} with the toggle. */
    public static int infamyPriceBonus(CareerRecord r, Params p) {
        return p.infamyPrices() ? infamyPriceBonus(r.infamy(), p.infamyPriceBonus()) : 0;
    }

    /** Whether the pirates leave this record's player alone for the infamy alone. */
    public static boolean piratesFriendly(CareerRecord r, Params p) {
        return p.infamyFriendly() && r.infamy().atLeast(p.piratesFriendlyRank());
    }

    // ------------------------------------------------------------------ promotion gifts

    /** The ledger key of a navy rank's gift. */
    public static String giftKey(NavyRank rank) {
        return "navy." + rank.id();
    }

    /** The ledger key of an infamy rank's gift. */
    public static String giftKey(InfamyRank rank) {
        return "infamy." + rank.id();
    }

    /**
     * The gift keys due when a career goes from {@code before} to {@code after}: every rank gained on either ladder
     * (above the old rank, up to and including the new one) whose gift is not in {@code gifted} yet, lowest first. A
     * rank is gifted once per player, so resigning and enlisting again gives nothing twice.
     */
    public static List<String> giftsDue(CareerRecord before, CareerRecord after, Set<String> gifted) {
        List<String> out = new ArrayList<>();
        if (after.enlisted()) {
            NavyRank from = before.enlisted() ? before.navy() : NavyRank.NONE;
            for (NavyRank r : NavyRank.values()) {
                if (r.ordinal() > from.ordinal() && r.ordinal() <= after.navy().ordinal() && !gifted.contains(giftKey(r))) {
                    out.add(giftKey(r));
                }
            }
        }
        for (InfamyRank r : InfamyRank.values()) {
            if (r.ordinal() > before.infamy().ordinal() && r.ordinal() <= after.infamy().ordinal() && !gifted.contains(giftKey(r))) {
                out.add(giftKey(r));
            }
        }
        return out;
    }
}
