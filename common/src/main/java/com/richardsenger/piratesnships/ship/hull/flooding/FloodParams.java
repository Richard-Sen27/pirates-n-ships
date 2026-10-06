package com.richardsenger.piratesnships.ship.hull.flooding;

/**
 * Plain parameters of the flooding simulation. JUnit tests build these directly; in game they come from
 * {@code FloodingConfig.params()}.
 *
 * @param enabled         {@code false} = no water ever enters from outside (draining, pumps and equalization still run)
 * @param flowMultiplier  multiplier on {@link #BASE_FLOW} for every passage (ports and links)
 * @param pumpPerTick     blocks of water one active pump removes per tick
 */
public record FloodParams(boolean enabled, double flowMultiplier, double pumpPerTick) {

    /**
     * Flow through one fully wetted block face at a head of one block, in blocks of water per tick. With the orifice
     * law {@code Q = BASE_FLOW · area · √head}, a 1×1 hole one block below the waterline lets in 1 block per second, and a
     * 2-block-deep hole about 1.4. A 150-block hold then takes about 2 minutes to fill through one hole: slow enough to
     * react with a pump or a patch, fast enough to matter.
     */
    public static final double BASE_FLOW = 0.05;

    public static final FloodParams DEFAULTS = new FloodParams(true, 1.0, 0.05);

    public FloodParams {
        if (flowMultiplier < 0 || pumpPerTick < 0) {
            throw new IllegalArgumentException("Negative flood parameter");
        }
    }

    public double flowCoefficient() {
        return BASE_FLOW * flowMultiplier;
    }
}
