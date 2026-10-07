package com.richardsenger.piratesnships.law.turnin;

import com.richardsenger.piratesnships.law.LawService.ProofClaimOutcome;
import com.richardsenger.piratesnships.law.bounty.PirateTier;
import org.jetbrains.annotations.Nullable;

/**
 * Pure rules of turning in at a navy officer (docs/design.md §9, §13.2). No world access; {@link OfficerTurnIns}
 * feeds them.
 *
 * <ul>
 *   <li>The officer takes nothing while turn-ins are switched off ({@code law.bounty.turn_in_officers}) or while he is
 *       hostile to the player (a wanted player, or one who just hit him): the navy arrests criminals, it doesn't pay
 *       them.</li>
 *   <li>A bounty proof pays exactly what {@code LawService.claimWithProof} pays; a proof naming the claimant himself,
 *       a blank proof or one whose bounties are gone is refused and kept.</li>
 *   <li>A shackled prisoner held by the player and standing within {@code law.bounty.delivery_range} of the officer is
 *       delivered alive: its bounties pay {@code alive_factor} times (the board's claim rule), a pirate NPC adds its
 *       rank reward ({@code law.pirate_turn_in}). A prisoner with neither a bounty nor a reward above 0 is refused
 *       and stays with the player.</li>
 * </ul>
 */
public final class TurnInRules {

    private TurnInRules() {
    }

    /** Whether the officer deals with the player at all. */
    public enum Gate { OPEN, DISABLED, HOSTILE }

    public static Gate gate(boolean turnInsEnabled, boolean officerHostile) {
        if (!turnInsEnabled) return Gate.DISABLED;
        return officerHostile ? Gate.HOSTILE : Gate.OPEN;
    }

    /** The message for a proof claim's outcome (a translation key suffix, see {@link OfficerTurnIns#MSG}). */
    public static String proofMessage(ProofClaimOutcome outcome) {
        return switch (outcome) {
            case CLAIMED -> "proof.paid";
            case NOT_A_PROOF -> "proof.blank";
            case NO_BOUNTY -> "proof.no_bounty";
            case SELF_CLAIM -> "proof.self";
        };
    }

    /**
     * What delivering one prisoner can earn.
     *
     * @param deliverable whether the officer takes the prisoner at all
     * @param pirateTier  the tier to hand to the pirate turn-in ({@code null}: a plain alive bounty claim)
     */
    public record Delivery(boolean deliverable, @Nullable PirateTier pirateTier) {
    }

    /**
     * @param prisonerIsPlayer a shackled player (never a pirate turn-in; only a bounty makes them deliverable)
     * @param hasBounty        the prisoner has an active bounty
     * @param tier             the pirate rank of an NPC prisoner, {@code null} if it is not a pirate
     * @param reward           the configured reward for {@code tier} (0 = the navy pays nothing for it)
     */
    public static Delivery delivery(boolean prisonerIsPlayer, boolean hasBounty, @Nullable PirateTier tier, int reward) {
        boolean pirate = !prisonerIsPlayer && tier != null && reward > 0;
        return new Delivery(hasBounty || pirate, pirate ? tier : null);
    }

    /** Doubloons a delivery pays: the bounties times the alive factor (rounded like the board), plus the pirate reward. */
    public static long expectedPayout(long bountySum, double aliveFactor, int pirateReward) {
        long bounty = Math.min(Integer.MAX_VALUE, Math.round(bountySum * aliveFactor));
        return bounty + Math.max(0, pirateReward);
    }

    /** Whether a prisoner at squared distance {@code distanceSq} from the officer is close enough to hand over. */
    public static boolean inRange(double distanceSq, double range) {
        return distanceSq <= range * range;
    }
}
