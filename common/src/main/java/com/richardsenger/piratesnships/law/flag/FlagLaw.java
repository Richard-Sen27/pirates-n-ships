package com.richardsenger.piratesnships.law.flag;

import com.richardsenger.piratesnships.law.crime.CrimeType;
import com.richardsenger.piratesnships.law.crime.WantedLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Pure flag rules (docs/design.md §4.7): the reaction table, false-flag legitimacy and the crimes flags lead to.
 * Detection of false colors is in {@link FalseColorsDetection}.
 */
public final class FlagLaw {

    private FlagLaw() {
    }

    /**
     * What the captain's flag claims about them, judged against their real standing.
     *
     * @param navyStanding     the captain's navy reputation (plain input until the reputation system exists)
     * @param hasActiveBounty  whether any bounty is on the captain
     */
    public record CaptainStanding(int navyStanding, boolean hasActiveBounty) {
    }

    /**
     * The reaction table. A flag is taken at face value here; use {@link #isFalseFlag} plus detection to decide
     * whether the observer sees through it.
     *
     * <pre>
     *              NONE/MERCHANT  NAVY      JOLLY_ROGER     CUSTOM
     *  NAVY        NEUTRAL        FRIENDLY  HOSTILE         NEUTRAL
     *  PIRATES     NEUTRAL        HOSTILE   FRIENDLY        NEUTRAL
     *  MERCHANTS   NEUTRAL        FRIENDLY  MAY_SURRENDER*  NEUTRAL
     * </pre>
     * (*) {@code HOSTILE} (they fight back) when NPC surrender is disabled. Whether pirate NPCs prey on neutral ships
     * anyway is an AI decision, not a flag reaction.
     */
    public static Reaction react(Faction observer, FlagKind flag, boolean surrenderEnabled) {
        return switch (observer) {
            case NAVY -> switch (flag) {
                case NAVY -> Reaction.FRIENDLY;
                case JOLLY_ROGER -> Reaction.HOSTILE;
                case NONE, MERCHANT, CUSTOM -> Reaction.NEUTRAL;
            };
            case PIRATES -> switch (flag) {
                case JOLLY_ROGER -> Reaction.FRIENDLY;
                case NAVY -> Reaction.HOSTILE;
                case NONE, MERCHANT, CUSTOM -> Reaction.NEUTRAL;
            };
            case MERCHANTS -> switch (flag) {
                case NAVY -> Reaction.FRIENDLY;
                case JOLLY_ROGER -> surrenderEnabled ? Reaction.MAY_SURRENDER : Reaction.HOSTILE;
                case NONE, MERCHANT, CUSTOM -> Reaction.NEUTRAL;
            };
        };
    }

    /**
     * Whether {@code flag} is a false flag for this captain.
     * <ul>
     *   <li>Jolly Roger: never (nobody pretends to be a pirate).</li>
     *   <li>Navy flag: false unless the captain has at least {@code minNavyStanding} and no bounty.</li>
     *   <li>None, merchant, custom: these all claim "harmless ship", so they are false for a captain with a bounty.</li>
     * </ul>
     */
    public static boolean isFalseFlag(FlagKind flag, CaptainStanding captain, int minNavyStanding) {
        return switch (flag) {
            case JOLLY_ROGER -> false;
            case NAVY -> captain.hasActiveBounty() || captain.navyStanding() < minNavyStanding;
            case NONE, MERCHANT, CUSTOM -> captain.hasActiveBounty();
        };
    }

    /**
     * FL2's interim false-colours rule (until reputation exists, docs/design.md §15): a ship flies false colours when
     * its flag is a false flag for the captain by {@link #isFalseFlag} <b>and</b> the captain's wanted level is at least
     * {@code wantedThreshold} (ordinal of {@link WantedLevel}: 0 clean, 1 suspect, 2 wanted, 3 notorious). The world
     * adapter passes a navy standing of 0 for everyone, so today a navy flag is false exactly for a captain at or above
     * the threshold (with {@code navy_flag_min_standing} above 0); §15 replaces the standing input and can drop the
     * wanted check. Only the navy flag is judged here: the harmless flags are not checked in the world yet.
     */
    public static boolean fliesFalseColours(FlagKind shown, CaptainStanding captain, int minNavyStanding,
                                            WantedLevel captainLevel, int wantedThreshold) {
        if (shown != FlagKind.NAVY) return false;
        return isFalseFlag(shown, captain, minNavyStanding) && captainLevel.ordinal() >= Math.max(0, wantedThreshold);
    }

    /**
     * The crime for a cannonball from another ship hitting a ship (FL2), or {@code null}:
     * <ul>
     *   <li>nothing for hitting one's own ship (the firing ship, or a ship of the same owner);</li>
     *   <li>{@link CrimeType#ATTACK_STRUCK_COLORS} for a ship that has struck its colours;</li>
     *   <li>{@link CrimeType#ATTACK_NEUTRAL_SHIP} for a ship flying a merchant or custom (neutral) flag;</li>
     *   <li>nothing for a ship without a flag, under the Jolly Roger, or under a navy flag (real navy ships do not
     *       exist yet; {@link #crimeForAttackingShip} covers them once they do).</li>
     * </ul>
     *
     * @param kind   the target's flag (also while struck), {@code NONE} without one
     * @param struck the target's flag is struck
     * @param ownShip the shooter hit its own ship or one of its owner's
     */
    public static @Nullable CrimeType crimeForShipHit(FlagKind kind, boolean struck, boolean ownShip) {
        if (ownShip || kind == FlagKind.NONE) return null;
        if (struck) return CrimeType.ATTACK_STRUCK_COLORS;
        return switch (kind) {
            case MERCHANT, CUSTOM -> CrimeType.ATTACK_NEUTRAL_SHIP;
            case NONE, NAVY, JOLLY_ROGER -> null;
        };
    }

    /** The crime for being caught under false colors (§4.7: "raises the criminal score heavily"). */
    public static CrimeType crimeWhenCaughtUnderFalseColors() {
        return CrimeType.CAUGHT_FALSE_COLORS;
    }

    /** The crime for being seen by {@code observer} under {@code flag}, or {@code null} if none. */
    public static @Nullable CrimeType crimeWhenSeen(FlagKind flag, Faction observer) {
        if (flag == FlagKind.JOLLY_ROGER && observer != Faction.PIRATES) return CrimeType.SEEN_UNDER_JOLLY_ROGER;
        return null;
    }

    /**
     * The crime for attacking a ship, or {@code null} if attacking it is legal.
     *
     * @param targetFlag        the flag the target ship flies (or flew before striking)
     * @param targetStruck      whether the target has struck its colors (surrendered)
     * @param targetIsRealNavy  whether the target really is a navy ship (a navy flag on a pirate is no protection)
     */
    public static @Nullable CrimeType crimeForAttackingShip(FlagKind targetFlag, boolean targetStruck, boolean targetIsRealNavy) {
        if (targetStruck) return CrimeType.ATTACK_STRUCK_COLORS;
        if (targetIsRealNavy) return CrimeType.ATTACK_NAVY;
        return switch (targetFlag) {
            case JOLLY_ROGER -> null;
            case NAVY, NONE, MERCHANT, CUSTOM -> CrimeType.ATTACK_NEUTRAL_SHIP;
        };
    }
}
