package com.richardsenger.piratesnships.hazards.waves;

import com.richardsenger.piratesnships.core.text.Num;
import java.util.List;

/**
 * The simulated wave surface of one dimension (docs/design.md §5.4, WV1, WAV2, WAV7): the height of the sea above the
 * still water level as a sum of sine trains under a slow group envelope. Pure and immutable: the same inputs give the
 * same heights on the server, on every client and in tests. The field drives ship roll, pitch and heave, spray and
 * spilling, vanilla boats, and on the client the visible sea surface, the foam and the wake (WAV3,
 * {@code hazards.waves.client.surface}).
 *
 * <h2>One global sea (WAV7)</h2>
 * The field is a function of the world point and the time only: {@link #heightAt(double, double, double)} is the same
 * for every ship, boat, effect and the drawn surface at the same {@code (x, z, t)}. WV1 anchored the field at each
 * consumer (the drifting wave direction acted only on the offset from an anchor, so far from the origin the slightest
 * drift would not sweep the phase), and with the physics anchored at the hull and the drawn sea at the camera the two
 * disagreed by up to a whole crest. Now the direction does not drift: it changes in steps
 * ({@link SeaStateModel.Tracker#tickDirection}), and the field crossfades between the two stationary seas
 * ({@link Heading}).
 *
 * <h2>Model</h2>
 * {@code h(p, t) = A · E(p, t) · [(1 − u(t)) · S(d_old) + u(t) · S(d_new)]} with
 * {@code S(d) = Σ wᵢ · sin(kᵢ · dᵢ·(p − o) − ωᵢ · (t − tₒ) + φᵢ)} and
 * <ul>
 *   <li>{@code A} the amplitude (state amplitude × {@code waves.amplitude}); the weights sum to 1;</li>
 *   <li>{@code kᵢ = 2π / λᵢ}, {@code ωᵢ = 2π / Tᵢ} with the trains of {@link WaveSpectrum}; time in ticks;</li>
 *   <li>{@code dᵢ} the running direction of train {@code i}: the heading's direction turned by the train's offset
 *       (within ±30°, a short-crested sea);</li>
 *   <li>{@code u} the crossfade of the {@link Heading}: a smoothstep from 0 to 1 over the fade, 1 outside it;</li>
 *   <li>{@code E} the group envelope ({@link Groups}): {@code 1 + g · ½ (sin ψ₁ + sin ψ₂)}, two slow terms of
 *       0.85 and 1.3 × the group period running east at the swell's group speed (half its phase speed), evaluated at
 *       the point; sets of bigger waves come and go, and {@code |h| ≤ A · (1 + g)};</li>
 *   <li>{@code o}, {@code tₒ} the {@link Origin} (the world origin and game time 0 in gameplay).</li>
 * </ul>
 * Precision: the phase {@code kᵢ dᵢ·p} is a double; at the world border ({@code 3·10⁷} blocks) it is about
 * {@code 5.5·10⁶} rad, with an error near {@code 10⁻⁹} rad, so the field is exact everywhere a ship can sail. No
 * float enters a phase (the mesh subtracts the camera's block from its vertices only after the height is computed).
 *
 * <h2>Origin (tests)</h2>
 * An {@link Origin} shifts the whole field in time and space. Gameplay always uses {@link Origin#NONE}; GameTests pin
 * the origin to the test's start and its hull (through
 * {@link SeaStateModel.Tracker#setOverride(SeaState, Double, long, Origin)}), so the phase the waves meet the hull at no
 * longer depends on the game time the test starts at or on where the runner placed its structure. The origin is synced
 * to clients with the rest of the sea.
 */
public final class WaveField {

    /**
     * One sine train.
     *
     * @param wavelength  [blocks]
     * @param periodTicks [ticks]
     * @param weight      share of the amplitude (the weights sum to 1)
     * @param phase       phase offset [rad]
     * @param offsetDeg   turn of the running direction from the field's direction [degrees]
     */
    public record Component(double wavelength, double periodTicks, double weight, double phase, double offsetDeg) {

        public double k() {
            return 2.0 * Math.PI / wavelength;
        }

        public double omega() {
            return 2.0 * Math.PI / periodTicks;
        }
    }

