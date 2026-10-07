package com.richardsenger.piratesnships.ship.hull.pump;

import com.richardsenger.piratesnships.ship.hull.HullAnalysis;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

/**
 * Which compartment a bilge pump drains (docs/design.md §4.5), pure. The pump's intake pipe runs straight down from
 * the pump's own cell (ship-local −y, the plot's down) through up to {@code reach} more cells, through decks and other
 * solid blocks. Along that column the <b>highest flooded</b> compartment is drained; when none along it has water, the
 * highest compartment it reaches is reported (so the player learns that this bilge is dry); with none at all, -1.
 *
 * <p>A pump standing in the hold (its block is not a full cube, so its cell belongs to the hold) drains the hold. A
 * pump on deck reaches the hold below through the deck planks, and a flooded deck basin (bulwarks around a deck) is
 * drained before the hold below it.
 */
public final class PumpIntake {

    /** Below this a compartment counts as dry. */
    public static final double DRY = 1e-6;

    private PumpIntake() {
    }

    /**
     * @param compartmentAtDepth compartment id of the cell {@code depth} blocks below the pump (0 = the pump's own cell),
     *                           -1 for none
     * @param reach              cells searched below the pump's own cell
     * @param flooded            whether a compartment has water
     * @return the compartment to drain or report, or -1
     */
    public static int choose(IntUnaryOperator compartmentAtDepth, int reach, IntPredicate flooded) {
        int first = -1;
        for (int d = 0; d <= Math.max(0, reach); d++) {
            int c = compartmentAtDepth.applyAsInt(d);
            if (c < 0) {
                continue;
            }
            if (flooded.test(c)) {
                return c;
            }
            if (first < 0) {
                first = c;
            }
        }
        return first;
    }

    /** {@link #choose} on an analysis, for a pump at grid coordinates {@code x, y, z}. */
    public static int find(HullAnalysis analysis, int x, int y, int z, int reach, IntPredicate flooded) {
        return choose(d -> analysis.compartmentAt(x, y - d, z), reach, flooded);
    }

    /**
     * Ticks of one crew pumping order: until the compartment would be empty at the pump's rate, at most
     * {@code maxTicks} (the crew member then re-checks and goes on while water is left). 0 = nothing to do, -1 = the pump
     * cannot work (no rate).
     */
    public static int crewTicks(double volume, double perTick, int maxTicks) {
        if (!(perTick > 0)) {
            return -1;
        }
        if (volume <= DRY) {
            return 0;
        }
        return (int) Math.max(1, Math.min(maxTicks, Math.ceil(volume / perTick)));
    }
}
