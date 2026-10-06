package com.richardsenger.piratesnships.law.world;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure theft rule (docs/design.md §13.1 "theft from village chests"). Vanilla has no ownership, so a container
 * belongs to the village when all of these hold:
 * <ul>
 *   <li>it is inside a piece of a village structure ({@code #minecraft:village}; can be switched off with
 *       {@code law.world.theft_require_village}, then every container a player did not place counts),</li>
 *   <li>no player placed it (placements are recorded per chunk),</li>
 *   <li>and a witness (villager, wandering trader or navy, awake, within range, with line of sight) sees the player.</li>
 * </ul>
 * Only a <b>net removal</b> between opening and closing the container counts, per item type: putting items in
 * never counts, and taking out what you put in during the same visit is not theft either. A player who swaps their
 * dirt for the village's diamonds still stole the diamonds.
 */
public final class TheftRule {

    private TheftRule() {
    }

    public record Params(boolean enabled, boolean requireVillage, double witnessRange, boolean requireLineOfSight) {
    }

    /** What the detector observed about one container visit. */
    public record Visit(boolean insideVillage, boolean placedByPlayer, int witnesses, int itemsRemoved) {
    }

    /** One potential witness: squared distance to the thief and whether it can see them. */
    public record Witness(double distanceSqr, boolean lineOfSight) {
    }

    public enum Verdict {
        DISABLED, NOTHING_TAKEN, NOT_IN_VILLAGE, PLAYER_PLACED, UNWITNESSED, THEFT;

        public boolean isTheft() {
            return this == THEFT;
        }
    }

    public static Verdict judge(Params params, Visit visit) {
        if (!params.enabled()) return Verdict.DISABLED;
        if (visit.itemsRemoved() <= 0) return Verdict.NOTHING_TAKEN;
        if (params.requireVillage() && !visit.insideVillage()) return Verdict.NOT_IN_VILLAGE;
        if (visit.placedByPlayer()) return Verdict.PLAYER_PLACED;
        if (visit.witnesses() <= 0) return Verdict.UNWITNESSED;
        return Verdict.THEFT;
    }

    /** Number of candidates that notice the thief. */
    public static int countWitnesses(List<Witness> candidates, Params params) {
        double r2 = params.witnessRange() * params.witnessRange();
        int n = 0;
        for (Witness w : candidates) {
            if (w.distanceSqr() <= r2 && (!params.requireLineOfSight() || w.lineOfSight())) n++;
        }
        return n;
    }

    /** Items removed in total: per key, {@code max(0, before - after)}. Keys are item types. */
    public static <K> int netRemoved(Map<K, Integer> before, Map<K, Integer> after) {
        Set<K> keys = new HashSet<>(before.keySet());
        long removed = 0;
        for (K k : keys) {
            int b = before.getOrDefault(k, 0);
            int a = after.getOrDefault(k, 0);
            if (b > a) removed += b - a;
        }
        return (int) Math.min(Integer.MAX_VALUE, removed);
    }
}