    /**
     * Where the field's time and space start: {@code t − ticks} and {@code p − (x, z)} enter the phases.
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
     * The direction the waves run toward and the crossfade from the previous one (WAV7): until {@code fadeStart} the
     * sea runs toward {@code previous}, over the next {@code fadeTicks} it crossfades (smoothstep) to the sea running
     * toward {@code current}, and from then on it is that sea alone. Both seas are stationary, so the field is
     * continuous in time and the same at the same point for everyone.
     *
     * @param current   compass bearing the waves run toward after the fade [degrees]
     * @param previous  compass bearing before it [degrees]
     * @param fadeStart game time the fade starts at [ticks]
     * @param fadeTicks length of the fade [ticks]; ≤ 0 switches at {@code fadeStart}
     */
    public record Heading(double current, double previous, double fadeStart, double fadeTicks) {

        /** A sea running toward {@code directionDeg} with no fade. */
        public static Heading steady(double directionDeg) {
            return new Heading(directionDeg, directionDeg, Double.NEGATIVE_INFINITY, 0.0);
        }

        /** Share of the {@link #current} sea at game time {@code t}: 0 before the fade, 1 after it, a smoothstep between. */
        public double fade(double t) {
            if (current == previous || !(t < fadeStart + fadeTicks)) {
                return 1.0;
            }
            if (t <= fadeStart) {
                return 0.0;
            }
            double s = (t - fadeStart) / fadeTicks;
            return s * s * (3.0 - 2.0 * s);
        }

        /** Whether the fade is still running (or not yet started) at {@code t}. */
        public boolean fading(double t) {
            return fade(t) < 1.0;
        }
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

        /** Wavenumber [rad/block] and angular frequency [rad/tick] of envelope term {@code i} (0 or 1). */
        public double k(int i) {
            return omega(i) / SPEED;
        }

        public double omega(int i) {
            return 2.0 * Math.PI / (periodTicks * (i == 0 ? FIRST : SECOND));
        }

        /** Phase of envelope term {@code i} [rad]. */
        public double phase(int i) {
            return i == 0 ? 0.9 : 4.1;
        }
    }

    /** The default sea: {@link WaveSpectrum#DEFAULT_COMPONENTS} trains peaking at the 34-block swell. */
    public static final List<Component> COMPONENTS = WaveSpectrum.components(WaveSpectrum.DEFAULT_COMPONENTS,
            WaveSpectrum.REFERENCE_WAVELENGTH);

    /** A flat sea. */
    public static final WaveField FLAT = new WaveField(0.0, 0.0);

    private final double amplitude;
    private final Heading heading;
    private final List<Component> components;
    private final Origin origin;
    private final Groups groups;
    // envelope terms: k, omega, phase (running east)
    private final double[][] g;
    // per component: k, omega, weight, phase, dir x/z; for the current and the previous direction
    private final double[][] c;
    private final double[][] cPrev;

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

    /** A field whose time and space are measured from {@code origin} (tests; gameplay uses {@link Origin#NONE}). */
    public WaveField(double amplitude, double directionDeg, List<Component> components, Origin origin) {
        this(amplitude, directionDeg, components, origin, Groups.NONE);
    }

    /** A steady field with wave groups ({@link Groups}). */
    public WaveField(double amplitude, double directionDeg, List<Component> components, Origin origin, Groups groups) {
        this(amplitude, Heading.steady(directionDeg), components, origin, groups);
    }

    /** The full constructor: a field whose direction may be crossfading ({@link Heading}). */
    public WaveField(double amplitude, Heading heading, List<Component> components, Origin origin, Groups groups) {
        this.origin = origin;
        this.groups = groups;
        this.g = new double[groups.depth() > 0.0 ? 2 : 0][];
        for (int i = 0; i < g.length; i++) {
            g[i] = new double[] {groups.k(i), groups.omega(i), groups.phase(i)};
        }
        this.amplitude = Math.max(0.0, amplitude);
        this.heading = heading;
        this.components = List.copyOf(components);
        this.c = trains(components, heading.current());
        this.cPrev = heading.current() == heading.previous() ? c : trains(components, heading.previous());
    }

    private static double[][] trains(List<Component> components, double directionDeg) {
        double[][] out = new double[components.size()][];
        for (int i = 0; i < out.length; i++) {
            Component comp = components.get(i);
            double dir = Math.toRadians(directionDeg + comp.offsetDeg());
            out[i] = new double[] {comp.k(), comp.omega(), comp.weight(), comp.phase(), Math.sin(dir), -Math.cos(dir)};
        }
        return out;
    }

    public double amplitude() {
        return amplitude;
    }

    /** The direction the waves run toward once any fade is over (compass bearing) [degrees]. */
    public double directionDegrees() {
        return heading.current();
    }

