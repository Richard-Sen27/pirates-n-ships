package com.richardsenger.piratesnships.crew.provisions;

/**
 * Days of supplies left, for the HUD (design.md §7.4). {@link Double#POSITIVE_INFINITY} when nothing is being
 * consumed (no crew, or consumption off). Food takes spoilage into account: food that will spoil before it is eaten
 * does not count.
 *
 * @param foodDays  days until the crew runs out of food
 * @param waterDays days until the crew runs out of water
 * @param rumDays   days until the rum runs out at the current issue
 */
public record SuppliesLeft(double foodDays, double waterDays, double rumDays) {

    public static final SuppliesLeft UNLIMITED = new SuppliesLeft(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);

    /** Days until the first essential supply (food or water) runs out. Rum is not essential. */
    public double overallDays() {
        return Math.min(foodDays, waterDays);
    }
}
