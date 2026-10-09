package com.richardsenger.piratesnships.law.brig;

import java.util.Collection;
import net.minecraft.util.RandomSource;

/**
 * When an NPC prisoner gets free (design.md §13.3). Pure; the random source is a parameter.
 * <ul>
 *   <li>A prisoner being led never escapes (its captor holds the chain).</li>
 *   <li>Locked in a cell, it only tries while the crew's morale is low ({@code lowMoraleChance}).</li>
 *   <li>Otherwise (door open or unlocked, or no cell at all) it tries at {@code chance} per check.</li>
 *   <li>If its own faction captures the ship it is freed, whether escapes are enabled or not (being rescued is not
 *       an escape attempt).</li>
 * </ul>
 * The morale input comes from the crew of the ship the prisoner is held on ({@link #crewMoraleLow}, FIX1); the
 * ship-capture input has no source yet (ship capture comes later).
 */
public final class EscapeRule {

    public enum Decision { STAY, ESCAPE, FREED }

    /** @param escapesEnabled config toggle {@code flags_brig.prisoner_escapes} */
    public record Inputs(boolean escapesEnabled, boolean inLockedCell, boolean beingLed, boolean crewMoraleLow,
                         boolean shipCapturedByOwnFaction) {
    }

    /** Chances per check, see {@link #perCheck}. */
    public record Params(double chance, double lowMoraleChance) {
    }

    private EscapeRule() {
    }

    public static Decision decide(Inputs in, Params params, RandomSource random) {
        if (in.shipCapturedByOwnFaction()) return Decision.FREED;
        if (!in.escapesEnabled() || in.beingLed()) return Decision.STAY;
        double p;
        if (in.inLockedCell()) {
            p = in.crewMoraleLow() ? params.lowMoraleChance() : 0.0;
        } else {
            p = in.crewMoraleLow() ? Math.max(params.chance(), params.lowMoraleChance()) : params.chance();
        }
        if (p <= 0.0) return Decision.STAY;
        return p >= 1.0 || random.nextDouble() < p ? Decision.ESCAPE : Decision.STAY;
    }

    /**
     * Whether the crew guarding a prisoner has low morale: the average morale of the crew aboard the prisoner's ship
     * is below {@code threshold} ({@code crew.desertion.desert_below}, the line under which a hand counts toward
     * desertion). No crew (no ship, or a ship without hands) is not low: there is no crew whose morale could sink,
     * and a cell holds as it did before crews existed.
     */
    public static boolean crewMoraleLow(Collection<Integer> crewMorale, int threshold) {
        if (crewMorale.isEmpty()) return false;
        double sum = 0;
        for (int m : crewMorale) sum += m;
        return sum / crewMorale.size() < threshold;
    }

    /** Converts a chance per minute (1200 ticks) into a chance per check every {@code intervalTicks}. */
    public static double perCheck(double perMinute, int intervalTicks) {
        if (perMinute <= 0.0) return 0.0;
        if (perMinute >= 1.0) return 1.0;
        return 1.0 - Math.pow(1.0 - perMinute, intervalTicks / 1200.0);
    }
}