    public Heading heading() {
        return heading;
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

    /**
     * The largest height this field can reach [blocks]: {@code A · (1 + group depth)}. A crossfade mixes two seas of
     * this bound with weights summing to 1, so it holds through a fade too.
     */
    public double maxHeight() {
        return amplitude * (1.0 + (g.length > 0 ? groups.depth() : 0.0));
    }

    /**
     * The group envelope at {@code (x, z)} and time {@code t} (1 without groups): between {@code 1 − depth} and
     * {@code 1 + depth}. The groups run east, so only x enters.
     */
    public double envelope(double x, double z, double t) {
        return envelopeAt(x - origin.x(), t - origin.ticks());
    }

    private double envelopeAt(double ox, double ot) {
        if (g.length == 0) {
            return 1.0;
        }
        double s = 0.0;
        for (double[] e : g) {
            s += Math.sin(e[0] * ox - e[1] * ot + e[2]);
        }
        return 1.0 + groups.depth() * 0.5 * s;
    }

    /** {@code ∂E/∂x} of the envelope [1/block]. */
    private double envelopeSlopeAt(double ox, double ot) {
        double s = 0.0;
        for (double[] e : g) {
            s += e[0] * Math.cos(e[0] * ox - e[1] * ot + e[2]);
        }
        return groups.depth() * 0.5 * s;
    }

    public boolean isFlat() {
        return amplitude <= 0.0;
    }

    /** Height of the sea above still water at {@code (x, z)} and game time {@code t} [blocks]: the one sea of WAV7. */
    public double heightAt(double x, double z, double t) {
        if (isFlat()) {
            return 0.0;
        }
        double ox = x - origin.x(), oz = z - origin.z(), ot = t - origin.ticks();
        return amplitude * envelopeAt(ox, ot) * mixed(ox, oz, ot, t);
    }

    /** {@code (1 − u) S(d_old) + u S(d_new)}: the trains' sum, without amplitude and envelope. */
    private double mixed(double ox, double oz, double ot, double t) {
        double u = heading.fade(t);
        double s = u > 0.0 ? u * sum(c, ox, oz, ot) : 0.0;
        if (u < 1.0) {
            s += (1.0 - u) * sum(cPrev, ox, oz, ot);
        }
        return s;
    }

    private static double sum(double[][] trains, double ox, double oz, double ot) {
        double h = 0.0;
        for (double[] w : trains) {
            h += w[2] * Math.sin(phase(w, ox, oz, ot));
        }
        return h;
    }

    /** Gradient {@code {∂h/∂x, ∂h/∂z}} of the surface at {@code (x, z)}, time {@code t} [blocks per block]. */
    public double[] slopeAt(double x, double z, double t) {
        double[] out = new double[2];
        if (isFlat()) {
            return out;
        }
        double ox = x - origin.x(), oz = z - origin.z(), ot = t - origin.ticks();
        double e = envelopeAt(ox, ot);
        double u = heading.fade(t);
        if (u > 0.0) {
            gradient(c, ox, oz, ot, amplitude * e * u, out);
        }
        if (u < 1.0) {
            gradient(cPrev, ox, oz, ot, amplitude * e * (1.0 - u), out);
        }
        if (g.length > 0) {
            out[0] += amplitude * envelopeSlopeAt(ox, ot) * mixed(ox, oz, ot, t);
        }
        return out;
    }

    private static void gradient(double[][] trains, double ox, double oz, double ot, double scale, double[] out) {
        for (double[] w : trains) {
            double s = scale * w[2] * w[0] * Math.cos(phase(w, ox, oz, ot));
            out[0] += s * w[4];
            out[1] += s * w[5];
        }
    }

    /**
     * The largest surface slope this field can reach [blocks per block]: {@code A · (1 + depth) · Σ wᵢ kᵢ} from the
     * trains plus {@code A · depth · ½ (k₁ + k₂)} from the group envelope's own slope.
     */
    public double maxSlope() {
        double s = 0.0;
        for (double[] w : c) {
            s += w[2] * w[0];
        }
        double env = 0.0;
        for (double[] e : g) {
            env += e[0];
        }
        return maxHeight() * s + amplitude * groups.depth() * 0.5 * env;
    }

    private static double phase(double[] w, double ox, double oz, double ot) {
        return w[0] * (w[4] * ox + w[5] * oz) - w[1] * ot + w[3];
    }

    @Override
    public String toString() {
        return Num.format("WaveField[amplitude %.2f, toward %.0f°%s, %d trains, groups ±%.2f]", amplitude, heading.current(),
                heading.current() == heading.previous() ? "" : Num.format(" (from %.0f°)", heading.previous()),
                components.size(), g.length > 0 ? groups.depth() : 0.0);
    }
}
