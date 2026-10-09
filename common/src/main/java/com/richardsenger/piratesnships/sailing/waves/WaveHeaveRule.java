package com.richardsenger.piratesnships.sailing.waves;

/**
 * How waves lift a ship (docs/design.md §5.4, WAV2). Pure.
 *
 * <p>Sable's buoyancy floats every solid block against the static water blocks of the world
 * (docs/sable-notes.md §4.1): the water is flat whatever the simulated sea does, so nothing lifts a hull on a crest.
 * This rule adds the missing part of the buoyancy: an upward world force proportional to the mean wave height under the
 * hull (its four sample points and the centre, {@link WaveTorqueRule#samplePoints}) and to the ship's displacement
 * (its weight, which the still water carries):
 * <pre>
 *   F = heave_strength · mass · g · mean height     (up on a crest, down in a trough)
 * </pre>
 * capped at {@code heave_per_mass · mass}. Sable's buoyancy pulls the hull back to the still waterline and its drag
 * damps the motion, so the hull rides up and down with the sea instead of only tilting; the mean over the hull's
 * length filters waves shorter than the ship, so a big ship heaves less in a short sea.
 */
public final class WaveHeaveRule {

    /**
     * @param enabled     {@code waves.enabled} and {@code waves.heave}
     * @param strength    {@code waves.heave_strength}: force per block of mean height, as a share of the weight
     * @param maxPerMass  {@code waves.heave_per_mass}: cap of the force per kpg [m/s²]
     */
    public record Params(boolean enabled, double strength, double maxPerMass) {

        public static final Params DEFAULTS = new Params(true, 0.5, 6.0);
    }

    private WaveHeaveRule() {
    }

    /** Mean of the finite heights, 0 for none. */
    public static double meanHeight(double[] heights) {
        double sum = 0.0;
        int n = 0;
        for (double h : heights) {
            if (Double.isFinite(h)) {
                sum += h;
                n++;
            }
        }
        return n == 0 ? 0.0 : sum / n;
    }

    /**
     * The upward world force [kpg·m/s²] for {@code meanHeight} blocks of wave under a ship of {@code mass} in a level of
     * gravity {@code gravity} (its magnitude); zero when disabled or massless.
     */
    public static double force(double meanHeight, double mass, double gravity, Params p) {
        if (!p.enabled() || !(mass > 0.0) || !Double.isFinite(meanHeight) || !(gravity > 0.0)) {
            return 0.0;
        }
        double f = p.strength() * mass * gravity * meanHeight;
        double max = Math.max(0.0, p.maxPerMass()) * mass;
        return Math.max(-max, Math.min(max, f));
    }
}
