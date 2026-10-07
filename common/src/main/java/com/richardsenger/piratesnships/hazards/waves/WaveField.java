package com.richardsenger.piratesnships.hazards.waves;

import java.util.List;

/**
 * The simulated wave surface of one dimension at one moment (docs/design.md §5.4, WV1): the height of the sea above
 * the still water level as a sum of two sine trains. Pure and immutable: the same inputs give the same heights on the
 * server, on every client and in tests. Nothing deforms the rendered water (§1 non-goals); the field drives ship roll
 * and pitch, spray and spilling.
 *
 * <h2>Model</h2>
 * {@code h(p, t) = A · Σ wᵢ · sin(kᵢ · dᵢ·(p − a) + kᵢ · rᵢ·a − ωᵢ · t + φᵢ)} with
 * <ul>
 *   <li>{@code A} the amplitude (state amplitude × {@code waves.amplitude}); the weights sum to 1, so |h| ≤ A;</li>
 *   <li>{@code kᵢ = 2π / λᵢ}, {@code ωᵢ = 2π / Tᵢ} with the wavelengths and periods of {@link #COMPONENTS}
 *       (34 and 23 blocks, 9 and 6.5 s, inside the 20–40 blocks and 6–10 s of the design); time in ticks;</li>
 *   <li>{@code dᵢ} the running direction of train {@code i}: the field's direction (the wind's, with a slow offset,
 *       {@link SeaStateModel#directionDegrees}) turned by the train's offset (0° and 35°, a crossed sea);</li>
 *   <li>{@code a} the <b>anchor</b>, a point near {@code p} (a ship's center): the direction drifts with the wind, and
 *       {@code dᵢ·p} at a position thousands of blocks from the origin would turn the slightest drift into a fast phase
 *       sweep. So the drifting direction acts only on the offset from the anchor, and the anchor's own phase uses a
 *       fixed reference direction {@code rᵢ}. The field is exactly continuous in time for a fixed anchor, and
 *       continuous in the anchor, so a sailing ship meets the waves at a smoothly changing phase.</li>
 * </ul>
 * {@link #height(double, double, double)} and {@link #slope(double, double, double)} use the point itself as the
 * anchor.
 */
public final class WaveField {

    /**
     * One sine train.
     *
     * @param wavelength   [blocks]
     * @param periodTicks  [ticks]
     * @param weight       share of the amplitude (the weights sum to 1)
     * @param phase        phase offset [rad]
     * @param offsetDeg    turn of the running direction from the field's direction [degrees]
     * @param refBearingDeg fixed reference direction for the anchor's phase (compass bearing) [degrees]
     */
    public record Component(double wavelength, double periodTicks, double weight, double phase, double offsetDeg, double refBearingDeg) {

        public double k() {
            return 2.0 * Math.PI / wavelength;
        }

        public double omega() {
            return 2.0 * Math.PI / periodTicks;
        }
    }

    /** The two trains of every sea: a long swell and a shorter crossing sea. */
    public static final List<Component> COMPONENTS = List.of(
            new Component(34.0, 180.0, 0.6, 0.0, 0.0, 90.0),
            new Component(23.0, 130.0, 0.4, 1.7, 35.0, 135.0));

    /** A flat sea. */
    public static final WaveField FLAT = new WaveField(0.0, 0.0);

    private final double amplitude;
    private final double directionDeg;
    private final List<Component> components;
    // per component: k, omega, weight, phase, dir x/z, ref x/z
    private final double[][] c;

    /**
     * @param amplitude    crest height [blocks] (state amplitude × config multiplier); ≤ 0 gives a flat sea
     * @param directionDeg compass bearing the waves run toward (0 = north = −Z, 90 = east = +X)
     */
    public WaveField(double amplitude, double directionDeg) {
        this(amplitude, directionDeg, COMPONENTS);
    }

    public WaveField(double amplitude, double directionDeg, List<Component> components) {
        this.amplitude = Math.max(0.0, amplitude);
        this.directionDeg = directionDeg;
        this.components = List.copyOf(components);
        this.c = new double[components.size()][];
        for (int i = 0; i < c.length; i++) {
            Component comp = components.get(i);
            double dir = Math.toRadians(directionDeg + comp.offsetDeg());
            double ref = Math.toRadians(comp.refBearingDeg());
            c[i] = new double[] {comp.k(), comp.omega(), comp.weight(), comp.phase(),
                    Math.sin(dir), -Math.cos(dir), Math.sin(ref), -Math.cos(ref)};
        }
    }

    public double amplitude() {
        return amplitude;
    }

    public double directionDegrees() {
        return directionDeg;
    }

    public List<Component> components() {
        return components;
    }

    public boolean isFlat() {
        return amplitude <= 0.0;
    }

    /** Height of the sea above still water at {@code (x, z)} and game time {@code t} [blocks]. */
    public double height(double x, double z, double t) {
        return heightAround(x, z, x, z, t);
    }

    /** Height at {@code (x, z)} with the anchor {@code (ax, az)} (see the class comment). */
    public double heightAround(double ax, double az, double x, double z, double t) {
        if (isFlat()) {
            return 0.0;
        }
        double h = 0.0;
        double dx = x - ax, dz = z - az;
        for (double[] w : c) {
            h += w[2] * Math.sin(phase(w, ax, az, dx, dz, t));
        }
        return amplitude * h;
    }

    /** Gradient {@code {∂h/∂x, ∂h/∂z}} of the surface at {@code (x, z)}, time {@code t} [blocks per block]. */
    public double[] slope(double x, double z, double t) {
        return slopeAround(x, z, x, z, t);
    }

    /** Gradient at {@code (x, z)} with the anchor {@code (ax, az)}. */
    public double[] slopeAround(double ax, double az, double x, double z, double t) {
        double[] g = new double[2];
        if (isFlat()) {
            return g;
        }
        double dx = x - ax, dz = z - az;
        for (double[] w : c) {
            double s = amplitude * w[2] * w[0] * Math.cos(phase(w, ax, az, dx, dz, t));
            g[0] += s * w[4];
            g[1] += s * w[5];
        }
        return g;
    }

    /** The largest surface slope this field can reach [blocks per block]: {@code A · Σ wᵢ kᵢ}. */
    public double maxSlope() {
        double s = 0.0;
        for (double[] w : c) {
            s += w[2] * w[0];
        }
        return amplitude * s;
    }

    private static double phase(double[] w, double ax, double az, double dx, double dz, double t) {
        return w[0] * (w[4] * dx + w[5] * dz) + w[0] * (w[6] * ax + w[7] * az) - w[1] * t + w[3];
    }

    @Override
    public String toString() {
        return String.format("WaveField[amplitude %.2f, toward %.0f°]", amplitude, directionDeg);
    }
}
