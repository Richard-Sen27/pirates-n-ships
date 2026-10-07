package com.richardsenger.piratesnships.law.brig;

import com.richardsenger.piratesnships.law.turnin.TurnInRules;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/**
 * Pure rules of the law outcomes as NPC interactions (LA2, docs/design.md §13.1, §13.3): paying a fine and ransoming
 * at a navy officer, press-ganging with the captain's whistle and releasing by hand. No world access;
 * {@link PrisonerInteractions} and {@code law.turnin.OfficerTurnIns} feed them.
 *
 * <p><b>Officer precedence</b> ({@link #officerAction}): a bounty proof in the hand used is always a proof claim. An
 * empty main hand hands over the player's prisoners near the officer (turn-in or ransom). Doubloons in the main hand
 * pay the fine; sneaking with doubloons hands over prisoners first if there are any (so a player never has to empty
 * the hand to deliver), else pays the fine too. Anything else passes to the officer's other interactions.
 */
public final class PrisonerInteractionRules {

    private PrisonerInteractionRules() {
    }

    // --- Fines --------------------------------------------------------------------------------------------------

    /**
     * What the coins in hand buy: whole points only, as many as the coins cover, never more than owed.
     *
     * @param points      whole points paid off (the score rounded up counts as owed, so a fractional rest is cleared
     *                    with its last whole point)
     * @param doubloons   the offer handed to {@code LawService.payFine}: {@code points × cost per point}
     * @param clearsScore the payment clears the whole score
     */
    public record FineQuote(int points, long doubloons, boolean clearsScore) {
        public static final FineQuote NONE = new FineQuote(0, 0, false);
    }

    public static FineQuote fineQuote(double score, long coins, int costPerPoint) {
        if (score <= 0 || coins <= 0 || costPerPoint <= 0) return FineQuote.NONE;
        long owed = (long) Math.ceil(score - 1e-9);
        if (owed <= 0) return FineQuote.NONE;
        long affordable = coins / costPerPoint;
        int points = (int) Math.min(Integer.MAX_VALUE, Math.min(owed, affordable));
        return new FineQuote(points, (long) points * costPerPoint, points > 0 && points >= owed);
    }

    // --- The officer --------------------------------------------------------------------------------------------

    /** What a use of a navy officer does, by {@link #officerAction}. */
    public enum OfficerAction { PROOF, PRISONERS, FINE, PASS }

    /**
     * @param proof         a bounty proof in the hand used
     * @param emptyMainHand the hand used is the main hand and it is empty
     * @param coinsMainHand the hand used is the main hand and holds doubloons
     * @param prisonersNear the player's prisoners stand within the delivery range of the officer
     */
    public static OfficerAction officerAction(boolean proof, boolean emptyMainHand, boolean coinsMainHand, boolean sneaking,
                                              boolean prisonersNear) {
        if (proof) return OfficerAction.PROOF;
        if (emptyMainHand) return prisonersNear ? OfficerAction.PRISONERS : OfficerAction.PASS;
        if (coinsMainHand) return sneaking && prisonersNear ? OfficerAction.PRISONERS : OfficerAction.FINE;
        return OfficerAction.PASS;
    }

    /** What the officer does with one prisoner handed over. */
    public enum Disposition { DELIVER, RANSOM, NO_PORT, REFUSE }

    /**
     * Turn-in first (a bounty or a pirate reward, {@link TurnInRules#delivery}), then ransom: a navy officer, navy
     * soldier or merchant ({@code ransomKind != null}; pirates never have one) is ransomed where the port rule allows
     * it. Players are never ransomed.
     */
    public static Disposition disposition(TurnInRules.Delivery delivery, @Nullable RansomRules.Kind ransomKind,
                                          boolean prisonerIsPlayer, boolean portAllows) {
        if (delivery.deliverable()) return Disposition.DELIVER;
        if (prisonerIsPlayer || ransomKind == null) return Disposition.REFUSE;
        return portAllows ? Disposition.RANSOM : Disposition.NO_PORT;
    }

    /** {@code law.ransom_needs_port}: when on, only an officer inside a registered navy outpost pays a ransom. */
    public static boolean portAllows(boolean needsPort, boolean officerInNavyPort) {
        return !needsPort || officerInNavyPort;
    }

    // --- Press-gang ---------------------------------------------------------------------------------------------

    /** Outcome of the press-gang check; {@link #OK} means it may happen. */
    public enum PressGang {
        OK, DISABLED, NOT_A_PRISONER, NOT_A_SAILOR, NOT_ON_SHIP, NOT_YOUR_SHIP;

        public boolean ok() {
            return this == OK;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * A shackled sailor standing on a ship that the captain owns or that has no owner can be press-ganged.
     *
     * @param shipOwner the owner of the ship the prisoner stands on, {@code null} for none (or no ship)
     */
    public static PressGang pressGang(boolean enabled, boolean prisoner, boolean sailor, boolean onShip,
                                      @Nullable UUID shipOwner, UUID captain) {
        if (!enabled) return PressGang.DISABLED;
        if (!prisoner) return PressGang.NOT_A_PRISONER;
        if (!sailor) return PressGang.NOT_A_SAILOR;
        if (!onShip) return PressGang.NOT_ON_SHIP;
        if (shipOwner != null && !shipOwner.equals(captain)) return PressGang.NOT_YOUR_SHIP;
        return PressGang.OK;
    }

    // --- Release ------------------------------------------------------------------------------------------------

    /**
     * Sneak-use with an empty main hand on one's own prisoner (the captor; a loose chain still belongs to it) releases
     * it. The off-hand event and any other hand content pass.
     */
    public static boolean releases(boolean enabled, boolean sneaking, boolean mainHand, boolean emptyHand, boolean heldByPlayer) {
        return enabled && sneaking && mainHand && emptyHand && heldByPlayer;
    }
}
