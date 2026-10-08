package com.richardsenger.piratesnships.mob.captain;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The pirate captain's duel (BOS1, docs/design.md §15, §8.5 "duel bosses"), pure: who may challenge, who gets a truce,
 * and when the duel ends. The world side is {@link DuelChallenge}.
 *
 * <ul>
 *   <li><b>Challenge:</b> sneak and use the captain with a sword in the main hand. Refused while {@code duel_enabled}
 *       is off, to creative and spectator players, while the captain duels someone else, and to a player the captain
 *       holds a grudge against (who struck him before challenging). A captain already fighting the player on sight
 *       accepts: the challenge is how a player stops the whole crew joining in.</li>
 *   <li><b>Truce:</b> every other pirate within {@code duel_truce_range} of the captain keeps out: it does not attack
 *       the challenger on sight (it still fights back when hit). The truce is renewed while the duel lasts, so pirates
 *       that come close later keep out too.</li>
 *   <li><b>End:</b> the captain or the challenger dies, the challenger goes farther than {@code duel_leave_range},
 *       or {@code duel_max_seconds} pass. Then the truces end at once.</li>
 * </ul>
 */
public final class DuelRules {

    /** Game ticks a renewed truce lasts; the duel renews it every second, so it lapses soon after an unseen end. */
    public static final long TRUCE_RENEW_TICKS = 40L;

    private DuelRules() {
    }

    /** Config snapshot ({@code mobs.captain.duel_*}). */
    public record Params(boolean enabled, double truceRange, double leaveRange, long maxTicks) {
    }

    public enum Refusal {
        /** Accepted. */
        NONE,
        DISABLED,
        NOT_SNEAKING,
        NO_SWORD,
        /** Creative or spectator. */
        EXEMPT,
        /** The captain duels someone else. */
        BUSY,
        /** The challenger struck the captain before. */
        GRUDGE
    }

    /**
     * Whether {@code challenger} may challenge a captain whose current duel opponent is {@code opponent}
     * ({@code null}: none). A challenger who already duels him gets {@link Refusal#NONE} (the duel goes on).
     */
    public static Refusal challenge(Params p, boolean sneaking, boolean sword, boolean exempt, @Nullable UUID opponent,
                                    UUID challenger, boolean grudge) {
        if (!p.enabled()) return Refusal.DISABLED;
        if (!sneaking) return Refusal.NOT_SNEAKING;
        if (!sword) return Refusal.NO_SWORD;
        if (exempt) return Refusal.EXEMPT;
        if (opponent != null && !opponent.equals(challenger)) return Refusal.BUSY;
        if (grudge) return Refusal.GRUDGE;
        return Refusal.NONE;
    }

    /** Whether a pirate {@code distanceSqr} (squared blocks) from the captain keeps out of the duel. */
    public static boolean inTruce(Params p, double distanceSqr) {
        return distanceSqr <= p.truceRange() * p.truceRange();
    }

    /** Game time the duel started at {@code start} runs out. */
    public static long deadline(Params p, long start) {
        return start + p.maxTicks();
    }

    /** Game time a truce renewed at {@code now} lasts until (never past the duel's deadline). */
    public static long truceUntil(long now, long deadline) {
        return Math.min(now + TRUCE_RENEW_TICKS, deadline);
    }

    public enum End { NONE, CAPTAIN_DIED, CHALLENGER_DIED, CHALLENGER_LEFT, TIMED_OUT }

    /**
     * Why a running duel ends now, or {@link End#NONE}.
     *
     * @param challengerAlive the challenger is online, in the captain's level and alive
     * @param distanceSqr     squared blocks between them (ignored when the challenger is gone)
     */
    public static End check(Params p, boolean captainAlive, boolean challengerAlive, double distanceSqr, long now, long deadline) {
        if (!captainAlive) return End.CAPTAIN_DIED;
        if (!challengerAlive) return End.CHALLENGER_DIED;
        if (distanceSqr > p.leaveRange() * p.leaveRange()) return End.CHALLENGER_LEFT;
        if (now >= deadline) return End.TIMED_OUT;
        return End.NONE;
    }
}
