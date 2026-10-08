package com.richardsenger.piratesnships.rpg.career;

import com.richardsenger.piratesnships.rpg.deeds.Deed;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The career rules (docs/design.md §15, CAR1), pure: promotion eligibility on both ladders, what the next rank still
 * needs, enlisting and the letter of marque, and what a deed does to a {@link CareerRecord} (counters, desertion, the
 * letter, prize money). {@code Careers} applies them to players.
 *
 * <p>The ladders exclude each other: a player in navy service never gains infamy, enlisting needs
 * {@link InfamyRank#DECKHAND}, and an infamy of {@link InfamyRank#BUCCANEER} or more set on a player in service is
 * desertion ({@link #turnsPirate}).
 */
public final class CareerRules {

    public static final long TICKS_PER_DAY = 24_000L;

    /** Deed ids of the quest deeds (QST1 adds them to {@link Deed}); matched by id so CAR1 compiles without them. */
    public static final String NAVY_QUEST_DEED = "complete_navy_quest";
    public static final String PIRATE_QUEST_DEED = "complete_pirate_quest";
    public static final String VILLAGE_QUEST_DEED = "complete_village_quest";

    /** What a victim is to the career counters. */
    public enum Victim { OTHER, CAPTAIN, OFFICER }

    /** One requirement of the next rank: a translation suffix, what the player has and what is needed. */
    public record Requirement(String key, long have, long need) {
        public boolean met() {
            return have >= need;
        }
    }

    public enum EnlistVerdict { OK, DISABLED, ALREADY_ENLISTED, INFAMOUS, PIRATE_FRIEND, WANTED, HOSTILE, LOW_STANDING }

    public enum LetterVerdict { OK, DISABLED, ENLISTED, ALREADY_HELD, BLOCKED, INFAMOUS, LOW_STANDING, TOO_POOR }

    /**
     * What a deed did: the new record, whether it was desertion (the player was in service and lost the rank), whether
     * it voided the letter, and the prize money it earned.
     */
    public record DeedOutcome(CareerRecord record, boolean deserted, boolean letterVoided, long prize) {
    }

    private CareerRules() {
    }

    // ------------------------------------------------------------------ counters

    /** Pirates killed (captains weighted) plus pirates turned in: what the navy ladder counts. */
    public static long piratesDefeated(CareerRecord r) {
        return r.count(CareerCounter.PIRATES_KILLED) + r.count(CareerCounter.PIRATES_TURNED_IN);
    }

    /** Merchants plundered, ships captured and navy officers killed: the infamy ladder's "captures or captains". */
    public static long captures(CareerRecord r) {
        return r.count(CareerCounter.MERCHANTS_PLUNDERED) + r.count(CareerCounter.SHIPS_CAPTURED)
                + r.count(CareerCounter.OFFICERS_KILLED);
    }

    /** The counters a deed adds. {@code amount} is the deed context's amount (doubloons of a fence sale). */
    public static Map<CareerCounter, Long> counters(Deed deed, Victim victim, long amount, int captainWeight) {
        Map<CareerCounter, Long> m = new EnumMap<>(CareerCounter.class);
        switch (deed) {
            case KILL_PIRATE -> {
                m.put(CareerCounter.PIRATES_KILLED, victim == Victim.CAPTAIN ? (long) Math.max(1, captainWeight) : 1L);
                if (victim == Victim.CAPTAIN) m.put(CareerCounter.CAPTAINS_KILLED, 1L);
            }
            case TURN_IN_PIRATE -> m.put(CareerCounter.PIRATES_TURNED_IN, 1L);
            case KILL_NAVY -> {
                m.put(CareerCounter.NAVY_KILLED, 1L);
                if (victim == Victim.OFFICER) m.put(CareerCounter.OFFICERS_KILLED, 1L);
            }
            case PLUNDER_MERCHANT -> m.put(CareerCounter.MERCHANTS_PLUNDERED, 1L);
            case CAPTURE_NAVY, CAPTURE_PIRATE -> m.put(CareerCounter.SHIPS_CAPTURED, 1L); // WS3b
            case FENCE_PLUNDER -> {
                if (amount > 0) m.put(CareerCounter.PLUNDER_COINS, amount);
            }
            default -> {
                switch (deed.id()) {
                    case NAVY_QUEST_DEED -> m.put(CareerCounter.NAVY_QUESTS, 1L);
                    case PIRATE_QUEST_DEED -> m.put(CareerCounter.PIRATE_QUESTS, 1L);
                    case VILLAGE_QUEST_DEED -> m.put(CareerCounter.VILLAGE_QUESTS, 1L);
                    default -> {
                    }
                }
            }
        }
        return m;
    }

    public static boolean isDesertionDeed(Deed deed, CareerThresholds t) {
        return t.desertionDeeds().contains(deed.id());
    }

    /**
     * Applies a deed at game time {@code now}: adds its counters; a desertion deed in service drops the navy rank
     * (the caller reports the crime) and voids an active letter; a pirate kill under an active letter earns prize money.
     */
    public static DeedOutcome onDeed(CareerRecord r, Deed deed, Victim victim, long amount, long now, CareerThresholds t) {
        CareerRecord out = r.plus(counters(deed, victim, amount, t.captainWeight()));
        boolean desertion = isDesertionDeed(deed, t);
        boolean deserted = desertion && out.enlisted();
        if (deserted) out = out.withNavy(NavyRank.NONE);
        boolean voided = desertion && out.letter() == LetterState.ACTIVE;
        if (voided) out = out.withLetter(LetterState.VOIDED, now + t.letter().voidTicks());
        long prize = 0;
        if (deed == Deed.KILL_PIRATE && out.letter() == LetterState.ACTIVE && t.letter().enabled()) {
            prize = victim == Victim.CAPTAIN ? t.letter().prizeCaptain() : t.letter().prizeDeckhand();
            out = out.plusPrize(prize);
        }
        return new DeedOutcome(out, deserted, voided, prize);
    }

    // ------------------------------------------------------------------ promotions

    /** What {@code target} needs, with what the player has. */
    public static List<Requirement> requirements(NavyRank target, CareerRecord r, int navyRep, CareerThresholds t) {
        CareerThresholds.NavyStep s = t.step(target);
        return List.of(new Requirement("navy_rep", navyRep, s.minNavyRep()),
                new Requirement("pirates_defeated", piratesDefeated(r), s.piratesDefeated()),
                new Requirement("navy_quests", r.count(CareerCounter.NAVY_QUESTS), s.quests()));
    }

    public static List<Requirement> requirements(InfamyRank target, CareerRecord r, int pirateRep, CareerThresholds t) {
        CareerThresholds.InfamyStep s = t.step(target);
        return List.of(new Requirement("pirate_rep", pirateRep, s.minPirateRep()),
                new Requirement("plunder_coins", r.count(CareerCounter.PLUNDER_COINS), s.plunderCoins()),
                new Requirement("captures", captures(r), s.captures()));
    }

    private static boolean allMet(List<Requirement> reqs) {
        for (Requirement q : reqs) if (!q.met()) return false;
        return true;
    }

    /**
     * The navy rank one above the current one if the player serves and meets its requirements; a pirate reputation
     * above {@code maxPirateRepToEnlist} blocks promotion. Enlisting itself is {@link #enlist}, not a promotion.
     */
    public static Optional<NavyRank> nextNavyRank(CareerRecord r, int navyRep, int pirateRep, CareerThresholds t) {
        if (!r.enlisted() || pirateRep > t.maxPirateRepToEnlist()) return Optional.empty();
        return r.navy().next().filter(next -> allMet(requirements(next, r, navyRep, t)));
    }

    /** The infamy rank one above the current one if met; never while the player serves in the navy. */
    public static Optional<InfamyRank> nextInfamyRank(CareerRecord r, int pirateRep, CareerThresholds t) {
        if (r.enlisted()) return Optional.empty();
        return r.infamy().next().filter(next -> allMet(requirements(next, r, pirateRep, t)));
    }

    /** Setting {@code infamy} on a player in service is turning pirate: desertion. */
    public static boolean turnsPirate(CareerRecord r, InfamyRank infamy) {
        return r.enlisted() && infamy.atLeast(InfamyRank.BUCCANEER);
    }

    // ------------------------------------------------------------------ enlisting and the letter

    /**
     * Whether the player may enlist (as {@link NavyRank#MIDSHIPMAN}): not serving, no infamy, pirate reputation at most
     * {@code maxPirateRepToEnlist}, no bounty, not hated by the navy, and the midshipman's navy reputation.
     */
    public static EnlistVerdict enlist(CareerRecord r, int navyRep, int pirateRep, boolean wanted, boolean navyHostile,
                                       boolean enabled, CareerThresholds t) {
        if (!enabled) return EnlistVerdict.DISABLED;
        if (r.enlisted()) return EnlistVerdict.ALREADY_ENLISTED;
        if (r.infamy() != InfamyRank.DECKHAND) return EnlistVerdict.INFAMOUS;
        if (wanted) return EnlistVerdict.WANTED;
        if (navyHostile) return EnlistVerdict.HOSTILE;
        if (pirateRep > t.maxPirateRepToEnlist()) return EnlistVerdict.PIRATE_FRIEND;
        if (navyRep < t.step(NavyRank.MIDSHIPMAN).minNavyRep()) return EnlistVerdict.LOW_STANDING;
        return EnlistVerdict.OK;
    }

    /** Whether the player may buy a letter of marque at game time {@code now} carrying {@code coins} doubloons. */
    public static LetterVerdict letter(CareerRecord r, int navyRep, long now, long coins, boolean enabled, CareerThresholds t) {
        CareerThresholds.LetterTerms l = t.letter();
        if (!enabled || !l.enabled()) return LetterVerdict.DISABLED;
        if (r.enlisted()) return LetterVerdict.ENLISTED;
        if (r.letter() == LetterState.ACTIVE) return LetterVerdict.ALREADY_HELD;
        if (now < r.letterBlockedUntil()) return LetterVerdict.BLOCKED;
        if (r.infamy().ordinal() > l.maxInfamy().ordinal()) return LetterVerdict.INFAMOUS;
        if (navyRep < l.minNavyRep()) return LetterVerdict.LOW_STANDING;
        if (coins < l.fee()) return LetterVerdict.TOO_POOR;
        return LetterVerdict.OK;
    }

    /** Whole in-game days (rounded up) until a voided letter stops blocking a new one; 0 when it does not. */
    public static long blockedDays(CareerRecord r, long now) {
        long left = r.letterBlockedUntil() - now;
        return left <= 0 ? 0 : (left + TICKS_PER_DAY - 1) / TICKS_PER_DAY;
    }
}
