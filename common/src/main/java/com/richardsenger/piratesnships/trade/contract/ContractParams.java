package com.richardsenger.piratesnships.trade.contract;

/**
 * Contract rules as plain values (filled from config by {@code TradeConfig.contractParams()}).
 *
 * @param enabled               harbor masters offer contracts
 * @param offersPerDay          offers a port generates per day
 * @param minQuantity           smallest quantity (before the good's stock factor)
 * @param maxQuantity           largest quantity (before the good's stock factor)
 * @param rewardBase            reward per unit of base value, before bonuses
 * @param priceDifferenceWeight extra reward per unit of base value per point of role price factor difference
 *                              (destination factor − origin factor)
 * @param distanceBonusPer1000  reward multiplier gained per 1000 blocks of distance
 * @param riskBonus             reward multiplier gained at risk 1 (route through pirate waters)
 * @param blocksPerDay          expected travel per day when setting deadlines
 * @param slackDays             extra days on top of the travel time
 * @param offerLifetimeDays     days an offer stays on the board after the day it was made
 * @param depositFraction       share of the reward paid as deposit on accepting
 * @param maxActivePerPlayer    accepted contracts one player may hold at once
 */
public record ContractParams(boolean enabled, int offersPerDay, int minQuantity, int maxQuantity,
                             double rewardBase, double priceDifferenceWeight, double distanceBonusPer1000, double riskBonus,
                             double blocksPerDay, int slackDays, int offerLifetimeDays, double depositFraction,
                             int maxActivePerPlayer) {

    public static final ContractParams DEFAULTS = new ContractParams(true, 3, 32, 192,
            0.6, 0.8, 0.15, 0.6,
            4000.0, 2, 2, 0.2,
            3);
}
