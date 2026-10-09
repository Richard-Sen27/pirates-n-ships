package com.richardsenger.piratesnships.hazards.waves;

import java.util.ArrayList;
import java.util.List;

/**
 * The wave spectrum of the simulated sea (docs/design.md §5.4, WAV2): which sine trains make up a {@link WaveField}.
 * Pure and deterministic: the trains come from a fixed seed, never from game randomness, so the server and every client
 * build the same sea from the same three numbers (the train count, the peak wavelength and the field's direction).
 *
 * <h2>Trains</h2>
 * {@code count} trains spread over the band {@link #MIN_WAVELENGTH}–{@link #MAX_WAVELENGTH} in equal steps of
 * {@code ln λ}, each jittered within its step; the train nearest {@link #REFERENCE_WAVELENGTH} is snapped onto it
 * (34 blocks, 9 s: WV1's swell) and runs exactly along the field's direction. The others run at a fixed offset within
 * ±{@link #DIRECTION_SPREAD_DEG} of it, with a fixed phase. Periods follow deep-water dispersion,
 * {@code T = sqrt(2π λ / g_eff)}, with {@link #G_EFF} chosen so that 34 blocks give exactly 9 s; longer waves run faster.
 *
 * <h2>Weights</h2>
 * The amplitude of train {@code i} is {@code sqrt(S(ωᵢ) · Δωᵢ)} under a JONSWAP spectrum (Pierson–Moskowitz with the
 * peak enhancement {@link #PEAK_ENHANCEMENT} of a sea still building under the wind),
 * {@code S(ω) = ω⁻⁵ · exp(−5/4 · (ωp/ω)⁴) · γ^exp(−(ω − ωp)² / (2σ²ωp²))}, normalised so the weights sum to 1 (so
 * {@code |Σ wᵢ sin| ≤ 1}). The peak wavelength depends on the sea state ({@link #peakWavelength}): a calm sea's energy
 * sits in the shorter trains, a storm's in the 34-block swell. Only the weights change with the state, never a train's
 * wavelength, period or phase, so easing from one state to another changes the surface continuously.
 */
public final class WaveSpectrum {

    /** The swell WV1 tuned the roll on: kept as one of the trains at every count. [blocks] */
    public static final double REFERENCE_WAVELENGTH = 34.0;
    /** Its period. [s] */
    public static final double REFERENCE_PERIOD_SECONDS = 9.0;
    /** Effective gravity of the dispersion relation, {@code 2π · 34 / 9²} [blocks/s²]. */
    public static final double G_EFF = 2.0 * Math.PI * REFERENCE_WAVELENGTH / (REFERENCE_PERIOD_SECONDS * REFERENCE_PERIOD_SECONDS);
    /** Shortest and longest train of the band [blocks]. */
    public static final double MIN_WAVELENGTH = 16.0;
    public static final double MAX_WAVELENGTH = 64.0;
    /** Largest turn of a train's running direction from the field's direction [degrees]. */
    public static final double DIRECTION_SPREAD_DEG = 30.0;
    /** JONSWAP peak enhancement γ and the widths σ below and above the peak frequency. */
    public static final double PEAK_ENHANCEMENT = 3.3;
    public static final double SIGMA_LOW = 0.07;
    public static final double SIGMA_HIGH = 0.09;
    /** Bounds and default of {@code waves.components}. */
    public static final int MIN_COMPONENTS = 2;
    public static final int MAX_COMPONENTS = 12;
    public static final int DEFAULT_COMPONENTS = 6;
    /** Seed of the trains' jitter, directions and phases. Changing it changes every sea. */
    public static final long SEED = 0x5EA5_7A7E_0002L;
    /** Share of a step of {@code ln λ} a train may move off its step's middle. */
    private static final double JITTER = 0.35;

    /** Peak wavelength per sea state amplitude (calm, moderate, rough, storm) [blocks]. */
    private static final double[] PEAK_AMPLITUDES = {
            SeaState.CALM.amplitude(), SeaState.MODERATE.amplitude(), SeaState.ROUGH.amplitude(), SeaState.STORM.amplitude()};
    private static final double[] PEAK_WAVELENGTHS = {28.0, 30.0, 32.0, REFERENCE_WAVELENGTH};

    /**
     * One train without its weight.
     *
     * @param wavelength [blocks]
     * @param periodTicks [ticks]
     * @param phase [rad]
     * @param offsetDeg turn from the field's direction [degrees]
     */
    public record Train(double wavelength, double periodTicks, double phase, double offsetDeg) {
    }

    @SuppressWarnings("unchecked")
    private static final List<Train>[] TRAINS = new List[MAX_COMPONENTS + 1];

    private WaveSpectrum() {
    }

    /** Clamps a configured train count to {@link #MIN_COMPONENTS}..{@link #MAX_COMPONENTS}. */
    public static int clampCount(int count) {
        return Math.max(MIN_COMPONENTS, Math.min(MAX_COMPONENTS, count));
    }

