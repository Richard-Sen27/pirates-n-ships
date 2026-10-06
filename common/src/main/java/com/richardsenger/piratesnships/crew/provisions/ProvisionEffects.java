package com.richardsenger.piratesnships.crew.provisions;

/**
 * What provisioning does to the crew, for the crew system (which owns morale, work speed, desertion and mutiny) to
 * apply. Plain data, produced once per update by {@link ProvisionRules#advance}.
 *
 * @param moraleChange        morale delta accumulated over the period (negative = loss), on the 0..100 scale.
 *                            Additive: splitting a period into smaller ones gives the same sum.
 * @param workSpeedMultiplier current work speed factor (hungry, thirsty and drunk multiply), 1 = normal
 * @param desertionRisk       0..1: how far the longest ongoing shortage is towards the critical duration
 *                            (thirst gets there faster than hunger)
 * @param scurvy              the crew has scurvy now: apply weakness and slower healing
 */
public record ProvisionEffects(double moraleChange, double workSpeedMultiplier, double desertionRisk, boolean scurvy) {

    public static final ProvisionEffects NONE = new ProvisionEffects(0, 1, 0, false);

    /**
     * The shortage has lasted long enough that the crew system should roll for desertion, or mutiny when that
     * toggle is on (design.md §7.3).
     */
    public boolean desertionCritical() {
        return desertionRisk >= 1.0;
    }
}
