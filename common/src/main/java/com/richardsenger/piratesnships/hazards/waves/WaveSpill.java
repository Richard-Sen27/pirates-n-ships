package com.richardsenger.piratesnships.hazards.waves;

/**
 * The wave height the flooding simulation adds to the sea at a ship's outside ports
 * ({@code FloodTickInput.waveHeight}, docs/design.md §4.5 "rough seas spill water over low rims", WV1). Pure.
 *
 * <p>The ship does not heave with the simulated waves (only roll and pitch are forced), so the surface the hull
 * meets is the still sea plus the wave height around it. The feed is the highest crest at the hull's sample points
 * (bow, stern, both sides and the middle), never below zero: troughs do not suck water out of a hull, and the
 * simulation keeps water that came in over a rim (it only drains above the sill). An open rim or hatch whose sill is
 * less than this height above the still water takes water in while the crest passes.
 */
public final class WaveSpill {

    private WaveSpill() {
    }

    /** {@code max(0, max(heights))}, or 0 when spilling is off or there are no samples. */
    public static double height(double[] waveHeights, boolean enabled) {
        if (!enabled || waveHeights.length == 0) {
            return 0.0;
        }
        double max = 0.0;
        for (double h : waveHeights) {
            if (Double.isFinite(h) && h > max) {
                max = h;
            }
        }
        return max;
    }
}
