package com.richardsenger.piratesnships.hazards.waves;

import java.util.List;

/**
 * The simulated wave surface of one dimension at one moment (docs/design.md §5.4, WV1, WAV2): the height of the sea
 * above the still water level as a sum of sine trains under a slow group envelope. Pure and immutable: the same inputs
 * give the same heights on the server, on every client and in tests. Nothing deforms the rendered water (§1 non-goals);
 * the field drives ship roll, pitch and heave, spray and spilling.
 *
 * <h2>Model</h2>
 * {@code h(p, t) = A · E(a, t) · Σ wᵢ · sin(kᵢ · dᵢ·(p − a) + kᵢ · rᵢ·a − ωᵢ · t + φᵢ)} with
 * <ul>
 *   <li>{@code A} the amplitude (state amplitude × {@code waves.amplitude}); the weights sum to 1;</li>
 *   <li>{@code kᵢ = 2π / λᵢ}, {@code ωᵢ = 2π / Tᵢ} with the trains of {@link WaveSpectrum} (by default
 *       {@code waves.components} trains of 16–64 blocks with dispersion periods, one of them WV1's 34-block, 9-s
 *       swell); time in ticks;</li>
 *   <li>{@code dᵢ} the running direction of train {@code i}: the field's direction (the wind's, with a slow offset,
 *       {@link SeaStateModel#directionDegrees}) turned by the train's offset (within ±30°, a short-crested sea);</li>
 *   <li>{@code a} the <b>anchor</b>, a point near {@code p} (a ship's center): the direction drifts with the wind, and
 *       {@code dᵢ·p} at a position thousands of blocks from the origin would turn the slightest drift into a fast phase
 *       sweep. So the drifting direction acts only on the offset from the anchor, and the anchor's own phase uses a
 *       fixed reference direction {@code rᵢ}. The field is exactly continuous in time for a fixed anchor, and
 *       continuous in the anchor, so a sailing ship meets the waves at a smoothly changing phase. Every train and the
 *       envelope use this construction;</li>
 *   <li>{@code E} the group envelope ({@link Groups}): {@code 1 + g · ½ (sin ψ₁ + sin ψ₂)}, two slow terms of
 *       0.85 and 1.3 × the group period running east at the swell's group speed (half its phase speed), evaluated at
 *       the anchor; sets of bigger waves come and go, and {@code |h| ≤ A · (1 + g)}.</li>
 * </ul>
 * {@link #height(double, double, double)} and {@link #slope(double, double, double)} use the point itself as the
 * anchor.
 *
 * <h2>Origin (tests)</h2>
 * An {@link Origin} shifts the whole field in time and space: {@code t} and {@code a} are measured from it. Gameplay
 * always uses {@link Origin#NONE}; GameTests pin the origin to the test's start and its hull (through
 * {@link SeaStateModel.Tracker#setOverride(SeaState, Double, long, Origin)}), so the phase the waves meet the hull at no
 * longer depends on the game time the test starts at or on where the runner placed its structure.
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

    /**
     * Where the field's time and space start: {@code t − ticks} and {@code a − (x, z)} enter the phases.
     *
     * @param ticks game time of phase zero [ticks]
     * @param x     world x of phase zero [blocks]
     * @param z     world z of phase zero [blocks]
     */
    public record Origin(double ticks, double x, double z) {
        /** Phase zero at game time 0 and the world origin: the gameplay field. */
        public static final Origin NONE = new Origin(0.0, 0.0, 0.0);
    }

    /**
     * The wave groups: a slow envelope on the amplitude.
     *
     * @param depth       {@code waves.group_depth}: the envelope swings the amplitude by up to ± this share (0 = none)
     * @param periodTicks {@code waves.group_period_seconds} × 20: the envelope's two terms run at 0.85 and 1.3 × this
     */
    public record Groups(double depth, double periodTicks) {
        /** No envelope. */
        public static final Groups NONE = new Groups(0.0, 1200.0);
        /** Period factors of the two envelope terms (51 and 78 s for the default 60 s). */
        public static final double FIRST = 0.85;
        public static final double SECOND = 1.3;
        /** Speed the groups run at: half the phase speed of the 34-block swell [blocks per tick]. */
        public static final double SPEED = WaveSpectrum.REFERENCE_WAVELENGTH
                / (2.0 * WaveSpectrum.periodTicks(WaveSpectrum.REFERENCE_WAVELENGTH));

        public Groups {
            depth = Math.max(0.0, Math.min(0.9, depth));
            periodTicks = Math.max(20.0, periodTicks);
        }
    }

    /** The default sea: {@link WaveSpectrum#DEFAULT_COMPONENTS} trains peaking at the 34-block swell. */
    public static final List<Component> COMPONENTS = WaveSpectrum.components(WaveSpectrum.DEFAULT_COMPONENTS,
            WaveSpectrum.REFERENCE_WAVELENGTH);

    /** A flat sea. */
    public static final WaveField FLAT = new WaveField(0.0, 0.0);

    private final double amplitude;
    private final double directionDeg;
    private final List<Component> components;
    private final Origin origin;
    private final Groups groups;
    // envelope terms: k, omega, phase (running east)
    private final double[][] g;
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
        this(amplitude, directionDeg, components, Origin.NONE);
    }

    /** A field whose time and anchor are measured from {@code origin} (tests; gameplay uses {@link Origin#NONE}). */
    public WaveField(double amplitude, double directionDeg, List<Component> components, Origin origin) {
        this(amplitude, directionDeg, components, origin, Groups.NONE);
    }

    /** A field with wave groups ({@link Groups}); the full constructor. */
    public WaveField(double amplitude, double directionDeg, List<Component> components, Origin origin, Groups groups) {
        this.origin = origin;
        this.groups = groups;
        double[] factors = {Groups.FIRST, Groups.SECOND};
        double[] phases = {0.9, 4.1};
        this.g = new double[groups.depth() > 0.0 ? 2 : 0][];
        for (int i = 0; i < g.length; i++) {
            double omega = 2.0 * Math.PI / (groups.periodTicks() * factors[i]);
            g[i] = new double[] {omega / Groups.SPEED, omega, phases[i]};
        }
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

    public Origin origin() {
        return origin;
    }

    public Groups groups() {
        return groups;
    }

    /** The largest height this field can reach [blocks]: {@code A · (1 + group depth)}. */
    public double maxHeight() {
        return amplitude * (1.0 + (g.length > 0 ? groups.depth() : 0.0));
    }

    /**
     * The group envelope at anchor {@code (ax, az)} and time {@code t} (1 without groups): between {@code 1 − depth}
     * and {@code 1 + depth}. The groups run east, so only the anchor's x enters.
     */
    public double envelope(double ax, double az, double t) {
        return envelopeAt(ax - origin.x(), t - origin.ticks());
    }

    private double envelopeAt(double oax, double ot) {
        if (g.length == 0) {
            return 1.0;
        }
        double s = 0.0;
        for (double[] e : g) {
            s += Math.sin(e[0] * oax - e[1] * ot + e[2]);
        }
        return 1.0 + groups.depth() * 0.5 * s;
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
        double oax = ax - origin.x(), oaz = az - origin.z(), ot = t - origin.ticks();
        for (double[] w : c) {
            h += w[2] * Math.sin(phase(w, oax, oaz, dx, dz, ot));
        }
        return amplitude * envelopeAt(oax, ot) * h;
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
        double oax = ax - origin.x(), oaz = az - origin.z(), ot = t - origin.ticks();
        double a = amplitude * envelopeAt(oax, ot);
        for (double[] w : c) {
            double s = a * w[2] * w[0] * Math.cos(phase(w, oax, oaz, dx, dz, ot));
            g[0] += s * w[4];
            g[1] += s * w[5];
        }
        return g;
    }

    /**
     * The largest surface slope this field can reach [blocks per block]: {@code A · (1 + depth) · Σ wᵢ kᵢ} (the
     * envelope is taken at the anchor, so it scales the slope but adds none of its own).
     */
    public double maxSlope() {
        double s = 0.0;
        for (double[] w : c) {
            s += w[2] * w[0];
        }
        return maxHeight() * s;
    }

    private static double phase(double[] w, double ax, double az, double dx, double dz, double t) {
        return w[0] * (w[4] * dx + w[5] * dz) + w[0] * (w[6] * ax + w[7] * az) - w[1] * t + w[3];
    }

    @Override
    public String toString() {
        return String.format("WaveField[amplitude %.2f, toward %.0f°, %d trains, groups ±%.2f]", amplitude, directionDeg,
                components.size(), g.length > 0 ? groups.depth() : 0.0);
    }
}
