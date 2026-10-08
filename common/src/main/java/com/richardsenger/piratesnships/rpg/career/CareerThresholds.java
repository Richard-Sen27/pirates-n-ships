package com.richardsenger.piratesnships.rpg.career;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * A pure snapshot of the career config (docs/design.md §15, CAR1) for {@link CareerRules}: what each rank needs, the
 * enlisting limit, the desertion deeds, the captain weight and the letter of marque's terms.
 * {@code CareerConfig.thresholds()} builds it from the live config; {@link #defaults()} holds the shipped defaults.
 *
 * @param navy                  requirements per navy rank from {@link NavyRank#MIDSHIPMAN} (what enlisting needs) up
 * @param infamy                requirements per infamy rank from {@link InfamyRank#BUCCANEER} up
 * @param maxPirateRepToEnlist  enlisting and navy promotion need a pirate reputation at most this
 * @param desertionDeeds        ids of the deeds that are desertion in service and void a letter of marque
 * @param captainWeight         how many pirate kills a pirate captain counts as
 */
public record CareerThresholds(Map<NavyRank, NavyStep> navy, Map<InfamyRank, InfamyStep> infamy, int maxPirateRepToEnlist,
                               Set<String> desertionDeeds, int captainWeight, LetterTerms letter) {

    /** What a navy rank needs: navy reputation, pirates defeated (killed or turned in) and navy quests done. */
    public record NavyStep(int minNavyRep, long piratesDefeated, long quests) {
    }

    /** What an infamy rank needs: pirate reputation, doubloons fenced and captures (see {@link CareerRules#captures}). */
    public record InfamyStep(int minPirateRep, long plunderCoins, long captures) {
    }

    /**
     * The letter of marque: granted at a navy officer for {@code fee} doubloons to a player not in service with a navy
     * reputation of at least {@code minNavyRep} and an infamy of at most {@code maxInfamy}; a voided letter blocks a new
     * one for {@code voidTicks}; each pirate kill under it earns {@code prizeDeckhand} (a captain {@code prizeCaptain}).
     */
    public record LetterTerms(boolean enabled, int minNavyRep, InfamyRank maxInfamy, long fee, long voidTicks,
                              long prizeDeckhand, long prizeCaptain) {
    }

    public CareerThresholds {
        Map<NavyRank, NavyStep> n = new EnumMap<>(NavyRank.class);
        n.putAll(navy);
        Map<InfamyRank, InfamyStep> f = new EnumMap<>(InfamyRank.class);
        f.putAll(infamy);
        navy = Collections.unmodifiableMap(n);
        infamy = Collections.unmodifiableMap(f);
        desertionDeeds = Set.copyOf(desertionDeeds);
        captainWeight = Math.max(1, captainWeight);
    }

    /** The requirements of {@code rank}; none for {@link NavyRank#NONE} or a rank missing from the map. */
    public NavyStep step(NavyRank rank) {
        return navy.getOrDefault(rank, new NavyStep(-100, 0, 0));
    }

    public InfamyStep step(InfamyRank rank) {
        return infamy.getOrDefault(rank, new InfamyStep(-100, 0, 0));
    }

    /** The defaults shipped in the config (the playtest will tune them). */
    public static CareerThresholds defaults() {
        Map<NavyRank, NavyStep> navy = new EnumMap<>(NavyRank.class);
        for (NavyRank r : NavyRank.values()) if (r != NavyRank.NONE) navy.put(r, defaultStep(r));
        Map<InfamyRank, InfamyStep> infamy = new EnumMap<>(InfamyRank.class);
        for (InfamyRank r : InfamyRank.values()) if (r != InfamyRank.DECKHAND) infamy.put(r, defaultStep(r));
        return new CareerThresholds(navy, infamy, 0, Set.of("attack_navy", "kill_navy", "attack_merchant_ship", "plunder_merchant"),
                5, new LetterTerms(true, 20, InfamyRank.BUCCANEER, 200, 3 * CareerRules.TICKS_PER_DAY, 5, 50));
    }

    public static NavyStep defaultStep(NavyRank rank) {
        return switch (rank) {
            case NONE, MIDSHIPMAN -> new NavyStep(10, 0, 0);
            case LIEUTENANT -> new NavyStep(25, 5, 1);
            case CAPTAIN -> new NavyStep(45, 15, 3);
            case COMMODORE -> new NavyStep(65, 30, 6);
            case ADMIRAL -> new NavyStep(85, 60, 10);
        };
    }

    public static InfamyStep defaultStep(InfamyRank rank) {
        return switch (rank) {
            case DECKHAND -> new InfamyStep(-100, 0, 0);
            case BUCCANEER -> new InfamyStep(15, 100, 0);
            case DREAD_CAPTAIN -> new InfamyStep(40, 1000, 3);
            case PIRATE_LORD -> new InfamyStep(75, 5000, 10);
        };
    }
}
