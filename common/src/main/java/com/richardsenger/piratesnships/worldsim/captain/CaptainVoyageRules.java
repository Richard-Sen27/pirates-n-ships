package com.richardsenger.piratesnships.worldsim.captain;

import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.worldsim.lane.Lane;
import com.richardsenger.piratesnships.worldsim.lane.SeaGrid;
import com.richardsenger.piratesnships.worldsim.navy.HuntRules;
import com.richardsenger.piratesnships.worldsim.navy.PatrolRoutes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The pure rules of a pirate captain's voyages (BOS2): when he puts to sea, where he sails, and whom he hunts. No world
 * access.
 *
 * <ul>
 *   <li><b>Schedule:</b> every {@code voyage_days} days a captain at his post gets one chance ({@code voyage_chance}) to
 *       put to sea ({@link #decide}). A chance is used up whether he sails or stays; a captain away from his post (at
 *       sea, coming home, in a fight, a prisoner) gets his chance when he is back.</li>
 *   <li><b>Route:</b> out along the lane toward a village or navy outpost and back ({@link #towardPort}), else out into
 *       open sea and back ({@link #outAndBack}).</li>
 *   <li><b>Quarry:</b> a player's ship whose owner holds a letter of marque or carries bounty proofs ({@link #hunted}).
 *       The chase itself is the navy's ({@link HuntRules#pick}, {@link HuntRules#judge}): the captain's verdict travels
 *       in {@link HuntRules.Candidate#ownerWanted} with the flag and bounty rules switched off ({@link #candidate},
 *       {@link #huntParams}), so only his rule picks a quarry; a quarry that strikes its colours is left alone after
 *       the linger time. The captain never strikes his own.</li>
 * </ul>
 */
public final class CaptainVoyageRules {

    /** Blocks from his post within which a loaded captain counts as at his post. */
    public static final double AT_POST_SLACK = 6.0;
    /** A loop leg shorter than this share of the cruise distance is tried in another direction. */
    static final double MIN_REACH_SHARE = 0.75;
    /** Directions tried for an open-sea cruise, in steps of 45° from the first. */
    static final int[] TURNS = {0, 1, -1, 2, -2, 3, -3, 4};

    /** @param voyageDays days between two chances; @param chance the chance to put to sea on such a day */
    public record Schedule(int voyageDays, double chance) {
    }

    /** @param letter a letter of marque draws him; @param minProofs this many bounty proofs draw him (0 = never) */
    public record Quarry(boolean letter, int minProofs) {
    }

    /** What a captain does on a scheduler check. */
    public enum Verdict {
        /** Puts to sea now. */
        SAIL,
        /** His day came and he stays at home. */
        STAYS,
        /** His day has not come yet. */
        NOT_DUE,
        /** Not at his post (at sea, coming home, fighting, a prisoner, wandered off). */
        AWAY,
        /** Lost (dead or handed over); his successor's schedule starts afresh. */
        LOST,
        /** Voyages are switched off. */
        DISABLED;

        /** Whether this verdict used up his chance for the day (the next one comes {@code voyage_days} later). */
        public boolean usesChance() {
            return this == SAIL || this == STAYS;
        }
    }

    private CaptainVoyageRules() {
    }

    // ------------------------------------------------------------------ schedule

    /** Whether {@code voyageDays} days (at least one) have passed since the day of his last chance. */
    public static boolean due(long today, long lastChanceDay, int voyageDays) {
        return today - lastChanceDay >= Math.max(1, voyageDays);
    }

    /**
     * The verdict of one check.
     *
     * @param active        voyages are on (all toggles)
     * @param alive         he is his island's living captain
     * @param atPost        he keeps his post ({@link #atPost}) and is neither at sea nor on his way home
     * @param lastChanceDay the day of his last chance
     * @param roll          a uniform random number in [0, 1)
     */
    public static Verdict decide(boolean active, boolean alive, boolean atPost, long today, long lastChanceDay, Schedule s, double roll) {
        if (!active) return Verdict.DISABLED;
        if (!alive) return Verdict.LOST;
        if (!atPost) return Verdict.AWAY;
        if (!due(today, lastChanceDay, s.voyageDays())) return Verdict.NOT_DUE;
        return roll < s.chance() ? Verdict.SAIL : Verdict.STAYS;
    }

    /**
     * Whether a captain keeps his post: his post is not loaded (he stands there, unseen), or he was found within
     * {@link #AT_POST_SLACK} of it, free and not dueling.
     */
    public static boolean atPost(boolean postLoaded, boolean found, double distanceToPost, boolean prisoner, boolean dueling) {
        if (!postLoaded) return true;
        return found && distanceToPost <= AT_POST_SLACK && !prisoner && !dueling;
    }

    // ------------------------------------------------------------------ route

    /** Out {@code distance} blocks along the lane toward a port (all of it if shorter) and back the same way. */
    public static List<Lane.Point> towardPort(List<Lane.Point> lane, double distance) {
        return PatrolRoutes.outAndBack(lane, distance);
    }

    /**
     * Out into open sea and back from {@code (sx, sz)}: straight out {@code distance} blocks, capped where the line
     * meets land ({@link PatrolRoutes#capToSea}), then back to the start. The first direction is
     * {@code firstDegrees} (0 = +x, 90 = +z); a direction that reaches less than {@value #MIN_REACH_SHARE} of the
     * distance is replaced by the next of {@link #TURNS}, and the farthest-reaching one is taken when none does.
     */
    public static List<Lane.Point> outAndBack(SeaGrid grid, double sx, double sz, double firstDegrees, double distance) {
        Lane.Point start = new Lane.Point((int) Math.round(sx), (int) Math.round(sz));
        Lane.Point best = start;
        double bestReach = -1;
        for (int turn : TURNS) {
            double a = Math.toRadians(firstDegrees + turn * 45.0);
            Lane.Point tip = PatrolRoutes.capToSea(grid, sx, sz, sx + Math.cos(a) * distance, sz + Math.sin(a) * distance);
            double reach = Math.hypot(tip.x() - sx, tip.z() - sz);
            if (reach > bestReach) {
                best = tip;
                bestReach = reach;
            }
            if (reach >= distance * MIN_REACH_SHARE) break;
        }
        List<Lane.Point> route = new ArrayList<>(3);
        route.add(start);
        route.add(best);
        route.add(start);
        return route;
    }

    // ------------------------------------------------------------------ quarry

    /** Whether the captain hunts a ship: a player's ship whose owner holds a letter of marque or carries proofs. */
    public static boolean hunted(boolean playerShip, boolean ownerHasLetter, int ownerProofs, Quarry q) {
        if (!playerShip) return false;
        return (q.letter() && ownerHasLetter) || (q.minProofs() > 0 && ownerProofs >= q.minProofs());
    }

    /**
     * A ship as the captain sees it, in the navy's chase terms: {@code hunted} is his verdict ({@link #hunted}); the
     * flag is left neutral so that only his verdict counts under {@link #huntParams}.
     */
    public static HuntRules.Candidate candidate(UUID ship, double x, double z, boolean playerShip, boolean struck, boolean hunted) {
        return new HuntRules.Candidate(ship, x, z, playerShip, FlagKind.NONE, struck, false, 0L, hunted);
    }

    /** The navy's chase parameters for the captain: no bounty minimum (no bounty draws him by itself). */
    public static HuntRules.Params huntParams(double huntRadius, double loseDistance, double contactDistance, int giveUpTicks,
                                              int surrenderLingerTicks) {
        return new HuntRules.Params(huntRadius, Long.MAX_VALUE, loseDistance, contactDistance, giveUpTicks, surrenderLingerTicks);
    }
}