    /** Deep-water period of a wave of {@code wavelength} blocks [ticks]. */
    public static double periodTicks(double wavelength) {
        return 20.0 * Math.sqrt(2.0 * Math.PI * wavelength / G_EFF);
    }

    /**
     * Peak wavelength of a sea whose state amplitude (before {@code waves.amplitude}) is {@code stateAmplitude}:
     * piecewise linear through calm 28, moderate 30, rough 32 and storm 34 blocks, held outside.
     */
    public static double peakWavelength(double stateAmplitude) {
        if (!(stateAmplitude > PEAK_AMPLITUDES[0])) {
            return PEAK_WAVELENGTHS[0];
        }
        for (int i = 1; i < PEAK_AMPLITUDES.length; i++) {
            if (stateAmplitude <= PEAK_AMPLITUDES[i]) {
                double f = (stateAmplitude - PEAK_AMPLITUDES[i - 1]) / (PEAK_AMPLITUDES[i] - PEAK_AMPLITUDES[i - 1]);
                return PEAK_WAVELENGTHS[i - 1] + f * (PEAK_WAVELENGTHS[i] - PEAK_WAVELENGTHS[i - 1]);
            }
        }
        return PEAK_WAVELENGTHS[PEAK_WAVELENGTHS.length - 1];
    }

    /** The {@code count} trains (clamped), shortest first. The same list for the same count, on every machine. */
    public static synchronized List<Train> trains(int count) {
        int n = clampCount(count);
        List<Train> cached = TRAINS[n];
        if (cached == null) {
            cached = buildTrains(n);
            TRAINS[n] = cached;
        }
        return cached;
    }

    private static List<Train> buildTrains(int n) {
        long[] state = {SEED ^ (n * 0xD1B54A32D192ED03L)};
        double lo = Math.log(MIN_WAVELENGTH), step = (Math.log(MAX_WAVELENGTH) - lo) / n;
        double[] logs = new double[n];
        double[] phases = new double[n];
        double[] offsets = new double[n];
        int ref = 0;
        double refLog = Math.log(REFERENCE_WAVELENGTH);
        for (int i = 0; i < n; i++) {
            double jitter = next(state) * 2.0 - 1.0;
            phases[i] = next(state) * 2.0 * Math.PI;
            offsets[i] = (next(state) * 2.0 - 1.0) * DIRECTION_SPREAD_DEG;
            logs[i] = lo + (i + 0.5 + JITTER * jitter) * step;
            if (Math.abs(logs[i] - refLog) < Math.abs(logs[ref] - refLog)) {
                ref = i;
            }
        }
        List<Train> trains = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double wavelength = i == ref ? REFERENCE_WAVELENGTH : Math.exp(logs[i]);
            double offset = i == ref ? 0.0 : offsets[i];
            trains.add(new Train(wavelength, periodTicks(wavelength), phases[i], offset));
        }
        return List.copyOf(trains);
    }

    /**
     * The weighted components of a sea of {@code count} trains peaking at {@code peakWavelength}; the weights sum to 1.
     * Each component's anchor reference bearing is 90° (east) turned by its offset.
     */
    public static List<WaveField.Component> components(int count, double peakWavelength) {
        List<Train> trains = trains(count);
        double[] w = weights(trains, peakWavelength);
        List<WaveField.Component> out = new ArrayList<>(trains.size());
        for (int i = 0; i < trains.size(); i++) {
            Train t = trains.get(i);
            out.add(new WaveField.Component(t.wavelength(), t.periodTicks(), w[i], t.phase(), t.offsetDeg(), 90.0 + t.offsetDeg()));
        }
        return out;
    }

    /** Normalised amplitude weights of {@code trains} under the spectrum peaking at {@code peakWavelength}. */
    public static double[] weights(List<Train> trains, double peakWavelength) {
        double peak = Math.max(1.0, peakWavelength);
        double[] w = new double[trains.size()];
        double sum = 0.0;
        for (int i = 0; i < w.length; i++) {
            // ω / ωp = sqrt(λp / λ); with equal steps of ln λ, Δω is proportional to ω
            double r = Math.sqrt(peak / trains.get(i).wavelength());
            w[i] = Math.sqrt(density(r) * r);
            sum += w[i];
        }
        for (int i = 0; i < w.length; i++) {
            w[i] = sum > 0.0 ? w[i] / sum : 1.0 / w.length;
        }
        return w;
    }

    /** JONSWAP density at the frequency ratio {@code r = ω / ωp}, with {@code ωp = 1}. */
    public static double density(double r) {
        if (!(r > 0.0)) {
            return 0.0;
        }
        double pm = Math.pow(r, -5.0) * Math.exp(-1.25 * Math.pow(r, -4.0));
        double sigma = r <= 1.0 ? SIGMA_LOW : SIGMA_HIGH;
        double peak = Math.pow(PEAK_ENHANCEMENT, Math.exp(-(r - 1.0) * (r - 1.0) / (2.0 * sigma * sigma)));
        return pm * peak;
    }

    /** SplitMix64 step, as a number in [0, 1): fixed and the same on every JVM. */
    private static double next(long[] state) {
        long z = state[0] += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }
}
