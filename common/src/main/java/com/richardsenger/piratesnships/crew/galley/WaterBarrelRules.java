package com.richardsenger.piratesnships.crew.galley;

import com.richardsenger.piratesnships.crew.provisions.ProvisionSettings;
import com.richardsenger.piratesnships.crew.provisions.ProvisionStore;
import com.richardsenger.piratesnships.crew.provisions.ProvisionType;

/**
 * Rules of the water barrel (design.md §7.4 "Fresh water"). Pure.
 *
 * <ul>
 *   <li>Holds whole water rations, up to {@link ProvisionSettings#waterBarrelRations()} (default 16, the same number
 *       the classifier counts for a full barrel item, so a barrel holds the same water placed or carried).</li>
 *   <li>A water bucket adds {@link ProvisionSettings#waterBucketRations()} (default 3) and a water bottle one ration,
 *       but only if all of it fits. An empty bucket takes that many out, a glass bottle one ration, only if the barrel
 *       holds enough. So water is never created or lost by filling and emptying.</li>
 *   <li>Rain adds one ration per lucky random tick to a barrel open to the sky (config toggle and chance).</li>
 *   <li>Fill level 0..4 for the model, comparator signal 0..15; both are 0 only when the barrel is empty.</li>
 * </ul>
 */
public final class WaterBarrelRules {

    /** Provision type id of the water inside a placed barrel (one unit = one ration). */
    public static final String WATER_ID = "pirates_n_ships:barrel_water";
    public static final int MAX_FILL = 4;

    private WaterBarrelRules() {
    }

    public static int capacity(ProvisionSettings s) {
        return Math.max(1, s.waterBarrelRations());
    }

    /** Rations after adding {@code amount}, or -1 if it does not fit. */
    public static int fill(int rations, int amount, int capacity) {
        return amount > 0 && rations + amount <= capacity ? rations + amount : -1;
    }

    /** Rations after taking {@code amount} out, or -1 if there is not enough. */
    public static int drain(int rations, int amount) {
        return amount > 0 && rations >= amount ? rations - amount : -1;
    }

    /** Rations after one random tick of rain ({@code roll} uniform in [0, 1)). */
    public static int rain(int rations, int capacity, boolean enabled, boolean rainingAbove, double chance, double roll) {
        if (!enabled || !rainingAbove || rations >= capacity || roll >= chance) {
            return rations;
        }
        return rations + 1;
    }

    /** Model fill level: 0 empty, 4 full, a partly filled barrel never shows 0 or 4. */
    public static int fillLevel(int rations, int capacity) {
        if (rations <= 0) {
            return 0;
        }
        if (rations >= capacity) {
            return MAX_FILL;
        }
        return Math.max(1, Math.min(MAX_FILL - 1, (int) Math.ceil((double) rations * (MAX_FILL - 1) / capacity)));
    }

    /** Comparator signal like a vanilla container: 0 when empty, at least 1 otherwise, 15 when full. */
    public static int comparator(int rations, int capacity) {
        if (rations <= 0) {
            return 0;
        }
        return Math.max(1, Math.min(15, (int) Math.floor(15.0 * rations / capacity)));
    }

    /** The provisions type of barrel water. */
    public static ProvisionType waterType(ProvisionSettings s) {
        return ProvisionType.water(WATER_ID, 1, s.waterWeightPerRation());
    }

    /** The barrel's contents as a store (water never ages, so one lot of age 0). */
    public static ProvisionStore store(int rations, ProvisionSettings s) {
        return rations <= 0 ? ProvisionStore.EMPTY : ProvisionStore.EMPTY.add(waterType(s), rations);
    }
}
