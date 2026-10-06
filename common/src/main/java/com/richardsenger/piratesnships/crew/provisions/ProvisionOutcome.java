package com.richardsenger.piratesnships.crew.provisions;

import java.util.Map;

/**
 * The result of one {@link ProvisionRules#advance} call: what happened during the period and the state at its end.
 *
 * @param elapsedTicks the length of the period
 * @param hungry       the crew is out of food at the end of the period
 * @param thirsty      the crew is out of water at the end of the period
 * @param rumIssued    rum is being issued at the end of the period (rations above 0 and rum left)
 * @param drunk        the "too much rum" penalty is active at the end of the period
 * @param hungryTicks  ticks of the period spent without food
 * @param thirstyTicks ticks of the period spent without water
 * @param rumTicks     ticks of the period with rum issued
 * @param consumed     units taken out of the store, by type id (apply these to the pantry's stacks)
 * @param spoiled      units that spoiled, by type id (also remove these from the stacks)
 * @param effects      what this means for the crew
 */
public record ProvisionOutcome(long elapsedTicks, boolean hungry, boolean thirsty, boolean rumIssued, boolean drunk,
                               double hungryTicks, double thirstyTicks, double rumTicks,
                               Map<String, Integer> consumed, Map<String, Integer> spoiled, ProvisionEffects effects) {

    public ProvisionOutcome {
        consumed = Map.copyOf(consumed);
        spoiled = Map.copyOf(spoiled);
    }

    /** A period in which nothing happened (consumption off). */
    public static ProvisionOutcome idle(long elapsedTicks) {
        return new ProvisionOutcome(elapsedTicks, false, false, false, false, 0, 0, 0, Map.of(), Map.of(), ProvisionEffects.NONE);
    }

    /** All units to remove from the stacks: consumed plus spoiled. */
    public Map<String, Integer> removed() {
        java.util.Map<String, Integer> all = new java.util.TreeMap<>(consumed);
        spoiled.forEach((k, v) -> all.merge(k, v, Integer::sum));
        return all;
    }
}
