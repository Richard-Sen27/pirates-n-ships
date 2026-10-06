package com.richardsenger.piratesnships.law.bounty;

import java.util.EnumMap;
import java.util.Map;

/**
 * Plain parameters of the bounty rules. Filled from config by {@code LawConfig.bountyRules()}.
 *
 * @param navyBounties          whether the navy places bounties automatically (false when the criminal score is off)
 * @param navyThreshold         score at which the navy bounty is placed (inclusive)
 * @param navyWithdrawRatio     the navy bounty is withdrawn once the score falls below {@code threshold * ratio}
 *                              (hysteresis, so a score hovering at the threshold doesn't flap)
 * @param navyPerPoint          navy bounty doubloons per criminal score point
 * @param playerBounties        whether players may place bounties
 * @param playerMinimum         smallest player bounty in doubloons
 * @param playerDurationTicks   how long a player bounty stays on the board ({@code 0} = forever)
 * @param aliveFactor           payout multiplier for delivering the target alive instead of a proof item
 * @param scoreAfterClaimFactor the target's criminal score is multiplied by this after a claim
 * @param turnInRewards         navy reward for delivering a captured pirate NPC, by tier
 */
public record BountyRules(boolean navyBounties, double navyThreshold, double navyWithdrawRatio, double navyPerPoint,
                          boolean playerBounties, int playerMinimum, long playerDurationTicks,
                          double aliveFactor, double scoreAfterClaimFactor, Map<PirateTier, Integer> turnInRewards) {

    public BountyRules {
        turnInRewards = Map.copyOf(turnInRewards);
    }

    public static BountyRules defaults() {
        Map<PirateTier, Integer> rewards = new EnumMap<>(PirateTier.class);
        for (PirateTier t : PirateTier.values()) rewards.put(t, t.defaultReward());
        return new BountyRules(true, 50.0, 0.5, 2.0, true, 10, 0L, 1.5, 0.0, rewards);
    }

    /** Score below which an existing navy bounty is withdrawn. */
    public double withdrawBelow() {
        return navyThreshold * navyWithdrawRatio;
    }

    /** The navy bounty for a score (at least 1 doubloon). */
    public int navyAmount(double score) {
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, Math.round(score * navyPerPoint)));
    }

    public int turnInReward(PirateTier tier) {
        return Math.max(0, turnInRewards.getOrDefault(tier, tier.defaultReward()));
    }

    public BountyRules withNavy(boolean enabled) {
        return new BountyRules(enabled, navyThreshold, navyWithdrawRatio, navyPerPoint, playerBounties, playerMinimum,
                playerDurationTicks, aliveFactor, scoreAfterClaimFactor, turnInRewards);
    }

    public BountyRules withPlayerBounties(boolean enabled, int minimum, long durationTicks) {
        return new BountyRules(navyBounties, navyThreshold, navyWithdrawRatio, navyPerPoint, enabled, minimum,
                durationTicks, aliveFactor, scoreAfterClaimFactor, turnInRewards);
    }

    public BountyRules withClaim(double alive, double scoreFactor) {
        return new BountyRules(navyBounties, navyThreshold, navyWithdrawRatio, navyPerPoint, playerBounties,
                playerMinimum, playerDurationTicks, alive, scoreFactor, turnInRewards);
    }
}
